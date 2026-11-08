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
   *  STF-08 — ردیف‌های پورسانتِ بازه (`from`..`to`، تاریخِ فاکتور): هر فاکتورِ لغونشده به نامِ فروشنده‌اش، به تفکیکِ
   *  دستهٔ کالای قلم‌ها (`Commission.saleLines`، برای «نرخِ دسته»)، و هر برگشتِ همین بازه منفی به نامِ فروشندهٔ
   *  **فاکتورِ اصل** (`Commission.returnLines`) با دستهٔ کالای برگشتی، حتی اگر فروش در ماهِ پیش بوده.
   *  [lineSeller] (POS-23، تنظیمِ خاموشِ دکان `LineSellerPref`): هر قلم به نامِ فروشندهٔ **ردیفِ خودش** (`SaleItem.seller`) و
   *  برگشتِ آن از همان فروشندهٔ ردیف؛ `false` ⇒ بیت‌به‌بیت پیشین (فروشندهٔ فاکتور برای همه).
   */
  fun commissionLines(d: ShopData, from: String, to: String, lineSeller: Boolean = false): List<Commission.Line> {
    fun inRange(date: String) = date >= from && date <= to
    fun legacy(v: Double) = Money.fromLegacy(v, Currency.AFN)
    val categoryOf = d.products.associate { it.id to it.category.trim() }
    val inRangeSales = d.sales.filter { it.status != "cancelled" && inRange(it.date) }
    val returns = d.saleReturns.filter { inRange(it.date) }
    //  فقط قلم‌های همین بازه (فاکتورِ بازه، یا قلمی که برگشتِ بازه نام می‌برد) — نه نگاشتِ همهٔ دفتر
    val saleIds = inRangeSales.mapTo(HashSet()) { it.id }
    val itemIds = returns.mapNotNullTo(HashSet()) { r -> r.saleItemId.takeIf { (lineSeller || r.productId.isBlank()) && it.isNotBlank() } }
    val items = d.saleItems.filter { it.saleId in saleIds }.groupBy { it.saleId }
    val itemOf = d.saleItems.filter { it.id in itemIds }.associateBy { it.id }
    fun seller(i: SaleItem?) = if (lineSeller) i?.seller else null
    val sold = inRangeSales.flatMap { s ->
      Commission.saleLines(s.seller.trim(), legacy(s.finalTotal), items[s.id].orEmpty().map { Commission.Item(categoryOf[it.productId], legacy(it.totalPrice), seller(it)) })
    }
    val origins = d.sales.associate { it.id to Commission.Origin(it.seller, it.status == "cancelled") }
    val back = returns.map {
      Commission.Returned(
        it.saleId, legacy(it.amount), categoryOf[it.productId.ifBlank { itemOf[it.saleItemId]?.productId.orEmpty() }], seller(itemOf[it.saleItemId]),
      )
    }
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
