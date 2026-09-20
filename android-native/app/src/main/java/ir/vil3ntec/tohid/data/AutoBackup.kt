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

  /**
   *  چند نسخه روی خودِ گوشی نگه داشته شود.
   *
   *  ⚠️ با دو نسخه در روز، شش تا یعنی همان **سه روز** پوششی که نسخهٔ
   *  شبانه داشت. کم گذاشتنش یعنی «خرابی دو روز بعد دیده شود و نسخهٔ
   *  سالمی نمانده باشد» — همان چیزی که این کار برایش هست.
   */
  const val KEEP = 6

  data class Snapshot(val file: File, val at: Long, val bytes: Long)

  /** هر دوازده ساعت یک نسخه — خواستهٔ صریحِ صاحب سامانه */
  val EVERY_HOURS = 12L

  /**
   *  برنامه‌ریزیِ کارِ خودکار. صدا زدنش چند بار بی‌ضرر است.
   *
   *  ⚠️ **`UPDATE` است نه `KEEP`.**
   *
   *  با `KEEP`، گوشی‌ای که نسخهٔ قبلیِ برنامه را داشته کارِ **شبانهٔ**
   *  ثبت‌شده‌اش را تا ابد نگه می‌داشت و این دوره‌ی تازه هیچ‌وقت روی آن
   *  نمی‌نشست — یعنی همهٔ مشتری‌های امروز بی‌صدا روی یک‌بار‌در‌روز
   *  می‌ماندند و هیچ‌کس نمی‌فهمید.
   */
  fun schedule(context: Context) {
    val request = PeriodicWorkRequestBuilder<Worker>(EVERY_HOURS, TimeUnit.HOURS)
      .setInitialDelay(untilNextSlot(), TimeUnit.MILLISECONDS)
      //  باتریِ کم را دست نمی‌زنیم؛ پشتیبانِ بعدی دوازده ساعت دیگر است
      .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
      .build()
    runCatching {
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
  }

  /**
   *  تا نوبتِ بعدی چقدر مانده — یازدهِ شب یا یازدهِ صبح.
   *
   *  ⚠️ ساعتِ ثابت، نه «دوازده ساعت از حالا»: دو نسخه‌ای که هر دو ظهر
   *  گرفته شوند یعنی شبی که هیچ نسخه‌ای ندارد. با ساعتِ ثابت، هر گوشی
   *  یکی برای پایانِ روزِ کاری دارد و یکی برای میانِ روز.
   */
  internal fun untilNextSlot(nowMs: Long = System.currentTimeMillis()): Long {
    var best = Long.MAX_VALUE
    for (hour in intArrayOf(11, 23)) {
      val target = Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMs) add(Calendar.DAY_OF_MONTH, 1)
      }
      val gap = target.timeInMillis - nowMs
      if (gap in 1 until best) best = gap
    }
    return best.coerceAtLeast(60_000)
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

    /*
     *  ⚠️ نامِ فایل **نوبت** را هم دارد.
     *
     *  با دو نسخه در روز و نامی که فقط تاریخ داشت، نسخهٔ دوم روی اولی
     *  می‌نشست و عملاً همان یکی‌در‌روزِ قبلی می‌ماند — فقط با دو برابر
     *  کار. «ب» برای پیش از ظهر، «ش» برای بعدش.
     */
    val slot = if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 12) "b" else "sh"
    val target = File(dir(context), "$PREFIX$today-$slot$SUFFIX")
    val tmp = File(dir(context), "$PREFIX$today-$slot$SUFFIX.tmp")
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
