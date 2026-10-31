package ir.vil3ntec.tohid.core.sale

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  POS-24: قاعده‌های پیش از پایانِ فاکتور. «نسیه بی مشتری» همیشه رد است (همان امروز)؛
 *  «مشتری از مبلغِ X به بالا» و «فاکتورِ صفر» قابلِ تنظیم و پیش‌فرض خاموش‌اند.
 */
class CheckoutRulesTest {

  private fun facts(
    total: Long = 50_000,
    remaining: Long = 0,
    credit: Boolean = false,
    customer: Boolean = false,
    lines: Int = 2,
  ) = CheckoutFacts(lines = lines, finalTotalMinor = total, remainingMinor = remaining, credit = credit, hasCustomer = customer)

  @Test
  fun offRulesBlockOnlyTodaysCreditRule() {
    val off = CheckoutRules.OFF
    assertFalse(off.active)
    assertNull(CheckoutGuard.check(off, facts()))
    assertNull(CheckoutGuard.check(off, facts(total = 0)))
    assertNull(CheckoutGuard.check(off, facts(total = 999_999_999)))
    //  همان قاعدهٔ ثابتِ امروز
    assertEquals(CheckoutBreach.CREDIT_NEEDS_CUSTOMER, CheckoutGuard.check(off, facts(credit = true, remaining = 10_000)))
    //  نسیه‌ای که همه‌اش پرداخت شد بدهی ندارد ⇒ مشتری لازم نیست (همان امروز)
    assertNull(CheckoutGuard.check(off, facts(credit = true, remaining = 0)))
    assertNull(CheckoutGuard.check(off, facts(credit = true, remaining = 10_000, customer = true)))
  }

  @Test
  fun customerRequiredFromThresholdInclusive() {
    val r = CheckoutRules(customerFromMinor = 100_000)
    assertTrue(r.active)
    assertNull(CheckoutGuard.check(r, facts(total = 99_999)))
    assertEquals(CheckoutBreach.CUSTOMER_REQUIRED, CheckoutGuard.check(r, facts(total = 100_000)))
    assertEquals(CheckoutBreach.CUSTOMER_REQUIRED, CheckoutGuard.check(r, facts(total = 100_001)))
    assertNull(CheckoutGuard.check(r, facts(total = 100_000, customer = true)))
  }

  @Test
  fun zeroThresholdMeansEveryInvoiceNeedsCustomer() {
    val r = CheckoutRules(customerFromMinor = 0)
    assertEquals(CheckoutBreach.CUSTOMER_REQUIRED, CheckoutGuard.check(r, facts(total = 1)))
    assertNull(CheckoutGuard.check(r, facts(total = 1, customer = true)))
  }

  @Test
  fun zeroTotalInvoiceBlockedOnlyWhenOn() {
    val r = CheckoutRules(blockZeroTotal = true)
    assertTrue(r.active)
    assertEquals(CheckoutBreach.ZERO_TOTAL, CheckoutGuard.check(r, facts(total = 0)))
    //  مشتری داشتن فاکتورِ صفر را درست نمی‌کند
    assertEquals(CheckoutBreach.ZERO_TOTAL, CheckoutGuard.check(r, facts(total = 0, customer = true)))
    assertNull(CheckoutGuard.check(r, facts(total = 1)))
    //  سبدِ خالی کارِ این قاعده نیست (پیش‌تر رد می‌شود)
    assertNull(CheckoutGuard.check(r, facts(total = 0, lines = 0)))
  }

  @Test
  fun fixedCreditRuleComesFirstThenZeroThenCustomer() {
    val all = CheckoutRules(customerFromMinor = 0, blockZeroTotal = true)
    assertEquals(CheckoutBreach.CREDIT_NEEDS_CUSTOMER, CheckoutGuard.check(all, facts(total = 0, credit = true, remaining = 5)))
    assertEquals(CheckoutBreach.ZERO_TOTAL, CheckoutGuard.check(all, facts(total = 0)))
    assertEquals(CheckoutBreach.CUSTOMER_REQUIRED, CheckoutGuard.check(all, facts(total = 5)))
  }

  @Test
  fun negativeThresholdIsNotARule() {
    assertNull(CheckoutRules.of(customerFromMinor = -1, blockZeroTotal = false).customerFromMinor)
    assertFalse(CheckoutRules.of(customerFromMinor = -1, blockZeroTotal = false).active)
    assertEquals(0L, CheckoutRules.of(customerFromMinor = 0, blockZeroTotal = false).customerFromMinor)
    assertEquals(500_000L, CheckoutRules.of(customerFromMinor = 500_000, blockZeroTotal = true).customerFromMinor)
  }

  @Test
  fun messagesAreClearAndCarryTheThreshold() {
    assertEquals("برای فروش نسیه، قرض‌دار را انتخاب کنید", CheckoutGuard.message(CheckoutBreach.CREDIT_NEEDS_CUSTOMER, ""))
    assertEquals(
      "قاعدهٔ دکان: فاکتورِ ۱٬۰۰۰ افغانی و بالاتر مشتری می‌خواهد — مشتری را انتخاب کنید",
      CheckoutGuard.message(CheckoutBreach.CUSTOMER_REQUIRED, "۱٬۰۰۰ افغانی"),
    )
    assertEquals(
      "قاعدهٔ دکان: فاکتورِ با مبلغِ صفر ثبت نمی‌شود",
      CheckoutGuard.message(CheckoutBreach.ZERO_TOTAL, ""),
    )
    assertEquals(listOf("credit_needs_customer", "zero_total", "customer_required"), CheckoutBreach.entries.map { it.code })
  }
}
