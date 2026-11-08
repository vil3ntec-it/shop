package ir.vil3ntec.tohid.ui.components.staff

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.RemoteApproval
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.repo.ApprovalRequestsApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  STF-02 «پرسیدن از مدیر» در صندوق (`RemoteAsk`، مشترکِ `ApprovalSheet` ِ گوشی و `ApprovalDialog` ِ کامپیوتر): با سرورِ
 *  جایگزین (`ApprovalRequestsApi`) و ساعتِ دستی — ساختن با همان کلید، انتظار تا پاسخ، ارجاعِ `{v:1, request}` در
 *  `Sale.approval`، رد، گذشتِ عمر، بی اینترنت، پس گرفتن و پرچمِ خاموش (همان امروز).
 */
class RemoteAskTest {

  private val id = "apr_00000000000000000000abcd"
  private var clock = 1_700_000_000_000L
  private val need = ApprovalSlip.discountNeed("s-1", 2_000, 10_000)

  private fun pending() = RemoteApproval.Request(
    id = id, perm = need.perm, doc = need.doc, pctBp = need.pctBp, amount = need.amount, reason = "مشتریِ همیشگی",
    status = RemoteApproval.Status.PENDING, requesterId = "u-staff", createdAt = clock, expiresAt = clock + 600_000,
  )

  /** سرورِ جایگزین: پاسخ‌های [creates] و [gets] به ترتیب (آخری تکرار می‌شود) */
  private inner class FakeApi(
    val cfg: ApiResult<RemoteApproval.Config> = ApiResult.Success(RemoteApproval.Config(enabled = true, approver = false)),
    val creates: MutableList<ApiResult<RemoteApproval.Request>> = mutableListOf(),
    val gets: MutableList<ApiResult<RemoteApproval.Request>> = mutableListOf(),
  ) : ApprovalRequestsApi {
    val bodies = mutableListOf<Map<String, Any?>>()
    val cancelled = mutableListOf<String>()
    override suspend fun config() = cfg
    override suspend fun create(body: Map<String, Any?>): ApiResult<RemoteApproval.Request> {
      bodies += body
      return if (creates.size > 1) creates.removeAt(0) else creates.first()
    }
    override suspend fun get(id: String): ApiResult<RemoteApproval.Request> = if (gets.size > 1) gets.removeAt(0) else gets.first()
    override suspend fun cancel(id: String): ApiResult<RemoteApproval.Request> {
      cancelled += id
      return ApiResult.Success(pending().copy(status = RemoteApproval.Status.CANCELLED))
    }
  }

  private fun ask(api: ApprovalRequestsApi?, decision: String = "approve", n: ApprovalSlip.Need = need) =
    RemoteAsk(api, n, "shop-1", decision, now = { clock }, sleep = { ms -> clock += ms })

  /** کارِ فقط-v3 (تخفیف/لغوِ شاگرد) روی دستگاهِ v1 یا سرورِ بی v3 ⇒ نه دکمه، نه پرسیدنِ سرور — PIN همان امروز */
  @Test fun `بی همگام‌سازیِ نسخهٔ ۳ دکمه برای کارِ فقط-v3 نیست`() = runBlocking {
    val v1Device = RemoteAsk(FakeApi(), need, "shop-1", "approve", now = { clock }, sleep = { ms -> clock += ms }, v3Only = true, deviceV3 = false)
    v1Device.load()
    assertFalse(v1Device.offered)
    assertNull("دستگاهِ v1 از سرور نمی‌پرسد", v1Device.config)
    val v1Server = RemoteAsk(
      FakeApi(cfg = ApiResult.Success(RemoteApproval.Config(enabled = true, approver = false, v3 = false))), need, "shop-1", "approve",
      now = { clock }, sleep = { ms -> clock += ms }, v3Only = true, deviceV3 = true,
    )
    v1Server.load()
    assertFalse(v1Server.offered)
    val credit = RemoteAsk(
      FakeApi(cfg = ApiResult.Success(RemoteApproval.Config(enabled = true, approver = false, v3 = false))),
      ApprovalSlip.Need("sales.credit", "sales/s-1", null, 50_000), "shop-1", "approve",
      now = { clock }, sleep = { ms -> clock += ms }, v3Only = false, deviceV3 = false,
    )
    credit.load()
    assertTrue("قرض از v1 هم می‌نشیند", credit.offered)
    val v3 = RemoteAsk(FakeApi(), need, "shop-1", "approve", now = { clock }, sleep = { ms -> clock += ms }, v3Only = true, deviceV3 = true)
    v3.load()
    assertTrue(v3.offered)
  }

  @Test fun `پرچمِ خاموش، مجاز یا بی سرور ⇒ دکمه دیده نمی‌شود و برگه همان امروز است`() = runBlocking {
    val off = ask(FakeApi(cfg = ApiResult.Success(RemoteApproval.Config(enabled = false, approver = false))))
    off.load()
    assertFalse(off.offered)
    val allow = ask(FakeApi(), decision = "allow")
    allow.load()
    assertFalse(allow.offered)
    assertNull("مجاز اصلاً از سرور نمی‌پرسد", allow.config)
    val offline = ask(FakeApi(cfg = ApiResult.Failure(ApiFailure.Offline())))
    offline.load()
    assertFalse(offline.offered)
    val noBackend = ask(null)
    noBackend.load()
    assertFalse(noBackend.offered)
    val payFix = ask(FakeApi(), n = ApprovalSlip.Need("sales.pay_fix", "sales/s-1"))
    payFix.load()
    assertFalse("سرور op ِ اصلاحِ پرداخت را با درخواست نمی‌سنجد", payFix.offered)
    val on = ask(FakeApi())
    on.load()
    assertTrue(on.offered)
  }

  @Test fun `تأیید ⇒ برگهٔ ارجاع که در فاکتور همان v1-request ِ سرور می‌شود`() = runBlocking {
    val p = pending()
    val api = FakeApi(
      creates = mutableListOf(ApiResult.Success(p)),
      gets = mutableListOf(ApiResult.Success(p), ApiResult.Success(p.copy(status = RemoteApproval.Status.APPROVED, decidedBy = "u-owner", deciderName = "احمد", deciderRole = "owner"))),
    )
    val a = ask(api)
    a.load()
    var got: Pair<ApprovalSlip.Slip, RemoteApproval.Request>? = null
    a.run("مشتریِ همیشگی") { s, r -> got = s to r }
    assertNotNull(got)
    val (slip, r) = got!!
    assertEquals(RemoteApproval.Wait.APPROVED, a.wait)
    assertEquals("owner", r.deciderRole)
    assertEquals("u-owner", slip.by)
    assertTrue(a.statusText()!!.contains("احمد"))
    assertEquals(buildJsonObject { put("v", 1); put("request", id) }, DiscountGate.json(slip))
    //  همان سبد با همان تخفیف ⇒ «تأییدشده»؛ تخفیفِ بیشتر ⇒ دوباره «نیازمندِ تأیید»
    assertEquals(DiscountGate.State.APPROVED, DiscountGate.state("approve", "s-1", 20.0, 100.0, slip))
    assertEquals(DiscountGate.State.NEEDS_APPROVAL, DiscountGate.state("approve", "s-1", 30.0, 100.0, slip))
    val b = api.bodies.single()
    assertEquals("sales/s-1", b["doc"])
    assertEquals(need.pctBp, b["pctBp"])
    //  پس از تأیید بستنِ برگه درخواست را پس نمی‌گیرد
    a.withdraw()
    delay(50)
    assertTrue(api.cancelled.isEmpty())
  }

  @Test fun `برگهٔ امضاشدهٔ PIN همان کلیدهای امروز را دارد`() {
    val signed = ApprovalSlip.Slip("shop-1", "sales/s-1", "sales.discount", 2_000, 2_000, "u-owner", "key-1", 1, 2, "n".repeat(20), "r", "sig")
    val j = DiscountGate.json(signed)
    assertEquals(JsonPrimitive("sig"), j["sig"])
    assertNull(j["request"])
  }

  @Test fun `رد و گذشتِ عمر ⇒ پایان بی تأیید؛ دوباره بپرس کلیدِ تازه می‌گیرد`() = runBlocking {
    val p = pending()
    val denied = FakeApi(
      creates = mutableListOf(ApiResult.Success(p)),
      gets = mutableListOf(ApiResult.Success(p.copy(status = RemoteApproval.Status.DENIED, decidedBy = "u-owner", note = "نه امروز"))),
    )
    val a = ask(denied)
    var approved = false
    a.run("دلیل") { _, _ -> approved = true }
    assertFalse(approved)
    assertEquals(RemoteApproval.Wait.DENIED, a.wait)
    assertTrue(a.statusText()!!.contains("نه امروز"))
    a.run("دلیل") { _, _ -> approved = true }
    assertEquals(2, denied.bodies.size)
    assertTrue("پرسشِ تازه پس از پایان کلیدِ تازه دارد", denied.bodies[0]["key"] != denied.bodies[1]["key"])

    //  سرور هنوز «در انتظار» می‌گوید ولی عمر گذشت ⇒ «منقضی» با ساعتِ دستگاه و پرسیدن بس
    val stuck = FakeApi(creates = mutableListOf(ApiResult.Success(p)), gets = mutableListOf(ApiResult.Success(p)))
    val e = ask(stuck)
    e.run("دلیل") { _, _ -> approved = true }
    assertFalse(approved)
    assertEquals(RemoteApproval.Wait.EXPIRED, e.wait)
    assertTrue(clock > p.expiresAt)
  }

  @Test fun `بی اینترنت ⇒ همان کلید دوباره، سپس «بی اینترنت» و ایست؛ در میانهٔ انتظار هم تا پایانِ عمر`() = runBlocking {
    val p = pending()
    val api = FakeApi(creates = mutableListOf(ApiResult.Failure(ApiFailure.Offline())))
    val a = ask(api)
    a.run("دلیل") { _, _ -> }
    assertEquals(RemoteApproval.Wait.OFFLINE, a.wait)
    assertFalse("ایست کرد ⇒ دکمهٔ «دوباره بپرس» برمی‌گردد", a.active)
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrRemoteApproval.offline, a.statusText())
    assertEquals(4, api.bodies.size)
    assertEquals("هر تکرار همان کلید (سرور یک درخواست می‌سازد)", 1, api.bodies.map { it["key"] }.toSet().size)

    //  ساخته شد، سپس اینترنت رفت و آمد ⇒ همچنان منتظر، سپس تأیید
    val api2 = FakeApi(
      creates = mutableListOf(ApiResult.Success(p)),
      gets = mutableListOf(
        ApiResult.Failure(ApiFailure.Offline()), ApiResult.Failure(ApiFailure.Offline()), ApiResult.Failure(ApiFailure.Offline()),
        ApiResult.Success(p.copy(status = RemoteApproval.Status.APPROVED, decidedBy = "u-mgr")),
      ),
    )
    val b = ask(api2)
    var ok = false
    b.run("دلیل") { _, _ -> ok = true }
    assertTrue(ok)
  }

  @Test fun `پس گرفتن در میانهٔ انتظار درخواست را در سرور پس می‌گیرد و حلقه می‌ایستد؛ خطای پایدار بی تکرار`() = runBlocking {
    val p = pending()
    val api = FakeApi(creates = mutableListOf(ApiResult.Success(p)), gets = mutableListOf(ApiResult.Success(p)))
    val a = RemoteAsk(api, need, "shop-1", "approve", now = { clock }, sleep = { kotlinx.coroutines.awaitCancellation() })
    val job = launch { a.run("دلیل") { _, _ -> error("تأیید نباید برسد") } }
    while (a.wait != RemoteApproval.Wait.WAITING) yield()
    assertTrue(a.active)
    a.withdraw()
    job.cancel()
    job.join()
    assertEquals(RemoteApproval.Wait.CANCELLED, a.wait)
    assertFalse(a.active)
    for (i in 0 until 100) { if (api.cancelled.isNotEmpty()) break; delay(10) }
    assertEquals(listOf(id), api.cancelled)

    val refused = FakeApi(creates = mutableListOf(ApiResult.Failure(ApiFailure.Conflict("این کار برای شما تأیید نمی‌خواهد", "approval_not_needed"))))
    val r = ask(refused)
    r.run("دلیل") { _, _ -> }
    assertEquals(RemoteApproval.Wait.FAILED, r.wait)
    assertEquals("این کار برای شما تأیید نمی‌خواهد", r.statusText())
    assertEquals(1, refused.bodies.size)
  }
}
