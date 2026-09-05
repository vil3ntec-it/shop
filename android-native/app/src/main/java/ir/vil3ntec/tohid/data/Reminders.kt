package ir.vil3ntec.tohid.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.vil3ntec.tohid.moneyPlain
import ir.vil3ntec.tohid.qtyPlain
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 *  یادآوریِ روزانه.
 *
 *  هشدارهای برنامه — موجودیِ تمام‌شده، قرضِ سررسیده، پشتیبانِ قدیمی — همه
 *  داخلِ خودِ برنامه بودند. یعنی تا کسی بازش نمی‌کرد، خبردار نمی‌شد؛ و
 *  دقیقاً روزهایی که سرش شلوغ است و باز نمی‌کند، همان روزهایی است که
 *  باید بداند.
 *
 *  یک بار در روز، بی‌سروصدا، دفتر خوانده می‌شود و اگر چیزی هست یک اعلان
 *  می‌آید. اگر چیزی نیست، هیچ اعلانی نمی‌آید — اعلانِ «همه‌چیز خوب است»
 *  فقط کاری می‌کند که کاربر اعلان‌ها را خاموش کند.
 */
object Reminders {

  private const val CHANNEL = "tohid-daily"
  private const val WORK = "tohid-daily-check"
  private const val NOTE_ID = 4201

  /** برنامه‌ریزیِ کارِ روزانه. صدا زدنش چند بار بی‌ضرر است. */
  fun schedule(context: Context) {
    val request = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS)
      .setInitialDelay(untilMorning(), TimeUnit.MILLISECONDS)
      .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
      .build()

    runCatching {
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        WORK,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    }
  }

  /** تا نُهِ صبحِ بعدی چقدر مانده — نه نصفِ شب، که کسی نگاه نمی‌کند */
  private fun untilMorning(): Long {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 9)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
    }
    return (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000)
  }

  private fun ensureChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
      CHANNEL,
      "یادآوری دکان",
      NotificationManager.IMPORTANCE_DEFAULT,
    ).apply { description = "موجودی تمام‌شده و قرض‌های سررسیده" }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager?.createNotificationChannel(channel)
  }

  private fun allowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED

  /**
   *  خلاصهٔ آنچه امروز باید بداند — یا `null` اگر چیزی نیست.
   *
   *  جدا نوشته شده تا بشود بدونِ اندروید هم سنجیدش.
   */
  fun summary(s: LedgerSummary): Pair<String, String>? {
    val out = s.outOfStock
    val low = s.lowStock
    val owed = s.owing

    if (out.isEmpty() && low.isEmpty() && owed.isEmpty()) return null

    val title = when {
      out.isNotEmpty() -> "${out.size} کالا تمام شده"
      low.isNotEmpty() -> "${low.size} کالا رو به اتمام است"
      else -> "طلب از مشتریان"
    }

    val lines = buildList {
      out.take(2).forEach { add("${it.name}: تمام شد") }
      low.take(2).forEach { add("${it.name}: ${qtyPlain(it.left)} مانده") }
      if (owed.isNotEmpty()) {
        add("${moneyPlain(owed.sumOf { it.amount })} افغانی طلب از ${owed.size} نفر")
      }
    }
    return title to lines.joinToString(" • ")
  }

  suspend fun check(context: Context) {
    if (!allowed(context)) return
    //  خلاصهٔ کوچک به‌جای کلِ دفتر — شرحش سرِ `LedgerSummary`
    val (title, text) = summary(LedgerSummary.require(context)) ?: return

    ensureChannel(context)

    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pending = open?.let {
      PendingIntent.getActivity(
        context,
        0,
        it.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP },
        PendingIntent.FLAG_IMMUTABLE,
      )
    }

    val note = NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setAutoCancel(true)
      .apply { pending?.let { setContentIntent(it) } }
      .build()

    runCatching { NotificationManagerCompat.from(context).notify(NOTE_ID, note) }
  }

  class DailyWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
      runCatching { check(applicationContext) }
      return Result.success()
    }
  }
}
