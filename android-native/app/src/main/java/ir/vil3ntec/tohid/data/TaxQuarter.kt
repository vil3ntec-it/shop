package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.report.QuarterTax

/**
 *  گزارشِ مالیاتِ سه‌ماهه (RPT-10، S49) — سمتِ دفترِ دکان برای `core/report/QuarterTax`.
 *
 *  ردیف‌ها همان‌هایی‌اند که `ReportEngine` می‌شمارد، پس «جمعِ درآمدِ ناخالص با جمعِ اسناد برابر است»:
 *  فروش = `finalTotal` ِ فاکتورِ لغونشده در روزِ خودش (= `ReportEngine.sales(…).net`)، برگشت = `amount` ِ
 *  ردیفِ برگشت در روزِ **خودش** و نه روزِ فاکتور، بی برگشتِ فاکتورِ لغوشده (= `returnAmount`)، خرید =
 *  `totalAmount` (= `ReportEngine.purchases(…).total`). هر مبلغ جدا به پول گرد می‌شود (`Money.fromLegacy`).
 *  مالیاتِ خرید در دفتر ثبت نمی‌شود، پس فقط جمعِ خرید برای دیدن می‌آید.
 *  منطقِ خالص؛ آزمون: `TaxQuarterTest`. تنظیم و پرچم: `TaxQuarterPref` (پیش‌فرض خاموش).
 */
object TaxQuarter {

  private fun minor(afghani: Double): Long =
    if (!afghani.isFinite()) 0L else runCatching { Money.fromLegacy(afghani, Currency.AFN).minor }.getOrDefault(0L)

  /** همهٔ ردیف‌های واقعیِ دفتر به شکلِ هسته */
  fun docs(d: ShopData): List<QuarterTax.Doc> {
    val cancelled = d.sales.filter { it.status == "cancelled" }.map { it.id }.toHashSet()
    return buildList {
      d.sales.filter { it.status != "cancelled" }.forEach { add(QuarterTax.Doc(QuarterTax.Kind.SALE, it.date, minor(it.finalTotal))) }
      d.saleReturns.filter { it.saleId !in cancelled }.forEach { add(QuarterTax.Doc(QuarterTax.Kind.RETURN, it.date, minor(it.amount))) }
      d.purchases.forEach { add(QuarterTax.Doc(QuarterTax.Kind.PURCHASE, it.date, minor(it.totalAmount))) }
    }
  }

  data class Report(val quarter: QuarterTax.Quarter, val summary: QuarterTax.Summary, val dueIso: String?)

  fun report(d: ShopData, cfg: QuarterTax.Config, x: QuarterTax.Quarter): Report? {
    val c = cfg.clamped()
    val (from, to) = QuarterTax.range(x.fiscalYear, x.q, c.startMonth) ?: return null
    return Report(x, QuarterTax.summarize(docs(d), from, to, c.rateBp), QuarterTax.dueDate(x, c))
  }

  /** سه‌ماهه‌ای که صفحه نخست نشان می‌دهد: همانی که یادآورش روشن است، وگرنه سه‌ماههٔ پیش (که باید پرداخت شود) */
  fun initial(today: String, cfg: QuarterTax.Config): QuarterTax.Quarter? {
    val c = cfg.clamped()
    QuarterTax.reminder(today, c)?.let { return it.quarter }
    return QuarterTax.quarterOf(today, c.startMonth)?.let(QuarterTax::previous)
  }

  /** «سرطان–سنبلهٔ ۱۴۰۵» */
  fun monthsLabel(x: QuarterTax.Quarter, startMonth: Int, lang: String = "fa"): String {
    val m = QuarterTax.months(x.fiscalYear, x.q, startMonth)
    if (m.size != 3) return ""
    val (y1, m1) = m[0]
    val (y3, m3) = m[2]
    val a = SolarHijri.monthName(m1, lang)
    val b = SolarHijri.monthName(m3, lang)
    return if (y1 == y3) "$a–$b $y3" else "$a $y1–$b $y3"
  }

  /** برگهٔ CSV/چاپ: یک ردیف برای هر بند؛ عدد لاتین و به افغانی (همان `ShrinkLoss.afn`) */
  fun sheet(r: Report, startMonth: Int): ReportCsv.Sheet {
    val s = r.summary
    val rows = mutableListOf(listOf("بند", "تعداد سند", "مبلغ (افغانی)"))
    rows += listOf("دوره", monthsLabel(r.quarter, startMonth), "${s.from} تا ${s.to}")
    rows += listOf("فروشِ مشمول", "${s.salesCount}", ShrinkLoss.afn(s.salesMinor))
    rows += listOf("برگشتِ فروش", "${s.returnsCount}", ShrinkLoss.afn(s.returnsMinor))
    rows += listOf("درآمدِ ناخالصِ خالص", "", ShrinkLoss.afn(s.netMinor))
    rows += listOf("نرخ (درصد)", "", QuarterTax.rateText(s.rateBp))
    rows += listOf("مالیاتِ فروش", "", ShrinkLoss.afn(s.salesTaxMinor))
    rows += listOf("مالیاتِ برگشت", "", ShrinkLoss.afn(s.returnsTaxMinor))
    rows += listOf("مالیاتِ خالصِ دوره", "", ShrinkLoss.afn(s.netTaxMinor))
    rows += listOf("خرید (مالیاتِ خرید ثبت نمی‌شود)", "${s.purchasesCount}", ShrinkLoss.afn(s.purchasesMinor))
    rows += listOf("سررسید", "", r.dueIso.orEmpty())
    return ReportCsv.Sheet("مالیات-سه‌ماهه-${r.quarter.fiscalYear}-Q${r.quarter.q}", rows)
  }
}
