package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.money.MoneyInput
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
    //  هر متنِ تا دو رقمِ اعشار (همان کادر) — از ۰٫۰۱ تا ۲۰۰۰ افغانی پول‌به‌پول، و نمونه‌هایی تا ۲^۵۱ پول (۲۲٫۵ هزار میلیارد
    //  افغانی). بالاتر از آن خودِ پیش‌نمایش (Double) یک پول می‌لغزد — «35398293071128.2» ⇒ 3539829307112821 — و راهِ
    //  رقم‌به‌رقمِ `cashMinor` درست است؛ پس برابری فقط تا همین‌جا سنجیده می‌شود.
    val minors = (0L..200_000L) + (1L..2_000L).map { it * 1_125_899_906_842L }
    val texts = minors.map { MoneyInput.ofMinor(it)!! } + listOf("12.", ".5", "0.5", "007", "1200.4")
    for (t in texts) {
      val preview = Money.fromLegacy(t.toDouble(), Currency.AFN).minor
      assertEquals(preview, CashKeys.cashMinor(t), "«$t»")
      val added = CashKeys.addNote(t, 1_000L) ?: continue
      assertEquals(preview + 1_000L, Money.fromLegacy(added.toDouble(), Currency.AFN).minor, "«$t» + ۱۰")
    }
  }
}
