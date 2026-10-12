package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** استودیوی عکس — زمینه سفید می‌شود، کالا می‌ماند، خروجی مربعِ هم‌اندازه است */
class PhotoStudioTest {

  private fun argb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b

  /** کالای قرمز با برچسبِ سفید، روی زمینهٔ خاکستریِ پرنویز و کمی ناهموار */
  private fun sample(w: Int = 320, h: Int = 240): PhotoStudio.Image {
    val rnd = Random(7)
    val px = IntArray(w * h) { i ->
      val x = i % w; val y = i / w
      val base = 150 + (x + y) * 30 / (w + h)
      val n = rnd.nextInt(13) - 6
      val inBody = x in 140..180 && y in 70..195
      val inLabel = x in 146..174 && y in 110..140
      when {
        inLabel -> argb(240, 240, 240)
        inBody -> argb(200 + n, 30, 40)
        else -> argb(base + n, base - 5 + n, base - 12 + n)
      }
    }
    return PhotoStudio.Image(w, h, px)
  }

  private fun rgb(c: Int) = Triple((c shr 16) and 255, (c shr 8) and 255, c and 255)

  @Test fun `زمینه سفید و کالا وسطِ مربعِ ۸۰۰`() {
    val out = PhotoStudio.process(sample())
    assertEquals(800, out.width)
    assertEquals(800, out.height)
    //  گوشه‌ها سفید
    for (i in listOf(0, 799, 799 * 800, 800 * 800 - 1)) assertEquals(Triple(255, 255, 255), rgb(out.px[i]))
    //  وسطِ پایینِ کالا قرمز می‌ماند
    val (r, g, b) = rgb(out.px[600 * 800 + 400])
    assertTrue("قرمز ماند: $r,$g,$b", r > 150 && g < 90 && b < 90)
    //  برچسبِ سفیدِ داخلِ کالا زمینه حساب نشد — از لبه راهی به آن نیست
    val label = rgb(out.px[400 * 800 + 400])
    assertTrue("برچسب سرِ جایش: $label", label.first > 200 && label.second > 200)
  }

  @Test fun `بی جدا کردن هم خروجی مربع است و کالا دست نمی‌خورد`() {
    val out = PhotoStudio.process(sample(), PhotoStudio.Options(removeBackground = false, autoCrop = false, enhance = false, sharpen = 0))
    assertEquals(800, out.width)
  }

  @Test fun `زمینهٔ شلوغ جدا نمی‌شود`() {
    val rnd = Random(3)
    val noisy = PhotoStudio.Image(200, 200, IntArray(200 * 200) { argb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)) })
    assertNull(PhotoStudio.foregroundMask(noisy, 28))
  }

  @Test fun `چرخش ابعاد را جابه‌جا می‌کند`() {
    val r = PhotoStudio.rotate(sample(320, 240), 90)
    assertEquals(240, r.width)
    assertEquals(320, r.height)
  }
}

/** ربات عکس عکسِ آماده را دوباره پردازش نمی‌کند */
class PhotoBotDetectTest {
  @Test fun `خروجیِ استودیو آماده شناخته می‌شود و عکسِ خام نه`() {
    val raw = PhotoStudio.Image(320, 240, IntArray(320 * 240) { (255 shl 24) or (150 shl 16) or (150 shl 8) or 140 })
    org.junit.Assert.assertFalse(PhotoBot.looksDone(raw))
    val done = PhotoStudio.process(raw, PhotoStudio.Options(removeBackground = false, enhance = false, sharpen = 0, margin = 10))
    assertTrue(PhotoBot.looksDone(done))
  }
}
