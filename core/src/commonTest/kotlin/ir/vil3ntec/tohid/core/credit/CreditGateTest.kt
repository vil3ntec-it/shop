package ir.vil3ntec.tohid.core.credit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** CRM-03 / S07 — سقفِ اعتبار در صندوق و تأییدِ مدیر */
class CreditGateTest {

  /** S07: سقف ۵٬۰۰۰، مانده ۴٬۰۰۰، فاکتورِ ۲٬۰۰۰ تماماً نسیه ⇐ «۱٬۰۰۰ بیش از سقف» */
  @Test
  fun s07OverByOneThousand() {
    val over = CreditGate.check(500_000, 400_000, 200_000)!!
    assertEquals(CreditGate.Over(500_000, 400_000, 200_000, 600_000), over)
    assertEquals(100_000, over.excessMinor)
  }

  /** مرزها: برابرِ سقف آزاد است؛ یک پول بیشتر نه؛ بی سقف، بی بردِ تازه یا پیش‌پرداخت */
  @Test
  fun boundaries() {
    assertNull(CreditGate.check(500_000, 400_000, 100_000))
    assertEquals(1, CreditGate.check(500_000, 400_000, 100_001)!!.excessMinor)
    assertNull(CreditGate.check(null, 99_999_999, 1))
    assertNull(CreditGate.check(500_000, 900_000, 0))
    assertNull(CreditGate.check(500_000, 900_000, -5))
    //  طلبِ مشتری (ماندهٔ منفی) جا باز می‌کند
    assertNull(CreditGate.check(500_000, -200_000, 700_000))
    assertEquals(1, CreditGate.check(500_000, -200_000, 700_001)!!.excessMinor)
    //  سقفِ صفر: هر بردی تأیید می‌خواهد
    assertEquals(1, CreditGate.check(0, 0, 1)!!.excessMinor)
  }

  /** بسته‌به‌خطا: سقفِ منفی یا عددِ بیرون از ±(2^53−1) ⇐ بیش از سقف، بی سرریز */
  @Test
  fun failsClosedOnBadNumbers() {
    assertTrue(CreditGate.check(-1, 0, 1) != null)
    assertTrue(CreditGate.check(CreditGate.MAX_SAFE + 1, 0, 1) != null)
    assertTrue(CreditGate.check(500_000, Long.MAX_VALUE, 1) != null)
    assertTrue(CreditGate.check(500_000, 0, Long.MAX_VALUE) != null)
    //  بزرگ‌ترین عددهای امن هنوز درست جمع می‌شوند
    val big = CreditGate.check(CreditGate.MAX_SAFE, CreditGate.MAX_SAFE, CreditGate.MAX_SAFE)!!
    assertEquals(CreditGate.MAX_SAFE * 2, big.afterMinor)
  }

  /** تأیید فقط از مدیر/صاحب، با نام و دلیل، برای همین مشتری و تا همین مبلغ */
  @Test
  fun approvalCoversOnlySameCustomerAndAmount() {
    val over = CreditGate.check(500_000, 400_000, 200_000)
    val ok = CreditGate.Approval("مدیرِ دکان", "manager", "c1", 200_000, "مشتریِ قدیمی")
    assertEquals(CreditGate.State.NEEDS_APPROVAL, CreditGate.state(over, "c1", null))
    assertEquals(CreditGate.State.APPROVED, CreditGate.state(over, "c1", ok))
    assertEquals(CreditGate.State.APPROVED, CreditGate.state(over, "c1", ok.copy(role = "owner", addMinor = 250_000)))
    //  سبدِ بزرگ‌تر از مبلغِ تأییدشده، مشتریِ دیگر، شاگرد، بی نام یا بی دلیل ⇐ هنوز تأیید لازم است
    assertEquals(CreditGate.State.NEEDS_APPROVAL, CreditGate.state(over, "c1", ok.copy(addMinor = 199_999)))
    assertEquals(CreditGate.State.NEEDS_APPROVAL, CreditGate.state(over, "c2", ok))
    assertEquals(CreditGate.State.NEEDS_APPROVAL, CreditGate.state(over, "c1", ok.copy(role = "staff")))
    assertEquals(CreditGate.State.NEEDS_APPROVAL, CreditGate.state(over, "c1", ok.copy(role = "cashier")))
    assertEquals(CreditGate.State.NEEDS_APPROVAL, CreditGate.state(over, "c1", ok.copy(by = " ")))
    assertEquals(CreditGate.State.NEEDS_APPROVAL, CreditGate.state(over, "c1", ok.copy(reason = "")))
    //  زیرِ سقف هیچ تأییدی لازم نیست
    assertEquals(CreditGate.State.FREE, CreditGate.state(null, "c1", null))
  }

  @Test
  fun approverRoles() {
    assertTrue(CreditGate.canApprove("owner"))
    assertTrue(CreditGate.canApprove(" Manager "))
    assertTrue(CreditGate.canApprove(""))
    assertTrue(CreditGate.canApprove(null))
    assertFalse(CreditGate.canApprove("staff"))
    assertFalse(CreditGate.canApprove("accountant"))
  }
}
