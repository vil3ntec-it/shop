package ir.vil3ntec.tohid.desktop

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import javax.imageio.ImageIO

/**
 *  پوستهٔ `BitmapFactory` روی کامپیوتر (IMG-PRO-09): بمبِ فشرده برنامهٔ ویندوز را از
 *  حافظه خالی نمی‌کند.
 *
 *  • `inJustDecodeBounds` فقط سرآیند را می‌خواند — PNG با IHDRِ درست و IDATِ خراب
 *    باید ابعاد بدهد (پوستهٔ پیشین کلِ تصویر را می‌خواند، خطا می‌گرفت و ۰ می‌داد).
 *  • رمزگشاییِ بی نمونه‌گیری بیش از ۶۴ مگاپیکسل ⇒ null.
 *  • `inSampleSize` تصویرِ بزرگ را با حافظهٔ محدود و ابعادِ درست می‌خواند؛ رنگ و جهت می‌مانند.
 */
class BitmapFactoryShimTest {

  /**
   *  PNGِ خاکستری با IDATِ درست (یا خرابِ عمدی) — بی ساختنِ BufferedImageِ بزرگ.
   *  پیش‌فرض همه‌صفر؛ `stripes` ستون‌های یک‌در‌میان سیاه/سفید (فقط ۸ بیتی).
   */
  private fun grayPng(w: Int, h: Int, bitDepth: Int = 8, brokenIdat: Boolean = false, stripes: Boolean = false): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
    fun chunk(type: String, data: ByteArray) {
      val len = data.size
      out.write(byteArrayOf((len ushr 24).toByte(), (len ushr 16).toByte(), (len ushr 8).toByte(), len.toByte()))
      val td = type.toByteArray(Charsets.ISO_8859_1) + data
      out.write(td)
      val crc = CRC32().apply { update(td) }.value
      out.write(byteArrayOf((crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte()))
    }
    val ihdr = ByteArray(13)
    for (i in 0 until 4) { ihdr[i] = (w ushr (24 - 8 * i)).toByte(); ihdr[4 + i] = (h ushr (24 - 8 * i)).toByte() }
    ihdr[8] = bitDepth.toByte(); ihdr[9] = 0
    chunk("IHDR", ihdr)
    val idat = if (brokenIdat) ByteArray(64) { 0x5a } else {
      val z = ByteArrayOutputStream()
      DeflaterOutputStream(z, Deflater(Deflater.BEST_COMPRESSION)).use { d ->
        val row = ByteArray(1 + (w * bitDepth + 7) / 8)
        if (stripes) for (x in 0 until w) row[1 + x] = if (x % 2 == 0) 0 else 255.toByte()
        repeat(h) { d.write(row) }
      }
      z.toByteArray()
    }
    chunk("IDAT", idat)
    chunk("IEND", ByteArray(0))
    return out.toByteArray()
  }

  private fun opts(bounds: Boolean = false, sample: Int = 1) =
    BitmapFactory.Options().apply { inJustDecodeBounds = bounds; inSampleSize = sample }

  @Test fun boundsComeFromTheHeaderOnly() {
    val png = grayPng(3000, 2000, brokenIdat = true)
    val o = opts(bounds = true)
    assertNull(BitmapFactory.decodeByteArray(png, 0, png.size, o))
    assertEquals(3000, o.outWidth)
    assertEquals(2000, o.outHeight)
    assertNull("IDATِ خراب رمزگشایی نمی‌شود", BitmapFactory.decodeByteArray(png, 0, png.size, opts()))
    //  عکسِ سالم هم با inJustDecodeBounds هیچ Bitmapی نمی‌دهد
    val ok = grayPng(40, 30)
    val o2 = opts(bounds = true)
    assertNull(BitmapFactory.decodeByteArray(ok, 0, ok.size, o2))
    assertEquals(listOf(40, 30), listOf(o2.outWidth, o2.outHeight))
  }

  @Test fun fullDecodeAboveTheCapIsRefusedButSampledDecodeWorks() {
    val png = grayPng(9000, 8000)                          // ۷۲ مگاپیکسل، چند ده کیلوبایت
    assertTrue(9000L * 8000 > BitmapFactory.MAX_FULL_PIXELS)
    assertNull(BitmapFactory.decodeByteArray(png, 0, png.size))
    assertNull(BitmapFactory.decodeByteArray(png, 0, png.size, opts()))
    val o = opts(sample = 8)
    val bmp = BitmapFactory.decodeByteArray(png, 0, png.size, o)
    assertNotNull(bmp)
    assertEquals(listOf(1125, 1000), listOf(bmp!!.width, bmp.height))
    assertEquals(listOf(1125, 1000), listOf(o.outWidth, o.outHeight))
  }

  /**
   *  سازوکار، نه سلیقه: بالای بودجه (۲۴ مگاپیکسل) نمونه‌گیری **هنگامِ خواندن** است — ستون‌های
   *  یک‌در‌میان با دورهٔ ۲ همه سیاه می‌خوانند (آرایهٔ تمام‌اندازه ساخته نشده)؛ زیرِ بودجه همان
   *  خواندنِ کامل و bicubic ِ پیشین می‌ماند و نتیجه خاکستری است (کیفیتِ عکسِ معمولی همان).
   */
  @Test fun aboveBudgetIsSampledWhileReadingBelowBudgetKeepsBicubic() {
    val big = grayPng(6000, 5000, stripes = true)          // ۳۰ مگاپیکسل
    val b = BitmapFactory.decodeByteArray(big, 0, big.size, opts(sample = 2))!!
    assertEquals(listOf(3000, 2500), listOf(b.width, b.height))
    assertEquals(0, b.getPixel(100, 100) and 0xff)
    val small = grayPng(400, 300, stripes = true)
    val s = BitmapFactory.decodeByteArray(small, 0, small.size, opts(sample = 2))!!
    assertEquals(listOf(200, 150), listOf(s.width, s.height))
    val gray = s.getPixel(100, 75) and 0xff
    assertTrue("خاکستری، نه سیاه: $gray", gray in 60..200)
  }

  @Test fun realBombIsMeasuredWithoutDecodingAndOnlyReadSampled() {
    val bomb = grayPng(20000, 20000, bitDepth = 1)         // ۴۰۰ مگاپیکسل
    assertTrue(bomb.size < 64 * 1024)
    val b = opts(bounds = true)
    BitmapFactory.decodeByteArray(bomb, 0, bomb.size, b)
    assertEquals(listOf(20000, 20000), listOf(b.outWidth, b.outHeight))
    assertNull(BitmapFactory.decodeByteArray(bomb, 0, bomb.size, opts()))
    assertNull(BitmapFactory.decodeByteArray(bomb, 0, bomb.size, opts(sample = 2)))   // ۱۰۰ مگاپیکسل هنوز بیش از سقف
    val small = BitmapFactory.decodeByteArray(bomb, 0, bomb.size, opts(sample = 32))
    assertEquals(listOf(625, 625), listOf(small!!.width, small.height))
  }

  @Test fun sampledDecodeKeepsColourAndOrientation() {
    val img = BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB)
    img.createGraphics().apply {
      color = Color.RED; fillRect(0, 0, 32, 24)
      color = Color.GREEN; fillRect(32, 0, 32, 24)
      color = Color.BLUE; fillRect(0, 24, 32, 24)
      color = Color.WHITE; fillRect(32, 24, 32, 24)
      dispose()
    }
    for (fmt in listOf("png", "jpg")) {
      val bytes = ByteArrayOutputStream().also { ImageIO.write(img, fmt, it) }.toByteArray()
      val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts(sample = 2))!!
      assertEquals(listOf(32, 24), listOf(bmp.width, bmp.height))
      fun near(c: Int, r: Int, g: Int, bl: Int) =
        Math.abs(((c shr 16) and 255) - r) < 40 && Math.abs(((c shr 8) and 255) - g) < 40 && Math.abs((c and 255) - bl) < 40
      assertTrue("$fmt بالا-چپ قرمز", near(bmp.getPixel(4, 4), 255, 0, 0))
      assertTrue("$fmt بالا-راست سبز", near(bmp.getPixel(28, 4), 0, 255, 0))
      assertTrue("$fmt پایین-چپ آبی", near(bmp.getPixel(4, 20), 0, 0, 255))
      assertTrue("$fmt پایین-راست سفید", near(bmp.getPixel(28, 20), 255, 255, 255))
    }
    //  همان خواندنِ سادهٔ پیشین برای عکسِ معمولی
    val plain = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    assertEquals(64, BitmapFactory.decodeByteArray(plain, 0, plain.size)!!.width)
  }
}
