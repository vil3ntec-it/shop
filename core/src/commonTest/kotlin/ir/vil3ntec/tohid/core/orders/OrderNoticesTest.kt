package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ORD-16: دکمهٔ «فرستادنِ دوباره» همان قاعده‌های `lib/notice-resend.js` */
class OrderNoticesTest {

  private val on = OrderNotices.RESENDABLE

  @Test
  fun onlyLatestUnsentWithFlagAndManage() {
    val received = OrderNotices.Item("r", "no_channel", 100)
    val accepted = OrderNotices.Item("a", "no_channel", 200)
    val all = listOf(received, accepted)
    assertTrue(OrderNotices.canResend(accepted, all, on, manage = true))
    assertFalse(OrderNotices.canResend(received, all, on, manage = true), "کهنه‌تر ⇒ notice_superseded")
    assertFalse(OrderNotices.canResend(accepted, all, on, manage = false), "بی orders.manage")
    assertFalse(OrderNotices.canResend(accepted, all, null, manage = true), "پرچمِ خاموش")
    assertFalse(OrderNotices.canResend(accepted, all, emptyList(), manage = true))
    for (s in listOf("sent", "pending", "sending", "superseded", "expired", "cancelled")) {
      assertFalse(OrderNotices.canResend(accepted.copy(status = s), listOf(accepted.copy(status = s)), on, manage = true), s)
    }
    for (s in listOf("failed", "rate_limited")) assertTrue(OrderNotices.canResend(accepted.copy(status = s), all, on, manage = true), s)
    //  هم‌لحظه کهنه نیست (همان قاعدهٔ سرور: فقط «دیرتر»)
    assertTrue(OrderNotices.canResend(received, listOf(received, accepted.copy(createdAt = 100)), on, manage = true))
  }

  @Test
  fun failedAndStaleCodes() {
    assertTrue(OrderNotices.failed("no_channel"))
    assertTrue(OrderNotices.failed("expired"))
    assertFalse(OrderNotices.failed("sent"))
    assertFalse(OrderNotices.failed("pending"))
    assertTrue(OrderNotices.stale("resend_limit"))
    assertTrue(OrderNotices.stale("notice_superseded"))
    assertFalse(OrderNotices.stale("idempotency_key_reused"))
  }
}
