package java.util.concurrent

/** shimِ iOS ِ `TimeUnit` — همان تبدیل‌های JDK (بریدن رو به صفر) */
enum class TimeUnit(private val nanos: Long) {
  NANOSECONDS(1L),
  MICROSECONDS(1_000L),
  MILLISECONDS(1_000_000L),
  SECONDS(1_000_000_000L),
  MINUTES(60_000_000_000L),
  HOURS(3_600_000_000_000L),
  DAYS(86_400_000_000_000L);

  private fun to(d: Long, unit: TimeUnit): Long = when {
    nanos == unit.nanos -> d
    nanos > unit.nanos -> {
      val r = nanos / unit.nanos
      if (d > Long.MAX_VALUE / r) Long.MAX_VALUE else if (d < Long.MIN_VALUE / r) Long.MIN_VALUE else d * r
    }
    else -> d / (unit.nanos / nanos)
  }

  fun toNanos(d: Long): Long = to(d, NANOSECONDS)
  fun toMicros(d: Long): Long = to(d, MICROSECONDS)
  fun toMillis(d: Long): Long = to(d, MILLISECONDS)
  fun toSeconds(d: Long): Long = to(d, SECONDS)
  fun toMinutes(d: Long): Long = to(d, MINUTES)
  fun toHours(d: Long): Long = to(d, HOURS)
  fun toDays(d: Long): Long = to(d, DAYS)
}
