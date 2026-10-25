package ir.vil3ntec.tohid.core.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** سنجشِ ساده و روی‌دستگاهِ عکس (v3 ۳۶.۶): تاری، تاریکی، نورِ تند — بی هوشِ مصنوعی. */
class CaptureQualityTest {

  private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

  /** «کالا» روی زمینهٔ روشن: لبه‌های تیز و بافت */
  private fun sharp(w: Int, h: Int, base: Int = 170): IntArray {
    val r = Random(7)
    return IntArray(w * h) { i ->
      val x = i % w; val y = i / w
      val inBox = x in w / 4 until 3 * w / 4 && y in h / 4 until 3 * h / 4
      val stripe = if (inBox && (x / 6 + y / 6) % 2 == 0) -90 else 0
      gray((base + stripe + r.nextInt(-12, 13)).coerceIn(0, 255))
    }
  }

  /** همان تصویر با میانگینِ ۹×۹ — دوربینِ لرزان */
  private fun blur(px: IntArray, w: Int, h: Int, k: Int = 4): IntArray = IntArray(w * h) { i ->
    val x = i % w; val y = i / w
    var s = 0; var n = 0
    for (dy in -k..k) for (dx in -k..k) {
      val xx = (x + dx).coerceIn(0, w - 1); val yy = (y + dy).coerceIn(0, h - 1)
      s += px[yy * w + xx] and 0xFF; n++
    }
    gray(s / n)
  }

  @Test fun `عکسِ تیز و روشن بی‌هشدار است`() {
    val v = CaptureQuality.judge(CaptureQuality.measure(sharp(640, 480), 640, 480))
    assertTrue(v.hints.toString(), v.ok)
  }

  @Test fun `همان عکسِ لرزیده ⇒ «ثابت نگه دار»`() {
    val v = CaptureQuality.judge(CaptureQuality.measure(blur(sharp(640, 480), 640, 480), 640, 480))
    assertEquals(listOf(CaptureQuality.Hint.BLUR), v.hints)
  }

  @Test fun `عکسِ تاریک ⇒ «نور بیشتر»، نه «تار است»`() {
    val dark = sharp(640, 480).map { val g = (it and 0xFF) / 5; gray(g) }.toIntArray()
    val v = CaptureQuality.judge(CaptureQuality.measure(dark, 640, 480))
    assertTrue(CaptureQuality.Hint.DARK in v.hints)
    assertFalse(CaptureQuality.Hint.BLUR in v.hints)
    val black = IntArray(320 * 240) { gray(3) }
    assertEquals(listOf(CaptureQuality.Hint.DARK), CaptureQuality.judge(CaptureQuality.measure(black, 320, 240)).hints)
  }

  @Test fun `نورِ سوخته ⇒ «نور تند است»`() {
    val burnt = sharp(640, 480).mapIndexed { i, p -> if (i % 640 < 400) gray(255) else p }.toIntArray()
    assertTrue(CaptureQuality.Hint.BRIGHT in CaptureQuality.judge(CaptureQuality.measure(burnt, 640, 480)).hints)
  }

  @Test fun `عکسِ خیلی کوچک ⇒ «نزدیک‌تر»`() {
    assertTrue(CaptureQuality.Hint.SMALL in CaptureQuality.judge(CaptureQuality.measure(sharp(200, 150), 200, 150)).hints)
  }

  @Test fun `عکسِ بزرگ روی شبکهٔ نمونه سنجیده می‌شود و همان حکم را می‌دهد`() {
    val big = sharp(4000, 3000)
    val m = CaptureQuality.measure(big, 4000, 3000)
    assertTrue(CaptureQuality.judge(m).ok)
    assertEquals(4000, m.width)
  }

  @Test fun `بهترین از چند تلاش — بی‌هشدار و تیزتر`() {
    val a = CaptureQuality.judge(CaptureQuality.measure(blur(sharp(320, 240), 320, 240), 320, 240))
    val b = CaptureQuality.judge(CaptureQuality.measure(sharp(320, 240), 320, 240))
    val c = CaptureQuality.judge(CaptureQuality.measure(IntArray(320 * 240) { gray(3) }, 320, 240))
    assertEquals(1, CaptureQuality.best(listOf(a, b, c)))
    assertEquals(-1, CaptureQuality.best(emptyList()))
  }
}
