package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.cost.CostMethod
import ir.vil3ntec.tohid.core.cost.CostPolicy
import ir.vil3ntec.tohid.core.promo.PromoEffectReport
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportLedger
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.data.PromoEffectBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.core.i18n.StrReportPages as S
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 *  PRM-16 — زبانهٔ «اثرِ تخفیف» در «گزارشِ تازه» (پشتِ همان تنظیمِ `report_fresh`)، بی Compose (آزمون: `PromoFreshTest`).
 *  فقط برای کسی که پول را می‌بیند و فقط وقتی دفتر قلمِ تخفیفِ خودکار دارد. عددها همه از `PromoEffectBook` (هستهٔ
 *  `PromoEffect` روی دفترِ برنامه، بها از همان `ReportCore.costs` با روشِ `CostPolicy` ِ صفحه)؛ این‌جا فقط جدول و برچسب.
 */
object PromoFresh {
  const val TAB_ID = "promo"

  fun tabs(base: List<ReportBoard.TabDef>, money: Boolean, has: Boolean): List<ReportBoard.TabDef> =
    if (money && has) base + ReportBoard.TabDef(TAB_ID, S.promoTab, true) else base

  /** نتیجهٔ زبانه: گزارش، یا null اگر هسته عدد را بیرون از بازه دید (هرگز عددِ غلط) */
  data class View(val report: PromoEffectReport?, val method: CostMethod, val from: String, val to: String)

  fun view(d: ShopData, l: ReportLedger, span: String, today: String, clock: ReportClock?, method: CostMethod = CostPolicy.DEFAULT): View {
    val r = ReportBoard.range(span, today)
    val costing = ReportFreshModel.costingOf(method)
    val report = try {
      PromoEffectBook.of(d, l, r.from, r.to, costing, ReportFreshModel.approvedOf(costing), clock)
    } catch (e: ArithmeticException) { null }
    return View(report, method, r.from, r.to)
  }

  private fun n(x: Long?): JsonPrimitive = if (x == null) JsonNull else JsonPrimitive(x)

  /** جدولِ زبانه؛ عنوانِ سود با برچسبِ همان روش (`CostPolicy.label`)؛ ردیفِ «جمع» هر قلم را یک بار می‌شمارد */
  fun tables(v: View): List<ReportTables.Table> {
    val r = v.report ?: return emptyList()
    if (r.rows.isEmpty()) return emptyList()
    val cols = listOf(
      ReportTables.Column("name", S.colPromo, "text"),
      ReportTables.Column("invoices", S.colInvoice, "count"),
      ReportTables.Column("qty", S.colQty, "qty"),
      ReportTables.Column("discount", S.colPromoDiscount, "money"),
      ReportTables.Column("revenue", S.colNetSales, "money"),
      ReportTables.Column("profit", CostPolicy.label(S.colProfit, v.method), "money"),
      ReportTables.Column("share", S.colShare, "bp"),
      ReportTables.Column("depth", S.colDepth, "bp"),
      ReportTables.Column("approx", S.colApprox, "flag"),
    )
    val rows = r.rows.map { x ->
      listOf(
        JsonPrimitive(x.name.ifEmpty { x.id }), JsonPrimitive(x.invoices.toLong()), JsonPrimitive(x.qtyMilli),
        JsonPrimitive(x.discountMinor), JsonPrimitive(x.revenueMinor), n(x.profitMinor),
        n(x.revenueShareBp), n(x.discountDepthBp), JsonPrimitive(x.profitApproximate),
      )
    }
    //  سهمِ فروشِ تخفیف‌خورده از کلِ فروش — همان گردِ نیمه به بالای هسته
    val share = ReportCore.bp(r.promoRevenueMinor, r.totalRevenueMinor)
    val total = listOf(
      JsonPrimitive(S.promoTotal), JsonNull, JsonNull, JsonPrimitive(r.promoDiscountMinor), JsonPrimitive(r.promoRevenueMinor),
      JsonNull, n(share), JsonNull, JsonPrimitive(r.profitApproximate),
    )
    return listOf(ReportTables.Table("promo", S.promoTitle, cols, rows, total))
  }

  /** یک جمله زیرِ جدول: روشِ بها و چرا تقریبی است، و قاعدهٔ قلمِ دوتخفیفه */
  fun note(v: View): String = when (val r = v.report) {
    null -> S.promoFailed
    else -> if (r.rows.isEmpty()) S.promoNone else S.promoNote + " " + CostPolicy.explain(v.method)
  }
}
