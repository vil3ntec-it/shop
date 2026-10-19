package ir.vil3ntec.tohid.core.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** آماده‌سازیِ عکس (v3 ۳۴.۳، ۳۶.۹) — همان سقف‌های سرور و همان چرخشِ EXIF. */
class ImagePrepTest {

  @Test fun `اندازه‌ها همان سرور است`() {
    assertEquals(1200, ImagePrep.DISPLAY_SIDE)
    assertEquals(600 * 1024, ImagePrep.DISPLAY_BYTES)
    assertEquals(400, ImagePrep.THUMB_SIDE)
    assertEquals(150 * 1024, ImagePrep.THUMB_BYTES)
  }

  @Test fun `کوچک‌کردن نسبت را نگه می‌دارد و هرگز بزرگ نمی‌کند`() {
    assertEquals(ImagePrep.Size(1200, 900), ImagePrep.fit(4000, 3000, 1200))
    assertEquals(ImagePrep.Size(900, 1200), ImagePrep.fit(3000, 4000, 1200))
    assertEquals(ImagePrep.Size(400, 225), ImagePrep.fit(1200, 675, 400))
    assertEquals(ImagePrep.Size(640, 480), ImagePrep.fit(640, 480, 1200))   // کوچک‌تر از سقف ⇒ همان
    assertEquals(ImagePrep.Size(400, 1), ImagePrep.fit(4000, 3, 400))      // هرگز صفر
  }

  @Test fun `نسخهٔ کوچک در مرزِ دو درصدیِ سرور می‌ماند`() {
    for ((w, h) in listOf(4000 to 3000, 3000 to 4000, 1920 to 1080, 1080 to 1920, 1200 to 677, 999 to 1001, 5000 to 1200)) {
      val d = ImagePrep.fit(w, h, ImagePrep.DISPLAY_SIDE)
      val t = ImagePrep.fit(d.w, d.h, ImagePrep.THUMB_SIDE)
      assertTrue("$w×$h", ImagePrep.aspectOk(t, d))
      assertTrue(t.w <= d.w && t.h <= d.h)
    }
    assertFalse(ImagePrep.aspectOk(ImagePrep.Size(400, 1), ImagePrep.Size(2000, 7)))
  }

  @Test fun `orientation پنج تا هشت ضلع‌ها را جابه‌جا می‌کند`() {
    for (o in 1..4) assertEquals(ImagePrep.Size(600, 400), ImagePrep.oriented(600, 400, o))
    for (o in 5..8) assertEquals(ImagePrep.Size(400, 600), ImagePrep.oriented(600, 400, o))
  }

  @Test fun `inSampleSize حافظه را نگه می‌دارد ولی از سقف پایین‌تر نمی‌رود`() {
    assertEquals(1, ImagePrep.sampleSize(1200, 900, 1200))
    assertEquals(2, ImagePrep.sampleSize(4000, 3000, 1200))
    assertEquals(4, ImagePrep.sampleSize(8000, 6000, 1200))
    assertTrue(8000 / ImagePrep.sampleSize(8000, 6000, 1200) >= 1200)
    assertTrue(ImagePrep.tooManyPixels(10000, 7000))
    assertFalse(ImagePrep.tooManyPixels(8000, 6000))
  }

  /**
   *  تصویرِ ۳×۲:   a b c
   *                d e f
   *  هر orientation باید همان چیزی را بدهد که بیننده می‌بیند.
   */
  @Test fun `چرخش و آینهٔ EXIF روی پیکسل‌ها — هر هشت حالت`() {
    val px = intArrayOf(1, 2, 3, 4, 5, 6)
    fun o(n: Int) = ImagePrep.orient(px, 3, 2, n)
    assertArrayEquals(intArrayOf(1, 2, 3, 4, 5, 6), o(1).first)
    assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), o(2).first)           // آینهٔ افقی
    assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), o(3).first)           // ۱۸۰
    assertArrayEquals(intArrayOf(4, 5, 6, 1, 2, 3), o(4).first)           // آینهٔ عمودی
    assertArrayEquals(intArrayOf(1, 4, 2, 5, 3, 6), o(5).first)           // ترانهاده
    assertArrayEquals(intArrayOf(4, 1, 5, 2, 6, 3), o(6).first)           // ۹۰ ساعت‌گرد
    assertArrayEquals(intArrayOf(6, 3, 5, 2, 4, 1), o(7).first)           // ترانهادهٔ معکوس
    assertArrayEquals(intArrayOf(3, 6, 2, 5, 1, 4), o(8).first)           // ۹۰ پادساعت‌گرد
    for (n in 5..8) assertEquals(ImagePrep.Size(2, 3), o(n).second)
    //  ۶: گوشهٔ بالا-چپِ اصل بالا-راست می‌نشیند (همان آزمونِ کرومیومِ وب)
    assertEquals(1, o(6).first[1])
  }

  @Test fun `کدگذاری زیرِ سقف — کیفیت پله‌ای، بعد کوچک‌تر`() {
    val tried = ArrayList<Pair<ImagePrep.Size, Int>>()
    //  «کدگذار»ی که حجمش با پیکسل و کیفیت بالا می‌رود
    val (bytes, size) = ImagePrep.encodeUnder(ImagePrep.Size(1200, 900), 50_000) { s, q ->
      tried += s to q
      ByteArray((s.w * s.h * q / 1000.0).toInt())
    }
    assertTrue(bytes.size <= 50_000)
    assertTrue(size.w < 1200)                                   // کیفیت کافی نبود ⇒ کوچک‌تر
    assertEquals(86, tried.first().second)
    assertTrue(tried.any { it.second == 56 && it.first.w == 1200 })   // همهٔ پله‌ها پیش از کوچک‌کردن
    assertTrue(ImagePrep.aspectOk(size, ImagePrep.Size(1200, 900)))
  }

  @Test(expected = IllegalStateException::class)
  fun `کدگذاری که هرگز زیرِ سقف نمی‌رود خطاست، نه حلقهٔ بی‌پایان`() {
    ImagePrep.encodeUnder(ImagePrep.Size(100, 100), 10) { _, _ -> ByteArray(1000) }
  }
}
