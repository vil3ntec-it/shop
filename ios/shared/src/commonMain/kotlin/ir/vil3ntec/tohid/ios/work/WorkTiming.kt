package ir.vil3ntec.tohid.ios.work

/**
 *  حسابِ زمان‌بندیِ WorkManager ِ آیفون (‎iosMain/…/androidx/work/Work.kt‎) — خالص و جدا، تا روی لینوکس در
 *  برابرِ قاعده‌های WorkManager ِ اندروید آزموده شود (`jvmTest/…/WorkTimingJvmTest`).
 */
object WorkTiming {
  /** کمینهٔ بازهٔ کارِ دوره‌ای در WorkManager ِ اندروید */
  const val MIN_PERIOD_MS: Long = 15 * 60_000L

  /** کمینهٔ عقب‌نشینی در WorkManager ِ اندروید (`MIN_BACKOFF_MILLIS`) */
  const val MIN_BACKOFF_MS: Long = 10_000L

  /** سقفِ عقب‌نشینی — همان کامپیوتر (‎desktop/src/shim/…/Work.kt‎) */
  const val MAX_BACKOFF_MS: Long = 15 * 60_000L

  /** بازهٔ دوره‌ای با کفِ اندروید */
  fun period(requested: Long): Long = requested.coerceAtLeast(MIN_PERIOD_MS)

  /**
   *  چند میلی‌ثانیه تا اجرای بعدیِ کارِ دوره‌ای (۰ = همین حالا).
   *  @param last زمانِ آخرین اجرای موفق (۰ = هرگز)
   *  ساعتِ دستگاه که عقب برود (آخرین اجرا «در آینده»)، کار همین حالا اجرا می‌شود: کارِ گم‌شده بدتر از یک اجرای زودتر است.
   */
  fun dueIn(last: Long, period: Long, now: Long): Long {
    if (last <= 0L) return 0L
    val left = last + period - now
    return if (left <= 0L || left > period) 0L else left
  }

  /** عقب‌نشینیِ بعد از یک `retry`: نمایی (دوبرابر) یا خطی (به‌اندازهٔ پایه)، بینِ کف و سقف */
  fun nextBackoff(current: Long, base: Long, linear: Boolean): Long =
    (if (linear) current + base else current * 2).coerceIn(MIN_BACKOFF_MS, MAX_BACKOFF_MS)
}
