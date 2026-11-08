package ir.vil3ntec.tohid.core.image

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WEB-15 — نسخهٔ کارتِ ویترین: ابعاد، نسبت، جهتِ EXIF، سقفِ بایت (کدگذارِ جعلی؛ JPEG ِ واقعی: `PhotoThumbDeskTest`) */
class PhotoThumbTest {

  private val RED = 0xFFFF0000.toInt()
  private val BLUE = 0xFF0000FF.toInt()
  private val WHITE = 0xFFFFFFFF.toInt()

  /** کدگذارِ جعلی: پیکسل‌ها را نگه می‌دارد و «بایت»ی به اندازهٔ پیکسل × کیفیت / [div] می‌دهد */
  private class Fake(val div: Int = 100) : PhotoThumb.Encoder {
    var px = IntArray(0); var w = 0; var h = 0
    val tries = ArrayList<Triple<Int, Int, Int>>()
    override fun jpeg(px: IntArray, width: Int, height: Int, quality: Int): ByteArray {
      this.px = px; w = width; h = height
      tries += Triple(width, height, quality)
      return ByteArray(maxOf(1, width * height * quality / div))
    }
  }

  private fun solid(w: Int, h: Int, c: Int) = IntArray(w * h) { c }

  @Test
  fun longestSideIs320AndNeverUpscaled() {
    for ((src, want) in listOf(
      (800 to 600) to (320 to 240), (600 to 800) to (240 to 320), (800 to 800) to (320 to 320),
      (2400 to 1200) to (320 to 160), (200 to 100) to (200 to 100), (320 to 320) to (320 to 320),
    )) {
      val f = Fake(div = 1000)
      val m = assertNotNull(PhotoThumb.make(solid(src.first, src.second, BLUE), src.first, src.second, 1, f), "$src")
      assertEquals(want, m.width to m.height, "$src")
      assertEquals(want, f.w to f.h)
      assertEquals(want, PhotoThumb.size(src.first, src.second, 1))
    }
  }

  @Test
  fun aspectRatioIsKeptWithinOnePixel() {
    for ((w, h) in listOf(799 to 533, 1001 to 333, 640 to 427, 333 to 1000, 801 to 799)) {
      val m = assertNotNull(PhotoThumb.make(solid(w, h, BLUE), w, h, 1, Fake(div = 1000)))
      assertEquals(320, maxOf(m.width, m.height), "$w×$h")
      val want = if (w >= h) m.width.toDouble() * h / w else m.height.toDouble() * w / h
      val got = if (w >= h) m.height else m.width
      assertTrue(abs(got - want) <= 1.0, "$w×$h ⇒ ${m.width}×${m.height}")
    }
  }

  @Test
  fun exifOrientationIsAppliedToPixelsNotLeftAsATag() {
    //  ۸۰۰×۶۰۰ با گوشهٔ بالا-چپِ قرمز (۸۰×۸۰)؛ EXIF ۶ = ۹۰° ساعت‌گرد ⇒ کارتِ ایستادهٔ ۲۴۰×۳۲۰ با قرمز در بالا-راست
    val w = 800; val h = 600
    val px = IntArray(w * h) { i -> if (i % w < 80 && i / w < 80) RED else BLUE }
    val f = Fake(div = 1000)
    val m = assertNotNull(PhotoThumb.make(px, w, h, 6, f))
    assertEquals(240 to 320, m.width to m.height)
    assertEquals(240 to 320, PhotoThumb.size(w, h, 6))
    assertEquals(RED, f.px[0 * f.w + (f.w - 1)], "بالا-راست قرمز")
    assertEquals(BLUE, f.px[0], "بالا-چپ دیگر قرمز نیست")
    assertEquals(BLUE, f.px[(f.h - 1) * f.w + (f.w - 1)])
    //  ۳ = ۱۸۰° ⇒ قرمز در پایین-راست؛ ۸ = ۹۰° پادساعت‌گرد ⇒ پایین-چپ؛ ۱ ⇒ همان بالا-چپ
    val f3 = Fake(div = 1000); PhotoThumb.make(px, w, h, 3, f3)
    assertEquals(320 to 240, f3.w to f3.h)
    assertEquals(RED, f3.px[(f3.h - 1) * f3.w + (f3.w - 1)])
    val f8 = Fake(div = 1000); PhotoThumb.make(px, w, h, 8, f8)
    assertEquals(240 to 320, f8.w to f8.h)
    assertEquals(RED, f8.px[(f8.h - 1) * f8.w])
    val f1 = Fake(div = 1000); PhotoThumb.make(px, w, h, 1, f1)
    assertEquals(RED, f1.px[0])
  }

  @Test
  fun bytesStayUnderTheCapByQualityThenSize() {
    //  ۳۲۰×۳۲۰ × ۷۰ / ۱۰۰ = ۷۱٬۶۸۰ «بایت» > ۳۲KB ⇒ کیفیت پایین می‌آید؛ هیچ پله‌ای جا نشد ⇒ ضلعِ ۲۴۰ با ۵۴
    val f = Fake(div = 100)
    val m = assertNotNull(PhotoThumb.make(solid(800, 800, BLUE), 800, 800, 1, f))
    assertTrue(m.bytes.size <= PhotoThumb.MAX_BYTES, "${m.bytes.size}")
    assertEquals(listOf(70, 62, 54), f.tries.filter { it.first == 320 }.map { it.third }, "پله‌های کیفیت به ترتیب")
    assertTrue(PhotoThumb.qualities().all { it > ImageQuality.FLOOR }, "هیچ پله‌ای «فشرده‌سازیِ زیاد» نیست")
    assertEquals(240 to 54, m.width to m.quality)
    //  جا شد ⇒ بالاترین کیفیتی که جا شد، بی کوچک‌تر کردن
    val g = Fake(div = 300)
    val n = assertNotNull(PhotoThumb.make(solid(800, 800, BLUE), 800, 800, 1, g))
    assertEquals(320, n.width)
    assertEquals(70, n.quality)
    assertEquals(1, g.tries.size)
    //  هیچ اندازه/کیفیتی جا نمی‌شود ⇒ null (عکس بی کارت می‌رود؛ هرگز کارتِ بزرگ‌تر از سقف)
    val never = PhotoThumb.Encoder { _, _, _, _ -> ByteArray(PhotoThumb.MAX_BYTES + 1) }
    assertNull(PhotoThumb.make(solid(800, 800, BLUE), 800, 800, 1, never))
    //  کدگذارِ خالی هم کارت نیست
    assertNull(PhotoThumb.make(solid(800, 800, BLUE), 800, 800, 1) { _, _, _, _ -> ByteArray(0) })
  }

  @Test
  fun appCapIsInsideTheServerCaps() {
    assertTrue(PhotoThumb.MAX_BYTES <= PhotoThumb.SERVER_MAX_BYTES)
    assertTrue(PhotoThumb.EDGE <= PhotoThumb.SERVER_MAX_EDGE)
    assertEquals(64 * 1024, PhotoThumb.SERVER_MAX_BYTES, "همان THUMB_MAX_BYTES ِ server/src/lib/product-photos.js")
    assertEquals(400, PhotoThumb.SERVER_MAX_EDGE, "همان THUMB_MAX_SIDE ِ server/src/lib/product-photos.js")
  }

  @Test
  fun shrinkAveragesAreasAndLeavesTheInputUntouched() {
    //  ۲×۲ ⇒ ۱×۱ میانگین؛ ۴×۲ ⇒ ۲×۱ هر نیمه جدا
    val q = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
    assertEquals(0xFF7F7F7F.toInt(), PhotoThumb.shrink(q, 2, 2, 1, 1)[0])
    val halves = intArrayOf(RED, RED, BLUE, BLUE, RED, RED, BLUE, BLUE)
    assertContentEquals(intArrayOf(RED, BLUE), PhotoThumb.shrink(halves, 4, 2, 2, 1))
    //  ورودی (پیکسل‌های عکسِ برنامه) دست نمی‌خورد، حتی بی کوچک کردن یا با شفافیت
    val src = IntArray(100 * 100) { 0x00000000 }
    val copy = src.copyOf()
    PhotoThumb.make(src, 100, 100, 6, Fake(div = 1000))
    assertContentEquals(copy, src)
  }

  @Test
  fun transparencyGoesOnWhiteNotBlack() {
    val f = Fake(div = 1000)
    PhotoThumb.make(IntArray(50 * 50) { 0x00000000 }, 50, 50, 1, f)
    assertTrue(f.px.all { it == WHITE }, "JPEG شفافیت ندارد ⇒ سفید")
  }

  @Test
  fun notAnImageGivesNoThumb() {
    assertNull(PhotoThumb.make(IntArray(0), 0, 0, 1, Fake()))
    assertNull(PhotoThumb.make(IntArray(10), 10, 10, 1, Fake()), "آرایهٔ کوتاه‌تر از ابعاد")
  }
}
