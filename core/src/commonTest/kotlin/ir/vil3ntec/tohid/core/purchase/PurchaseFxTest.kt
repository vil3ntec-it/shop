package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.purchase.PurchaseFx.Account
import ir.vil3ntec.tohid.core.purchase.PurchaseFx.Rate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PUR-11 — خرید به ارزِ دیگر: تبدیل، گرد، سند، حسابِ ارزی و اختلافِ نرخ (عددها دستی) */
class PurchaseFxTest {

  private val usd70 = Rate("USD", 7000, 1)
  private val usd7025 = Rate("USD", 7025, 1)

  @Test fun rateValidation() {
    assertEquals(Rate("USD", 7025, 1), PurchaseFx.rate("USD", 7025, null))
    assertEquals(Rate("EUR", 7600, 1), PurchaseFx.rate(" EUR ", 7600, 1))
    assertNull(PurchaseFx.rate("AFN", 100, 1), "افغانی ارزِ دیگر نیست")
    assertNull(PurchaseFx.rate("usd", 7025, 1), "کدِ کوچک")
    assertNull(PurchaseFx.rate("USD", 0, 1))
    assertNull(PurchaseFx.rate("USD", -5, 1))
    assertNull(PurchaseFx.rate("USD", 7025, 7), "واحدِ نرخ فقط از FX_PER")
    assertEquals(1_000L, PurchaseFx.defaultPer("PKR"))
    assertEquals(1_000_000L, PurchaseFx.defaultPer("IRR"))
    assertEquals(1L, PurchaseFx.defaultPer("EUR"))
  }

  @Test fun conversionWithRateUnitsAndHalfUpToPul() {
    //  ۱۰۰ دالر به ۷۰٫۲۵ ⇒ ۷٬۰۲۵ افغانی
    assertEquals(702_500, PurchaseFx.toAfn(10_000, usd7025))
    //  ۱۰٬۰۰۰ کلدار به ۲۴۶ افغانی برای هر ۱٬۰۰۰ ⇒ ۲٬۴۶۰ افغانی
    assertEquals(246_000, PurchaseFx.toAfn(1_000_000, Rate("PKR", 24_600, 1_000)))
    //  ۷٬۵۰۰٬۰۰۰ ریال به ۲۵ افغانی برای هر میلیون ⇒ ۱۸۷٫۵۰ افغانی
    assertEquals(18_750, PurchaseFx.toAfn(7_500_000, Rate("IRR", 2_500, 1_000_000)))
    //  نیمه رو به بالا به یک پول: ۰٫۰۱ دالر ⇒ ۷۰٫۲۵ پول ⇒ ۷۰؛ ۰٫۰۲ ⇒ ۱۴۰٫۵ ⇒ ۱۴۱
    assertEquals(70, PurchaseFx.toAfn(1, usd7025))
    assertEquals(141, PurchaseFx.toAfn(2, usd7025))
    //  منفی (سندِ کسر): floor(x + ½) ⇒ −۱۴۰٫۵ ⇒ −۱۴۰
    assertEquals(-140, PurchaseFx.toAfn(-2, usd7025))
    //  برگشت: پول ⇒ سنت
    assertEquals(10_000, PurchaseFx.fromAfn(702_500, usd7025))
    assertEquals(1, PurchaseFx.fromAfn(70, usd7025))
  }

  @Test fun lineTotalRoundsToOneMinorUnitOfTheCurrency() {
    assertEquals(999, PurchaseFx.lineFx(3_000, 333))
    //  ۱٫۵ × ۳٫۳۳ = ۴٫۹۹۵ ⇒ ۵٫۰۰
    assertEquals(500, PurchaseFx.lineFx(1_500, 333))
    //  ریال بی اعشار: ۲٫۵ × ۳ ریال = ۷٫۵ ⇒ ۸
    assertEquals(8, PurchaseFx.lineFx(2_500, 3))
  }

  @Test fun allocateIsExactLargestRemainder() {
    assertEquals(listOf(334L, 334L, 333L), PurchaseFx.allocate(1001, listOf(1, 1, 1)))
    assertEquals(listOf(-334L, -334L, -333L), PurchaseFx.allocate(-1001, listOf(-1, -1, -1)))
    assertEquals(listOf(0L, 10L), PurchaseFx.allocate(10, listOf(0, 5)))
    assertEquals(listOf(7L, 0L), PurchaseFx.allocate(7, listOf(0, 0)))
    val w = listOf(1001L, 1001L, 1L)
    assertEquals(140_725L, PurchaseFx.allocate(140_725, w).sum())
  }

  @Test fun documentAfnIsOneConversionAndLinesSumExactly() {
    //  دو ردیفِ ۱۰٫۰۱ دالر به ۷۰٫۲۵: هر ردیف تنها ۷۰۳٫۲۰ (۷۰۳٫۲۰۲۵) ولی سند ۱٬۴۰۶٫۴۰۵ ⇒ ۱٬۴۰۶٫۴۱
    val doc = PurchaseFx.doc(usd7025, listOf(1_001, 1_001), 0)
    assertEquals(2_002, doc.fxTotal)
    assertEquals(140_641, doc.afnTotal)
    assertEquals(PurchaseFx.toAfn(2_002, usd7025), doc.afnLines.sum())
    assertEquals(listOf(70_321L, 70_320L), doc.afnLines)
    assertEquals(0, doc.afnPaid)
  }

  @Test fun paidAtPurchaseFillsLinesInOrderAtTheSameRate() {
    val doc = PurchaseFx.doc(usd7025, listOf(1_000, 3_000), 2_000)
    assertEquals(listOf(1_000L, 1_000L), doc.fxPaidLines)
    //  ردیفِ تمام‌پرداخت همان سهمِ کامل ⇒ بدهیِ ردیف دقیقاً صفر
    assertEquals(doc.afnLines[0], doc.afnPaidLines[0])
    assertEquals(PurchaseFx.toAfn(1_000, usd7025), doc.afnPaidLines[1])
    //  بیش از جمع ⇒ همان جمع
    val all = PurchaseFx.doc(usd7025, listOf(1_000, 3_000), 99_999)
    assertEquals(4_000, all.fxPaid)
    assertEquals(all.afnTotal, all.afnPaid)
  }

  @Test fun mixedCurrencyAverageCostUsesTheRecordedAfnEquivalent() {
    //  ۱۰ دانه به ۷۰۰ افغانی (۷٬۰۰۰) + ۱۰ دانه به ۱۰ دالر با ۷۲ (۷٬۲۰۰) ⇒ میانگینِ ۷۱۰ افغانی
    val afnLot = 10L * 70_000
    val usdLot = PurchaseFx.doc(Rate("USD", 7_200, 1), listOf(PurchaseFx.lineFx(10_000, 1_000)), 0).afnTotal
    assertEquals(720_000, usdLot)
    assertEquals(71_000, (afnLot + usdLot) / 20)
    //  کلدارِ همان کالا (۱۰ دانه × ۲٬۸۰۰ کلدار، ۲۵۰ افغانی برای ۱٬۰۰۰) ⇒ ۷٬۰۰۰ افغانی؛ میانگینِ سه خرید ۷۰۶٫۶۷
    val pkrLot = PurchaseFx.doc(Rate("PKR", 25_000, 1_000), listOf(PurchaseFx.lineFx(10_000, 280_000)), 0).afnTotal
    assertEquals(700_000, pkrLot)
    assertEquals(70_667, PurchaseFx.mulDivHalfUp(afnLot + usdLot + pkrLot, 1, 30))
  }

  @Test fun partialPaymentsAtOtherRatesAccumulateExchangeDifference() {
    //  خرید ۱۰۰ دالر به ۷۰ ⇒ بدهیِ ۱۰۰ دالر با ارزشِ دفتریِ ۷٬۰۰۰ افغانی
    var acc = Account("USD", 10_000, 700_000)
    //  ۴۰ دالر به ۷۲: دفتری ۲٬۸۰۰، ارزش ۲٬۸۸۰ ⇒ ۸۰ افغانی زیان
    val a = assertNotNull(PurchaseFx.payInFx(acc, 4_000, Rate("USD", 7_200, 1)))
    assertEquals(4_000, a.fx); assertEquals(288_000, a.value); assertEquals(280_000, a.carried); assertEquals(8_000, a.diff)
    assertEquals(null, a.afnPaid)
    acc = Account("USD", acc.fx - a.fx, acc.afn - a.carried)
    assertEquals(Account("USD", 6_000, 420_000), acc)
    //  ماندهٔ ۶۰ دالر به افغانی به نرخِ ۶۹: «همه» = ۴٬۱۴۰ ⇒ همهٔ مانده صاف، ۶۰ افغانی سود
    val r69 = Rate("USD", 6_900, 1)
    val all = PurchaseFx.toAfn(acc.fx, r69)
    assertEquals(414_000, all)
    val b = assertNotNull(PurchaseFx.payInAfn(acc, all, r69))
    assertEquals(6_000, b.fx); assertEquals(414_000, b.value); assertEquals(420_000, b.carried); assertEquals(-6_000, b.diff)
    assertEquals(414_000, b.afnPaid)
    acc = Account("USD", acc.fx - b.fx, acc.afn - b.carried)
    assertEquals(Account("USD", 0, 0), acc, "حسابِ ارزی و ارزشِ دفتری با هم صفر")
    assertEquals(2_000, a.diff + b.diff, "جمعِ اختلاف: ۲۰ افغانی زیان")
  }

  @Test fun fullPaymentAtTheSameRateHasNoDifference() {
    val doc = PurchaseFx.doc(usd7025, listOf(1_001, 1_001, 333), 0)
    val acc = Account("USD", doc.fxTotal, doc.afnTotal)
    val s = assertNotNull(PurchaseFx.payInFx(acc, acc.fx, usd7025))
    assertEquals(0, s.diff)
    assertEquals(acc.afn, s.carried)
  }

  @Test fun overpaymentBecomesCreditAtThePaymentRate() {
    val acc = Account("USD", 6_000, 420_000)
    val s = assertNotNull(PurchaseFx.payInFx(acc, 7_000, usd70))
    //  همهٔ ارزشِ دفتری + ۱۰ دالرِ بیشتر به ۷۰
    assertEquals(420_000 + 70_000, s.carried)
    assertEquals(490_000, s.value)
    assertEquals(0, s.diff)
    //  حسابِ بی بدهی (پیش‌پرداخت) ⇒ ارزش به نرخِ پرداخت، بی اختلاف
    val pre = assertNotNull(PurchaseFx.payInFx(Account("USD", 0, 0), 1_000, usd70))
    assertEquals(70_000, pre.carried); assertEquals(0, pre.diff)
  }

  @Test fun afnPaymentSnapsToTheWholeBalanceForCoarseCurrencies() {
    //  ریال به ۲۵ افغانی برای هر میلیون: یک پول = ۴۰۰ ریال؛ ماندهٔ ۷٬۵۰۰٬۱۲۳ ریال
    val irr = Rate("IRR", 2_500, 1_000_000)
    val acc = Account("IRR", 7_500_123, 18_750)
    val all = PurchaseFx.toAfn(acc.fx, irr)
    assertEquals(18_750, all)
    val s = assertNotNull(PurchaseFx.payInAfn(acc, all, irr))
    assertEquals(acc.fx, s.fx, "پولِ «همه» دقیقاً همهٔ مانده را صاف می‌کند")
    assertEquals(0, s.diff)
    //  کمتر از همه ⇒ بخشی، هرگز بیش از مانده
    val part = assertNotNull(PurchaseFx.payInAfn(acc, 10_000, irr))
    assertEquals(4_000_000, part.fx)
    assertTrue(part.fx < acc.fx)
  }

  @Test fun badPaymentsAreRefused() {
    val acc = Account("USD", 10_000, 700_000)
    assertNull(PurchaseFx.payInFx(acc, 0, usd70))
    assertNull(PurchaseFx.payInFx(acc, -1, usd70))
    assertNull(PurchaseFx.payInFx(acc, 100, Rate("EUR", 7_600, 1)), "نرخِ ارزِ دیگر")
    //  کمتر از یک سنت به افغانی
    assertNull(PurchaseFx.payInAfn(Account("USD", 0, 0), 30, usd7025))
  }

  @Test fun noOverflowSilently() {
    //  ضربِ میانیِ بزرگ (بیش از ۲^۶۳) درست تقسیم می‌شود
    assertEquals(4_000_000_000_000_000L / 1_000_000_000_000L * 1_000_000_000L,
      PurchaseFx.mulDivHalfUp(4_000_000_000_000_000L, 1_000_000_000L, 1_000_000_000_000L))
    assertEquals(5_000_000_000L, PurchaseFx.mulDivHalfUp(1_000_000_000_000_000L, 5_000_000_000L, 1_000_000_000_000_000L))
    //  حاصلِ بیرون از ۲^۵۳ ⇒ خطا، نه عددِ چرخیده
    assertFailsWith<ArithmeticException> { PurchaseFx.mulDivHalfUp(9_000_000_000_000_000L, 9_000_000_000_000_000L, 100) }
    assertFailsWith<ArithmeticException> { PurchaseFx.toAfn(9_000_000_000_000_000L, Rate("USD", 9_000_000_000_000_000L, 1)) }
    assertNull(PurchaseFx.payInFx(Account("USD", 1, 1), 9_000_000_000_000_000L, Rate("USD", 9_000_000_000_000_000L, 1)))
  }
}
