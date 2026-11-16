package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.audit.Sha256
import ir.vil3ntec.tohid.core.gateway.SimpleAccount
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import ir.vil3ntec.tohid.pay.platform.ShopQrPicture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 *  e-paystep — عکسِ QR ِ حساب‌پیِ دکان (صاحب‌کار ۱۴۰۵/۰۸/۲۶): «انتخابِ عکسِ QR» ِ کامپیوتر (`ShopQrPictureDesk`) عکسِ بزرگ و شفاف را
 *  کوچک، روی سفید و بی فراداده می‌کند و متنِ QR را از **خودِ عکسِ کوچک‌شده** می‌خواند؛ عکسِ بی QR و فایلِ غیرِ عکس رد. صندوق
 *  (`HesabPayEnv.qrPicture`) عکسِ ذخیره را فقط وقتی نشان می‌دهد که دوباره همان متنِ QR ِ حساب خوانده شد (سنجشِ هش: `HesabQrImageTest`).
 */
class ShopQrPictureTest {

  private val payload = "HP|+93799111222|shop"
  private val dir get() = File(DesktopContext.filesDir, "hesabpay-qr")

  @After fun down() { dir.deleteRecursively(); HesabPayEnv.of(DesktopContext).forget() }

  /** «اسکرین‌شاتِ» QR: خانه‌های سیاه روی زمینهٔ شفاف، با حاشیه */
  private fun shot(text: String = payload, cell: Int = 18, alpha: Boolean = true): ByteArray {
    val rows = QrCode.encode(text, QrCode.LARGE_MAX_VERSION)
    val n = rows.size
    val side = (n + 8) * cell
    val img = BufferedImage(side, side, if (alpha) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    if (!alpha) { g.color = Color.WHITE; g.fillRect(0, 0, side, side) }
    g.color = Color.BLACK
    for (y in 0 until n) for (x in 0 until n) if (rows[y][x]) g.fillRect((x + 4) * cell, (y + 4) * cell, cell, cell)
    g.dispose()
    return ByteArrayOutputStream().use { ImageIO.write(img, "png", it); it.toByteArray() }
  }

  @Test fun aLargeTransparentScreenshotBecomesASmallCleanJpegAndItsQrIsRead() = runBlocking {
    val big = shot()
    val r = ShopQrPicture.prepare(big) as ShopQrPicture.Prep.Ok
    assertEquals(payload, r.payload)
    val out = ImageIO.read(r.jpeg.inputStream())
    assertTrue("ضلعِ بلند ≤ ۱۰۲۴: ${out.width}", maxOf(out.width, out.height) <= ShopQrPicture.MAX_SIDE)
    assertTrue("JPEG", r.jpeg[0] == 0xFF.toByte() && r.jpeg[1] == 0xD8.toByte())
    assertFalse("بی EXIF", String(r.jpeg, Charsets.ISO_8859_1).contains("Exif"))
    //  زمینهٔ شفاف روی سفید (نه سیاه): گوشهٔ بالا-چپ روشن است
    assertTrue(Color(out.getRGB(2, 2)).red > 200)
  }

  @Test fun aPictureWithoutAQrOrNotAPictureIsRefused() = runBlocking {
    val blank = BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB).also { val g = it.createGraphics(); g.color = Color.WHITE; g.fillRect(0, 0, 300, 300); g.dispose() }
    val png = ByteArrayOutputStream().use { ImageIO.write(blank, "png", it); it.toByteArray() }
    assertEquals(ShopQrPicture.Prep.NoQr, ShopQrPicture.prepare(png))
    assertEquals(ShopQrPicture.Prep.NotImage, ShopQrPicture.prepare("not a picture at all".toByteArray()))
  }

  @Test fun theTillShowsTheStoredPictureOnlyWhenItReadsAsTheAccountsOwnQr() = runBlocking {
    val jpeg = (ShopQrPicture.prepare(shot(alpha = false)) as ShopQrPicture.Prep.Ok).jpeg
    val hash = Sha256.hex(Sha256.digest(jpeg))
    dir.mkdirs()
    File(dir, hash).writeBytes(jpeg)
    val env = HesabPayEnv.of(DesktopContext)
    assertNotNull(env.qrPicture(SimpleAccount("0799111222", payload, hash)))
    //  متنِ حساب چیزِ دیگری است (عکسِ QR ِ کسِ دیگر) ⇒ هیچ عکس، همان QR ِ کشیده
    assertNull(env.qrPicture(SimpleAccount("0799111222", "HP|+93700999888|other", hash)))
    //  بی عکس یا بی متنِ QR ⇒ هیچ
    assertNull(env.qrPicture(SimpleAccount("0799111222", payload, "")))
    assertNull(env.qrPicture(SimpleAccount("0799111222", "", hash)))
  }
}
