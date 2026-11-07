package ir.vil3ntec.tohid.core.pay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  PRM-15 — اعطای دستیِ اعتبارِ فروشگاه (هدیه بی کارت): فقط با تصمیمِ «مجاز» ِ `sales.discount` برای همان مبلغ (هدیه
 *  همان دادنِ پول بی نقد است)، فقط به مشتریِ موجود و فعال، با مبلغِ درست و دلیلِ نوشته. «نیازمندِ تأیید» هم رد است
 *  (این‌جا پنجرهٔ PIN نیست). روی JVM و شبیه‌سازِ iOS.
 */
class StoreCreditGrantTest {
  @Test
  fun anAllowedGrantToAnActiveCustomerWithAReasonIsAccepted() {
    assertNull(StoreCredit.grantProblem(true, 50_000, "  مشتریِ همیشگی  ", "allow"))
    assertNull(StoreCredit.grantProblem(true, 1, "abc", "allow"))
    assertNull(StoreCredit.grantProblem(true, GiftCard.MAX_AMOUNT, "abc", "allow"))
  }

  @Test
  fun onlyAnAllowDecisionGrantsNotApproveOrDeny() {
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", "deny"))
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", "approve"))
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", ""))
    //  اجازه پیش از هر چیز: بی اجازه حتی مبلغِ بد هم «اجازه نیست» است
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(false, 0, null, "deny"))
  }

  @Test
  fun anApproveDecisionNeedsTheApprovalOfAnotherManagerWhoIsAllowedTheSameAmount() {
    val mgr = StoreCredit.Approval("مدیرِ دکان", "manager", "مشتریِ عمده", "allow")
    assertNull(StoreCredit.grantProblem(true, 50_000, "هدیه", "approve", mgr, "شاگرد"))
    assertNull(StoreCredit.grantProblem(true, 50_000, "هدیه", "approve", mgr.copy(role = "owner"), "شاگرد"))
    //  بی تأیید، با تأییدکننده‌ای که خودش این مبلغ را «مجاز» ندارد، با نقشِ دیگر، بی دلیل یا خودتأیید ⇒ نه
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", "approve", null, "شاگرد"))
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", "approve", mgr.copy(approverDecision = "approve"), "شاگرد"))
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", "approve", mgr.copy(role = "staff"), "شاگرد"))
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", "approve", mgr.copy(reason = "  "), "شاگرد"))
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", "approve", mgr, " مدیرِ دکان "))
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", "approve", mgr.copy(by = ""), "شاگرد"))
    //  تأیید «ممنوع» را باز نمی‌کند؛ و پس از تأیید هم مشتری، مبلغ و دلیل سرِ جایشان
    assertEquals(StoreCredit.GRANT_NOT_ALLOWED, StoreCredit.grantProblem(true, 50_000, "هدیه", "deny", mgr, "شاگرد"))
    assertEquals(StoreCredit.NO_CUSTOMER, StoreCredit.grantProblem(false, 50_000, "هدیه", "approve", mgr, "شاگرد"))
    assertEquals(StoreCredit.INVALID_AMOUNT, StoreCredit.grantProblem(true, 0, "هدیه", "approve", mgr, "شاگرد"))
    assertEquals(GiftCard.Code.REASON_REQUIRED, StoreCredit.grantProblem(true, 50_000, "ab", "approve", mgr, "شاگرد"))
  }

  @Test
  fun customerAmountAndReasonAreRequired() {
    assertEquals(StoreCredit.NO_CUSTOMER, StoreCredit.grantProblem(false, 50_000, "هدیه", "allow"))
    assertEquals(StoreCredit.INVALID_AMOUNT, StoreCredit.grantProblem(true, 0, "هدیه", "allow"))
    assertEquals(StoreCredit.INVALID_AMOUNT, StoreCredit.grantProblem(true, -100, "هدیه", "allow"))
    assertEquals(StoreCredit.INVALID_AMOUNT, StoreCredit.grantProblem(true, GiftCard.MAX_AMOUNT + 1, "هدیه", "allow"))
    assertEquals(GiftCard.Code.REASON_REQUIRED, StoreCredit.grantProblem(true, 50_000, null, "allow"))
    assertEquals(GiftCard.Code.REASON_REQUIRED, StoreCredit.grantProblem(true, 50_000, "  ab  ", "allow"))
    assertEquals(GiftCard.Code.TEXT_TOO_LONG, StoreCredit.grantProblem(true, 50_000, "x".repeat(GiftCard.MAX_TEXT + 1), "allow"))
  }

  @Test
  fun theBalanceAfterAGrantMovesTowardsCredit() {
    //  ماندهٔ دفتر: مثبت = بدهی، منفی = طلب. هدیه یک «رسید» ِ غیرنقد است ⇒ اول از بدهی کم می‌شود، بعد طلب
    assertEquals(-50_000L, StoreCredit.afterGrant(0, 50_000))
    assertEquals(20_000L, StoreCredit.afterGrant(70_000, 50_000))
    assertEquals(-80_000L, StoreCredit.afterGrant(-30_000, 50_000))
    assertEquals(50_000L, StoreCredit.available(StoreCredit.afterGrant(0, 50_000)))
    assertEquals(0L, StoreCredit.available(StoreCredit.afterGrant(70_000, 50_000)))
  }
}
