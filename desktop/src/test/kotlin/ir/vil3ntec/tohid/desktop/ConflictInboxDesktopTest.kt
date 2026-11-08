package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.i18n.StrConflictInbox
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.core.sync.ConflictInbox
import ir.vil3ntec.tohid.data.ConflictFix
import ir.vil3ntec.tohid.data.RejectedDocs
import ir.vil3ntec.tohid.data.repo.ConflictInboxRepository
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 *  صندوقِ سندهای ردشده روی کامپیوتر — نیمهٔ برنامه، روی دیسکِ واقعیِ کامپیوتر و یک سرورِ HTTP ِ واقعیِ کوچک:
 *
 *  ۱) صندوقِ شاگرد (همین کامپیوتر) فاکتورِ نسیهٔ بالای سقف می‌فرستد و سرور رد می‌کند ⇒ op با دلیلش در دفترِ کنارِ صف
 *     (`OpQueue.drop`) ⇒ روی همان فاکتور «سرور نپذیرفت: نیازمندِ تأییدِ …» (نه فقط در لاگ).
 *  ۲) کامپیوترِ صاحب صندوق را می‌خواند: همان سند با دلیل، کارمند، دستگاه، مبلغ و قلم‌ها.
 *  ۳) «دوباره ثبت با تأییدِ من» دو بار هم‌زمان (دو کلیک) ⇒ هر دو با **همان** شناسهٔ ردیف به همان در می‌روند و سرور فقط
 *     یک بار می‌نشاند (دومی «پیش‌تر ثبت شده بود»)؛ دلیلِ «دوباره هم نپذیرفت» از بدنهٔ خطا خوانده می‌شود.
 *  ۴) نشانِ فاکتورِ شاگرد پس از آن «صاحب یا مدیر دوباره ثبت کرد» است.
 *  ۵) «اصلاح و ثبت»: کلیدِ سبد به ردیف بسته می‌ماند و پس از ثبتِ فاکتور «بستن با همین فاکتور» روی دیسک می‌ماند.
 *  نیمهٔ سرور (یک بار و فقط یک بار در دفترِ واقعی، دستگاهِ سازنده دو بار نمی‌گیرد) در `server/test/conflict-inbox.test.js`
 *  و سفرِ JB6 ِ `server/test/e2e/journeys-b.e2e.mjs` (دستگاهِ B همان کامپیوترِ صاحب است).
 */
class ConflictInboxDesktopTest {
  private val ctx = DesktopContext
  private val key = "conflict-inbox-desk-${System.nanoTime()}"
  private val cfId = "cf_00112233445566778899aabb"
  private lateinit var server: TestHttpServer

  @Before fun start() { server = TestHttpServer() }

  @After fun stop() {
    server.close()
    File(ctx.filesDir, "oplog-${OpQueue.fileKey(key)}-dropped.json").delete()
    File(ctx.filesDir, "oplog-${OpQueue.fileKey(key)}.json").delete()
    ctx.getSharedPreferences("tohid-conflict-fix", 0).edit().clear().commit()
  }

  private fun repo(): ConflictInboxRepository {
    val tokens = object : TokenStorage {
      override var accessToken: String? = "t"
      override var refreshToken: String? = null
      override var accessExpiresAt: Long = 0
      override fun save(access: String?, refresh: String?, expiresAt: Long) {}
      override fun clear() {}
    }
    return ConflictInboxRepository(ApiClient(HttpEngine(baseUrl = { server.baseUrl }, allowInsecure = true), tokens))
  }

  private fun op(id: String, table: String, row: String, vararg f: Pair<String, Any>) = SyncOp(
    opId = id, ts = 1_700_000_000_000L, table = table, rowId = row, type = SyncOp.INSERT,
    fields = buildJsonObject { f.forEach { (k, v) -> put(k, if (v is Number) JsonPrimitive(v) else JsonPrimitive(v.toString())) } },
  )

  private val row = """{"id":"$cfId","kind":"approval_required","reason":"approval_required","perm":"sales.credit","status":"%s",
    "at":1700000000000,"doc":{"table":"sales","rowId":"s-desk-1","saleId":"s-desk-1"},"number":41,"path":"v1",
    "staff":{"id":"u-ali","name":"علی","role":"staff"},"device":{"id":"desk-pos","name":"کامپیوترِ صندوق"},
    "amountMinor":1000000,"itemCount":1,"items":[{"name":"برنج","qtyMilli":2000}],"resubmittable":%s,"fixable":%s,
    "busy":false,"lastError":null,"resolvedAt":null,"resolvedBy":null,"note":"","fixedDoc":""}"""

  @Test fun `ردِ سرور روی فاکتور، صندوقِ صاحب، دوباره ثبتِ یک‌باره و نشانِ پس از آن`(): Unit = runBlocking {
    //  ۱) دفترِ کنارِ صفِ همین کامپیوتر — همان که `SyncV1Engine.pushQueue` برای ردِ سرور می‌نویسد
    val q = OpQueue(ctx, key)
    q.drop(listOf(op("op-s1", "sales", "s-desk-1", "finalTotal" to 10000, "debtGiven" to 6000, "paymentMethod" to "credit")), "approval_required")
    q.drop(listOf(op("op-i1", "saleItems", "s-desk-1-i1", "saleId" to "s-desk-1", "productId" to "p-rice", "quantity" to 2)), "tx_rejected")
    val local = RejectedDocs.v1(OpQueue(ctx, key).dropped())
    assertEquals(listOf("s-desk-1", "s-desk-1"), local.map { it.saleId })
    val mark = ConflictInbox.markers(local, emptyList()).getValue("s-desk-1")
    assertEquals("approval_required", mark.reason)
    assertEquals(StrConflictInbox.serverRejected(StrConflictInbox.reason("approval_required")), ir.vil3ntec.tohid.ui.v3.sales.history.RejectedLive.text(mark).first)

    //  ۲) و ۳) سرور: «یک بار و فقط یک بار» با کلیدِ همان ردیف
    val applied = AtomicInteger(0)
    val ids = java.util.Collections.synchronizedList(ArrayList<String>())
    server.on("/api/v1/shop/conflict-inbox") { 200 to """{"conflicts":[${row.format("open", "true", "true")}],"open":1}""" }
    server.on("/api/v1/shop/conflict-inbox/$cfId/resubmit") { r ->
      ids += r.path
      val first = applied.compareAndSet(0, 1)
      200 to """{"conflict":${row.format("resubmitted", "false", "false")},"duplicate":${!first},"applied":${if (first) 4 else 0}}"""
    }
    val page = (repo().list() as ApiResult.Success).value
    val item = page.items.single()
    assertEquals(1, page.open)
    assertEquals(listOf("علی", "کامپیوترِ صندوق", "برنج"), listOf(item.staff.name, item.deviceName, item.items.single().name))
    assertEquals(setOf("resubmit", "fix", "dismiss"), ConflictInbox.actions(item))
    val two = listOf(async { repo().resubmit(item.id) }, async { repo().resubmit(item.id) }).awaitAll()
    assertTrue(two.all { it is ApiResult.Success })
    assertEquals(listOf(false, true), two.map { (it as ApiResult.Success).value.duplicate }.sorted())
    assertEquals(1, applied.get())
    assertEquals(setOf("/api/v1/shop/conflict-inbox/$cfId/resubmit"), ids.toSet())
    assertTrue(two.all { (it as ApiResult.Success).value.item.status == ConflictInbox.RESUBMITTED })

    //  ردِ دوباره (مدیرِ بی اجازه): دلیل از بدنهٔ خطا
    server.on("/api/v1/shop/conflict-inbox/cf_x/resubmit") {
      409 to """{"error":{"code":"resubmit_rejected","message":"سرور این سند را دوباره هم نپذیرفت","reason":"approval_required","partial":false}}"""
    }
    val bad = repo().resubmit("cf_x") as ApiResult.Failure
    assertEquals("approval_required", ConflictInboxRepository.rejectReason(bad.error))

    //  ۴) نشانِ فاکتورِ شاگرد پس از دوباره ثبت
    server.on("/api/v1/shop/conflict-inbox/mine") {
      200 to """{"conflicts":[{"id":"$cfId","table":"sales","rowId":"s-desk-1","opId":"op-s1","device":"desk-pos","reason":"approval_required",
        "status":"resubmitted","at":1700000000000,"resolvedAt":1700000100000,"note":"","fixedDoc":""}]}"""
    }
    val mine = (repo().mine() as ApiResult.Success).value
    val after = ConflictInbox.markers(local, mine).getValue("s-desk-1")
    assertEquals(ConflictInbox.RESUBMITTED, after.status)
    assertEquals(StrConflictInbox.markResubmitted, ir.vil3ntec.tohid.ui.v3.sales.history.RejectedLive.text(after).first)
  }

  @Test fun `«اصلاح و ثبت» ـ ردیف فقط پس از ثبتِ همان سبد بسته می‌شود`() {
    ConflictFix.begin(ctx, "cart-k1", cfId)
    assertTrue(ConflictFix.pending(ctx, cfId))
    //  سبدِ دیگری ثبت شد ⇒ این ردیف دست نمی‌خورد
    ConflictFix.posted(ctx, "cart-other", "s-other")
    assertTrue(ConflictFix.pending(ctx, cfId))
    ConflictFix.posted(ctx, "cart-k1", "s-fixed")
    val prefs = ctx.getSharedPreferences("tohid-conflict-fix", 0)
    assertEquals(null, prefs.getString("cart-k1", null))
    //  بی نشستِ سرور در آزمون ⇒ «بستن با همین فاکتور» روی دیسک می‌ماند تا وصل شدن (`retry`)
    assertEquals("s-fixed", prefs.getString("done:$cfId", null))
  }
}
