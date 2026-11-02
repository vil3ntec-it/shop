package java.util

import platform.Foundation.*

/*
 *  shimِ iOS ِ `java.util.Calendar`/`GregorianCalendar`/`TimeZone` — فقط آنچه کدِ مشترک به کار
 *  می‌برد (`Jalali`، `ReportEngine`، `StorefrontOwner`…): `getInstance`، `get`/`set`/`add` روی
 *  سال/ماه/روز/ساعت/دقیقه/ثانیه/میلی‌ثانیه/روزِ سال/روزِ هفته، `timeInMillis`، و منطقهٔ زمانی.
 *
 *  ⛔ قاعده‌های JDK: ماه از ۰ (ژانویه = ۰)، روزِ هفته از ۱ (یکشنبه = ۱)، `set`/`add` ِ «نرم»
 *  (روزِ ۳۲ ⇐ ماهِ بعد؛ ۳۱ ژانویه + ۱ ماه ⇐ آخرِ فوریه) — NSCalendar ِ میلادی همین را می‌کند.
 *  منطقهٔ ناشناخته ⇐ GMT (مثلِ `TimeZone.getTimeZone` ِ JDK).
 */
class TimeZone private constructor(internal val ns: NSTimeZone) {
  fun getID(): String = ns.name
  val id: String get() = ns.name

  /** فاصله با UTC در آن لحظه (با ساعتِ تابستانی)، میلی‌ثانیه */
  fun getOffset(date: Long): Int = (ns.secondsFromGMTForDate(NSDate.dateWithTimeIntervalSince1970(date / 1000.0)) * 1000).toInt()

  val rawOffset: Int get() = getOffset(0L)

  companion object {
    fun getDefault(): TimeZone = TimeZone(NSTimeZone.defaultTimeZone)
    fun getTimeZone(id: String): TimeZone = TimeZone(NSTimeZone.timeZoneWithName(id) ?: NSTimeZone.timeZoneForSecondsFromGMT(0))
  }
}

open class Calendar internal constructor(private var zone: TimeZone) {
  private val cal: NSCalendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)!!.also { it.timeZone = zone.ns }

  var timeInMillis: Long = currentMillis()

  fun getTimeZone(): TimeZone = zone
  fun setTimeZone(value: TimeZone) { zone = value; cal.timeZone = value.ns }

  private fun date() = NSDate.dateWithTimeIntervalSince1970(timeInMillis.floorDiv(1000L).toDouble())
  private fun millisPart() = timeInMillis.mod(1000L).toInt()

  /** بی روزِ هفته برای `set` (وگرنه NSCalendar روزِ هفتهٔ کهنه را با تاریخِ تازه آشتی می‌دهد) */
  private fun parts(weekday: Boolean = false): NSDateComponents = cal.components(
    NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or NSCalendarUnitHour or
      NSCalendarUnitMinute or NSCalendarUnitSecond or (if (weekday) NSCalendarUnitWeekday else 0uL),
    fromDate = date(),
  )

  fun get(field: Int): Int {
    val c = parts(weekday = true)
    return when (field) {
      YEAR -> c.year.toInt()
      MONTH -> c.month.toInt() - 1
      DAY_OF_MONTH -> c.day.toInt()
      HOUR_OF_DAY -> c.hour.toInt()
      HOUR -> c.hour.toInt() % 12
      MINUTE -> c.minute.toInt()
      SECOND -> c.second.toInt()
      MILLISECOND -> millisPart()
      DAY_OF_WEEK -> c.weekday.toInt()
      DAY_OF_YEAR -> cal.ordinalityOfUnit(NSCalendarUnitDay, inUnit = NSCalendarUnitYear, forDate = date()).toInt()
      AM_PM -> if (c.hour < 12) AM else PM
      ERA -> 1
      else -> throw IllegalArgumentException("iOS Calendar: فیلدِ $field")
    }
  }

  fun set(field: Int, value: Int) {
    val c = parts()
    var ms = millisPart()
    when (field) {
      YEAR -> c.year = value.toLong()
      MONTH -> c.month = value.toLong() + 1
      DAY_OF_MONTH -> c.day = value.toLong()
      HOUR_OF_DAY -> c.hour = value.toLong()
      MINUTE -> c.minute = value.toLong()
      SECOND -> c.second = value.toLong()
      MILLISECOND -> ms = value
      DAY_OF_YEAR -> { c.month = 1; c.day = value.toLong() }
      else -> throw IllegalArgumentException("iOS Calendar: set ِ فیلدِ $field")
    }
    store(c, ms)
  }

  fun set(year: Int, month: Int, date: Int) {
    val c = parts()
    c.year = year.toLong(); c.month = month.toLong() + 1; c.day = date.toLong()
    store(c, millisPart())
  }

  fun set(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int, second: Int = get(SECOND)) {
    val c = NSDateComponents()
    c.year = year.toLong(); c.month = month.toLong() + 1; c.day = date.toLong()
    c.hour = hourOfDay.toLong(); c.minute = minute.toLong(); c.second = second.toLong()
    store(c, millisPart())
  }

  /** همان `clear()` ِ JDK: آغازِ ۱۹۷۰ ِ همین منطقه (۰۰:۰۰:۰۰.۰۰۰ ِ محلی) */
  fun clear() {
    timeInMillis = -zone.getOffset(0L).toLong()
  }

  fun clone(): Any = GregorianCalendar(zone).also { it.timeInMillis = timeInMillis }

  fun add(field: Int, amount: Int) {
    if (field == MILLISECOND) { timeInMillis += amount; return }
    val unit = when (field) {
      YEAR -> NSCalendarUnitYear
      MONTH -> NSCalendarUnitMonth
      DAY_OF_MONTH, DAY_OF_YEAR, DAY_OF_WEEK -> NSCalendarUnitDay
      HOUR_OF_DAY, HOUR -> NSCalendarUnitHour
      MINUTE -> NSCalendarUnitMinute
      SECOND -> NSCalendarUnitSecond
      else -> throw IllegalArgumentException("iOS Calendar: add ِ فیلدِ $field")
    }
    val ms = millisPart()
    val next = cal.dateByAddingUnit(unit, value = amount.toLong(), toDate = date(), options = 0u) ?: return
    timeInMillis = (next.timeIntervalSince1970 * 1000.0).toLong() + ms
  }

  private fun store(c: NSDateComponents, ms: Int) {
    val d = cal.dateFromComponents(c) ?: return
    timeInMillis = (d.timeIntervalSince1970 * 1000.0).toLong() + ms
  }

  companion object {
    const val ERA = 0
    const val YEAR = 1
    const val MONTH = 2
    const val DATE = 5
    const val DAY_OF_MONTH = 5
    const val DAY_OF_YEAR = 6
    const val DAY_OF_WEEK = 7
    const val AM_PM = 9
    const val HOUR = 10
    const val HOUR_OF_DAY = 11
    const val MINUTE = 12
    const val SECOND = 13
    const val MILLISECOND = 14
    const val AM = 0
    const val PM = 1
    const val SUNDAY = 1
    const val MONDAY = 2
    const val TUESDAY = 3
    const val WEDNESDAY = 4
    const val THURSDAY = 5
    const val FRIDAY = 6
    const val SATURDAY = 7
    const val JANUARY = 0
    const val DECEMBER = 11

    fun getInstance(): Calendar = GregorianCalendar()
    fun getInstance(zone: TimeZone): Calendar = GregorianCalendar(zone)
  }
}

class GregorianCalendar(zone: TimeZone = TimeZone.getDefault()) : Calendar(zone) {
  /** نیمه‌شبِ همان روز در منطقهٔ پیش‌فرض (ماه از ۰) */
  constructor(year: Int, month: Int, dayOfMonth: Int) : this() {
    set(year, month, dayOfMonth, 0, 0, 0)
    set(MILLISECOND, 0)
  }
}

private fun currentMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
