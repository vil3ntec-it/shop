package ir.vil3ntec.tohid.ui.v3.sales.history

import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.pay.PayStatus
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFixFlow
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.PayStatusFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.data.ShopData

/** «کجا» ِ فیلتر: همه، فاکتورِ دکان، سفارشِ اینترنتی (همان مرزِ X/Z: `CashShift.isOnline`) */
enum class HistoryChannel(val id: String) {
  ALL("all"), STORE("store"), ONLINE("online");

  companion object {
    fun of(id: String?): HistoryChannel = entries.firstOrNull { it.id == id } ?: ALL
  }
}

/**
 *  نمایه‌های یک‌باره برای ردیف‌های فهرست — `HistoryModel.row` بی این‌ها برای هر ردیف همهٔ اقلام، برگشت‌ها و سابقه را
 *  می‌گشت؛ با ده هزار فاکتور پیمایش کُند می‌شد. معنا همان است (`SalesEngine.returnable`، `PayStatusFlow.chip`).
 */
class RowLookup(d: ShopData) {
  val items: Map<String, List<SaleItem>> = d.saleItems.groupBy { it.saleId }
  val returnDocs: Map<String, List<String>> =
    d.saleReturns.filter { it.docId != null }.groupBy { it.saleId }.mapValues { (_, r) -> r.mapNotNull { it.docId }.distinct() }
  val payChanges: Map<String, List<PayStatus.Change>> = PayStatusFlow.bySale(d)
  val fixed: Set<String> = PayFixFlow.fixedSales(d)
  val debtors: Map<String, String> = d.debtors.associate { it.id to it.name }
  private val products: Map<String, String> = d.products.associate { it.id to it.name }

  fun canReturn(s: Sale): Boolean = s.status != "cancelled" && items[s.id].orEmpty().any { SalesEngine.returnable(it) > 0 }

  /** همان `SalesEngine.itemName`، با نمایهٔ کالا */
  fun itemName(item: SaleItem): String =
    if (item.productId.isBlank()) item.name.ifBlank { HistoryModel.FREE_ITEM }
    else products[item.productId] ?: item.name.ifBlank { StrMoney.deletedProduct }
}

/**
 *  دفترِ تاریخچه برای صفحه: روزها با جمعِ دفتر (`SalesPeriods`) و سه نمای روز / ماه / سال، یک بار در پس‌زمینه برای هر
 *  تغییرِ دفتر. تقویمِ ماه و سال همان تنظیمِ برنامه است ([gregorian]: خورشیدی، یا میلادی با «تقویمِ میلادی»).
 */
class HistoryBook(
  val days: List<SalesPeriods.Day>,
  val today: String,
  val gregorian: Boolean,
  val look: RowLookup,
) {
  private val byUnit: Map<SalesPeriods.Unit, List<SalesPeriods.Period>> =
    SalesPeriods.Unit.entries.associateWith { SalesPeriods.periods(days, it, gregorian) }

  fun periods(unit: SalesPeriods.Unit): List<SalesPeriods.Period> = byUnit.getValue(unit)

  fun currentKey(unit: SalesPeriods.Unit): String = SalesPeriods.key(today, unit, gregorian)

  /** دورهٔ جاری (امروز، این ماه، امسال) — بی فاکتور هم با جمعِ صفر */
  fun current(unit: SalesPeriods.Unit): SalesPeriods.Period {
    val key = currentKey(unit)
    return periods(unit).firstOrNull { it.key == key } ?: SalesPeriods.range(key, unit, gregorian).let { (from, to) ->
      SalesPeriods.Period(key, unit, from, to, SalesPeriods.Totals(), emptyList())
    }
  }

  /** دوره‌های دیگر، تازه‌ترین اول */
  fun older(unit: SalesPeriods.Unit): List<SalesPeriods.Period> = currentKey(unit).let { k -> periods(unit).filter { it.key != k } }

  fun find(unit: SalesPeriods.Unit, key: String): SalesPeriods.Period? = if (key == currentKey(unit)) current(unit) else periods(unit).firstOrNull { it.key == key }

  companion object {
    /** تقویمِ گروه‌بندی همان نمایشِ تاریخ است (`displayDate`) */
    fun gregorianNow(): Boolean = I18n.gregorian(I18n.lang, LocaleChoice.calendar)

    fun build(d: ShopData, clock: ReportClock, now: Long, gregorian: Boolean = gregorianNow()): HistoryBook =
      HistoryBook(SalesPeriods.days(d, clock, now), clock.date(now), gregorian, RowLookup(d))
  }
}

/** برچسبِ دوره‌ها — روز «امروز/دیروز/۱۲ عقرب ۱۴۰۵»، ماه «عقرب ۱۴۰۵»، سال «۱۴۰۵» (تقویمِ برنامه) */
object PeriodLabel {

  /** سرخطِ بخش در فهرست */
  fun title(p: SalesPeriods.Period, today: String, gregorian: Boolean): String = when (p.unit) {
    SalesPeriods.Unit.DAY -> when (p.key) {
      today -> Str.today
      HistoryModel.yesterday(today) -> StrKit.yesterday
      else -> full(p, gregorian)
    }
    else -> full(p, gregorian)
  }

  /** برچسبِ کامل (کاغذ و سرخطِ دورهٔ جاری) */
  fun full(p: SalesPeriods.Period, gregorian: Boolean, lang: Lang = I18n.lang, months: String = LocaleChoice.months): String {
    if (p.key.isEmpty()) return StrHistory.noDate
    val cal = if (gregorian) I18n.CAL_GREGORIAN else I18n.CAL_SOLAR
    return when (p.unit) {
      SalesPeriods.Unit.DAY -> I18n.dateLong(p.key, lang, cal, months)
      SalesPeriods.Unit.MONTH -> {
        val m = p.key.drop(5).toIntOrNull() ?: return p.key
        val names = if (gregorian) I18n.gregorianMonths(lang) else I18n.solarMonths(lang, months)
        (names.getOrNull(m - 1) ?: p.key) + " " + p.key.take(4)
      }
      SalesPeriods.Unit.YEAR -> p.key
    }
  }

  /** سرخطِ کادرِ دورهٔ جاری: «امروز»، «این ماه · عقرب ۱۴۰۵»، «امسال · ۱۴۰۵» */
  fun current(p: SalesPeriods.Period, gregorian: Boolean): String = when (p.unit) {
    SalesPeriods.Unit.DAY -> Str.today + " · " + full(p, gregorian)
    SalesPeriods.Unit.MONTH -> StrHistory.thisMonth + " · " + full(p, gregorian)
    SalesPeriods.Unit.YEAR -> StrHistory.thisYear + " · " + full(p, gregorian)
  }

  fun unitName(u: SalesPeriods.Unit): String = when (u) {
    SalesPeriods.Unit.DAY -> StrHistory.day
    SalesPeriods.Unit.MONTH -> StrHistory.month
    SalesPeriods.Unit.YEAR -> StrHistory.year
  }

  /** خطِ زیرِ مبلغ: «۱۲ فاکتور · ۲ اینترنتی · ۱ لغوشده» */
  fun counts(t: SalesPeriods.Totals): String = listOfNotNull(
    StrHistory.invoices(ir.vil3ntec.tohid.plain(t.invoices)),
    t.online.takeIf { it > 0 }?.let { StrHistory.online(ir.vil3ntec.tohid.plain(it)) },
    t.cancelled.takeIf { it > 0 }?.let { StrHistory.cancelled(ir.vil3ntec.tohid.plain(it)) },
  ).joinToString(" · ")

  /** پولِ واحدِ کوچک ⇒ «۱۲٬۵۰۰ ؋» ِ صفحه */
  fun amount(minor: Long): String = ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText.amount(PayFlow.major(minor))
}

/** فیلترِ فهرست — جمع‌ها همیشه کلِ دوره‌اند (همان X/Z)؛ فیلتر فقط ردیف‌ها را کم می‌کند */
fun HistoryFilter.matches(s: Sale, channel: HistoryChannel): Boolean {
  val online = CashShift.isOnline(s)
  val byChannel = when (channel) {
    HistoryChannel.ALL -> true
    HistoryChannel.STORE -> !online
    HistoryChannel.ONLINE -> online
  }
  if (!byChannel) return false
  return when (this) {
    HistoryFilter.CASH, HistoryFilter.CREDIT, HistoryFilter.NON_CASH -> s.status != "cancelled" && PayFlow.payGroup(s) == id
    HistoryFilter.CANCELLED -> s.status == "cancelled"
    HistoryFilter.ALL -> true
  }
}
