package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.core.image.ImagePrep
import ir.vil3ntec.tohid.core.image.ImageQuality
import ir.vil3ntec.tohid.core.image.ProductGallery
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.ProductGallerySource
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRD-17/PRD-27 — صفحهٔ «عکس‌های بیشتر و ویترین»: خواندن، افزودنِ تکمیلی (بی انتخاب)، عکسِ جدای ویترین (با انتخاب)،
 *  سقفِ هشت، برداشتن از کالا و پاسخ‌های سرور؛ اندازه‌های آماده‌سازی همان `DERIV` ِ سرور.
 */
class ProductGalleryStateTest {

  private class Upload(val role: String, val select: Boolean, val thumb: Boolean)

  private class Fake(
    var items: MutableList<ProductGallery.Item> = mutableListOf(),
    var shownSource: String = "legacy_photo",
    var shownId: String = "",
  ) : ProductGallerySource {
    var listFail: ApiResult.Failure? = null
    var uploadFail: ApiResult.Failure? = null
    var detachFail: ApiResult.Failure? = null
    val uploads = ArrayList<Upload>()
    val detached = ArrayList<String>()
    private var n = 0
    override suspend fun items(productId: String): ApiResult<List<ProductGallery.Item>> = listFail ?: ApiResult.Success(items.toList())
    override suspend fun shown(productId: String): ApiResult<ImageQuality.Shown> =
      ApiResult.Success(ImageQuality.Shown(shownSource, shownId, 1, null, null, null))
    override suspend fun bytes(url: String): ApiResult<ByteArray> = ApiResult.Success(byteArrayOf(1, 2, 3))
    override suspend fun upload(productId: String, role: String, display: ByteArray, thumb: ByteArray?, select: Boolean): ApiResult<String> {
      uploadFail?.let { return it }
      uploads += Upload(role, select, thumb != null)
      val id = "u${++n}"
      items.add(0, ProductGallery.Item(id, role, 1, "/api/shop/images/file/$id/1/thumb", null))
      if (select) { shownSource = "shop"; shownId = id }
      return ApiResult.Success(id)
    }
    override suspend fun detach(imageId: String): ApiResult<Unit> {
      detachFail?.let { return it }
      detached += imageId
      items.removeAll { it.id == imageId }
      return ApiResult.Success(Unit)
    }
  }

  private fun extra(id: String, role: String = "detail") = ProductGallery.Item(id, role, 1, "/api/shop/images/file/$id/1/thumb", null)
  private fun fail(f: ApiFailure, status: Int) = ApiResult.Failure(f.withStatus(status))

  @Test
  fun `خواندن — تکمیلی‌ها و ویترین، نسخهٔ کوچک؛ بانکِ خاموش «روشن نیست»؛ گذرا «دوباره»`() = runBlocking {
    val src = Fake(mutableListOf(extra("e1"), ProductGallery.Item("m1", "main", 1, null, null)))
    val s = ProductGalleryState(src, "p1")
    s.load()
    assertEquals(listOf("e1"), s.extras.map { it.id })
    assertEquals(ProductGallery.Storefront.DEVICE, s.storefront)
    assertNull(s.storefrontItem)
    assertNotNull(s.thumbs["e1"])
    assertFalse(s.off)

    val off = Fake().apply { listFail = fail(ApiFailure.Forbidden("خاموش", "image_bank_disabled"), 403) }
    val o = ProductGalleryState(off, "p1")
    o.load()
    assertTrue(o.off)
    val flaky = Fake().apply { listFail = ApiResult.Failure(ApiFailure.Offline()) }
    val f = ProductGalleryState(flaky, "p1")
    f.load()
    assertTrue(f.failed)
    assertNull(f.items)
  }

  @Test
  fun `تکمیلی بی انتخاب؛ ویترین با انتخاب و نقشِ ویترین؛ سقفِ هشت پیش از فرستادن`() = runBlocking {
    val src = Fake()
    val s = ProductGalleryState(src, "p1")
    s.load()
    assertEquals(ProductGalleryState.Outcome.Done, s.add("back", byteArrayOf(9), byteArrayOf(8)))
    assertEquals("back", src.uploads.last().role)
    assertFalse("تکمیلی عکسِ ویترین را عوض نمی‌کند", src.uploads.last().select)
    assertEquals(ProductGallery.Storefront.DEVICE, s.storefront)

    assertEquals(ProductGalleryState.Outcome.Done, s.setStorefront(byteArrayOf(9), null))
    assertEquals(ProductGallery.STOREFRONT_ROLE, src.uploads.last().role)
    assertTrue(src.uploads.last().select)
    assertEquals(ProductGallery.Storefront.OWN, s.storefront)
    assertEquals(src.shownId, s.storefrontItem?.id)
    assertEquals("عکسِ ویترین در تکمیلی‌ها نیست", listOf("u1"), s.extras.map { it.id })

    repeat(7) { s.add("detail", byteArrayOf(1), null) }
    assertEquals(8, s.extras.size)
    val before = src.uploads.size
    assertEquals(ProductGalleryState.Outcome.Full, s.add("detail", byteArrayOf(1), null))
    assertEquals("پر ⇒ هیچ فرستادنی", before, src.uploads.size)

    src.uploadFail = ApiResult.Failure(ApiFailure.Invalid("سقفِ عکس‌های این دکان پر است", "quota").withStatus(400))
    val out = s.setStorefront(byteArrayOf(1), null)
    assertEquals(ProductGalleryState.Outcome.Failed("سقفِ عکس‌های این دکان پر است"), out)
    assertFalse(s.busy)
  }

  @Test
  fun `برداشتن از کالا — انجام، عکسِ ویترین ۴۰۹، رفته ۴۰۴ و گذرا`() = runBlocking {
    val src = Fake(mutableListOf(extra("e1"), extra("e2", "label")))
    val s = ProductGalleryState(src, "p1")
    s.load()
    assertEquals(ProductGallery.Detach.DONE, s.detach("e1"))
    assertEquals(listOf("e2"), s.extras.map { it.id })
    assertNull(s.thumbs["e1"])

    src.detachFail = fail(ApiFailure.Conflict("ویترین", "image_selected"), 409)
    assertEquals(ProductGallery.Detach.SELECTED, s.detach("e2"))
    assertEquals(listOf("e2"), s.extras.map { it.id })
    src.detachFail = fail(ApiFailure.NotFound("نیست", "not_found"), 404)
    assertEquals(ProductGallery.Detach.GONE, s.detach("e2"))
    src.detachFail = ApiResult.Failure(ApiFailure.Offline())
    assertEquals(ProductGallery.Detach.FAILED, s.detach("e2"))
    assertFalse(s.busy)
  }

  @Test
  fun `اندازه‌های آماده‌سازی همان DERIV ِ سرور است`() {
    assertEquals(ProductGallery.DISPLAY_SIDE, ImagePrep.DISPLAY_SIDE)
    assertEquals(ProductGallery.DISPLAY_MAX_BYTES, ImagePrep.DISPLAY_BYTES)
    assertEquals(ProductGallery.THUMB_SIDE, ImagePrep.THUMB_SIDE)
    assertEquals(ProductGallery.THUMB_MAX_BYTES, ImagePrep.THUMB_BYTES)
    assertEquals(ProductGallery.MAX_INPUT_BYTES, ir.vil3ntec.tohid.data.ImageBulk.MAX_FILE_BYTES)
  }

  @Test
  fun `صفحه زیرِ کالا ثبت است و راهش پشتِ بانکِ روشن`() {
    val r = V3Modules.table.resolve(ProductsPaths.photoGallery("p1"))
    assertNotNull(r)
    assertEquals("products/{id}/photo/gallery", r!!.route.pattern)
    assertEquals("products/{id}", r.route.parent)
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/v3/products/ProductPhotoExtrasAndroid.kt") }.first { it.isFile }.readText()
    assertTrue(src.contains("if (bankOn) add(V3MenuItem(Icons.Outlined.PhotoLibrary, StrProductGallery.title)"))
    //  عکسِ تازهٔ دستگاه عکسِ جدای ویترین را کنار نمی‌زند (`ImageBank.uploadOwn`)
    val bank = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/data/ImageBank.kt") }.first { it.isFile }.readText()
    assertTrue(bank.contains("ProductGallery.keepStorefrontPhoto("))
    assertTrue(bank.contains("if (!up.selected && !force) {"))
  }
}
