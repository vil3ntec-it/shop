package ir.vil3ntec.tohid.data

/**
 *  دوره‌های کادرِ کشوییِ «خریدها» (e-rows-63b، همان یک کادرِ کشوییِ ۶۳w): امروز، این ماه، ماه‌های پیشینِ دارای خرید (تا
 *  [RECENT_MONTHS])، امسال — به تقویمِ برنامه (`SalesPeriods.key`: خورشیدی، یا میلادی). خالص؛ «همهٔ زمان‌ها» = null.
 *  کلید: `day:<yyyy-MM-dd>`، `m:<yyyy-MM>`، `y:<yyyy>`.
 */
object PurchasePeriods {
  const val RECENT_MONTHS = 6

  fun day(iso: String): String = "day:$iso"
  fun month(iso: String, gregorian: Boolean): String = "m:" + SalesPeriods.key(iso, SalesPeriods.Unit.MONTH, gregorian)
  fun year(iso: String, gregorian: Boolean): String = "y:" + SalesPeriods.key(iso, SalesPeriods.Unit.YEAR, gregorian)

  /** کلیدهای کادر، به ترتیب: امروز، این ماه، ماه‌های پیشینِ دارای خرید (تازه‌ترین اول)، امسال */
  fun keys(dates: List<String>, today: String, gregorian: Boolean): List<String> {
    val thisMonth = month(today, gregorian)
    val older = dates.asSequence().filter { it.isNotBlank() && it <= today }.map { month(it, gregorian) }
      .filter { it != thisMonth && it != "m:" }.distinct().sortedDescending().take(RECENT_MONTHS).toList()
    return listOf(day(today), thisMonth) + older + year(today, gregorian)
  }

  /** روزِ [date] (`yyyy-MM-dd`) در دورهٔ [key] هست؟ کلیدِ ناخوانا ⇒ همه */
  fun contains(key: String, date: String, gregorian: Boolean, today: String): Boolean {
    val (kind, value) = key.substringBefore(':') to key.substringAfter(':', "")
    if (value.isEmpty()) return true
    return when (kind) {
      "day" -> date == value
      "m" -> month(date, gregorian) == key
      "y" -> year(date, gregorian) == key
      else -> true
    }
  }

  /** نوعِ کلید برای نوشتهٔ کادر: امروز، این ماه، امسال یا دورهٔ نام‌دار */
  enum class Kind { TODAY, THIS_MONTH, THIS_YEAR, MONTH, YEAR, OTHER }

  fun kind(key: String, today: String, gregorian: Boolean): Kind = when {
    key == day(today) -> Kind.TODAY
    key == month(today, gregorian) -> Kind.THIS_MONTH
    key == year(today, gregorian) -> Kind.THIS_YEAR
    key.startsWith("m:") -> Kind.MONTH
    key.startsWith("y:") -> Kind.YEAR
    else -> Kind.OTHER
  }

  /** «۲۰۲۶-۰۷» / «۱۴۰۵-۰۷» ⇒ (سال، ماه)؛ ناخوانا ⇒ null */
  fun yearMonth(key: String): Pair<Int, Int>? {
    val v = key.substringAfter(':', "")
    val y = v.take(4).toIntOrNull() ?: return null
    val m = v.drop(5).toIntOrNull() ?: return null
    return y to m
  }
}
