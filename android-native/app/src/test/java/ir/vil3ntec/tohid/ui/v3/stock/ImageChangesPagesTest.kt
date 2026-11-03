package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.core.image.ImageChanges
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.ImageChangesSource
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  IMG-PRO-17 — صفحه‌های «تغییرِ دسته‌ایِ عکس‌ها» روی درِ سرورِ #882: راه فقط با بانکِ روشن و نقشِ غیرِ شاگرد؛ دسته فقط
 *  قلمِ معنادار می‌فرستد؛ تطبیقِ نامطمئن فقط با تأییدِ همان کالا؛ برگشت یا همه یا هیچ و ۴۰۹ نامِ کالاهای عوض‌شده را دارد.
 */
class ImageChangesPagesTest {

  private fun change(id: String, status: String = "applied") =
    ImageChanges.Change(id, "", status, 1, "u", 1L, null, "")

  private fun failure(code: String, detail: String? = null, conflict: Boolean = true): ApiFailure {
    val f = if (conflict) ApiFailure.Conflict("پیامِ سرور", code) else ApiFailure.NotFound("پیدا نشد", code)
    return if (detail == null) f else f.withDetail(Json.parseToJsonElement(detail).jsonObject)
  }

  private class Fake : ImageChangesSource {
    var listNext: ApiResult<List<ImageChanges.Change>> = ApiResult.Success(emptyList())
    var detailNext: ApiResult<ImageChanges.Change>? = null
    val currents = HashMap<String, ImageChanges.Current>()
    val applyQueue = ArrayDeque<ApiResult<ImageChanges.Change>>()
    var rollbackNext: ApiResult<Pair<ImageChanges.Change, Boolean>>? = null
    val sent = mutableListOf<JsonObject>()
    val resolved = mutableListOf<String>()
    override suspend fun list() = listNext
    override suspend fun detail(id: String) = detailNext!!
    override suspend fun current(productId: String): ApiResult<ImageChanges.Current> {
      resolved += productId
      return currents[productId]?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiFailure.Offline())
    }
    override suspend fun apply(body: JsonObject): ApiResult<ImageChanges.Change> { sent += body; return applyQueue.removeFirst() }
    override suspend fun rollback(id: String, reason: String) = rollbackNext!!
  }

  private fun cur(id: String, source: String, update: Int = 0, central: String? = "c-$id") =
    ImageChanges.Current(id, source, if (source == "central") central.orEmpty() else "y-$id", 1, central, 1, update, false)

  private val products = listOf("p1", "p2", "p3").associateWith { ImageChanges.Product(it, "کالا $it", "62600$it") }

  @Test
  fun `راه فقط با بانکِ روشن و نقشِ غیرِ شاگرد`() {
    assertFalse("بانک هنوز پرسیده نشده", ImageChangesGate.visible(null, staff = false))
    assertFalse("پرچمِ خاموش (پیش‌فرض)", ImageChangesGate.visible(false, staff = false))
    assertFalse("شاگرد", ImageChangesGate.visible(true, staff = true))
    assertTrue(ImageChangesGate.visible(true, staff = false))
    val off = StockExtras.Flags(places = false, repack = false, staff = false, stockOn = false, labelTools = false)
    assertFalse(StockExtra.IMAGE_CHANGES in StockExtras.of(off))
    assertTrue(StockExtra.IMAGE_CHANGES in StockExtras.of(off.copy(imageChanges = true)))
    assertFalse(StockExtra.IMAGE_CHANGES in StockExtras.of(off.copy(imageChanges = true, staff = true)))
  }

  @Test
  fun `فهرست و ردِ سرور`() = runBlocking {
    val f = Fake()
    val s = ImageChangesListState(f)
    f.listNext = ApiResult.Success(listOf(change("ch2", "rolled_back"), change("ch1")))
    s.load()
    assertEquals(listOf("ch2", "ch1"), s.changes!!.map { it.id })
    f.listNext = ApiResult.Failure(ApiFailure.Offline())
    s.load()
    assertTrue(s.failed)
    assertEquals("بی‌اینترنت فهرست می‌ماند", 2, s.changes!!.size)
    f.listNext = ApiResult.Failure(ApiFailure.Forbidden("خاموش", ImageChanges.OFF))
    s.load()
    assertTrue(s.off)
    assertNull(s.changes)
  }

  @Test
  fun `دستهٔ تازه فقط قلمِ معنادار می‌فرستد و تطبیقِ نامطمئن فقط با تأییدِ همان کالا`() = runBlocking {
    val f = Fake()
    f.currents["p1"] = cur("p1", "central", update = 3)
    f.currents["p2"] = cur("p2", "central", update = 0)
    f.currents["p3"] = cur("p3", "shop", central = "c9")
    var keys = 0
    val s = ImageBatchState(f) { "key-" + (++keys) }
    listOf("p1", "p2", "p3").forEach { assertTrue(s.toggle(it)) }
    s.check(products)
    assertEquals(listOf("p1", "p2", "p3"), f.resolved)
    assertEquals(listOf("p1", "p3"), s.lines.map { it.productId })
    assertEquals(
      listOf(ImageChanges.Planned.Skipped("p2", ImageChanges.Skip.ALREADY)),
      s.planned!!.filterIsInstance<ImageChanges.Planned.Skipped>(),
    )
    //  ۴۰۹ needs_confirmation برای قلمِ دوم ⇒ هیچ کالایی عوض نشد؛ تأییدِ همان یک کالا و فرستادنِ دوباره با همان کلید
    f.applyQueue += ApiResult.Failure(failure("needs_confirmation", """{"index":1}"""))
    f.applyQueue += ApiResult.Success(change("ch9"))
    s.send("عکس‌های تازه")
    assertEquals("p3", s.problem!!.productId)
    assertTrue(s.problem!!.confirmable)
    assertNull(s.done)
    s.confirm("p3")
    s.send("عکس‌های تازه")
    assertEquals("ch9", s.done!!.id)
    val first = f.sent[0]["items"]!!.jsonArray.map { it.jsonObject }
    val second = f.sent[1]["items"]!!.jsonArray.map { it.jsonObject }
    assertTrue("پیش از تأیید هیچ confirm", first.none { it.containsKey("confirm") })
    assertEquals(listOf(null, "true"), second.map { it["confirm"]?.toString() })
    assertEquals("کلید تا نشستن ثابت", (f.sent[0]["key"] as JsonPrimitive).content, (f.sent[1]["key"] as JsonPrimitive).content)
    assertEquals(1, keys)
  }

  @Test
  fun `کالای بارکدناجور تأیید ندارد و برداشته می‌شود؛ سقفِ صد کالا`() = runBlocking {
    val f = Fake()
    f.currents["p1"] = cur("p1", "central", update = 2)
    f.currents["p2"] = cur("p2", "central", update = 5)
    val s = ImageBatchState(f) { "k" }
    s.toggle("p1"); s.toggle("p2")
    s.check(products)
    f.applyQueue += ApiResult.Failure(failure("barcode_mismatch", """{"index":0,"product_id":"p1"}"""))
    s.send("")
    assertFalse(s.problem!!.confirmable)
    assertEquals("p1", s.problem!!.productId)
    s.remove("p1")
    assertEquals(listOf("p2"), s.lines.map { it.productId })
    assertEquals(listOf("p2"), s.selected)
    //  کارِ دیگر ⇒ بررسیِ دوباره لازم
    s.choose(ImageChanges.Action.REVERT_CENTRAL)
    assertNull(s.planned)
    val big = ImageBatchState(f) { "k" }
    repeat(ImageChanges.MAX_ITEMS) { assertTrue(big.toggle("q$it")) }
    assertFalse("سقفِ سرور", big.toggle("q-extra"))
  }

  @Test
  fun `برگشت یا همه یا هیچ و ۴۰۹ نامِ کالاهای عوض‌شده را دارد`() = runBlocking {
    val f = Fake()
    val s = ImageChangeDetailState(f, "ch1")
    f.detailNext = ApiResult.Success(change("ch1"))
    s.load()
    f.rollbackNext = ApiResult.Failure(failure("changed_since", """{"products":["p1","p2"]}"""))
    s.rollback("اشتباه")
    assertEquals(ImageChangeDetailState.Outcome.ChangedSince(listOf("p1", "p2")), s.outcome)
    assertFalse("هیچ چیز برنگشت", s.change!!.rolledBack)
    f.rollbackNext = ApiResult.Success(change("ch1", "rolled_back") to false)
    s.rollback("")
    assertEquals(ImageChangeDetailState.Outcome.Done, s.outcome)
    assertTrue(s.change!!.rolledBack)
    f.rollbackNext = ApiResult.Success(change("ch1", "rolled_back") to true)
    s.rollback("")
    assertEquals(ImageChangeDetailState.Outcome.Already, s.outcome)
  }

  @Test
  fun `سه صفحه زیرِ انبار ثبت‌اند`() {
    val t = V3Modules.table
    assertEquals(StockPaths.IMAGE_CHANGES, t.resolve(StockPaths.IMAGE_CHANGES)!!.route.pattern)
    assertEquals(StockPaths.LIST, t.resolve(StockPaths.IMAGE_CHANGES)!!.route.parent)
    assertEquals(StockPaths.IMAGE_CHANGES_NEW, t.resolve(StockPaths.IMAGE_CHANGES_NEW)!!.route.pattern)
    assertEquals("stock/images/changes/{id}", t.resolve(StockPaths.imageChange("01HX"))!!.route.pattern)
    assertEquals(listOf("stock", StockPaths.IMAGE_CHANGES, "stock/images/changes/01HX"), t.chain(StockPaths.imageChange("01HX")))
  }
}
