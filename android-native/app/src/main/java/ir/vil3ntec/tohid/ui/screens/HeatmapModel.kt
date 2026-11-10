package ir.vil3ntec.tohid.ui.screens

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
 */
object HeatmapModel {
  const val TAB_ID = "heatmap"
  val TAB = ReportBoard.TabDef(TAB_ID, "ساعت و روز", true)

  /**
   *  گوشی (RPT-08): همان نقشه زیرِ «پایانِ امروز» با سه بازهٔ کوتاه‌تر از «همه» (نخستین پیش‌فرض) — «امروز» یک ردیف
   *  است و نقشه نمی‌خواهد؛ بازه‌ها همان `ReportBoard.range` ِ کامپیوتر.
   */
  val PHONE_SPANS = listOf("week", "month", "quarter")

  /** زبانه‌های صفحه: همان زبانه‌ها، و «ساعت و روز» فقط با دیدنِ پول */
  fun tabs(base: List<ReportBoard.TabDef>, money: Boolean): List<ReportBoard.TabDef> = if (money) base + TAB else base

  /** روزِ هفتهٔ ISO (۱ دوشنبه … ۷ یکشنبه) */
  private val DAY_NAMES = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

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
      "heatmap.grid", "شمارِ فروش: روز × ساعت",
      listOf(col("day", "روز", "text")) + hours.map { col("h$it", hourLabel(it), "count") } + col("total", "جمع", "count"),
      h.weekdays.indices.map { r -> listOf(p(dayName(h.weekdays[r]))) + hours.map { p(h.cell(r, it).count) } + p(h.rowTotals[r].count) },
      listOf(p("جمع")) + hours.map { p(h.hourTotals[it].count) } + p(h.total.count),
    )
    val cols = listOf(col("name", "", "text"), col("count", "شمارِ فروش", "count"), col("net", "خالصِ مبلغ", "money"))
    val byHour = ReportTables.Table(
      "heatmap.hours", "به تفکیکِ ساعت", listOf(cols[0].copy(label = "ساعت")) + cols.drop(1),
      hours.map { line(hourLabel(it), h.hourTotals[it]) }, line("جمع", h.total),
    )
    val byDay = ReportTables.Table(
      "heatmap.days", "به تفکیکِ روزِ هفته", listOf(cols[0].copy(label = "روز")) + cols.drop(1),
      h.weekdays.indices.map { line(dayName(h.weekdays[it]), h.rowTotals[it]) }, line("جمع", h.total),
    )
    return listOf(grid, byHour, byDay)
  }

  /** «شنبه ۱۸:۰۰» ِ پرفروش‌ترین خانه؛ بی فروش ⇒ null */
  fun peakLabel(h: SalesHeatmap): String? = h.peak?.let { (r, hour) -> "${dayName(h.weekdays[r])} ${hourLabel(hour)}" }
}
