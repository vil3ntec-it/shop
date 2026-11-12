package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.core.image.ImageStudio
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.ImageStudioSource
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ۴-۲۳ (۱) — «استودیوی عکس» روی درِ `/api/shop/images/studio`: پرچمِ خاموش ⇒ «روشن نیست»؛ دستهٔ تازه با یک کلید (تکرار ⇒
 *  همان)، پیش از فرستادن دستِ‌کم یک و نه بیش از سقف؛ کارهای دسته فقط آنچه سرور می‌پذیرد؛ خروجیِ پرخطر فقط با تأییدِ جدای همان.
 */
class ImageStudioPagesTest {

  private fun item(id: String, risk: List<String> = emptyList(), review: String? = "needs_review", status: String = "done") =
    ImageStudio.Item(id, status, null, 1, if (status == "done") 2 else null, review, risk, "/f/$id/1", if (status == "done") "/f/$id/2" else null)

  private fun batch(state: String = "active", items: List<ImageStudio.Item> = emptyList(), finished: Int = 0, failed: Int = 0) =
    ImageStudio.Batch("b1", state, "catalog", 2, ImageStudio.Progress(0, 0, finished - failed, failed, 0, finished, finished * 50), 1, items, null)

  private class Fake : ImageStudioSource {
    var configNext: ApiResult<ImageStudio.Config?> = ApiResult.Success(ImageStudio.Config(ImageStudio.PRESETS, 2, 3))
    var batchesNext: ApiResult<List<ImageStudio.Summary>> = ApiResult.Success(emptyList())
    var batchNext: ApiResult<ImageStudio.Batch>? = null
    var mineNext: ApiResult<Pair<List<ImageStudio.Mine>, String?>> = ApiResult.Success(emptyList<ImageStudio.Mine>() to null)
    val created = mutableListOf<Triple<String, List<String>, String>>()
    val actions = mutableListOf<String>()
    val reviews = mutableListOf<String>()
    var reviewNext: ApiResult<Unit> = ApiResult.Success(Unit)
    override suspend fun config() = configNext
    override suspend fun batches() = batchesNext
    override suspend fun batch(id: String, after: String) = batchNext!!
    override suspend fun create(key: String, imageIds: List<String>, preset: String): ApiResult<ImageStudio.Batch> {
      created += Triple(key, imageIds, preset)
      return ApiResult.Success(ImageStudio.Batch("b-$key", "active", preset, imageIds.size, ImageStudio.Progress(imageIds.size, 0, 0, 0, 0, 0, 0), 0, emptyList(), null))
    }
    override suspend fun action(id: String, action: String): ApiResult<Unit> { actions += action; return ApiResult.Success(Unit) }
    override suspend fun review(imageId: String, version: Int, approve: Boolean, confirmRisk: Boolean): ApiResult<Unit> {
      reviews += "$imageId:$version:$approve:$confirmRisk"
      return reviewNext
    }
    override suspend fun mine(cursor: String) = mineNext
    override suspend fun bytes(url: String): ApiResult<ByteArray> = ApiResult.Success(ByteArray(0))
  }

  @Test
  fun `پرچمِ خاموش ⇒ روشن نیست، بی هیچ نوشتن`() = runBlocking {
    val f = Fake()
    f.batchesNext = ApiResult.Failure(ApiFailure.Forbidden("خاموش", "image_studio_disabled"))
    val l = ImageStudioListState(f)
    l.load()
    assertTrue(l.off)
    assertNull(l.batches)
    f.configNext = ApiResult.Failure(ApiFailure.Forbidden("خاموش", "image_studio_disabled"))
    val n = ImageStudioNewState(f) { "k" }
    n.load()
    assertTrue(n.off)
    assertNull(n.start())
    assertTrue(f.created.isEmpty())
  }

  @Test
  fun `دستهٔ تازه — سنجش پیش از فرستادن و یک کلید برای تکرار`() = runBlocking {
    val f = Fake()
    f.mineNext = ApiResult.Success(listOf(ImageStudio.Mine("i1", "p1", null), ImageStudio.Mine("i2", "p2", null), ImageStudio.Mine("i3", "p3", null)) to null)
    var keys = 0
    val n = ImageStudioNewState(f) { "key-" + (++keys) }
    n.load()
    assertEquals(3, n.photos!!.size)
    assertNull(n.start())
    assertEquals(ImageStudio.Check.Empty, n.problem)
    listOf("i1", "i2", "i3").forEach { n.toggle(it) }
    assertNull(n.start())
    assertEquals(ImageStudio.Check.TooMany(2), n.problem)
    n.toggle("i3")
    n.preset = "enhance"
    assertEquals("b-key-1", n.start())
    assertEquals("دوباره فرستادن ⇒ همان کلید", "b-key-1", n.start())
    assertEquals(listOf(Triple("key-1", listOf("i1", "i2"), "enhance"), Triple("key-1", listOf("i1", "i2"), "enhance")), f.created)
    assertEquals(1, keys)
  }

  @Test
  fun `کارهای دسته فقط آنچه سرور می‌پذیرد`() = runBlocking {
    val f = Fake()
    f.batchNext = ApiResult.Success(batch("active", finished = 2, failed = 0))
    val s = ImageStudioBatchState(f, "b1")
    s.load()
    s.act("pause")
    assertTrue("تمام‌شده ایستادن ندارد", f.actions.isEmpty())
    f.batchNext = ApiResult.Success(batch("paused"))
    s.load()
    s.act("resume")
    assertEquals(listOf("resume"), f.actions)
  }

  @Test
  fun `خروجیِ پرخطر فقط با تأییدِ جدای همان نسخه`() = runBlocking {
    val f = Fake()
    val risky = item("i1", risk = listOf("crop_cut"))
    val safe = item("i2")
    f.batchNext = ApiResult.Success(batch(items = listOf(risky, safe)))
    val s = ImageStudioBatchState(f, "b1")
    s.load()
    s.review(risky, approve = true)
    assertEquals("بارِ اول فقط پرسش", "i1", s.riskAsk)
    assertTrue(f.reviews.isEmpty())
    s.review(safe, approve = true, confirmRisk = true)
    assertEquals("بی خطر ⇒ confirm_risk نمی‌رود", listOf("i2:2:true:false"), f.reviews)
    assertEquals("تأییدِ عکسِ دیگر پرسشِ خطرِ این یکی را پاک نمی‌کند", "i1", s.riskAsk)
    s.review(risky, approve = true, confirmRisk = true)
    assertEquals("i1:2:true:true", f.reviews.last())
    assertNull(s.riskAsk)
    assertEquals(ImageStudioBatchState.Outcome.Reviewed(true), s.outcome)
    s.review(item("i3", risk = listOf("x")), approve = false)
    assertEquals("ردِ پرخطر بی پرسش و بی confirm_risk", "i3:2:false:false", f.reviews.last())
    f.reviewNext = ApiResult.Failure(ApiFailure.Conflict("پیش‌تر بررسی شده", "already_reviewed"))
    s.review(safe, approve = false)
    assertEquals(ImageStudioBatchState.Outcome.Failed("پیش‌تر بررسی شده"), s.outcome)
    val done = item("i4", review = "approved")
    s.review(done, approve = false)
    assertFalse("بررسی‌شده دوباره نمی‌رود", f.reviews.any { it.startsWith("i4") })
  }

  @Test
  fun `سه صفحه زیرِ انبار ثبت‌اند و راهش با همان دروازهٔ بانک`() {
    val t = V3Modules.table
    assertEquals(StockPaths.IMAGE_STUDIO, t.resolve(StockPaths.IMAGE_STUDIO)!!.route.pattern)
    assertEquals(StockPaths.LIST, t.resolve(StockPaths.IMAGE_STUDIO)!!.route.parent)
    assertEquals(StockPaths.IMAGE_STUDIO_NEW, t.resolve(StockPaths.IMAGE_STUDIO_NEW)!!.route.pattern)
    assertEquals("stock/images/studio/{id}", t.resolve(StockPaths.imageStudio("01HX"))!!.route.pattern)
    val off = StockExtras.Flags(places = false, repack = false, staff = false, stockOn = false, labelTools = false)
    assertFalse(StockExtra.IMAGE_STUDIO in StockExtras.of(off))
    assertTrue(StockExtra.IMAGE_STUDIO in StockExtras.of(off.copy(imageChanges = true)))
    assertFalse(StockExtra.IMAGE_STUDIO in StockExtras.of(off.copy(imageChanges = true, staff = true)))
  }
}
