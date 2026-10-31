package ir.vil3ntec.tohid.core.staff

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.CurrencyMismatchException
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.staff.Commission.Line
import ir.vil3ntec.tohid.core.staff.Commission.Plan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** STF-08 — پورسانتِ فروش */
class CommissionTest {

  private val afn = Currency.AFN
  private fun af(a: Long) = Money(a * 100, afn)

  @Test
  fun ratePrecedenceCategoryThenSellerThenDefault() {
    val plan = Plan(defaultBp = 200, sellerBp = mapOf("s2" to 300), categoryBp = mapOf("cigarettes" to 0))
    val r = Commission.compute(
      listOf(
        Line("s1", "rice", af(1_000)),       // ۲٪ ⇒ ۲۰
        Line("s2", "rice", af(1_000)),       // ۳٪ ⇒ ۳۰
        Line("s2", "cigarettes", af(5_000)), // ۰٪
      ),
      plan, afn,
    )
    assertEquals(listOf("s1", "s2"), r.sellers.map { it.sellerId })
    assertEquals(listOf(af(1_000), af(6_000)), r.sellers.map { it.base })
    assertEquals(listOf(af(20), af(30)), r.sellers.map { it.commission })
  }

  @Test
  fun roundsOnceOnTheSellerTotalNotPerLine() {
    //  ده ردیفِ ۱۵ افغانی با ۳٪: هر ردیف ۰٫۴۵ (گردِ ردیفی ⇒ ۰)، جمع ۴٫۵ ⇒ ۵ (نیمه رو به بالا)
    val r = Commission.compute(List(10) { Line("s1", null, af(15)) }, Plan(300), afn)
    assertEquals(af(5), r.sellers.single().commission)
    assertEquals(af(150), r.sellers.single().base)
  }

  @Test
  fun returnsClawBackFromTheOriginalSeller() {
    val plan = Plan(500)
    val r = Commission.compute(listOf(Line("s1", null, af(2_000)), Line("s1", null, af(-500))), plan, afn)
    assertEquals(af(75), r.sellers.single().commission)
    //  فقط برگشت در این دوره ⇒ پورسانتِ منفی (پس گرفتن)، نه صفرِ پنهان
    val back = Commission.compute(listOf(Line("s1", null, af(-500))), plan, afn)
    assertEquals(af(-25), back.sellers.single().commission)
  }

  @Test
  fun unassignedLinesAreReportedNotPaid() {
    val r = Commission.compute(listOf(Line("", null, af(700)), Line(" ", null, af(300)), Line("s1", null, af(100))), Plan(1_000), afn)
    assertEquals(af(1_000), r.unassignedBase)
    assertEquals(listOf("s1"), r.sellers.map { it.sellerId })
    assertEquals(af(10), r.sellers.single().commission)
  }

  @Test
  fun guardsRatesAndCurrency() {
    assertFailsWith<IllegalArgumentException> { Plan(10_001) }
    assertFailsWith<IllegalArgumentException> { Plan(100, sellerBp = mapOf("s" to -1)) }
    assertFailsWith<CurrencyMismatchException> {
      Commission.compute(listOf(Line("s1", null, Money(100, Currency.USD))), Plan(100), afn)
    }
    assertEquals(Commission.Result(emptyList(), Money.zero(afn)), Commission.compute(emptyList(), Plan(100), afn))
  }
}
