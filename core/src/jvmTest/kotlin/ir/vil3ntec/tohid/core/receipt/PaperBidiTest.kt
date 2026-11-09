package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.text.LocaleFormat
import java.text.Bidi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  HW-01: نشانهٔ «−»/«+» ِ تخفیف، گردکردن و ماندهٔ منفی روی کاغذِ راست‌به‌چپ چپِ رقمِ خودش می‌نشیند (همان صفحهٔ
 *  برنامه)، نه «۵۰−». ترتیبِ دیدنی با `java.text.Bidi` ِ خودِ JVM (همان الگوریتمِ Unicode که `StaticLayout` و شیمِ
 *  Java2D به کار می‌برند؛ LRM در ۱۷ و ۲۱ یکی است) — هم رقمِ فارسی/پشتو و هم لاتین، در کاغذِ ۵۸ و ۸۰.
 */
class PaperBidiTest {

  /** متن ⇒ نویسه‌ها به ترتیبِ دیدنی از چپ به راست، با جهتِ پایهٔ راست‌به‌چپ؛ نویسهٔ کنترل حذف */
  private fun visual(s: String): String {
    val b = Bidi(s, Bidi.DIRECTION_RIGHT_TO_LEFT)
    val n = b.runCount
    val levels = ByteArray(n) { b.getRunLevel(it).toByte() }
    val runs = Array<Any>(n) { i ->
      val r = s.substring(b.getRunStart(i), b.getRunLimit(i))
      if (b.getRunLevel(i) % 2 == 1) r.reversed() else r
    }
    Bidi.reorderVisually(levels, 0, runs, 0, n)
    return runs.joinToString("").filter { it != PaperBidi.LRM }
  }

  private class Fmt(val digits: String, val symbol: String) : ReceiptFormat {
    override fun money(afn: Double) = LocaleFormat.formatAfghani(afn, symbol, digits)
    override fun amount(afn: Double) = LocaleFormat.formatAfghani(afn, LocaleFormat.SYMBOL_NONE, digits)
    override fun qty(q: Double) = LocaleFormat.displayDigits(q.toLong().toString(), digits)
    override fun digits(text: String) = LocaleFormat.displayDigits(text, digits)
    override fun phone(text: String) = text
    override fun date(iso: String) = iso
  }

  private fun input() = ReceiptInput(
    shop = ShopHeader("دکان"), invoiceNo = "B1-1", dateIso = "2026-11-01", time = "10:00", cashier = "علی", kind = "نقدی",
    cancelled = false, items = listOf(LineView("نان", 2.0, "عدد", 51.0, 102.0)), subtotal = 102.0,
    discounts = listOf(DiscountLine("تخفیف", 50.0)), total = 50.0, payments = emptyList(), paid = 50.0, rounding = -2.0,
  )

  @Test
  fun signSitsLeftOfItsDigitsOnRtlPaper() {
    for (width in listOf(384, 576)) for (f in listOf(Fmt(LocaleFormat.DIGITS_FA, LocaleFormat.SYMBOL_SIGN), Fmt(LocaleFormat.DIGITS_LATN, LocaleFormat.SYMBOL_WORD))) {
      val rows = ReceiptLayout.build(input(), width, f).blocks.filterIsInstance<ReceiptBlock.Row>().associate { it.label to it.value }
      val five = f.digits("50")
      val two = f.digits("2")
      val discount = visual(PaperBidi.signs(rows.getValue("تخفیف")))
      assertTrue(discount.contains("−$five"), "$width ${f.digits}: «$discount»")
      val rounding = visual(PaperBidi.signs(rows.getValue(ReceiptLabels.FA.rounding)))
      assertTrue(rounding.contains("−$two"), "$width ${f.digits}: «$rounding»")
      //  جای واژه/نشانِ پول همان مبلغِ بی‌نشانه است (سمتِ چپِ کاغذ)
      val plain = visual(f.money(50.0))
      assertEquals(plain.replace(five, "−$five"), discount)
    }
  }

  @Test
  fun negativeAmountsAndPlusSignsToo() {
    val neg = LocaleFormat.formatAfghani(-150.0, LocaleFormat.SYMBOL_SIGN, LocaleFormat.DIGITS_FA)
    assertTrue(visual(PaperBidi.signs("مانده: $neg")).contains("-۱۵۰"))
    assertTrue(visual(PaperBidi.signs("+۲ ؋")).contains("+۲"))
    assertTrue(visual(PaperBidi.signs("(−۳۰ ؋)")).contains("−۳۰"))
  }

  @Test
  fun dashesInsideCodesAndDatesAreUntouchedAndItIsIdempotent() {
    for (s in listOf("B1-1", "2026-11-01", "۱۴۰۵-۰۸-۱۸", "Pepsi 330", "نان − تازه", "")) assertEquals(s, PaperBidi.signs(s))
    val once = PaperBidi.signs("−۵۰ ؋ و -۲")
    assertEquals(once, PaperBidi.signs(once))
    assertEquals(2, once.count { it == PaperBidi.LRM })
  }
}
