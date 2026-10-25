package ir.vil3ntec.tohid.core.hw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** لوگوی دکان ⇒ یک‌بیتیِ چاپگر (فاکتورِ نسخهٔ ۲) */
class LogoDitherTest {

  private fun solid(w: Int, h: Int, argb: Int) = IntArray(w * h) { argb }
  private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

  @Test fun `جعبهٔ لوگو — ۶۰٪ِ پهنا و بلندیِ ثابت برای هر کاغذ`() {
    assertEquals(230 to 112, LogoDither.box(384))
    assertEquals(345 to 144, LogoDither.box(576))
    assertEquals(230 to 112, LogoDither.box(100))
  }

  @Test fun `اندازه — کوچک تا جعبه با نسبتِ ثابت، بزرگ حداکثر دو برابر`() {
    val big = LogoDither.toMono(solid(1000, 500, gray(0)), 1000, 500, 576)
    assertEquals(288, big.width); assertEquals(144, big.height)
    val wide = LogoDither.toMono(solid(1000, 100, gray(0)), 1000, 100, 384)
    assertEquals(230, wide.width); assertEquals(23, wide.height)
    val tiny = LogoDither.toMono(solid(20, 10, gray(0)), 20, 10, 384)
    assertEquals(40, tiny.width); assertEquals(20, tiny.height)
  }

  @Test fun `خطی — سیاه سیاه و سفید سفید؛ شفاف سفید است نه سیاه`() {
    val px = IntArray(40 * 20) { i -> if ((i % 40) < 20) gray(0) else 0x00000000 }
    val img = LogoDither.toMono(px, 40, 20, 384, LogoDither.Mode.LINE)
    assertEquals(80, img.width)
    assertTrue(img[0, 0]); assertTrue(img[39, 19])
    assertTrue(!img[40, 0] && !img[79, 19])
    assertEquals(40 * 40, img.blackCount())
  }

  @Test fun `خودکار — نشانِ سیاه‌وسفید خطی، عکسِ خاکستری لرزش`() {
    val line = IntArray(64 * 64) { if (it % 7 == 0) gray(10) else gray(250) }
    assertEquals(LogoDither.toMono(line, 64, 64, 384, LogoDither.Mode.LINE), LogoDither.toMono(line, 64, 64, 384))
    val photo = solid(64, 64, gray(128))
    val auto = LogoDither.toMono(photo, 64, 64, 384)
    assertEquals(LogoDither.toMono(photo, 64, 64, 384, LogoDither.Mode.PHOTO), auto)
    //  خاکستریِ میانه نیم‌سیاه است و کمی روشن‌تر (سرِ حرارتی تیره می‌زند)
    val share = auto.blackCount().toDouble() / (auto.width * auto.height)
    assertTrue("سهمِ سیاه $share", share in 0.40..0.50)
  }

  @Test fun `حالت از کلید؛ ناشناخته ⇒ خودکار`() {
    assertEquals(LogoDither.Mode.LINE, LogoDither.Mode.of("line"))
    assertEquals(LogoDither.Mode.AUTO, LogoDither.Mode.of("??"))
    assertEquals(LogoDither.Mode.AUTO, LogoDither.Mode.of(null))
  }

  @Test fun `چرخشِ EXIF — ۶ یعنی ۹۰ درجه ساعت‌گرد، ۳ یعنی ۱۸۰، ۱ همان`() {
    //  ۳×۲:  a b c / d e f
    val px = intArrayOf(1, 2, 3, 4, 5, 6)
    val same = LogoDither.orient(px, 3, 2, 1)
    assertEquals(3, same.width); assertTrue(same.argb.contentEquals(px))
    val r90 = LogoDither.orient(px, 3, 2, 6)
    assertEquals(2, r90.width); assertEquals(3, r90.height)
    assertTrue(r90.argb.contentEquals(intArrayOf(4, 1, 5, 2, 6, 3)))
    assertTrue(LogoDither.orient(px, 3, 2, 3).argb.contentEquals(intArrayOf(6, 5, 4, 3, 2, 1)))
    assertTrue(LogoDither.orient(px, 3, 2, 8).argb.contentEquals(intArrayOf(3, 6, 2, 5, 1, 4)))
  }
}
