package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.core.i18n.StrReportPages as S
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 *  PRD-15 — زبانهٔ «بهای میانگین» در «گزارشِ تازه» (پشتِ `report_fresh`، فقط برای کسی که پول را می‌بیند)، بی Compose
 *  (آزمون: `StockCostFreshTest`). عدد فقط `ReportCore.stockCost` (همان بازپخشِ میانگینِ موزون که سودِ گزارش‌ها را می‌سازد)؛
 *  فقط خواندنی — هیچ کادر یا دکمه‌ای بهای میانگین را عوض نمی‌کند. ردیفِ «جمع» = ارزشِ کالاهای با موجودیِ مثبت.
 */
object StockCostFresh {
  const val TAB_ID = "stock-cost"

  fun tabs(base: List<ReportBoard.TabDef>, money: Boolean): List<ReportBoard.TabDef> =
    if (money) base + ReportBoard.TabDef(TAB_ID, S.stockCostTab, false) else base

  private fun n(x: Long?): JsonPrimitive = if (x == null) JsonNull else JsonPrimitive(x)

  fun tables(c: ReportCore.StockCost, names: (String) -> String = { it }): List<ReportTables.Table> {
    if (c.rows.isEmpty()) return emptyList()
    val cols = listOf(
      ReportTables.Column("name", S.colProduct, "text"),
      ReportTables.Column("qty", S.colQty, "qty"),
      ReportTables.Column("unitCost", S.colUnitCost, "money"),
      ReportTables.Column("value", S.colStockValue, "money"),
      ReportTables.Column("approx", S.colApprox, "flag"),
    )
    val rows = c.rows.map { r ->
      listOf(
        JsonPrimitive(r.name.ifEmpty { names(r.productId) }), JsonPrimitive(r.qtyMilli), n(r.unitCostMinor),
        JsonPrimitive(r.valueMinor), JsonPrimitive(r.approximate),
      )
    }
    val total = listOf(JsonPrimitive(ir.vil3ntec.tohid.core.i18n.StrReports.total), JsonNull, JsonNull, JsonPrimitive(c.totals.valueMinor), JsonPrimitive(c.totals.approximate))
    return listOf(ReportTables.Table("stock-cost", S.stockCostTitle, cols, rows, total))
  }

  /** زیرِ جدول: قاعده، و کالاهای منفی که در جمع نیستند */
  fun note(c: ReportCore.StockCost): String =
    S.stockCostNote + if (c.totals.negative > 0) " " + S.negativeStock(c.totals.negative) else ""
}
