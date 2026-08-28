package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.formatDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 *  تاریخِ آخرین پشتیبان.
 *
 *  دفترِ دکان فقط روی همین گوشی است. گوشی گم می‌شود، خراب می‌شود، پاک
 *  می‌شود — و آن‌وقت کارِ چند ماه رفته است. نسخهٔ وب برای همین بعد از هفت
 *  روز یادآوری می‌کرد؛ اینجا هم همان.
 */
object BackupClock {

  private const val PREFS = "tohid"
  private const val KEY = "last_backup_at"
  const val STALE_DAYS = 7

  private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun mark(context: Context) {
    prefs(context).edit().putLong(KEY, System.currentTimeMillis()).apply()
  }

  fun lastAt(context: Context): Long = prefs(context).getLong(KEY, 0L)

  /** چند روز از آخرین پشتیبان گذشته؛ اگر هرگز نگرفته باشد، null */
  fun daysSince(context: Context, now: Long = System.currentTimeMillis()): Int? {
    val at = lastAt(context)
    if (at <= 0L) return null
    return ((now - at) / 86_400_000L).toInt()
  }

  /** آیا وقتش رسیده که یادآوری کنیم */
  fun isStale(context: Context, now: Long = System.currentTimeMillis()): Boolean {
    val days = daysSince(context, now) ?: return true
    return days >= STALE_DAYS
  }

  fun text(context: Context): String {
    val at = lastAt(context)
    if (at <= 0L) return "هنوز پشتیبانی گرفته نشده"
    val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(at))
    val days = daysSince(context) ?: 0
    return when {
      days <= 0 -> "امروز"
      days == 1 -> "دیروز — ${formatDate(iso)}"
      else -> "${formatDate(iso)} (${days} روز پیش)"
    }
  }
}
