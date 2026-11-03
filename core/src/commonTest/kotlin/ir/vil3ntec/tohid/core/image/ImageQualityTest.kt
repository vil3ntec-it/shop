package ir.vil3ntec.tohid.core.image

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  IMG-PRO-16 — سنجهٔ فشرده‌سازی و داوریِ نسخه‌های نهایی (`ImageQuality`): کیفیتِ JPEG از جدولِ DQT، ابعاد از SOF/IHDR/VP8،
 *  نشانِ «فشرده‌سازیِ زیاد»، «نسخهٔ کوچکِ ناجور» و «منبعِ کوچک»، و نمایشِ بی بزرگ‌نمایی.
 */
class ImageQualityTest {

  private fun b(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

  /** سرآیندِ JPEG ِ کمینه: SOI، DQT ِ ۸بیتیِ جدولِ ۰، SOF0 با ابعاد، SOS */
  private fun jpeg(q: Int, w: Int, h: Int, sixteenBit: Boolean = false): ByteArray {
    val t = ImageQuality.ijgTable(q)
    val dqt = if (sixteenBit) {
      b(0xFF, 0xDB, 0x00, 2 + 1 + 128, 0x10) + ByteArray(128) { i -> if (i % 2 == 0) 0 else t[i / 2].toByte() }
    } else {
      b(0xFF, 0xDB, 0x00, 2 + 1 + 64, 0x00) + ByteArray(64) { t[it].toByte() }
    }
    val sof = b(0xFF, 0xC0, 0x00, 11, 8, h shr 8, h and 255, w shr 8, w and 255, 1, 1, 0x11, 0)
    return b(0xFF, 0xD8) + b(0xFF, 0xE0, 0x00, 4, 0, 0) + dqt + sof + b(0xFF, 0xDA, 0x00, 2) + ByteArray(100) + b(0xFF, 0xD9)
  }

  @Test
  fun jpegQualityFromTheLuminanceTableMatchesIjgFrom20To100() {
    for (q in 20..100) assertEquals(q, ImageQuality.qualityOf(ImageQuality.ijgTable(q)), "q=$q")
    //  زیرِ ۲۰ جدول در ۲۵۵ می‌بُرد؛ عدد کمتر از مرز می‌ماند و داوری همان «فشرده‌سازیِ زیاد»
    for (q in 1..19) assertTrue(ImageQuality.qualityOf(ImageQuality.ijgTable(q))!! < ImageQuality.FLOOR)
    assertNull(ImageQuality.qualityOf(IntArray(10)))
    assertNull(ImageQuality.qualityOf(IntArray(64)), "صفر در جدول ⇒ بدشکل")
  }

  @Test
  fun measuresJpegFromHeadersOnly() {
    val m = ImageQuality.measure(jpeg(86, 1200, 900))
    assertEquals(ImageQuality.Kind.JPEG, m.kind)
    assertEquals(1200 to 900, m.width to m.height)
    assertEquals(86, m.quality)
    assertEquals(ImageQuality.Grade.SHARP, ImageQuality.grade(m))
    assertEquals(64, ImageQuality.measure(jpeg(64, 400, 300, sixteenBit = true)).quality, "جدولِ ۱۶بیتی")
    assertEquals(ImageQuality.Grade.ACCEPTABLE, ImageQuality.grade(ImageQuality.measure(jpeg(64, 400, 300))))
    assertEquals(ImageQuality.Grade.OVER_COMPRESSED, ImageQuality.grade(ImageQuality.measure(jpeg(30, 400, 300))))
  }

  @Test
  fun pngWebpAndBrokenFiles() {
    val png = b(0x89, 'P'.code, 'N'.code, 'G'.code, 13, 10, 26, 10, 0, 0, 0, 13, 'I'.code, 'H'.code, 'D'.code, 'R'.code, 0, 0, 1, 0x90, 0, 0, 0, 0xC8) + ByteArray(10)
    val p = ImageQuality.measure(png)
    assertEquals(ImageQuality.Kind.PNG, p.kind)
    assertEquals(400 to 200, p.width to p.height)
    assertEquals(ImageQuality.Grade.LOSSLESS, ImageQuality.grade(p))
    val vp8x = "RIFF".encodeToByteArray() + b(0, 0, 0, 0) + "WEBPVP8X".encodeToByteArray() + b(10, 0, 0, 0, 0, 0, 0, 0) + b(0x8F, 0x01, 0) + b(0x2B, 0x01, 0) + ByteArray(4)
    val w = ImageQuality.measure(vp8x)
    assertEquals(ImageQuality.Kind.WEBP, w.kind)
    assertEquals(400 to 300, w.width to w.height)
    assertEquals(ImageQuality.Grade.UNKNOWN, ImageQuality.grade(w), "کیفیتِ WebP حدس زده نمی‌شود")
    for (junk in listOf(ByteArray(0), b(0xFF, 0xD8), b(0xFF, 0xD8, 0xFF, 0xDB, 0xFF, 0xFF), "GIF89a".encodeToByteArray(), jpeg(80, 0, 0))) {
      val m = ImageQuality.measure(junk)
      assertEquals(ImageQuality.Kind.OTHER, m.kind)
      assertNull(m.width)
    }
  }

  @Test
  fun verdictFlagsOverCompressionMismatchedThumbAndSmallSource() {
    val display = ImageQuality.measure(jpeg(86, 1200, 900))
    val thumb = ImageQuality.measure(jpeg(80, 400, 300))
    assertEquals(emptyList<ImageQuality.Flag>(), ImageQuality.verdict(thumb, display), "خروجیِ درستِ برنامه ⇒ بی نشان")
    assertEquals(listOf(ImageQuality.Flag.OVER_COMPRESSED), ImageQuality.verdict(thumb, ImageQuality.measure(jpeg(40, 1200, 900))))
    assertEquals(listOf(ImageQuality.Flag.THUMB_MISMATCH), ImageQuality.verdict(ImageQuality.measure(jpeg(80, 400, 400)), display), "نسبتِ دیگر")
    assertEquals(listOf(ImageQuality.Flag.THUMB_MISMATCH), ImageQuality.verdict(ImageQuality.measure(jpeg(80, 1600, 1200)), display), "کوچک بزرگ‌تر از بزرگ")
    assertEquals(listOf(ImageQuality.Flag.SMALL_SOURCE), ImageQuality.verdict(null, ImageQuality.measure(jpeg(86, 280, 200))))
    assertEquals(listOf(ImageQuality.Flag.UNREADABLE), ImageQuality.verdict(ImageQuality.measure(b(1, 2, 3)), display))
  }

  @Test
  fun fitNeverUpscales() {
    //  گوشی: قابِ ۱۰۸۰ پیکسل ⇒ نسخهٔ ۱۲۰۰ کوچک می‌شود
    assertEquals(1080 to 810, ImageQuality.fit(1200, 900, 1080, 2000))
    //  صفحهٔ بزرگ: قابِ ۲۴۰۰ ⇒ همان ۱۲۰۰ (بی کشیدن)
    assertEquals(1200 to 900, ImageQuality.fit(1200, 900, 2400, 2400))
    assertTrue(ImageQuality.nativeIn(1200, 900, 2400, 2400))
    assertEquals(false, ImageQuality.nativeIn(1200, 900, 1080, 2000))
    assertEquals(0 to 0, ImageQuality.fit(0, 10, 100, 100))
  }

  @Test
  fun readsTheShownVersionsOfAProduct() {
    val s = ImageQuality.shownOf(
      Json.parseToJsonElement(
        """{"product_id":"p1","source":"central","image_id":"c1","version":2,
          "urls":{"thumb":"/api/shop/images/file/c1/2/thumb","display":"/api/shop/images/file/c1/2/display","zoom":null}}""",
      ).jsonObject,
    )
    assertEquals("central", s.source)
    assertEquals(2, s.version)
    assertEquals("/api/shop/images/file/c1/2/thumb", s.thumbUrl)
    assertNull(s.zoomUrl)
    assertTrue(s.hasVersions)
    assertEquals("/shop/images/file/c1/2/thumb", ImageQuality.filePath(s.thumbUrl))
    assertEquals("/shop/images/file/c1/2/thumb", ImageQuality.filePath("/api/v1/shop/images/file/c1/2/thumb"))
    for (bad in listOf(null, "https://evil.example/x.jpg", "/api/sync/v1/photos/p1", "/api/shop/images/file/../../admin")) assertNull(ImageQuality.filePath(bad), bad)
    val legacy = ImageQuality.shownOf(Json.parseToJsonElement("""{"product_id":"p1","source":"legacy_photo","url":"/api/sync/v1/photos/p1"}""").jsonObject)
    assertEquals(false, legacy.hasVersions)
  }
}
