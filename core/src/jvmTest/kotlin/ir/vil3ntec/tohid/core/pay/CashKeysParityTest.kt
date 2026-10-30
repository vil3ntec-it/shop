package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  دکمه‌های اسکناس (commonMain) همان اسکناس‌های هستهٔ پرداخت‌اند، و متنِ کادر همان پولی می‌شود که پیش‌نمایشِ
 *  پنل (`PayFlow.minor` = `Money.fromLegacy`) از آن می‌سازد. هستهٔ پرداخت و `Money` هنوز در jvmMain‌اند، پس
 *  این هم‌سنجی این‌جاست و نه در commonTest.
 */
class CashKeysParityTest {

  @Test
  fun notesAreThePayCoreNotes() {
    assertEquals(Pay.NOTES, CashKeys.NOTES)
    CashKeys.NOTES.forEach { assertEquals(true, Pay.isDenomination(it, coins = false), "اسکناسِ $it") }
  }

  @Test
  fun cashTextEqualsPreviewMinor() {
    val texts = listOf("0.005", "1.005", "899.6", "1200.45", "0.1", "12.", ".5", "100000000.07", "90071992547409.91") +
      (1..20_000).map { (it * 0.37).toString() } + (1..2_000).map { "$it.${it % 100}" }
    for (t in texts) {
      val preview = Money.fromLegacy(t.toDouble(), Currency.AFN).minor
      assertEquals(preview, CashKeys.cashMinor(t), "«$t»")
      val added = CashKeys.addNote(t, 1_000L) ?: continue
      assertEquals(preview + 1_000L, Money.fromLegacy(added.toDouble(), Currency.AFN).minor, "«$t» + ۱۰")
    }
  }
}
