package ir.vil3ntec.tohid.ui.v3.branches

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.stock.BranchTransfer
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Direction
import ir.vil3ntec.tohid.core.stock.BranchTransfer.End
import ir.vil3ntec.tohid.core.stock.BranchTransfer.Status
import ir.vil3ntec.tohid.core.stock.BranchTransfer.TLine
import ir.vil3ntec.tohid.core.stock.BranchTransferLedger
import ir.vil3ntec.tohid.data.BranchTransferBook
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.data.repo.BranchTransfersSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  INV-11/S21 (فازِ ۴ ِ شعبه‌ها): حالِ صفحه‌های «انتقال میانِ شعبه‌ها» بی سرور — پالایه‌ها و خلاصه، «خاموش» و خطا، هر گام یک
 *  کلید تا پاسخِ روشن (تکرارِ پس از قطعِ نت همان گام)، پس از هر پاسخِ روشن آینهٔ دفتر، فرم (پیش‌نویس/ارسال/ویرایش)؛ و آینهٔ
 *  دفترِ دستگاه (`BranchTransferBook`): یک ردیف برای هر (انتقال، کالا، سو)، مقدار «تنظیم» نه «افزوده»، کالای نرسیده نه، و نقشِ
 *  صندوق‌دار هرگز. کالبدِ صفحه (V3RoutePage، یک V3Fab، Chrome.OWN) از متنِ کد.
 */
class BranchTransfersStateTest {

  private val a = "brn_" + "a".repeat(24)
  private val b = "brn_" + "b".repeat(24)
  private fun id(n: Int) = "bxf_" + n.toString().padStart(24, '0')

  private fun t(n: Int, status: Status, dir: Direction, received: Long? = null, returned: Long = 0, sentAt: Long? = 10) = BranchTransfer.Transfer(
    id = id(n), direction = dir,
    fromBranch = if (dir == Direction.OUT) End(BranchTransfer.MAIN, "", true) else End(a, "الف", false),
    toBranch = if (dir == Direction.OUT) End(b, "ب", false) else End(BranchTransfer.MAIN, "", true),
    from = "shop_floor", to = "warehouse", status = status,
    lines = listOf(TLine("tea", 3000, received, if (status.open) 3000 - (received ?: 0) else 0, 0, returned)), sentAt = sentAt,
  )

  private class Fake(var all: List<BranchTransfer.Transfer>) : BranchTransfersSource {
    var listAnswer: ApiResult<List<BranchTransfer.Transfer>>? = null
    var stepAnswer: ((BranchTransfer.Call) -> ApiResult<BranchTransfer.Transfer>)? = null
    val calls = mutableListOf<BranchTransfer.Call>()
    override suspend fun list() = listAnswer ?: ApiResult.Success(all)
    override suspend fun destinations() = ApiResult.Success(listOf(End("brn_" + "b".repeat(24), "ب", false)))
    override suspend fun detail(id: String): ApiResult<Pair<BranchTransfer.Transfer, List<BranchTransfer.Step>>> =
      all.firstOrNull { it.id == id }?.let { ApiResult.Success(it to emptyList()) } ?: ApiResult.Failure(ApiFailure.NotFound("نه", "transfer_not_found"))
    override suspend fun step(call: BranchTransfer.Call): ApiResult<BranchTransfer.Transfer> {
      calls += call
      return stepAnswer?.invoke(call) ?: ApiResult.Success(all.first())
    }
  }

  @Test fun `فهرست پالایه دارد، خلاصه همان شمارها، خاموش و خطا روشن`() = runBlocking {
    val all = listOf(
      t(1, Status.IN_TRANSIT, Direction.IN), t(2, Status.PARTIAL, Direction.IN, received = 1000), t(3, Status.IN_TRANSIT, Direction.OUT),
      t(4, Status.DRAFT, Direction.OUT, sentAt = null), t(5, Status.RECEIVED, Direction.IN, received = 3000), t(6, Status.CANCELLED, Direction.OUT, returned = 3000),
    )
    val f = Fake(all)
    val seen = mutableListOf<List<BranchTransfer.Transfer>>()
    val s = BranchTransfersState(f, BranchTransfer.MAIN) { seen += it }
    s.load()
    assertEquals(listOf(all), seen)
    assertEquals(listOf(id(1), id(2)), s.shown.map { it.id })
    assertEquals(2 to 1, s.incomingOpen to s.outgoingOpen)
    assertEquals(1, s.draftCount)
    s.filter = TransferFilter.DRAFTS
    assertEquals(listOf(id(4)), s.shown.map { it.id })
    s.filter = TransferFilter.CLOSED
    assertEquals(listOf(id(5), id(6)), s.shown.map { it.id })
    assertEquals("از الف", BranchTransfersState.title(all[0]))
    assertEquals("به ب", BranchTransfersState.title(all[2]))
    //  پرچمِ خاموش (۴۰۴ ِ درِ ناشناخته) ⇒ «روشن نیست»؛ بی‌اینترنت ⇒ خطا و فهرستِ پیشین می‌ماند
    f.listAnswer = ApiResult.Failure(ApiFailure.Offline())
    s.load()
    assertTrue(s.failed)
    assertEquals(all, s.list)
    f.listAnswer = ApiResult.Failure(ApiFailure.NotFound("نه", "not_found"))
    s.load()
    assertTrue(s.off)
    assertNull(s.list)
  }

  @Test fun `هر گام یک کلید تا پاسخِ روشن، پس از آن آینهٔ دفتر و کلیدِ تازه`() = runBlocking {
    val incoming = t(1, Status.IN_TRANSIT, Direction.IN)
    val f = Fake(listOf(incoming))
    val seen = mutableListOf<List<BranchTransfer.Transfer>>()
    val s = BranchTransferState(f, BranchTransfer.MAIN, id(1)) { seen += it }
    s.load()
    assertTrue(s.canReceive)
    assertFalse(s.canSend || s.canCancel)
    val k1 = s.stepKey
    //  قطعِ نت ⇒ پیام، همان کلید (تلاشِ دوباره همان گام)
    f.stepAnswer = { ApiResult.Failure(ApiFailure.Offline()) }
    assertNull(s.receive(emptyMap(), final = true, reason = ""))
    assertNotNull(s.message)
    assertEquals(k1, s.stepKey)
    //  پاسخِ روشن ⇒ انتقالِ تازه، آینه، کلیدِ تازه
    val done = incoming.copy(status = Status.RECEIVED, lines = listOf(TLine("tea", 3000, 3000, 0, 0)))
    f.stepAnswer = { ApiResult.Success(done) }
    assertEquals(done, s.receive(emptyMap(), final = true, reason = ""))
    assertEquals(2, f.calls.size)
    assertEquals(f.calls[0].headers[BranchTransfer.HEADER_KEY], f.calls[1].headers[BranchTransfer.HEADER_KEY])
    assertEquals(k1, f.calls[1].headers[BranchTransfer.HEADER_KEY])
    assertNotEquals(k1, s.stepKey)
    assertEquals(done, seen.last().single())
    assertEquals(Status.RECEIVED, s.transfer?.status)
    //  کسری بی دلیل ⇒ پیش‌سنجی، بی درخواست
    val s2 = BranchTransferState(Fake(listOf(incoming)), BranchTransfer.MAIN, id(1))
    s2.load()
    assertNull(s2.receive(mapOf("tea" to 1000L), final = true, reason = ""))
    assertNotNull(s2.message)
    //  ناموجود/بیرون ⇒ «پیدا نشد»
    val s3 = BranchTransferState(f, BranchTransfer.MAIN, id(9))
    s3.load()
    assertTrue(s3.missing)
  }

  @Test fun `فرستنده پیش‌نویس می‌فرستد و لغو می‌کند، لغوِ فرستاده فقط با دلیل`() = runBlocking {
    val draft = t(4, Status.DRAFT, Direction.OUT, sentAt = null)
    val f = Fake(listOf(draft))
    val s = BranchTransferState(f, BranchTransfer.MAIN, id(4))
    s.load()
    assertTrue(s.canSend && s.canCancel)
    assertFalse(s.canReceive)
    f.stepAnswer = { ApiResult.Success(draft.copy(status = Status.IN_TRANSIT, sentAt = 20, lines = listOf(TLine("tea", 3000, null, 3000, 0)))) }
    s.sendDraft()
    assertTrue(f.calls.single().path.endsWith("/send"))
    assertTrue(s.canCancel)
    assertNull(s.cancel(""))
    assertEquals("بی دلیل درخواستی نرفت", 1, f.calls.size)
    s.cancel("اشتباه")
    assertTrue(f.calls.last().path.endsWith("/cancel"))
    assertEquals("اشتباه", f.calls.last().body!!["reason"]!!.jsonPrimitive.content)
  }

  @Test fun `فرم — پیش‌نویس و ارسال همان در با پرچمِ پیش‌نویس، ویرایش PUT، یک بار`() = runBlocking {
    val f = Fake(listOf(t(4, Status.DRAFT, Direction.OUT, sentAt = null)))
    val s = BranchTransferFormState(f, BranchTransfer.MAIN)
    s.load()
    assertEquals("یک مقصد ⇒ خودش", "brn_" + "b".repeat(24), s.draft.toBranch)
    assertNull("بی کالا ⇒ پیش‌سنجی", s.submit(asDraft = false))
    assertTrue(f.calls.isEmpty())
    s.draft = s.draft.with("tea", 2000)
    assertNotNull(s.submit(asDraft = true))
    assertEquals("true", f.calls.single().body!!["draft"]!!.jsonPrimitive.content)
    assertNull("پس از پاسخِ روشن دوباره نمی‌رود", s.submit(asDraft = false))
    assertEquals(1, f.calls.size)
    val e = BranchTransferFormState(f, BranchTransfer.MAIN, editing = id(4))
    e.load()
    assertEquals(listOf("tea"), e.draft.lines.map { it.productId })
    e.submit(asDraft = true)
    assertEquals("PUT", f.calls.last().method)
    assertTrue(f.calls.last().path.endsWith("/${id(4)}"))
  }

  @Test fun `آینهٔ دفتر — یک ردیف برای هر کالا و سو، تنظیم نه افزودن، کالای نرسیده نه، صندوق‌دار نه`() {
    val d = ShopData(
      products = listOf(Product("tea", "چای", "", "دانه"), Product("rice", "برنج", "", "کیلو")),
      warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "tea", units = 10.0)),
    )
    val sent = t(3, Status.IN_TRANSIT, Direction.OUT).copy(lines = listOf(TLine("tea", 3000, null, 3000, 0), TLine("ghost", 1000, null, 1000, 0)))
    val w1 = BranchTransferLedger.planAll(listOf(sent), BranchTransfer.MAIN, BranchTransferBook.have(d))
    val d1 = BranchTransferBook.apply(d, w1, listOf(sent), "2026-11-16", 5)!!
    val row = d1.warehouseEntries.single { it.adjKind == BranchTransferLedger.ADJ_KIND }
    assertEquals(-3.0, row.units, 0.0)
    assertEquals(sent.id, row.refId)
    assertTrue(row.isAdjustment)
    assertTrue(row.notes.contains("ب"))
    assertEquals("کالای نیامده در دفتر ⇒ این بار هیچ", 2, d1.warehouseEntries.size)
    //  دوباره ⇒ هیچ
    assertNull(BranchTransferBook.apply(d1, BranchTransferLedger.planAll(listOf(sent), BranchTransfer.MAIN, BranchTransferBook.have(d1)), listOf(sent), "2026-11-16", 6))
    //  رد پس از ۱ ⇒ همان ردیف −۱
    val rej = sent.copy(status = Status.REJECTED, lines = listOf(TLine("tea", 3000, 1000, 0, 0, 2000)))
    val d2 = BranchTransferBook.apply(d1, BranchTransferLedger.planAll(listOf(rej), BranchTransfer.MAIN, BranchTransferBook.have(d1)), listOf(rej), "2026-11-16", 7)!!
    assertEquals(listOf(-1.0), d2.warehouseEntries.filter { it.adjKind == BranchTransferLedger.ADJ_KIND }.map { it.units })
    assertEquals(d1.warehouseEntries.size, d2.warehouseEntries.size)
    //  نقش: صندوق‌دار (قاعدهٔ ردیف) هرگز؛ صاحب، مدیر، شاگرد، انباردار بله
    assertFalse(BranchTransferBook.canMirror("cashier"))
    assertFalse(BranchTransferBook.canMirror("accountant"))
    for (r in listOf("owner", "manager", "staff", "storekeeper")) assertTrue(r, BranchTransferBook.canMirror(r))
  }

  @Test fun `کالبدِ صفحه — مسیرهای Chrome_OWN، V3RoutePage، یک V3Fab و V3Summary، کامپیوتر با ui_desk`() {
    val root = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/ui/v3/branches") }.first { it.isDirectory }
    val module = File(root, "BranchesModule.kt").readText()
    for (p in listOf("BranchTransferPaths.LIST", "BranchTransferPaths.NEW", "\"branches/transfers/{id}\"", "\"branches/transfers/{id}/edit\"")) {
      val line = module.lines().first { p in it && "V3Route(" in it }
      assertTrue(line, "Chrome.OWN" in line)
    }
    val pages = File(root, "BranchTransfersPages.kt").readText()
    for (fn in listOf("BranchTransfersRoute", "BranchTransferRoute", "BranchTransferFormRoute")) {
      val at = pages.indexOf("fun RouteScope.$fn(")
      assertTrue(fn, at >= 0)
      val body = pages.substring(at, pages.indexOf("\n}\n", at))
      assertTrue("$fn: V3RoutePage", "V3RoutePage(" in body)
      assertTrue("$fn: V3Fab در جای قاب", Regex("""fab = """).containsMatchIn(body))
      assertFalse("$fn: دکمهٔ تمام‌پهنای «تازه»", Regex("""V3Button\(\s*Str\w+\.new""").containsMatchIn(body))
    }
    assertTrue(pages.contains("V3Summary("))
    assertTrue(pages.contains("if (desk) return DeskBranchTransfers("))
    val desk = File(root, "DeskBranchTransfers.kt").readText()
    for (k in listOf("DeskGrid(", "DeskPane(", "DeskSplit(", "DeskToolbar(")) assertTrue(k, desk.contains(k))
  }
}
