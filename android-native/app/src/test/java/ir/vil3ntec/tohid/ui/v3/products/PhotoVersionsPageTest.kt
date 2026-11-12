package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.core.image.ImagePrep
import ir.vil3ntec.tohid.core.image.ImageQuality
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.PhotoVersionsSource
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 *  IMG-PRO-16 — «نسخه‌های عکس»: نسخهٔ کوچک و بزرگِ عکسِ بانک خوانده و سنجیده می‌شوند، داوری همان `ImageQuality`، و
 *  کدگذارِ واقعیِ JPEG (همان پله‌های `ImagePrep.QUALITIES` ِ برنامه) هرگز «فشرده‌سازیِ زیاد» نمی‌سازد.
 */
class PhotoVersionsPageTest {

  /**
   *  عکسِ آزمون با لبه‌های تیز، کدگذاری‌شده با کیفیتِ [q] (همان مقیاسِ IJG ِ `Bitmap.compress`).
   *  `java.awt.image` و `javax.imageio` در classpath ِ کامپایلِ آزمونِ اندروید (android.jar) نیستند ولی JVM ِ آزمون دارد —
   *  پس با بازتاب صدا زده می‌شوند (همان راهِ `DesignTokensTest`) تا آزمون در CI ِ اندروید و کامپیوتر هر دو کامپایل شود.
   */
  private fun jpeg(w: Int, h: Int, q: Int): ByteArray {
    val int = Int::class.javaPrimitiveType!!
    val imageC = Class.forName("java.awt.image.BufferedImage")
    val im = imageC.getConstructor(int, int, int).newInstance(w, h, imageC.getField("TYPE_INT_RGB").getInt(null))
    val g = imageC.getMethod("createGraphics").invoke(im)
    val graphicsC = Class.forName("java.awt.Graphics")
    val colorC = Class.forName("java.awt.Color")
    val setColor = graphicsC.getMethod("setColor", colorC)
    val fillRect = graphicsC.getMethod("fillRect", int, int, int, int)
    fun color(rgb: Int) = colorC.getConstructor(int).newInstance(rgb)
    setColor.invoke(g, color(0xFFFFFF))
    fillRect.invoke(g, 0, 0, w, h)
    setColor.invoke(g, color(0x1a3a6b))
    fillRect.invoke(g, w / 8, h / 8, w / 2, h / 3)
    //  «نوشته»: میله‌های باریکِ سیاه با فاصلهٔ یک‌درمیان (لبهٔ تیز مثلِ حروف و بارکد) — بی قلم تا آزمون به فونتِ سیستم تکیه نکند
    setColor.invoke(g, color(0x000000))
    var x = w / 10
    while (x < w * 9 / 10) { fillRect.invoke(g, x, h * 2 / 3, 1 + (x % 3), h / 6); x += 4 + (x % 5) }
    graphicsC.getMethod("dispose").invoke(g)
    val ioC = Class.forName("javax.imageio.ImageIO")
    val writerC = Class.forName("javax.imageio.ImageWriter")
    val paramC = Class.forName("javax.imageio.ImageWriteParam")
    val metaC = Class.forName("javax.imageio.metadata.IIOMetadata")
    val iioC = Class.forName("javax.imageio.IIOImage")
    val writer = (ioC.getMethod("getImageWritersByFormatName", String::class.java).invoke(null, "jpeg") as Iterator<*>).next()
    val p = writerC.getMethod("getDefaultWriteParam").invoke(writer)
    paramC.getMethod("setCompressionMode", int).invoke(p, paramC.getField("MODE_EXPLICIT").getInt(null))
    paramC.getMethod("setCompressionQuality", Float::class.javaPrimitiveType!!).invoke(p, q / 100f)
    val out = ByteArrayOutputStream()
    (ioC.getMethod("createImageOutputStream", Any::class.java).invoke(null, out) as java.io.Closeable).use { os ->
      writerC.getMethod("setOutput", Any::class.java).invoke(writer, os)
      val iio = iioC.getConstructor(Class.forName("java.awt.image.RenderedImage"), List::class.java, metaC).newInstance(im, null, null)
      writerC.getMethod("write", metaC, iioC, paramC).invoke(writer, null, iio, p)
    }
    writerC.getMethod("dispose").invoke(writer)
    return out.toByteArray()
  }

  @Test
  fun `پله‌های کیفیتِ خودِ برنامه هرگز فشرده‌سازیِ زیاد نمی‌سازند`() {
    assertTrue(ImagePrep.QUALITIES.min() >= ImageQuality.FLOOR)
    for (q in ImagePrep.QUALITIES) {
      val m = ImageQuality.measure(jpeg(1200, 900, q))
      assertEquals("کیفیتِ سنجیده همان کیفیتِ کدگذار", q, m.quality)
      assertEquals(1200 to 900, m.width to m.height)
      assertFalse("q=$q", ImageQuality.grade(m) == ImageQuality.Grade.OVER_COMPRESSED)
    }
    assertEquals(ImageQuality.Grade.OVER_COMPRESSED, ImageQuality.grade(ImageQuality.measure(jpeg(400, 300, 30))))
  }

  private class Fake(var shown: ApiResult<ImageQuality.Shown>, val files: Map<String, ByteArray>) : PhotoVersionsSource {
    val asked = mutableListOf<String>()
    override suspend fun shown(productId: String) = shown
    override suspend fun bytes(url: String): ApiResult<ByteArray> {
      asked += url
      return files[url]?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiFailure.Offline())
    }
  }

  private val bank = ImageQuality.Shown("central", "c1", 2, "/api/shop/images/file/c1/2/thumb", "/api/shop/images/file/c1/2/display", null)

  @Test
  fun `هر دو نسخه خوانده و داوری می‌شوند`() = runBlocking {
    val f = Fake(ApiResult.Success(bank), mapOf(bank.thumbUrl!! to jpeg(400, 300, 80), bank.displayUrl!! to jpeg(1200, 900, 86)))
    val s = PhotoVersionsState(f, "p1")
    s.load()
    assertTrue(s.loaded)
    assertEquals(listOf(bank.thumbUrl, bank.displayUrl), f.asked)
    assertEquals(80, s.thumb!!.measure.quality)
    assertEquals(86, s.display!!.measure.quality)
    assertEquals(emptyList<ImageQuality.Flag>(), s.flags)
  }

  @Test
  fun `نسخهٔ پرفشرده و نسخهٔ کوچکِ ناجور نشان می‌گیرند`() = runBlocking {
    val f = Fake(ApiResult.Success(bank), mapOf(bank.thumbUrl!! to jpeg(400, 400, 80), bank.displayUrl!! to jpeg(1200, 900, 35)))
    val s = PhotoVersionsState(f, "p1")
    s.load()
    assertEquals(listOf(ImageQuality.Flag.OVER_COMPRESSED, ImageQuality.Flag.THUMB_MISMATCH), s.flags)
  }

  @Test
  fun `ردِ سرور، بی‌اینترنت و کالای بی عکسِ بانک`() = runBlocking {
    val off = PhotoVersionsState(Fake(ApiResult.Failure(ApiFailure.Forbidden("خاموش", "image_bank_disabled")), emptyMap()), "p1")
    off.load()
    assertTrue(off.off)
    val legacy = Fake(ApiResult.Success(ImageQuality.Shown("legacy_photo", "", 0, null, null, null)), emptyMap())
    val l = PhotoVersionsState(legacy, "p1")
    l.load()
    assertFalse(l.shown!!.hasVersions)
    assertTrue("بی نسخه چیزی خوانده نمی‌شود", legacy.asked.isEmpty())
    assertTrue(l.flags.isEmpty())
    val broken = Fake(ApiResult.Success(bank), emptyMap())
    val b = PhotoVersionsState(broken, "p1")
    b.load()
    assertTrue(b.failed)
    assertFalse(b.loaded)
    assertNull(b.display)
  }

  @Test
  fun `صفحه زیرِ کالا ثبت است و راهش پشتِ بانکِ روشن`() {
    val r = V3Modules.table.resolve(ProductsPaths.photoVersions("p1"))
    assertNotNull(r)
    assertEquals("products/{id}/photo/versions", r!!.route.pattern)
    assertEquals("products/{id}", r.route.parent)
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/v3/products/ProductPhotoExtrasAndroid.kt") }.first { it.isFile }.readText()
    assertTrue(src.contains("if (bankOn) add(V3MenuItem(Icons.Outlined.PhotoSizeSelectLarge, StrImageChanges.versionsTitle)"))
  }

  // ── IMG-11: نسخهٔ بزرگ‌نمایی (۲۴۰۰px ِ سرور) ──

  private val zoomed = bank.copy(zoomUrl = "/api/shop/images/file/c1/2/zoom")

  @Test
  fun `نسخهٔ بزرگ‌نمایی خوانده، سنجیده و با نسخهٔ بزرگ سنجیده می‌شود`() = runBlocking {
    val f = Fake(ApiResult.Success(zoomed), mapOf(
      bank.thumbUrl!! to jpeg(400, 300, 80), bank.displayUrl!! to jpeg(1200, 900, 86), zoomed.zoomUrl!! to jpeg(2400, 1800, 85),
    ))
    val s = PhotoVersionsState(f, "p1")
    s.load()
    assertTrue(s.loaded)
    assertEquals(listOf(bank.thumbUrl, bank.displayUrl, zoomed.zoomUrl), f.asked)
    assertEquals(2400 to 1800, s.zoom!!.measure.width to s.zoom!!.measure.height)
    assertEquals(true, s.zoomFits)
    assertEquals("داوریِ دو نسخهٔ پیشین دست نخورد", emptyList<ImageQuality.Flag>(), s.flags)
  }

  @Test
  fun `نبودِ نسخهٔ بزرگ‌نمایی صفحه را ناکام نمی‌کند و بی نشانی چیزی خواسته نمی‌شود`() = runBlocking {
    val missing = Fake(ApiResult.Success(zoomed), mapOf(bank.thumbUrl!! to jpeg(400, 300, 80), bank.displayUrl!! to jpeg(1200, 900, 86)))
    val s = PhotoVersionsState(missing, "p1")
    s.load()
    assertTrue(s.loaded)
    assertFalse(s.failed)
    assertNull(s.zoom)
    assertNull(s.zoomFits)
    val none = Fake(ApiResult.Success(bank), mapOf(bank.thumbUrl!! to jpeg(400, 300, 80), bank.displayUrl!! to jpeg(1200, 900, 86)))
    PhotoVersionsState(none, "p1").load()
    assertEquals(listOf(bank.thumbUrl, bank.displayUrl), none.asked)
  }

  @Test
  fun `نسخهٔ بزرگ‌نماییِ ناجور نشان می‌گیرد`() {
    fun m(w: Int, h: Int) = ImageQuality.Measure(ImageQuality.Kind.JPEG, w, h, 1000, 85)
    val display = m(1200, 900)
    assertTrue(zoomFits(display, m(2400, 1800)))
    assertTrue("بی نسخهٔ بزرگ فقط سقف", zoomFits(null, m(2000, 1500)))
    assertFalse("نسبتِ دیگر", zoomFits(display, m(2400, 2400)))
    assertFalse("کوچک‌تر از نسخهٔ بزرگ", zoomFits(display, m(800, 600)))
    assertFalse("بیش از ۲۴۰۰", zoomFits(display, m(3200, 2400)))
    assertFalse("ناخوانا", zoomFits(display, ImageQuality.Measure(ImageQuality.Kind.OTHER, null, null, 10, null)))
  }

  @Test
  fun `حسابِ بزرگ‌نمایی — سقفِ اندازهٔ واقعی، نقطهٔ ثابت، مرزِ قاب و دو بار زدن`() {
    //  عکسِ ۲۴۰۰ در قابِ ۱۰۰۰×۷۵۰ ⇒ سقف ۲٫۴ (هر پیکسل یک پیکسل)
    val max = ZoomMath.max(2400, 1000)
    assertEquals(2.4f, max, 1e-4f)
    assertEquals("عکسِ کوچک‌تر از قاب بزرگ نمی‌شود", 1f, ZoomMath.max(800, 800), 0f)
    val box = floatArrayOf(1000f, 750f, 1000f, 750f)
    fun at(st: ZoomMath.State, f: Float, cx: Float, cy: Float) = ZoomMath.zoomAt(st, f, cx, cy, box[0], box[1], box[2], box[3], max)
    val a = at(ZoomMath.State(), 2f, 50f, 20f)
    assertEquals(2f, a.s, 1e-4f)
    //  همان نقطه زیرِ انگشت: c = t + s·p
    val p = 50f
    assertEquals(50f, a.x + a.s * p, 1e-3f)
    assertEquals(-50f, a.x, 1e-3f)
    assertEquals(-20f, a.y, 1e-3f)
    val top = at(a, 10f, 0f, 0f)
    assertEquals("سقف", max, top.s, 1e-4f)
    val far = ZoomMath.drag(top, 1e6f, -1e6f, box[0], box[1], box[2], box[3], max)
    assertEquals("مرزِ افقی", (1000f * max - 1000f) / 2, far.x, 1e-2f)
    assertEquals("مرزِ عمودی", -(750f * max - 750f) / 2, far.y, 1e-2f)
    fun same(w: ZoomMath.State, g: ZoomMath.State, why: String) {
      assertEquals(why, w.s, g.s, 1e-4f); assertEquals(why, w.x, g.x, 1e-3f); assertEquals(why, w.y, g.y, 1e-3f)
    }
    same(ZoomMath.State(), at(top, 1e-6f, 0f, 0f), "کمتر از ۱ نه؛ در ۱ در مرکز")
    same(ZoomMath.State(), ZoomMath.drag(ZoomMath.State(), 300f, 300f, box[0], box[1], box[2], box[3], max), "در ۱ جابه‌جا نه")
    val max4 = ZoomMath.max(4000, 1000)
    val tapped = ZoomMath.doubleTap(ZoomMath.State(), 0f, 0f, box[0], box[1], box[2], box[3], max4)
    assertEquals(ZoomMath.TAP, tapped.s, 1e-4f)
    same(ZoomMath.State(), ZoomMath.doubleTap(tapped, 0f, 0f, box[0], box[1], box[2], box[3], max4), "دو بار زدن در بزرگی ⇒ برگشت")
    assertEquals("دو بار زدن هم از سقف نمی‌گذرد", max, ZoomMath.doubleTap(ZoomMath.State(), 0f, 0f, box[0], box[1], box[2], box[3], max).s, 1e-4f)
    assertEquals(240, ZoomMath.percent(top))
  }
}
