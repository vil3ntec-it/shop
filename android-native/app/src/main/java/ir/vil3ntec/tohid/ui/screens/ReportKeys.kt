package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.report.ReportPeriod
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  RPT-13 — «دورهٔ قبل / بعد» و «مقایسه با دورهٔ قبل» در «گزارش‌ها»ی کامپیوتر (`DeskReports`).
 *  حسابِ دوره در `core/report/ReportPeriod`؛ این‌جا فقط نگاشتِ بازه‌های صفحه و خطِ مقایسه. بی Context.
 *  فقط با `ReportKeysPref` روشن (پیش‌فرض خاموش) صدا زده می‌شود.
 */
internal object ReportKeys {

  /** نامِ بازهٔ صفحه (`Span.name`) ⇒ واحدِ جابه‌جایی */
  fun unitOf(span: String): ReportPeriod.Unit = when (span) {
    "TODAY", "WEEK", "CUSTOM" -> ReportPeriod.Unit.DAYS
    "MONTH" -> ReportPeriod.Unit.GMONTH
    "JMONTH" -> ReportPeriod.Unit.JMONTH
    "JYEAR" -> ReportPeriod.Unit.JYEAR
    else -> ReportPeriod.Unit.NONE
  }

  /** بازهٔ مؤثر: `shift` دوره از بازهٔ پایه؛ نشد ⇒ همان پایه */
  fun range(span: String, from: String, to: String, shift: Int, today: String): Pair<String, String> =
    ReportPeriod.shift(unitOf(span), from, to, shift, today)?.let { it.from to it.to } ?: (from to to)

  /** آیا `shift` دوره از پایه ممکن است (نه آینده، نه «همه») */
  fun can(span: String, from: String, to: String, shift: Int, today: String): Boolean =
    shift <= 0 && ReportPeriod.shift(unitOf(span), from, to, shift, today) != null

  private fun minor(v: Double): Long = Math.round(v * 100)

  /** «فروشِ خالص +۱٬۲۰۰ ؋ (+12.3٪)» برای هر عددِ اصلیِ همین گزارش */
  fun parts(d: ShopData, key: String, from: String, to: String, pFrom: String, pTo: String): List<String> {
    fun line(label: String, cur: Long, prev: Long, isMoney: Boolean): String {
      val c = ReportPeriod.change(cur, prev)
      val sign = if (c.diff > 0) "+" else ""
      val amount = if (isMoney) "$sign${money(c.diff / 100.0)} ؋" else "$sign${c.diff / 100}"
      val pct = c.bp?.let { (if (it > 0) "+" else "") + ReportFreshModel.percent(it) } ?: StrHome.compareNoBase
      return "$label $amount ($pct)"
    }
    return when (key) {
      "sales", "profit", "sellers" -> {
        val a = ReportEngine.sales(d, from, to)
        val b = ReportEngine.sales(d, pFrom, pTo)
        listOf(
          line(StrHome.compareNet, minor(a.net), minor(b.net), true),
          line(StrHome.compareInvoices, a.count * 100L, b.count * 100L, false),
          line(StrHome.compareGross, minor(a.grossProfit), minor(b.grossProfit), true),
        )
      }
      "purchases" -> listOf(line(StrHome.comparePurchases, minor(ReportEngine.purchases(d, from, to).total), minor(ReportEngine.purchases(d, pFrom, pTo).total), true))
      "expenses" -> listOf(
        line(StrHome.compareExpenses, minor(d.expenses.filter { it.date in from..to }.sumOf { it.amount }), minor(d.expenses.filter { it.date in pFrom..pTo }.sumOf { it.amount }), true),
      )
      else -> emptyList()
    }
  }
}

/** خطِ «نسبت به دورهٔ قبل» زیرِ کارت‌ها (Ctrl+Shift+C) */
@Composable
internal fun ReportCompareLine(d: ShopData, key: String, span: String, from: String, to: String, today: String) {
  val prev = remember(span, from, to, today) { ReportPeriod.shift(ReportKeys.unitOf(span), from, to, -1, today) }
  if (prev == null) {
    Text(StrHome.compareNone, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    return
  }
  val parts = remember(d, key, from, to, prev) { ReportKeys.parts(d, key, from, to, prev.from, prev.to) }
  if (parts.isEmpty()) return
  Text(
    StrHome.compareWith(formatDate(prev.from), formatDate(prev.to)) + " " + parts.joinToString(" · "),
    style = MaterialTheme.typography.bodySmall,
    color = Shop.colors.muted,
  )
}
