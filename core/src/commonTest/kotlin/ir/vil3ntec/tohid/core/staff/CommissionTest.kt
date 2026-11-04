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
    //  تصمیمِ صاحب‌کار O-05 (ب): برگشت کم می‌شود ولی جمعِ دوره زیرِ صفر نمی‌رود — فقط برگشت در این دوره ⇒ پورسانتِ ۰،
    //  و پایهٔ منفی دیده می‌ماند (نه پنهان)
    val back = Commission.compute(listOf(Line("s1", null, af(-500))), plan, afn)
    assertEquals(af(0), back.sellers.single().commission)
    assertEquals(af(-500), back.sellers.single().base)
    //  فروش و برگشتِ بیشتر از آن در یک دوره: همان ۰، نه بدهیِ فروشنده
    val more = Commission.compute(listOf(Line("s1", null, af(200)), Line("s1", null, af(-700))), plan, afn)
    assertEquals(af(0), more.sellers.single().commission)
    assertEquals(af(-500), more.sellers.single().base)
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

  @Test
  fun percentTextRoundTrips() {
    assertEquals(250, Commission.bpOfPercent("2.5"))
    assertEquals(250, Commission.bpOfPercent("۲٫۵"))
    assertEquals(5, Commission.bpOfPercent("0,05"))
    assertEquals(10_000, Commission.bpOfPercent("100"))
    assertEquals(0, Commission.bpOfPercent(" 0 "))
    for (bad in listOf("", "-1", "100.01", "2.555", "abc", "1e2", "2.")) assertEquals(null, Commission.bpOfPercent(bad), bad)
    assertEquals("2.5", Commission.percentOf(250))
    assertEquals("3", Commission.percentOf(300))
    assertEquals("0.05", Commission.percentOf(5))
    for (bp in listOf(0, 1, 99, 250, 333, 10_000)) assertEquals(bp, Commission.bpOfPercent(Commission.percentOf(bp)))
  }
  @Test
  fun returnLinesGoToTheOriginalSellerAndSkipCancelledSales() {
    val origins = mapOf(
      "f1" to Commission.Origin("s1"),
      "f2" to Commission.Origin(" s2 "),
      "f3" to Commission.Origin("s1", cancelled = true),
      "f4" to Commission.Origin(""),
    )
    val back = Commission.returnLines(
      listOf(
        Commission.Returned("f1", af(300)),   // فروشِ ماهِ پیش، برگشتِ این ماه ⇒ از s1 کم
        Commission.Returned("f2", af(200)),
        Commission.Returned("f3", af(900)),   // اصلِ لغوشده ⇒ بیرون
        Commission.Returned("f4", af(50)),    // اصلِ بی فروشنده ⇒ بی فروشنده
        Commission.Returned("gone", af(70)),  // اصلِ ناپیدا ⇒ بی فروشنده، نه پنهان
      ),
      origins,
    )
    assertEquals(listOf(Line("s1", null, af(-300)), Line("s2", null, af(-200)), Line("", null, af(-50)), Line("", null, af(-70))), back)
    val r = Commission.compute(listOf(Line("s1", null, af(1_000)), Line("s2", null, af(1_000))) + back, Plan(1_000), afn)
    assertEquals(listOf(af(70), af(80)), r.sellers.map { it.commission })
    assertEquals(af(-120), r.unassignedBase)
  }

  //  STF-08 — «نرخِ دسته»: فاکتور به تفکیکِ دستهٔ قلم‌ها، با پخشِ تخفیفِ فاکتور به نسبتِ مبلغ
  @Test
  fun saleLinesSplitTheFinalTotalByCategoryAfterTheInvoiceDiscount() {
    fun item(cat: String?, a: Long) = Commission.Item(cat, af(a))
    //  ۶۰۰ برنج + ۴۰۰ سیگار، تخفیفِ فاکتور ۱۰۰ ⇒ ۵۴۰ و ۳۶۰
    val lines = Commission.saleLines("s1", af(900), listOf(item("rice", 600), item("cigarettes", 400)))
    assertEquals(listOf(Line("s1", "rice", af(540)), Line("s1", "cigarettes", af(360))), lines)
    val plan = Plan(defaultBp = 1_000, categoryBp = mapOf("cigarettes" to 0))
    assertEquals(af(54), Commission.compute(lines, plan, afn).sellers.single().commission)
    //  قلمِ هم‌دسته و بی‌دسته: یک ردیف برای هر کدام؛ جمعِ ردیف‌ها همیشه همان finalTotal (باقیِ خرد با بزرگ‌ترین باقی‌مانده)
    val odd = Commission.saleLines("s1", Money(99_999, afn), listOf(item("rice", 333), item(null, 333), item("rice", 334)))
    assertEquals(listOf("rice", null), odd.map { it.category })
    assertEquals(99_999L, odd.sumOf { it.net.minor })
  }

  @Test
  fun saleLinesFallBackToOneUncategorisedLineAsBefore() {
    fun item(cat: String?, a: Long) = Commission.Item(cat, af(a))
    val whole = listOf(Line("s1", null, af(900)))
    assertEquals(whole, Commission.saleLines("s1", af(900), emptyList()))
    assertEquals(whole, Commission.saleLines("s1", af(900), listOf(item(null, 600), item(" ", 400))))
    //  فاکتورِ بیش از جمعِ قلم‌ها (هزینهٔ افزوده) یا جمعِ صفر ⇒ همان یک ردیف
    assertEquals(whole, Commission.saleLines("s1", af(900), listOf(item("rice", 500))))
    assertEquals(whole, Commission.saleLines("s1", af(900), listOf(item("rice", 0))))
    //  بی نرخِ دسته، پورسانتِ تفکیک‌شده همان پورسانتِ یک‌ردیفه است
    val split = Commission.saleLines("s1", af(900), listOf(item("rice", 600), item("tea", 400)))
    assertEquals(Commission.compute(whole, Plan(333), afn), Commission.compute(split, Plan(333), afn))
  }

  @Test
  fun returnsCarryTheirCategorySoAZeroRateCategoryIsNotClawedBackAtTheSellerRate() {
    val origins = mapOf("a" to Commission.Origin("s1"))
    val back = Commission.returnLines(listOf(Commission.Returned("a", af(400), "cigarettes"), Commission.Returned("a", af(100))), origins)
    assertEquals(listOf(Line("s1", "cigarettes", af(-400)), Line("s1", null, af(-100))), back)
    val plan = Plan(defaultBp = 1_000, categoryBp = mapOf("cigarettes" to 0))
    val sold = Commission.saleLines("s1", af(1_000), listOf(Commission.Item("rice", af(600)), Commission.Item("cigarettes", af(400))))
    //  ۶۰۰ × ۱۰٪ − ۱۰۰ × ۱۰٪ = ۵۰؛ برگشتِ سیگار ۰
    assertEquals(af(50), Commission.compute(sold + back, plan, afn).sellers.single().commission)
  }
}
