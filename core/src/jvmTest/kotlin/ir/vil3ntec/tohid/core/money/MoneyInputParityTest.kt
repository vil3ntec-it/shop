package ir.vil3ntec.tohid.core.money

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  متنِ «تسویهٔ کامل» همان پولی است که دفترِ قرض (`DebtBook.minor` = `Money.fromLegacy`) از مانده می‌بیند.
 *  `Money` هنوز در jvmMain است، پس این هم‌سنجی این‌جاست و نه در commonTest.
 */
class MoneyInputParityTest {

  @Test
  fun settleTextEqualsLedgerMinor() {
    val balances = listOf(
      899.6, 1200.4, 1200.45, 0.01, 0.005, 0.1 + 0.2, 10.1 + 20.2, 899.5999999999999,
      500.3 + 399.3, 12_345_678.91, 1_000_000_000.05, 9e13,
    ) + (1..5_000).map { it * 0.37 }
    for (b in balances) {
      val ledger = Money.fromLegacy(b, Currency.AFN).minor
      val text = MoneyInput.settleInput(b)!!
      assertEquals(ledger, Money.fromLegacy(text.toDouble(), Currency.AFN).minor, "مانده $b ⇒ «$text»")
    }
  }
}
