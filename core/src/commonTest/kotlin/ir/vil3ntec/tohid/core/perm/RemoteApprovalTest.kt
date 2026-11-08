package ir.vil3ntec.tohid.core.perm

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  STF-02 «پرسیدن از مدیر» — قاعده‌های کلاینتِ `RemoteApproval` در برابرِ قراردادِ `server/src/lib/approval-requests.js`:
 *  دیده شدنِ دکمه (پرچم + «نیازمندِ تأیید» + کارِ پذیرفتنی)، بدنهٔ درخواست، ارجاعِ `{v:1, request}`، حالِ انتظار، پرسیدنِ
 *  آرام و صندوقِ تأییدِ مدیر (فقط صاحب/مدیرِ دارای اختیارِ همان مقدار، نه خودش).
 */
class RemoteApprovalTest {

  private val id = "apr_0123456789abcdef01234567"
  private val t0 = 1_700_000_000_000L

  private fun req(
    status: RemoteApproval.Status = RemoteApproval.Status.PENDING,
    requester: String = "u-staff",
    decidedBy: String? = null,
    pctBp: Long? = 2_000,
    amount: Long? = 2_000,
  ) = RemoteApproval.Request(
    id = id, perm = "sales.discount", doc = "sales/s-1", pctBp = pctBp, amount = amount, reason = "مشتریِ همیشگی",
    status = status, requesterId = requester, requesterName = "کریم", createdAt = t0, expiresAt = t0 + 600_000,
    decidedBy = decidedBy,
  )

  @Test fun offeredOnlyWithFlagApproveAndServerPerm() {
    val on = RemoteApproval.Config(enabled = true, approver = false)
    assertTrue(RemoteApproval.offered(on, "approve", "sales.discount"))
    assertTrue(RemoteApproval.offered(on, "approve", "sales.cancel"))
    assertTrue(RemoteApproval.offered(on, "approve", "returns.create"))
    assertFalse(RemoteApproval.offered(on, "approve", "sales.pay_fix"), "سرور op ِ اصلاحِ پرداخت را با درخواست نمی‌سنجد")
    assertFalse(RemoteApproval.offered(on, "allow", "sales.discount"), "مجاز تأیید نمی‌خواهد")
    assertFalse(RemoteApproval.offered(on, "deny", "sales.discount"), "ممنوع با تأیید هم نه")
    assertFalse(RemoteApproval.offered(RemoteApproval.Config(enabled = false, approver = false), "approve", "sales.discount"), "پرچمِ خاموش ⇒ همان امروز")
    assertFalse(RemoteApproval.offered(null, "approve", "sales.discount"), "بی اینترنت یا پاسخ ⇒ همان امروز")
  }

  @Test fun bodyMatchesServerContract() {
    val key = RemoteApproval.newKey(Random(7))
    val need = ApprovalSlip.discountNeed("s-1", 2_000, 10_000)
    val b = assertNotNull(RemoteApproval.body(key, need, "  مشتریِ همیشگی "))
    assertEquals(key, b["key"])
    assertEquals("sales.discount", b["perm"])
    assertEquals("sales/s-1", b["doc"])
    assertEquals(need.pctBp, b["pctBp"], "همان درصدِ `discountBp` (با بخششِ نیم افغانی) که سرور می‌سنجد")
    assertEquals(2_000L, b["amount"])
    assertEquals("مشتریِ همیشگی", b["reason"])
    assertFalse("ttlMs" in b, "عمرِ پیش‌فرض را سرور می‌گذارد")
    val cancel = assertNotNull(RemoteApproval.body(key, CancelApproval.need("s-1", 12_500), "اشتباه"))
    assertFalse("pctBp" in cancel)
    assertEquals(12_500L, cancel["amount"])
    assertNull(RemoteApproval.body(key, need, " "), "دلیل لازم است")
    assertNull(RemoteApproval.body(key, need, "دو\nخط"))
    assertNull(RemoteApproval.body("short", need, "x"), "کلیدِ کوتاه")
    assertNull(RemoteApproval.body(key, need.copy(perm = "sales.pay_fix"), "x"))
    assertNull(RemoteApproval.body(key, need.copy(doc = "bad doc"), "x"))
    assertNull(RemoteApproval.body(key, ApprovalSlip.Need("returns.create", "returns/r-1"), "x"), "برگشت بی مبلغ معنا ندارد")
  }

  @Test fun newKeyIsValidAndFresh() {
    val a = RemoteApproval.newKey()
    val b = RemoteApproval.newKey()
    assertTrue(Regex("^[A-Za-z0-9_-]{8,64}$").matches(a))
    assertTrue(a != b)
  }

  @Test fun parseReadsServerShape() {
    val m = mapOf(
      "request" to mapOf(
        "id" to id, "perm" to "sales.discount", "doc" to "sales/s-1", "pctBp" to 2000, "amount" to 2000.0,
        "reason" to "r", "status" to "approved", "requesterId" to "u-staff", "requesterName" to "کریم",
        "createdAt" to t0, "expiresAt" to t0 + 600_000, "decidedBy" to "u-owner", "deciderName" to "احمد",
        "deciderRole" to "owner", "note" to "",
      ),
      "duplicate" to false,
    )
    val r = assertNotNull(RemoteApproval.parse(m))
    assertEquals(RemoteApproval.Status.APPROVED, r.status)
    assertEquals(2_000L, r.pctBp)
    assertEquals(2_000L, r.amount)
    assertEquals("u-owner", r.decidedBy)
    assertEquals("owner", r.deciderRole)
    assertNull(RemoteApproval.parse(mapOf("request" to mapOf("id" to "x", "perm" to "p", "doc" to "d"))), "شناسهٔ نادرست")
    assertEquals(1, RemoteApproval.parseList(mapOf("requests" to listOf(m["request"], "junk"))).size)
    val cfg = RemoteApproval.parseConfig(mapOf("enabled" to true, "approver" to false, "ttlMs" to 600000))
    assertTrue(cfg.enabled)
    assertFalse(cfg.approver)
    assertEquals(600_000L, cfg.ttlMs)
    assertFalse(RemoteApproval.parseConfig(null).enabled)
  }

  @Test fun approvedRequestBecomesReferenceSlipThatCoversOnlyItsNeed() {
    assertNull(RemoteApproval.slipOf("shop-1", req()), "در انتظار برگه نیست")
    assertNull(RemoteApproval.slipOf("shop-1", req(RemoteApproval.Status.DENIED, decidedBy = "u-owner")))
    val s = assertNotNull(RemoteApproval.slipOf("shop-1", req(RemoteApproval.Status.APPROVED, decidedBy = "u-owner")))
    assertTrue(RemoteApproval.isRef(s))
    assertEquals(mapOf<String, Any?>("v" to 1, "request" to id), RemoteApproval.ref(s))
    assertEquals("u-owner", s.by)
    assertNull(ApprovalSlip.covers(s, ApprovalSlip.discountNeed("s-1", 2_000, 10_000)))
    assertNull(ApprovalSlip.covers(s, ApprovalSlip.discountNeed("s-1", 1_000, 10_000)), "کمتر هم پوشیده است")
    assertEquals("approval_too_small", ApprovalSlip.covers(s, ApprovalSlip.discountNeed("s-1", 3_000, 10_000)))
    assertEquals("approval_mismatch", ApprovalSlip.covers(s, ApprovalSlip.discountNeed("s-2", 2_000, 10_000)))
    val signed = s.copy(key = "k-1", nonce = "n".repeat(20))
    assertFalse(RemoteApproval.isRef(signed), "برگهٔ امضاشدهٔ PIN همان راهِ امروز را می‌رود")
  }

  @Test fun waitStatesFollowServerAndClock() {
    assertEquals(RemoteApproval.Wait.SENDING, RemoteApproval.waitOf(null, t0))
    assertEquals(RemoteApproval.Wait.OFFLINE, RemoteApproval.waitOf(null, t0, netErrors = 2))
    assertEquals(RemoteApproval.Wait.WAITING, RemoteApproval.waitOf(req(), t0 + 1))
    assertEquals(RemoteApproval.Wait.WAITING, RemoteApproval.waitOf(req(), t0 + 1, netErrors = 1), "یک خطای گذرا هنوز «منتظر»")
    assertEquals(RemoteApproval.Wait.OFFLINE, RemoteApproval.waitOf(req(), t0 + 1, netErrors = 3))
    assertEquals(RemoteApproval.Wait.EXPIRED, RemoteApproval.waitOf(req(), t0 + 600_001))
    assertEquals(RemoteApproval.Wait.APPROVED, RemoteApproval.waitOf(req(RemoteApproval.Status.APPROVED, decidedBy = "u"), t0 + 1))
    assertEquals(RemoteApproval.Wait.EXPIRED, RemoteApproval.waitOf(req(RemoteApproval.Status.APPROVED, decidedBy = "u"), t0 + 600_001))
    assertEquals(RemoteApproval.Wait.DENIED, RemoteApproval.waitOf(req(RemoteApproval.Status.DENIED), t0 + 1))
    assertEquals(RemoteApproval.Wait.CANCELLED, RemoteApproval.waitOf(req(RemoteApproval.Status.CANCELLED), t0 + 1))
    assertEquals(RemoteApproval.Wait.FAILED, RemoteApproval.waitOf(req(RemoteApproval.Status.USED), t0 + 1))
    assertTrue(RemoteApproval.done(RemoteApproval.Wait.DENIED))
    assertFalse(RemoteApproval.done(RemoteApproval.Wait.OFFLINE), "بی اینترنت پایان نیست؛ دوباره می‌پرسد")
    assertEquals(RemoteApproval.Wait.EXPIRED, RemoteApproval.waitOfError("request_expired"))
    assertEquals(RemoteApproval.Wait.FAILED, RemoteApproval.waitOfError("approval_requests_disabled"))
    assertNull(RemoteApproval.waitOfError("offline"))
    assertEquals(600L, RemoteApproval.secondsLeft(req(), t0))
    assertEquals(0L, RemoteApproval.secondsLeft(req(), t0 + 700_000))
  }

  @Test fun pollingIsPoliteAndStopsAtExpiry() {
    val exp = t0 + 600_000
    assertEquals(2_000L, RemoteApproval.pollDelayMs(0, t0, exp))
    assertEquals(5_000L, RemoteApproval.pollDelayMs(4, t0, exp))
    assertEquals(RemoteApproval.POLL_MAX_MS, RemoteApproval.pollDelayMs(50, t0, exp))
    for (i in 0..60) {
      val d = RemoteApproval.pollDelayMs(i, t0, exp)
      assertTrue(d in 500L..RemoteApproval.POLL_MAX_MS, "فاصله در بازهٔ مؤدبانه: $d")
    }
    assertEquals(1_500L, RemoteApproval.pollDelayMs(50, exp - 1_000, exp), "پرسیدنِ آخر درست پس از پایانِ عمر")
    assertEquals(500L, RemoteApproval.pollDelayMs(50, exp + 5_000, exp))
  }

  @Test fun inboxShowsOnlyToApproversAllowedForThatAmount() {
    val r = req()
    assertTrue(RemoteApproval.canDecide("u-owner", "owner", "allow", r, t0 + 1))
    assertTrue(RemoteApproval.canDecide("u-mgr", "manager", "allow", r, t0 + 1))
    assertFalse(RemoteApproval.canDecide("u-mgr", "manager", "approve", r, t0 + 1), "مدیرِ کم‌اختیار برای همین مقدار نه")
    assertFalse(RemoteApproval.canDecide("u-mgr", "manager", "deny", r, t0 + 1))
    assertFalse(RemoteApproval.canDecide("u-x", "staff", "allow", r, t0 + 1), "فقط صاحب/مدیر")
    assertFalse(RemoteApproval.canDecide("u-staff", "owner", "allow", r, t0 + 1), "کسی کارِ خودش را تأیید نمی‌کند")
    assertFalse(RemoteApproval.canDecide("", "owner", "allow", r, t0 + 1))
    assertFalse(RemoteApproval.canDecide("u-owner", "owner", "allow", r, t0 + 600_001), "گذشته نه")
    assertFalse(RemoteApproval.canDecide("u-owner", "owner", "allow", req(RemoteApproval.Status.APPROVED, decidedBy = "u"), t0 + 1))
    assertEquals(mapOf("pct" to 20.0, "amount" to 2_000.0), RemoteApproval.evalReq(r))

    val small = r.copy(id = "apr_0123456789abcdef0123456a", amount = 500, pctBp = 500, createdAt = t0 + 5)
    val mine = r.copy(id = "apr_0123456789abcdef0123456b", requesterId = "u-mgr", createdAt = t0 + 9)
    //  مدیری که فقط تا ۱۰٪ مجاز است: ۲۰٪ را نمی‌بیند، ۵٪ را می‌بیند؛ درخواستِ خودش را هیچ
    val box = RemoteApproval.inbox(listOf(r, small, mine), "u-mgr", "manager", t0 + 10) { q ->
      if ((q.pctBp ?: 0) <= 1_000) "allow" else "approve"
    }
    assertEquals(listOf(small.id), box.map { it.id })
    assertEquals("sales" to "s-1", RemoteApproval.docParts("sales/s-1"))
  }
}
