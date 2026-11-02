package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.data.DocGapsSheet
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonPrimitive

/**
 *  RPT-17 — زبانهٔ «شماره‌های جاافتاده» در «گزارشِ تازه» ِ کامپیوتر (`DeskFreshReports`، پشتِ `report_fresh`)، همان
 *  برگهٔ زبانهٔ «گزارش‌ها» (`DocGapsSheet.sheet`، هستهٔ `core/report/DocGaps`) و همان تنظیمِ `DocGapsPref`؛ بی Compose
 *  (آزمون: `DocGapsFreshTest`). خاموش ⇒ همان زبانه‌های امروز. همهٔ ستون‌ها متن‌اند جز «تعداد»، تا جدولِ تازه شماره‌ها را
 *  مبلغ نشمارد و جمع نزند؛ بی بازه (کلِ دفتر، مثلِ زبانهٔ «گزارش‌ها»).
 */
object DocGapsFresh {
  const val TAB_ID = "doc-gaps"

  /** زبانه‌های صفحه: همان زبانه‌ها و — با تنظیمِ روشن — «شماره‌های جاافتاده» در پایان */
  fun tabs(base: List<ReportBoard.TabDef>, on: Boolean): List<ReportBoard.TabDef> =
    if (on) base + ReportBoard.TabDef(TAB_ID, StrReports.docGaps, false) else base

  /** همان برگهٔ «گزارش‌ها» به شکلِ جدولِ گزارشِ تازه (سرِ ستون‌ها = سطرِ اولِ برگه) */
  fun tables(d: ShopData, today: String): List<ReportTables.Table> {
    val rows = DocGapsSheet.sheet(d, today).rows
    val head = rows.firstOrNull() ?: return emptyList()
    val countAt = head.indexOf(StrReports.colCount)
    val cols = head.mapIndexed { i, h -> ReportTables.Column("c$i", h, if (i == countAt) "count" else "text") }
    val body = rows.drop(1).map { r ->
      r.mapIndexed { i, v -> if (i == countAt) (v.toLongOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(v)) else JsonPrimitive(v) }
    }
    return listOf(ReportTables.Table("doc-gaps", StrReports.docGaps, cols, body, null))
  }
}
