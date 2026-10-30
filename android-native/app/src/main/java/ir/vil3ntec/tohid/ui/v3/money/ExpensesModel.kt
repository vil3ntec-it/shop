package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.ShopData

/**
 *  مصارف (`expenses?month=&category=`) — منطقِ خالص، زیرِ آزمون. ماه همان `yyyy-MM` ِ دفتر است
 *  (`date.take(7)`) و برچسبش ماهِ خورشیدیِ پانزدهمِ همان ماه (`formatMonth`)؛ جمع و تفکیک از
 *  خودِ موتور (`LedgerEngine.expenseTotal` / `expensesByCategory`).
 */
internal object ExpensesModel {

  /** بازهٔ یک ماه برای موتور — همان «‎-01 تا ‎-31» ِ امروز */
  fun range(month: String): Pair<String, String> = "$month-01" to "$month-31"

  /** `yyyy-MM` ِ معتبر یا null */
  fun parseMonth(raw: String?): String? = raw?.trim()?.takeIf { Regex("""\d{4}-\d{2}""").matches(it) && it.takeLast(2).toInt() in 1..12 }

  /** ماه‌هایی که مصرف دارند و ماهِ جاری، تازه‌ترین اول */
  fun months(d: ShopData, today: String): List<String> =
    (d.expenses.map { it.date.take(7) }.filter { parseMonth(it) != null } + today.take(7)).distinct().sortedDescending()

  /** ماهِ پیش/بعد (`delta` = −۱ / +۱) */
  fun shift(month: String, delta: Int): String {
    val y = month.take(4).toInt()
    val m = month.takeLast(2).toInt() - 1 + delta
    val year = y + Math.floorDiv(m, 12)
    val mm = Math.floorMod(m, 12) + 1
    return "%04d-%02d".format(year, mm)
  }

  /** مصارفِ ماه (و دسته)، تازه‌ترین ثبت اول — همان ترتیبِ امروز */
  fun shown(d: ShopData, month: String, category: String?): List<Expense> =
    d.expenses.filter { it.date.startsWith(month) && (category == null || it.category == category) }.sortedByDescending { it.createdAt }

  fun total(d: ShopData, month: String): Double = range(month).let { (from, to) -> LedgerEngine.expenseTotal(d, from, to) }

  fun byCategory(d: ShopData, month: String): List<Pair<String, Double>> =
    range(month).let { (from, to) -> LedgerEngine.expensesByCategory(d, from, to) }

  /** سهمِ هر دسته از جمع (۰ تا ۱) — برای نوارِ رنگی */
  fun shares(rows: List<Pair<String, Double>>): List<Float> {
    val sum = rows.sumOf { it.second }
    return rows.map { if (sum > 0) (it.second / sum).toFloat() else 0f }
  }
}
