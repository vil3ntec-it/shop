package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.report.DocGaps

/**
 *  RPT-17 — دفتر ⇒ ورودیِ `core/report/DocGaps`، و برگهٔ «شماره‌های جاافتاده» (`ReportCsv`).
 *
 *  - فروش: `invoiceNumber` با `series` ِ دستگاه (F0-03)؛ فروشِ بی شماره بیرون (شمرده می‌شود).
 *    سریِ «O» (فروشِ تحویلِ سفارشِ آنلاین = شمارهٔ سفارشِ سرور، `OrderFulfillment.SERIES`) و «W»
 *    (فاکتورِ آنلاینِ قدیمیِ سرور) شمارهٔ دستگاه نیستند: گزارش نمی‌شوند، ولی پرشِ شمارندهٔ دستگاه‌ها
 *    را توضیح می‌دهند (`SyncEngine.withInvoiceCursor` همهٔ فروش‌ها را می‌بیند).
 *  - خرید: یک سند برای هر `invoiceNo` > 0 (ردیف‌های یک فاکتور با هم)؛ ردیفِ کهنهٔ بی شماره بیرون.
 *
 *  فقط خواندنی؛ بی android.* (آزمون: `DocGapsSheetTest`).
 */
object DocGapsSheet {

  /** سری‌هایی که شماره‌شان از سرور است */
  val SERVER_SERIES: Set<String> = setOf(OrderFulfillment.SERIES, ONLINE_SERIES)

  fun docs(d: ShopData): List<DocGaps.Doc> {
    val sales = d.sales.map { s ->
      DocGaps.Doc(
        kind = DocGaps.Kind.SALE,
        series = s.series?.trim().orEmpty(),
        number = (s.invoiceNumber ?: 0).toLong(),
        id = s.id,
        date = s.date,
        cancelled = s.status == "cancelled",
      )
    }
    val purchases = d.purchases.filter { it.invoiceNo > 0 }.groupBy { it.invoiceNo }.map { (no, rows) ->
      val first = rows.minWith(compareBy<Purchase> { it.date }.thenBy { it.id })
      DocGaps.Doc(DocGaps.Kind.PURCHASE, "", no.toLong(), first.id, first.date)
    }
    return sales + purchases
  }

  fun report(d: ShopData, maxGaps: Int = 500): DocGaps.Report = DocGaps.of(docs(d), maxGaps, SERVER_SERIES)

  private fun kindText(k: DocGaps.Kind): String = when (k) {
    DocGaps.Kind.SALE -> StrReports.kindSale
    DocGaps.Kind.PURCHASE -> StrReports.kindPurchase
  }

  /**
   *  هر جای خالی یک ردیف (اول جاافتاده‌ها، بعد پرش‌ها؛ هر کدام به ترتیبِ سری و شماره)، بعد هر شمارهٔ
   *  تکراری یک ردیف. «از/تا» با سری («A1-5») یا «#» نوشته می‌شوند تا جدول آن‌ها را مبلغ نشمارد و جمع نزند.
   */
  fun sheet(d: ShopData, today: String, maxGaps: Int = 500): ReportCsv.Sheet {
    val r = report(d, maxGaps)
    val rows = mutableListOf(
      listOf(
        StrReports.colKind, StrReports.colSeries, StrReports.colFrom, StrReports.colTo,
        StrReports.colCount, StrReports.colStatus, StrReports.colBefore, StrReports.colAfter,
      ),
    )
    fun series(s: String) = s.ifEmpty { StrReports.noSeries }
    //  «#12» برای سندِ بی سری، همان شکلِ رسید؛ همیشه متن، نه عدد
    fun no(s: String, n: Long) = if (s.isEmpty()) "#$n" else DocGaps.label(s, n)
    val gaps = r.runs.flatMap { run -> run.gaps.map { run to it } }
      .sortedBy { if (it.second.cause == DocGaps.Cause.MISSING) 0 else 1 }
    for ((run, g) in gaps) {
      rows += listOf(
        kindText(run.kind), series(run.series), no(run.series, g.from), no(run.series, g.to),
        g.count.toString(),
        if (g.cause == DocGaps.Cause.MISSING) StrReports.statusMissing else StrReports.statusJump(g.jumpSeries.ifEmpty { StrReports.noSeries }),
        g.beforeDate, g.afterDate,
      )
    }
    for (run in r.runs) for (dup in run.duplicates) {
      val label = no(run.series, dup.number)
      rows += listOf(kindText(run.kind), series(run.series), label, label, "", StrReports.statusDuplicate(dup.ids.size), "", "")
    }
    if (r.truncated) rows += listOf("", "", "", "", "", StrReports.moreGaps(r.gaps - r.listed), "", "")
    return ReportCsv.Sheet("${StrReports.sheetName}-$today", rows)
  }
}
