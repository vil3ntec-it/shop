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
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 *  IMG-PRO-16 — «نسخه‌های عکس»: نسخهٔ کوچک و بزرگِ عکسِ بانک خوانده و سنجیده می‌شوند، داوری همان `ImageQuality`، و
 *  کدگذارِ واقعیِ JPEG (همان پله‌های `ImagePrep.QUALITIES` ِ برنامه) هرگز «فشرده‌سازیِ زیاد» نمی‌سازد.
 */
class PhotoVersionsPageTest {

  /** عکسِ آزمون با لبه‌های تیز، کدگذاری‌شده با کیفیتِ [q] (همان مقیاسِ IJG ِ `Bitmap.compress`) */
  private fun jpeg(w: Int, h: Int, q: Int): ByteArray {
    val im = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g = im.createGraphics()
    g.color = java.awt.Color.WHITE
    g.fillRect(0, 0, w, h)
    g.color = java.awt.Color(0x1a3a6b)
    g.fillRect(w / 8, h / 8, w / 2, h / 3)
    //  «نوشته»: میله‌های باریکِ سیاه با فاصلهٔ یک‌درمیان (لبهٔ تیز مثلِ حروف و بارکد) — بی قلم تا آزمون به فونتِ سیستم تکیه نکند
    g.color = java.awt.Color.BLACK
    var x = w / 10
    while (x < w * 9 / 10) { g.fillRect(x, h * 2 / 3, 1 + (x % 3), h / 6); x += 4 + (x % 5) }
    g.dispose()
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val p = writer.defaultWriteParam.apply { compressionMode = ImageWriteParam.MODE_EXPLICIT; compressionQuality = q / 100f }
    val out = ByteArrayOutputStream()
    ImageIO.createImageOutputStream(out).use { os -> writer.output = os; writer.write(null, IIOImage(im, null, null), p) }
    writer.dispose()
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
}
