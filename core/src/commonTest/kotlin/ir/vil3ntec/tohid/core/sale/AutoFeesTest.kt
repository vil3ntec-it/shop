package ir.vil3ntec.tohid.core.sale

import ir.vil3ntec.tohid.core.sale.AutoFees.Fee
import ir.vil3ntec.tohid.core.sale.AutoFees.Line
import ir.vil3ntec.tohid.core.sale.AutoFees.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  POS-36 — هزینهٔ خودکارِ ردیف یا فاکتور. پیش از این «کیسه» یا «بسته‌بندی» باید هر بار دستی به سبد
 *  اضافه می‌شد و نیمی از فاکتورها بی آن می‌ماند.
 */
class AutoFeesTest {

  private val rules = listOf(Rule("کیسه", 500), Rule("بسته‌بندی", 200, listOf("rice", "sugar")))

  @Test
  fun invoiceFeeOnceAndLineFeePerUnitOfListedItems() {
    val lines = listOf(Line("rice", 3000, "rice"), Line("sugar", 500, "sugar"), Line("tea", 2000, "tea"))
    assertEquals(
      listOf(Fee("fee:1", "کیسه", 1000, 500), Fee("fee:2", "بسته‌بندی", 3500, 200)),
      AutoFees.fees(lines, rules),
    )
  }

  @Test
  fun noFeesForAnEmptyCartOrOnlyOldFeeLinesOrNoRules() {
    assertEquals(emptyList(), AutoFees.fees(emptyList(), rules))
    assertEquals(emptyList(), AutoFees.fees(listOf(Line("fee:1", 1000, null)), rules))
    assertEquals(emptyList(), AutoFees.fees(listOf(Line("tea", 1000, "tea")), emptyList()))
    //  کالای نام‌برده در سبد نیست ⇒ فقط هزینهٔ فاکتور
    assertEquals(listOf("fee:1"), AutoFees.fees(listOf(Line("tea", 1000, "tea")), rules).map { it.key })
    //  قلمِ آزاد هم فاکتور را «با قلم» می‌کند
    assertEquals(listOf("fee:1"), AutoFees.fees(listOf(Line("x1", 1000, null)), rules).map { it.key })
  }

  @Test
  fun parseReadsBothKindsAndFlagsBadLines() {
    val ids = mapOf("برنج" to "rice", "شکر" to "sugar", "622000" to "tea")
    val p = AutoFees.parse("کیسه = ۵\nبرنج، شکر: بسته‌بندی = 2\n622000: جعبه = 1٫5\nبی‌مبلغ\nنیست: x = 1\nکیسه = 0\n: y = 1", ids::get)
    assertEquals(listOf(Rule("کیسه", 500), Rule("بسته‌بندی", 200, listOf("rice", "sugar")), Rule("جعبه", 150, listOf("tea"))), p.rules)
    assertEquals(
      listOf(AutoFees.Problem.BAD_LINE, AutoFees.Problem.UNKNOWN_ITEM, AutoFees.Problem.BAD_AMOUNT, AutoFees.Problem.BAD_LINE),
      p.problems.map { it.second },
    )
  }

  @Test
  fun formatRoundTrips() {
    val names = mapOf("rice" to "برنج", "sugar" to "شکر")
    val text = AutoFees.format(rules, { names.getValue(it) }, { (it / 100.0).toString().removeSuffix(".0") })
    assertEquals("کیسه = 5\nبرنج، شکر: بسته‌بندی = 2", text)
    assertEquals(rules, AutoFees.parse(text, mapOf("برنج" to "rice", "شکر" to "sugar")::get).rules)
  }
}
