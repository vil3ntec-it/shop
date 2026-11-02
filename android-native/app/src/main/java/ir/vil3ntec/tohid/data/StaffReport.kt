package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.report.ReportDates
import ir.vil3ntec.tohid.core.staff.Commission
import ir.vil3ntec.tohid.core.staff.TimeClock

/**
 *  دفترِ دکان ⇐ ورودیِ `core/staff` برای کارتِ «ورود و خروج» (STF-07، STF-08). فقط نگاشت است؛ قاعده‌ها در هسته‌اند
 *  و این‌جا بی android.* است (آزمون: `StaffReportTest`). کارت فقط با پرچمِ سرورِ `shop.staff_time` کشیده می‌شود.
 */
object StaffReport {

  /**
   *  STF-08 — ردیف‌های پورسانتِ بازه (`from`..`to`، تاریخِ فاکتور): هر فاکتورِ لغونشده به نامِ فروشنده‌اش، و هر برگشتِ
   *  همین بازه منفی به نامِ فروشندهٔ **فاکتورِ اصل** (`Commission.returnLines`)، حتی اگر فروش در ماهِ پیش بوده.
   */
  fun commissionLines(d: ShopData, from: String, to: String): List<Commission.Line> {
    fun inRange(date: String) = date >= from && date <= to
    val sold = d.sales.filter { it.status != "cancelled" && inRange(it.date) }
      .map { Commission.Line(it.seller.trim(), null, Money.fromLegacy(it.finalTotal, Currency.AFN)) }
    val origins = d.sales.associate { it.id to Commission.Origin(it.seller, it.status == "cancelled") }
    val back = d.saleReturns.filter { inRange(it.date) }
      .map { Commission.Returned(it.saleId, Money.fromLegacy(it.amount, Currency.AFN)) }
    return sold + Commission.returnLines(back, origins)
  }

  /** یک کارمند در «۷ روزِ گذشته» و «این ماه»؛ `null` یعنی در آن بازه نوبتی ندارد */
  data class Period(val staffId: String, val week: TimeClock.PeriodTotal?, val month: TimeClock.PeriodTotal?)

  /** روزِ آغازِ «۷ روزِ گذشته»: امروز و شش روزِ پیش (همان «هفته» ِ گزارش‌ها) */
  fun weekFrom(today: String): String = ReportDates.addDays(today, -6)

  /**
   *  STF-07 — گزارشِ بازه‌ای: جمعِ ۷ روزِ گذشته و ماهِ جاری (`monthFrom`..`today`) برای هر کارمند، به ترتیبِ شناسه.
   *  نوبت‌ها باید از آغازِ زودترینِ دو بازه آمده باشند (`STAFF_WINDOW_MS`).
   */
  fun periods(spans: List<TimeClock.Span>, today: String, monthFrom: String): List<Period> {
    val week = TimeClock.periodTotals(spans, weekFrom(today), today).associateBy { it.staffId }
    val month = TimeClock.periodTotals(spans, monthFrom, today).associateBy { it.staffId }
    return (week.keys + month.keys).sorted().map { Period(it, week[it], month[it]) }
  }
}
