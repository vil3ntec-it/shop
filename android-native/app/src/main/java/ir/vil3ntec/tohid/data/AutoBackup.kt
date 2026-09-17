package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 *  پشتیبانِ خودکارِ شبانه.
 *
 *  ── چه اشکالی را می‌بندد ────────────────────────────────────────────
 *  پشتیبان فقط یک دکمهٔ دستی در تنظیمات بود. متنِ خودِ برنامه هم همین را
 *  می‌گفت: «هر چند وقت یک‌بار پشتیبان بگیرید» — یعنی مسئولیتِ کارِ چند
 *  سالِ دکان روی حافظهٔ آدمی بود که سرش شلوغ است. و تنها شبکهٔ ایمنیِ
 *  خودکار، `before-restore.json` بود که فقط پیش از بازیابی ساخته می‌شد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── این چه چیزی را نجات می‌دهد و چه چیزی را نه ─────────────────────
 *  این نسخه‌ها **روی خودِ گوشی** می‌مانند. یعنی جلوی این‌ها را می‌گیرد:
 *  پاک شدنِ اشتباهیِ کالا یا فاکتور، بازیابیِ فایلِ غلط، خرابیِ دفتر.
 *
 *  ولی گوشی که گم یا آب شود، این‌ها هم با آن می‌روند. برای آن، همان
 *  پشتیبانِ دستی لازم است که فایلش از گوشی بیرون می‌رود — و حالا
 *  عکس‌ها را هم با خودش می‌برد (`BackupBundle`). برنامه هنوز یادآوری
 *  می‌کند که آن را بگیرید؛ این فقط جای خالیِ روزهایی را پر می‌کند که
 *  کسی یادش نمانده.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  سه نسخه نگه داشته می‌شود، نه یکی. اگر خرابی دو روز بعد دیده شود،
 *  نسخهٔ دیروز هم همان خرابی را دارد.
 */
object AutoBackup {

  private const val WORK = "tohid-auto-backup"
  private const val DIR = "auto-backups"
  private const val PREFIX = "auto-"
  private const val SUFFIX = ".json"

  /** چند نسخه نگه داشته شود */
  const val KEEP = 3

  data class Snapshot(val file: File, val at: Long, val bytes: Long)

  /** برنامه‌ریزیِ کارِ شبانه. صدا زدنش چند بار بی‌ضرر است. */
  fun schedule(context: Context) {
    val request = PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS)
      .setInitialDelay(untilNight(), TimeUnit.MILLISECONDS)
      //  باتریِ کم را دست نمی‌زنیم؛ پشتیبان فردا هم گرفته می‌شود
      .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
      .build()
    runCatching {
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
  }

  /** تا یازدهِ شبِ بعدی چقدر مانده — وقتی دکان بسته است و گوشی بیکار */
  private fun untilNight(): Long {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 23)
      set(Calendar.MINUTE, 0)
      set(Calendar.SECOND, 0)
      set(Calendar.MILLISECOND, 0)
      if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
    }
    return (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000)
  }

  fun dir(context: Context): File =
    File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

  /** نسخه‌های موجود، تازه‌ترین اول */
  fun list(context: Context): List<Snapshot> =
    dir(context).listFiles().orEmpty()
      .filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) }
      .map { Snapshot(it, it.lastModified(), it.length()) }
      .sortedByDescending { it.at }

  /**
   *  گرفتنِ یک نسخه.
   *
   *  عمداً از خودِ فایلِ دفتر کپی می‌شود، نه از سریالایزِ دوباره: هم
   *  ارزان‌تر است، هم دقیقاً همان بایت‌هایی می‌ماند که برنامه می‌خواند.
   *  اگر دفتری نیست، کاری هم نیست.
   */
  fun take(context: Context, today: String): Result<File?> = runCatching {
    val ledger = File(context.filesDir, "shop-data.json")
    if (!ledger.exists() || ledger.length() == 0L) return@runCatching null

    val target = File(dir(context), "$PREFIX$today$SUFFIX")
    val tmp = File(dir(context), "$PREFIX$today$SUFFIX.tmp")
    ledger.copyTo(tmp, overwrite = true)
    tmp.renameTo(target)
    prune(context)
    target
  }

  /** فقط تازه‌ترین‌ها می‌مانند */
  private fun prune(context: Context) {
    list(context).drop(KEEP).forEach { runCatching { it.file.delete() } }
  }

  /**
   *  فرستادنِ تازه‌ترین نسخه به سرور.
   *
   *  ── چرا این‌جا و نه یک کارِ جدا ─────────────────────────────────
   *  نسخهٔ همین حالا ساخته‌شده همان چیزی است که باید برود. کارِ دوم
   *  یعنی یک بیدارباشِ دیگر روی گوشیِ کاربر برای فرستادنِ فایلی که
   *  ممکن است تا آن موقع عوض شده باشد.
   *
   *  ⛔ **هیچ‌وقت استثنا بیرون نمی‌دهد و هیچ‌وقت کارِ شبانه را نمی‌شکند.**
   *  نسخهٔ محلی گرفته شده و سرِ جایش است؛ نرفتنِ ابری یک «دفعهٔ بعد»
   *  است، نه یک خطا. نت نبودن، اشتراک تمام شدن، سرور خواب بودن — هیچ‌کدام
   *  نباید باعث شوند پشتیبانِ **محلی** هم گرفته نشود.
   *
   *  ⚠️ **همان ZIPی می‌رود که کاربر دستی می‌گیرد** (`BackupBundle`)، نه
   *  فقط دفترِ JSON: وگرنه برگرداندنِ پشتیبانِ ابری همهٔ عکس‌ها را
   *  می‌انداخت — همان اشکالی که یک بار در پشتیبانِ دستی بود.
   */
  suspend fun push(context: Context, manual: Boolean = false): Boolean {
    if (!ir.vil3ntec.tohid.data.repo.Backend.isReady(context)) return false
    if (!ir.vil3ntec.tohid.data.repo.Backend.isOnline(context)) return false
    return runCatching {
      /*
       *  ⚠️ **خودِ فایلِ دفتر خوانده می‌شود، نه سریالایزِ دوباره.**
       *
       *  کارِ شبانه در `WorkManager` می‌دود و هیچ `ShopStore`ی در دست
       *  ندارد؛ ساختنِ یکی فقط برای پشتیبان یعنی خواندنِ کلِ دفتر در
       *  حافظه، دو بار. و همین بایت‌ها همان چیزی‌اند که `take()` برای
       *  نسخهٔ محلی کپی می‌کند، پس هر دو نسخه دقیقاً یکی‌اند.
       */
      val ledger = File(context.filesDir, "shop-data.json")
      if (!ledger.exists() || ledger.length() == 0L) return false
      val ledgerJson = ledger.readText(Charsets.UTF_8)

      val bytes = java.io.ByteArrayOutputStream().use { out ->
        BackupBundle.write(context, out, ledgerJson).getOrThrow()
        out.toByteArray()
      }
      if (bytes.isEmpty()) return false
      ir.vil3ntec.tohid.data.repo.Backend.backups(context)
        .upload(
          bytes = bytes,
          manual = manual,
          appVersion = ir.vil3ntec.tohid.BuildConfig.VERSION_NAME,
          ext = "zip",
        )
        .isSuccess
    }.getOrDefault(false)
  }

  class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
      runCatching { take(applicationContext, ir.vil3ntec.tohid.todayIso()) }
      //  ⚠️ بعد از نسخهٔ محلی، نه به‌جای آن. اگر ترتیب برعکس بود، یک
      //  خطای شبکه می‌توانست شبی را بی هیچ پشتیبانی بگذارد.
      runCatching { push(applicationContext) }
      return Result.success()
    }
  }
}
