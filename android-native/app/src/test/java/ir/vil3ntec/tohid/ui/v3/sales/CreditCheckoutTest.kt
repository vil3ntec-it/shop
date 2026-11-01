package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.credit.CreditGate
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 *  CRM-03 / S07 در صندوقِ نسخهٔ ۳ (`CheckoutRoute`): همان قاعدهٔ `Sale.kt` ِ رابطِ امروز — نسیهٔ بیش از سقف بی
 *  تأییدی که همین مشتری و دست‌کم همین برد را بپوشاند ثبت نمی‌شود؛ تأییدِ درست همراهِ فاکتور می‌رود.
 */
class CreditCheckoutTest {

  private val over = CreditGate.Over(limitMinor = 100_000, balanceMinor = 90_000, addMinor = 20_000, afterMinor = 110_000)
  private val sale = SalesEngine.Checkout(debtorId = "d1", creditGate = true)
  private val ok = CreditGate.Approval("مدیر", "manager", "d1", 20_000, "مشتریِ همیشگی")

  @Test
  fun `زیرِ سقف یا بی مشتری همان گزینه‌ها`() {
    assertSame(sale, CheckoutModel.credited(sale, null, null))
    val walkIn = SalesEngine.Checkout(creditGate = true)
    assertSame(walkIn, CheckoutModel.credited(walkIn, over, null))
  }

  @Test
  fun `بیش از سقف بی تأیید ⇒ اول درخواستِ تأیید`() {
    assertNull(CheckoutModel.credited(sale, over, null))
  }

  @Test
  fun `تأییدِ همان مشتری و همان برد همراهِ فاکتور می‌رود`() {
    assertEquals(ok, CheckoutModel.credited(sale, over, ok)?.creditApproval)
    val wider = ok.copy(addMinor = 25_000)
    assertEquals(wider, CheckoutModel.credited(sale, over, wider)?.creditApproval)
  }

  @Test
  fun `مشتریِ دیگر، بردِ بزرگ‌تر، نقشِ بی‌حق یا دلیلِ خالی دوباره تأیید می‌خواهد`() {
    assertNull(CheckoutModel.credited(sale, over, ok.copy(debtorId = "d2")))
    assertNull(CheckoutModel.credited(sale, over, ok.copy(addMinor = 19_999)))
    assertNull(CheckoutModel.credited(sale, over, ok.copy(role = "staff")))
    assertNull(CheckoutModel.credited(sale, over, ok.copy(reason = " ")))
  }
}
