package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.StrDayEnd as S
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.report.HeatCell
import ir.vil3ntec.tohid.core.report.HeatSale
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.core.report.SalesHeatmap
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonPrimitive

/**
 *  زبانهٔ «ساعت و روز» ِ گزارشِ تازه (RPT-08) — بی Compose (آزمون: `HeatmapModelTest`).
 *
 *  رویدادها از دفتر: هر فاکتورِ لغونشدهٔ بازه با لحظهٔ ثبتش (`createdAt`) و مبلغِ نهایی، و هر برگشتِ بازه با مبلغِ
 *  **منفی** در خانهٔ لحظهٔ خودش (برگشتِ فاکتورِ لغوشده بیرون، همان قاعدهٔ `ReportEngine.sales`). بازه با تاریخِ کاریِ
 *  سند است، مثلِ زبانه‌های دیگر؛ سندِ بی لحظه (۰) جایی در نقشه ندارد و بیرون می‌ماند. حساب همه در
 *  `core/report/SalesHeatmap` است (ساعتِ دیواریِ دکان، روزِ کاری که از نیمه‌شب می‌گذرد، هفته از آغازِ هفتهٔ دکان)؛ این‌جا فقط
 *  جدول‌ها. پول همه `Long` ِ یک‌صدم. زبانه فقط برای کسی است که پول را می‌بیند، مثلِ «پایانِ روز».
 *  LOC-06 (تصمیمِ ۱۴۰۵/۰۸/۲۳ #۲۴): برچسب‌ها و سرستون‌ها از `StrDayEnd` — صفحه و خروجیِ اکسل/چاپ/PDF به زبانِ برنامه.
 */
object HeatmapModel {
  const val TAB_ID = "heatmap"
  val TAB: ReportBoard.TabDef get() = ReportBoard.TabDef(TAB_ID, S.heatTab, true)

  /**
   *  گوشی (RPT-08): همان نقشه زیرِ «پایانِ امروز» با سه بازهٔ کوتاه‌تر از «همه» (نخستین پیش‌فرض) — «امروز» یک ردیف
   *  است و نقشه نمی‌خواهد؛ بازه‌ها همان `ReportBoard.range` ِ کامپیوتر.
   */
  val PHONE_SPANS = listOf("week", "month", "quarter")

  /** زبانه‌های صفحه: همان زبانه‌ها، و «ساعت و روز» فقط با دیدنِ پول */
  fun tabs(base: List<ReportBoard.TabDef>, money: Boolean): List<ReportBoard.TabDef> = if (money) base + TAB else base

  /** روزِ هفتهٔ ISO (۱ دوشنبه … ۷ یکشنبه) */
  private val DAY_NAMES: List<String> get() = listOf(S.monday, S.tuesday, S.wednesday, S.thursday, S.friday, S.saturday, S.sunday)

  fun dayName(isoWeekday: Int): String = DAY_NAMES.getOrElse(isoWeekday - 1) { "" }

  /** «۰۸:۰۰» با رقمِ لاتین، مثلِ ستونِ «ساعت» ِ گزارش‌ها */
  fun hourLabel(hour: Int): String = hour.toString().padStart(2, '0') + ":00"

  private fun minor(afn: Double): Long = Money.fromLegacy(afn, Currency.AFN).minor

  fun events(d: ShopData, from: String, to: String): List<HeatSale> {
    fun inRange(date: String) = date >= from && date <= to
    val cancelled = d.sales.filter { it.status == "cancelled" }.mapTo(HashSet()) { it.id }
    val sold = d.sales.filter { it.status != "cancelled" && it.createdAt > 0 && inRange(it.date) }
      .map { HeatSale(it.createdAt, minor(it.finalTotal), isSale = true) }
    val back = d.saleReturns.filter { it.createdAt > 0 && inRange(it.date) && it.saleId !in cancelled }
      .map { HeatSale(it.createdAt, -minor(it.amount), isSale = false) }
    return sold + back
  }

  /** ردیف‌ها از آغازِ هفتهٔ دکان (`weekStart` به شمارِ ISO، پیش‌فرض ۶ = شنبه؛ LOC-03) */
  fun build(d: ShopData, range: ReportBoard.Range, offsetMinutes: Int, dayStartMinutes: Int, weekStart: Int = 6): SalesHeatmap =
    SalesHeatmap.build(events(d, range.from, range.to), offsetMinutes, dayStartMinutes, weekStart)

  private fun p(s: String) = JsonPrimitive(s)
  private fun p(n: Number) = JsonPrimitive(n)
  private fun col(key: String, label: String, kind: String) = ReportTables.Column(key, label, kind)
  private fun line(name: String, c: HeatCell) = listOf(p(name), p(c.count), p(c.totalMinor))

  /** نقشه (شمارِ فروشِ هر روز × ساعت)، و جمعِ هر ساعت و هر روز با مبلغ — همان راهِ اکسل، چاپ و PDF */
  fun tables(h: SalesHeatmap): List<ReportTables.Table> {
    val hours = 0 until SalesHeatmap.HOURS
    val grid = ReportTables.Table(
      "heatmap.grid", S.heatGridTitle,
      listOf(col("day", S.colDay, "text")) + hours.map { col("h$it", hourLabel(it), "count") } + col("total", S.total, "count"),
      h.weekdays.indices.map { r -> listOf(p(dayName(h.weekdays[r]))) + hours.map { p(h.cell(r, it).count) } + p(h.rowTotals[r].count) },
      listOf(p(S.total)) + hours.map { p(h.hourTotals[it].count) } + p(h.total.count),
    )
    val cols = listOf(col("name", "", "text"), col("count", S.colSalesCount, "count"), col("net", S.colNetAmount, "money"))
    val byHour = ReportTables.Table(
      "heatmap.hours", S.byHourTitle, listOf(cols[0].copy(label = S.colHour)) + cols.drop(1),
      hours.map { line(hourLabel(it), h.hourTotals[it]) }, line(S.total, h.total),
    )
    val byDay = ReportTables.Table(
      "heatmap.days", S.byDayTitle, listOf(cols[0].copy(label = S.colDay)) + cols.drop(1),
      h.weekdays.indices.map { line(dayName(h.weekdays[it]), h.rowTotals[it]) }, line(S.total, h.total),
    )
    return listOf(grid, byHour, byDay)
  }

  /** «شنبه ۱۸:۰۰» ِ پرفروش‌ترین خانه؛ بی فروش ⇒ null */
  fun peakLabel(h: SalesHeatmap): String? = h.peak?.let { (r, hour) -> "${dayName(h.weekdays[r])} ${hourLabel(hour)}" }
}
