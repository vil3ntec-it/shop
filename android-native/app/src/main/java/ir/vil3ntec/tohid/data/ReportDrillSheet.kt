package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.ui.screens.DayEndModel

/**
 *  RPT-13 — «ریز» ِ ردیفِ برگه‌های «گزارش‌ها» ِ کامپیوتر (`ui/v3/analytics/ReportsDesk.kt` ‏`DeskReports`؛ کلیک یا Enter ِ
 *  `KeyMap.REPORTS_DRILL` روی ردیفِ جدول). ردیفِ [row] (۰ = نخستین ردیفِ پس از سرستون) **همان** ردیفِ `ReportCsv.of(key, …)` است:
 *
 *  - «فروش» (`sales`): همان فاکتور (همان صافی و ترتیبِ برگه، لغوشده هم) ⇐ قلم‌هایش: کالا، مقدار، فی، جمع؛ جمعِ «جمع» = «مبلغ کل»
 *    ِ همان ردیف.
 *  - «فروشِ هر کاربر» (`sellers`): همان فروشنده (خانهٔ اول) ⇐ فاکتورهایش در همان بازه (همان صافیِ `ReportEngine.bySeller`: بی
 *    لغوشده): روز، فاکتور، فروش؛ شمار = «تعدادِ فاکتور» و جمع = «فروشِ خالص» ِ همان ردیف. با «فروشندهٔ هر ردیف» (POS-23) یک
 *    فاکتور میانِ چند فروشنده پخش است ⇒ ریز نیست (null) تا جمعِ ریز با ردیف نخواند.
 *
 *  برگهٔ دیگر یا ردیفِ بی‌جا ⇒ null. فقط خواندنی، بی Context؛ آزمون: `ReportDrillSheetTest`.
 */
object ReportDrillSheet {

  /** برگه‌هایی که ردیفشان ریز دارد */
  val KEYS: Set<String> = setOf("sales", "sellers")

  private fun whole(v: Double): String = Math.round(v).toString()

  fun of(key: String, d: ShopData, from: String, to: String, row: Int, lineSeller: Boolean = false): ReportCsv.Sheet? = when (key) {
    "sales" -> saleLines(d, from, to, row)
    "sellers" -> if (lineSeller) null else sellerInvoices(d, from, to, row)
    else -> null
  }

  /** همان صافی و ترتیبِ `ReportCsv.sales` */
  private fun saleLines(d: ShopData, from: String, to: String, row: Int): ReportCsv.Sheet? {
    val sale = d.sales.filter { it.date >= from && it.date <= to }.sortedBy { it.createdAt }.getOrNull(row) ?: return null
    val names = d.products.associate { it.id to it.name }
    val rows = mutableListOf(listOf("کالا", "مقدار", "فی", "جمع"))  // داده: سرستونِ ریزِ ردیف، همان زبانِ برگه‌های «گزارش‌ها»
    d.saleItems.filter { it.saleId == sale.id }.forEach { l ->
      rows += listOf(l.name.ifBlank { names[l.productId].orEmpty() }, ir.vil3ntec.tohid.qty(l.quantity), whole(l.unitPrice), whole(l.totalPrice))
    }
    return ReportCsv.Sheet(DayEndModel.docLabel(d, "sale:${sale.id}"), rows)
  }

  /** همان گروهِ `ReportEngine.bySeller` (بی «فروشندهٔ هر ردیف»): فروشنده ⇐ فاکتورهایش */
  private fun sellerInvoices(d: ShopData, from: String, to: String, row: Int): ReportCsv.Sheet? {
    val who = ReportEngine.bySeller(d, from, to).getOrNull(row)?.seller ?: return null
    val rows = mutableListOf(listOf("روز", "فاکتور", "فروش"))  // داده: سرستونِ ریزِ ردیف، همان زبانِ برگه‌های «گزارش‌ها»
    d.sales.filter { it.status != "cancelled" && it.date >= from && it.date <= to && it.seller.trim().ifEmpty { StrGoods.unknownSeller } == who }
      .sortedBy { it.createdAt }
      .forEach { s -> rows += listOf(s.date, DayEndModel.docLabel(d, "sale:${s.id}"), whole(s.finalTotal)) }
    return ReportCsv.Sheet(who, rows)
  }
}
