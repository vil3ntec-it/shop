package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.pay.DayClose
import ir.vil3ntec.tohid.core.report.ReportClock

/**
 *  «تاریخچه» به روز، ماه و سال — جمع‌ها یک بار برای همهٔ دفتر، **بی قاعدهٔ جمعِ تازه**.
 *
 *  ── پول همان X/Z و پایانِ روز است ─────────────────────────────────────
 *  جمعِ پولِ هر روز همان «دفتر» ِ پایانِ روز (`DayEnd`، RPT-04) است: همان رویدادهای `PayFlow.ledgerEvents`
 *  (فروش، برگشت و رسید به تفکیکِ روش؛ فاکتورِ لغوشده، سفارشِ اینترنتی، امانتِ پیک و پرداختِ نارسیده بیرون — همان
 *  قاعده‌های X/Z) و همان جمع‌زنِ `DayClose.of`، با همان ساعتِ گزارش‌ها (روزِ کاری). ماه و سال فقط جمعِ روزهای
 *  خودشان‌اند. آزمونِ `SalesPeriodsTest` برابری با `DayEnd.report` و Z ِ شیفت را عدد به عدد می‌سنجد.
 *
 *  ── کارایی ─────────────────────────────────────────────────────────
 *  رویدادهای همهٔ دفتر یک بار ساخته و به روز بخش می‌شوند (نه یک بار برای هر روز). تنها چیزی که به پایانِ پنجرهٔ
 *  پایانِ روز بند است — اصلاحِ روشِ پرداخت (POS-40) و وضعیتِ پرداخت (PAY-07) که بعداً می‌رسند — فقط برای روزهای
 *  فاکتورهای همان سندها دوباره با پنجرهٔ دقیقِ `DayEnd.window` ساخته می‌شود؛ پس هر روز بیت‌به‌بیت همان پایانِ روز است.
 *
 *  ── چند کشو (PAY-17) ────────────────────────────────────────────────
 *  `DayEnd` فقط کشوی اصلی را در «دفتر» می‌شمارد؛ تاریخچه همهٔ فاکتورهاست، پس مهرِ کشوی نام‌دار پیش از ساختنِ
 *  رویدادها برداشته می‌شود (همان «همهٔ کشوها با هم»). بی «چند کشو» (پیش‌فرض) هیچ چیز عوض نمی‌شود.
 *
 *  خالص: بی Compose و بی سکو (کدِ مشترکِ گوشی، کامپیوتر و آیفون).
 */
object SalesPeriods {

  enum class Unit { DAY, MONTH, YEAR }

  /** جمع‌های یک دوره؛ پول به واحدِ کوچک (۱ افغانی = ۱۰۰) مثلِ X/Z */
  data class Totals(
    /** فاکتورهای ثبت‌شده در دکان — نه لغوشده، نه سفارشِ اینترنتی */
    val invoices: Int = 0,
    val cancelled: Int = 0,
    val cancelledMinor: Long = 0,
    /** سفارشِ اینترنتی (W): پولِ دخل نیست و در جمع نمی‌آید — همان X/Z */
    val online: Int = 0,
    val onlineMinor: Long = 0,
    /** رویدادهای برگشتِ پول (مرجوعی، بازپرداختِ پیش‌پرداخت) */
    val returns: Int = 0,
    /** روش ⇐ فروش / برگشت / رسیدِ دفتر؛ نقد اول، بعد به ترتیبِ دیدن — همان `DayClose.MethodLine.ledger` */
    val methods: Map<String, DayClose.Sums> = emptyMap(),
  ) {
    /** جمعِ همهٔ روش‌ها با همان جمعِ دقیقِ `DayClose.Sums` (بیرون از ±۲^۵۳ خطا، نه گرد) */
    val total: DayClose.Sums get() = methods.values.fold(DayClose.Sums()) { a, s -> a + s }
    val sales: Long get() = total.sales
    val refunds: Long get() = total.refunds
    /** دریافتِ بدهی و شارژِ کارتِ هدیه — در «خالصِ» X/Z هست، در فروشِ فاکتورها نه */
    val receipts: Long get() = total.receipts
    val cash: Long get() = methods[PayFlow.CASH]?.sales ?: 0L
    val credit: Long get() = methods[PayFlow.ACCOUNT]?.sales ?: 0L
    val other: Long get() = sales - cash - credit
    /** فروش − برگشت */
    val net: Long get() = sales - refunds
    val isEmpty: Boolean get() = invoices == 0 && cancelled == 0 && online == 0 && returns == 0 && methods.values.all { it == DayClose.Sums() }

    operator fun plus(o: Totals): Totals = Totals(
      invoices + o.invoices, cancelled + o.cancelled, cancelledMinor + o.cancelledMinor,
      online + o.online, onlineMinor + o.onlineMinor, returns + o.returns, merge(methods, o.methods),
    )
  }

  /** یک روزِ کاری: جمع و فاکتورهایش، تازه‌ترین اول */
  data class Day(val date: String, val totals: Totals, val sales: List<Sale>)

  /** یک بخشِ فهرست: روز، ماه یا سال؛ [from]..[to] تاریخِ میلادیِ `YYYY-MM-DD` (برای چاپ و برچسب) */
  data class Period(val key: String, val unit: Unit, val from: String, val to: String, val totals: Totals, val sales: List<Sale>)

  /** روزِ کاریِ یک فاکتور با همان ساعتِ پایانِ روز؛ فاکتورِ بی لحظه (دفترِ کهنه) ⇒ تاریخِ ثبت‌شده‌اش */
  fun dayOf(s: Sale, clock: ReportClock): String = if (s.createdAt > 0) clock.date(s.createdAt) else s.date

  /** همهٔ روزهای دارای فاکتور یا پول، تازه‌ترین اول */
  fun days(d: ShopData, clock: ReportClock, now: Long): List<Day> {
    val data = allDrawers(d)
    val dayOf: (Long) -> String = { clock.date(it) }
    val events = HashMap<String, MutableList<DayClose.Event>>()
    for (e in DayEnd.events(data, FAR_PAST, FAR_FUTURE)) events.getOrPut(dayOf(e.at)) { ArrayList() } += e
    //  روزهایی که سندِ دیررسشان به پنجرهٔ پایانِ روز بند است ⇒ همان پنجره، دقیق
    val late = PayFixFlow.bySale(d).keys + PayStatusFlow.bySale(d).keys
    if (late.isNotEmpty()) {
      val days = data.sales.filter { it.id in late && it.createdAt > 0 }.mapTo(HashSet()) { dayOf(it.createdAt) }
      for (day in days) {
        val w = DayEnd.window(day) ?: continue
        events[day] = DayEnd.events(data, w.first, w.second).filterTo(ArrayList()) { dayOf(it.at) == day }
      }
    }
    val sales = HashMap<String, MutableList<Sale>>()
    for (s in d.sales) sales.getOrPut(dayOf(s, clock)) { ArrayList() } += s
    return (events.keys + sales.keys).sortedDescending().map { day ->
      val list = sales[day].orEmpty().sortedByDescending { it.createdAt }
      Day(day, totals(day, list, events[day].orEmpty(), dayOf, now), list)
    }
  }

  private fun totals(day: String, sales: List<Sale>, events: List<DayClose.Event>, dayOf: (Long) -> String, now: Long): Totals {
    var invoices = 0; var cancelled = 0; var cancelledMinor = 0L; var online = 0; var onlineMinor = 0L
    for (s in sales) when {
      s.status == "cancelled" -> { cancelled++; cancelledMinor += PayFlow.minor(s.finalTotal) }
      CashShift.isOnline(s) -> { online++; onlineMinor += PayFlow.minor(s.finalTotal) }
      else -> invoices++
    }
    val methods = if (events.isEmpty()) emptyMap()
    else DayClose.of(day, emptyList(), events, dayOf, now).methods.associateTo(LinkedHashMap()) { it.method to it.ledger }
    return Totals(invoices, cancelled, cancelledMinor, online, onlineMinor, events.count { it.type == "refund" }, methods)
  }

  /** روزها ⇒ بخش‌های روز، ماه یا سال (تقویمِ خورشیدی، یا میلادی با تنظیمِ تقویم)، تازه‌ترین اول */
  fun periods(days: List<Day>, unit: Unit, gregorian: Boolean): List<Period> {
    if (unit == Unit.DAY) return days.map { Period(it.date, unit, it.date, it.date, it.totals, it.sales) }
    val groups = LinkedHashMap<String, MutableList<Day>>()
    for (day in days) groups.getOrPut(key(day.date, unit, gregorian)) { ArrayList() } += day
    return groups.map { (k, list) ->
      val (from, to) = range(k, unit, gregorian)
      Period(k, unit, from, to, list.fold(Totals()) { a, x -> a + x.totals }, list.flatMap { it.sales })
    }
  }

  /** کلیدِ دورهٔ یک روز: روز `YYYY-MM-DD`، ماه `YYYY-MM`، سال `YYYY` (خورشیدی یا میلادی)؛ تاریخِ ناخوانا ⇒ "" */
  fun key(iso: String, unit: Unit, gregorian: Boolean): String {
    if (unit == Unit.DAY) return iso
    val (y, m) = if (gregorian) {
      val g = CivilDays.jdnOfIso(iso)?.let(CivilDays::gregorianOf) ?: return ""
      g.year to g.month
    } else {
      val s = SolarHijri.toSolar(iso) ?: return ""
      s.year to s.month
    }
    val year = y.toString().padStart(4, '0')
    return if (unit == Unit.YEAR) year else year + "-" + m.toString().padStart(2, '0')
  }

  /** نخستین و واپسین روزِ میلادیِ یک کلید ([key])؛ کلیدِ ناخوانا ⇒ "" و "" */
  fun range(key: String, unit: Unit, gregorian: Boolean): Pair<String, String> {
    val none = "" to ""
    if (unit == Unit.DAY) return key to key
    val y = key.take(4).toIntOrNull() ?: return none
    val months = if (unit == Unit.YEAR) 1 to 12 else (key.drop(5).toIntOrNull() ?: return none).let { it to it }
    return if (gregorian) {
      val from = iso(y, months.first, 1)
      val next = if (months.second == 12) iso(y + 1, 1, 1) else iso(y, months.second + 1, 1)
      from to (CivilDays.addDays(next, -1) ?: return none)
    } else {
      val from = SolarHijri.fromSolar(y, months.first, 1) ?: return none
      from to (SolarHijri.fromSolar(y, months.second, SolarHijri.monthLength(y, months.second)) ?: return none)
    }
  }

  private fun iso(y: Int, m: Int, d: Int) = y.toString().padStart(4, '0') + "-" + m.toString().padStart(2, '0') + "-" + d.toString().padStart(2, '0')

  private fun merge(a: Map<String, DayClose.Sums>, b: Map<String, DayClose.Sums>): Map<String, DayClose.Sums> {
    if (b.isEmpty()) return a
    if (a.isEmpty()) return b
    val out = LinkedHashMap<String, DayClose.Sums>()
    out[PayFlow.CASH] = DayClose.Sums()
    for ((k, v) in a) out[k] = (out[k] ?: DayClose.Sums()) + v
    for ((k, v) in b) out[k] = (out[k] ?: DayClose.Sums()) + v
    return out
  }

  /** PAY-17: مهرِ کشوی نام‌دار برداشته می‌شود تا رویدادهای «دفتر» همهٔ فاکتورها را بشمارند؛ بی کشوی نام‌دار همان دفتر */
  private fun allDrawers(d: ShopData): ShopData {
    if (d.auditLog.none { it.type == CashShift.OPEN && it.drawer != null }) return d
    return d.copy(
      sales = d.sales.map { if (it.shiftId != null) it.copy(shiftId = null) else it },
      saleReturns = d.saleReturns.map { if (it.shiftId != null) it.copy(shiftId = null) else it },
      transactions = d.transactions.map { if (it.shiftId != null) it.copy(shiftId = null) else it },
    )
  }

  /** دورترین گذشته و آینده برای یک بارِ همهٔ دفتر — دور از سرریزِ `Long` در جمع‌وتفریقِ پنجره */
  private const val FAR_PAST = Long.MIN_VALUE / 4
  private const val FAR_FUTURE = Long.MAX_VALUE / 4
}
