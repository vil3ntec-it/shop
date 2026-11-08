package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.image.ImageQuality
import ir.vil3ntec.tohid.core.image.PhotoThumb
import ir.vil3ntec.tohid.core.shopper.ShopperPhoto
import ir.vil3ntec.tohid.data.PhotoStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random

/**
 *  WEB-15 — `PhotoStore.thumb` روی JPEG ِ واقعی (رمزگشا و رمزگذارِ همان پوستهٔ کامپیوتر، ImageIO): همان بایت‌هایی که
 *  سرور می‌سنجد (`product-photos.checkThumb`: نوعِ واقعی، ≤ ۴۰۰px، ≤ ۶۴KB، بی EXIF orientation، نسبتِ اصل).
 */
class PhotoThumbDeskTest {

  /** ۸۰۰×۶۰۰ آبی با مربعِ قرمزِ ۱۲۰ پیکسلی در بالا-چپ */
  private fun marked(w: Int = 800, h: Int = 600, noise: Boolean = false): BufferedImage {
    val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val rnd = Random(7)
    for (y in 0 until h) for (x in 0 until w) {
      img.setRGB(x, y, when {
        x < 120 && y < 120 -> 0xFF0000
        noise -> rnd.nextInt(0xFFFFFF)
        else -> 0x2040C0
      })
    }
    return img
  }

  private fun jpeg(img: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()

  /** همان JPEG با قطعهٔ APP1 ِ EXIF (فقط تگِ orientation) پس از SOI — مثلِ عکسِ دوربینِ گوشی */
  private fun withExifOrientation(jpeg: ByteArray, orientation: Int): ByteArray {
    val tiff = byteArrayOf(
      0x4D, 0x4D, 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08,          // MM، IFD در ۸
      0x00, 0x01,                                              // یک مدخل
      0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01,          // 0x0112 SHORT ×۱
      0x00, orientation.toByte(), 0x00, 0x00,
      0x00, 0x00, 0x00, 0x00,                                  // IFD ِ بعدی نیست
    )
    val body = "Exif".toByteArray() + byteArrayOf(0, 0) + tiff
    val len = body.size + 2
    val app1 = byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (len shr 8).toByte(), len.toByte()) + body
    return jpeg.copyOfRange(0, 2) + app1 + jpeg.copyOfRange(2, jpeg.size)
  }

  private fun read(b: ByteArray): BufferedImage = checkNotNull(ImageIO.read(b.inputStream()))

  private fun isRed(c: Int) = ((c shr 16) and 255) > 180 && ((c shr 8) and 255) < 90 && (c and 255) < 90
  private fun isBlue(c: Int) = (c and 255) > 140 && ((c shr 16) and 255) < 90

  private fun assertServerShape(t: ByteArray) {
    assertTrue("JPEG", t.size > 100 && t[0] == 0xFF.toByte() && t[1] == 0xD8.toByte() && t[2] == 0xFF.toByte())
    assertTrue("≤ سقفِ برنامه: ${t.size}", t.size <= PhotoThumb.MAX_BYTES)
    assertEquals("بی EXIF orientation", 1, ShopperPhoto.orientationOf(t))
    assertFalse("بی قطعهٔ Exif", String(t, Charsets.ISO_8859_1).contains("Exif\u0000\u0000"))
    //  همان سنجهٔ کیفیتِ برنامه (جدولِ DQT): هرگز «فشرده‌سازیِ زیاد»
    val q = ImageQuality.measure(t).quality
    assertTrue("کیفیتِ JPEG: $q", q != null && q >= ImageQuality.FLOOR)
  }

  @Test fun anUprightPhotoBecomesA320CardWithTheSameAspect() {
    val t = checkNotNull(PhotoStore.thumb(jpeg(marked())))
    assertServerShape(t)
    val img = read(t)
    assertEquals(listOf(320, 240), listOf(img.width, img.height))
    assertTrue("قرمز همان بالا-چپ", isRed(img.getRGB(8, 8)))
    assertTrue(isBlue(img.getRGB(img.width - 8, 8)))
    //  عمودی هم
    val tall = read(checkNotNull(PhotoStore.thumb(jpeg(marked(600, 800)))))
    assertEquals(listOf(240, 320), listOf(tall.width, tall.height))
  }

  @Test fun exifOrientationIsBakedIntoThePixels() {
    val src = withExifOrientation(jpeg(marked()), 6)
    assertEquals("سنجشِ خودِ آزمون", 6, ShopperPhoto.orientationOf(src))
    val t = checkNotNull(PhotoStore.thumb(src))
    assertServerShape(t)
    val img = read(t)
    assertEquals("۹۰° ساعت‌گرد ⇒ ایستاده", listOf(240, 320), listOf(img.width, img.height))
    assertTrue("بالا-چپ ِ اصل ⇒ بالا-راست", isRed(img.getRGB(img.width - 8, 8)))
    assertTrue(isBlue(img.getRGB(8, 8)))
    //  ۳ (۱۸۰°) ⇒ پایین-راست
    val r3 = read(PhotoStore.thumb(withExifOrientation(jpeg(marked()), 3))!!)
    assertEquals(listOf(320, 240), listOf(r3.width, r3.height))
    assertTrue(isRed(r3.getRGB(r3.width - 8, r3.height - 8)))
  }

  @Test fun aBusyPhotoStillFitsTheCap() {
    //  نویزِ کامل (بدترین حالتِ JPEG) هم زیرِ سقف می‌رود — با کیفیتِ کمتر یا ضلعِ کوچک‌تر، نه بزرگ‌تر از سقف
    val t = checkNotNull(PhotoStore.thumb(jpeg(marked(800, 800, noise = true))))
    assertServerShape(t)
    val img = read(t)
    assertTrue("${img.width}", img.width in PhotoThumb.MIN_EDGE..PhotoThumb.EDGE && img.width == img.height)
    //  عکسِ عادیِ کالا (زمینهٔ یک‌دست) بسیار کمتر از سقف
    assertTrue(PhotoStore.thumb(jpeg(marked()))!!.size < 12 * 1024)
  }

  @Test fun transparencyLandsOnWhiteAndJunkGivesNoThumb() {
    val png = BufferedImage(400, 400, BufferedImage.TYPE_INT_ARGB)
    val g = png.createGraphics()
    g.color = Color(0, 0, 0, 0); g.fillRect(0, 0, 400, 400)
    g.color = Color.RED; g.fillRect(150, 150, 100, 100); g.dispose()
    val bytes = ByteArrayOutputStream().also { ImageIO.write(png, "png", it) }.toByteArray()
    val img = read(PhotoStore.thumb(bytes)!!)
    assertEquals(listOf(320, 320), listOf(img.width, img.height))
    val corner = img.getRGB(4, 4)
    assertTrue("شفاف ⇒ سفید، نه سیاه", ((corner shr 16) and 255) > 240 && ((corner shr 8) and 255) > 240 && (corner and 255) > 240)
    assertTrue(isRed(img.getRGB(160, 160)))
    assertNull(PhotoStore.thumb(ByteArray(300) { 7 }))
    assertNull(PhotoStore.thumb(ByteArray(0)))
  }
}
