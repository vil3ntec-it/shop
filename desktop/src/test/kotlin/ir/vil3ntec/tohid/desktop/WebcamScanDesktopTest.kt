package ir.vil3ntec.tohid.desktop

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import ir.vil3ntec.tohid.scan.BarcodeGuard
import ir.vil3ntec.tohid.scan.CodeKind
import ir.vil3ntec.tohid.scan.WebcamDecoder
import ir.vil3ntec.tohid.scan.WebcamLoop
import ir.vil3ntec.tohid.scan.WebcamPlatform
import java.awt.Color
import java.awt.image.BufferedImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  HW-08 — وب‌کمِ کامپیوتر به‌عنوانِ بارکدخوان، بی دوربین: فریمِ ساختگی با بارکدِ واقعی (نوشتهٔ خودِ ZXing، با حاشیه و
 *  مثلِ تصویرِ وب‌کم بزرگ‌تر از بارکد) ⇒ رمزگشایی، ردهٔ رقمِ کنترلی، QR فقط وقتی خواسته، و سدِ خوانشِ نصفه و تکرارِ
 *  گوشی (`BarcodeGuard`) با آهنگِ ده فریم در ثانیه.
 */
class WebcamScanDesktopTest {

  /** بارکد وسطِ یک فریمِ ۶۴۰×۴۸۰ ِ خاکستری، مثلِ کالایی که جلوی وب‌کم گرفته شده */
  private fun frame(text: String, format: BarcodeFormat, w: Int = 360, h: Int = 160): BufferedImage {
    val m = MultiFormatWriter().encode(text, format, w, h)
    val img = BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.color = Color(200, 200, 200); g.fillRect(0, 0, 640, 480)
    val ox = (640 - m.width) / 2
    val oy = (480 - m.height) / 2
    for (y in 0 until m.height) for (x in 0 until m.width) {
      img.setRGB(ox + x, oy + y, if (m.get(x, y)) 0x111111 else 0xF5F5F5)
    }
    g.dispose()
    return img
  }

  private val sale = WebcamDecoder.formats(qr = false, qrOnly = false)

  @Test fun `HW-08 EAN-13 از فریمِ وب‌کم خوانده می‌شود و رقمِ کنترلی دارد`() {
    val r = WebcamDecoder.read(frame("5901234123457", BarcodeFormat.EAN_13), sale)
    assertEquals("5901234123457", r?.text)
    assertEquals(CodeKind.CHECKED, r?.kind)
  }

  @Test fun `HW-08 CODE-128 و ITF با ردهٔ خودشان`() {
    val c = WebcamDecoder.read(frame("ABC-1234", BarcodeFormat.CODE_128), sale)
    assertEquals("ABC-1234", c?.text)
    assertEquals(CodeKind.OTHER, c?.kind)
    val i = WebcamDecoder.read(frame("12345678901231", BarcodeFormat.ITF), sale)
    assertEquals("12345678901231", i?.text)
    assertEquals(CodeKind.ITF, i?.kind)
  }

  @Test fun `HW-08 QR فقط وقتی صفحه خواسته و فریمِ بی بارکد هیچ`() {
    val qr = frame("https://vill3n.top/s/abc", BarcodeFormat.QR_CODE, 240, 240)
    assertNull(WebcamDecoder.read(qr, sale))
    assertEquals("https://vill3n.top/s/abc", WebcamDecoder.read(qr, WebcamDecoder.formats(qr = true, qrOnly = false))?.text)
    assertEquals("https://vill3n.top/s/abc", WebcamDecoder.read(qr, WebcamDecoder.formats(qr = false, qrOnly = true))?.text)
    val blank = BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB)
    assertNull(WebcamDecoder.read(blank, sale))
  }

  @Test fun `HW-08 کدِ ناشناس دو فریم می‌خواهد و فریمِ زودتر از صد میلی‌ثانیه رمزگشایی نمی‌شود`() {
    var decodes = 0
    val loop = WebcamLoop(qrOnly = false, guard = BarcodeGuard()) { decodes++; WebcamDecoder.Read("5901234123457", CodeKind.CHECKED) }
    val f = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
    assertNull(loop.offer(f, 1_000))
    assertNull(loop.offer(f, 1_050))
    assertEquals(1, decodes)
    assertEquals("5901234123457", loop.offer(f, 1_100))
    assertEquals(2, decodes)
  }

  @Test fun `HW-08 کالای دکان از همان فریمِ اول و خوانشِ نصفهٔ ITF هرگز`() {
    val f = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
    val known = WebcamLoop(qrOnly = false, guard = BarcodeGuard(known = { it == "5901234123457" })) {
      WebcamDecoder.Read("5901234123457", CodeKind.CHECKED)
    }
    assertEquals("5901234123457", known.offer(f, 1_000))
    val half = WebcamLoop(qrOnly = false, guard = BarcodeGuard()) { WebcamDecoder.Read("49495030626", CodeKind.ITF) }
    for (t in 0 until 20) assertNull(half.offer(f, 1_000L + t * 100))
  }

  @Test fun `HW-08 QR ِ تنها بی سدِ بارکد و کامپیوتر وب‌کم را می‌شناسد`() {
    val f = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
    val loop = WebcamLoop(qrOnly = true, guard = BarcodeGuard()) { WebcamDecoder.Read("shop-link", CodeKind.OTHER) }
    assertEquals("shop-link", loop.offer(f, 1_000))
    assertTrue(WebcamPlatform.supported)
  }
}
