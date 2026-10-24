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
 *  پشتیبانِ خودکار — نسخهٔ محلیِ نیم‌ساعته و پایانِ روز، و ابریِ دوازده‌ساعته.
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
 *  ── چند بار و چند تا (OPS-02) ──────────────────────────────────────
 *  • نسخهٔ محلی **هر نیم ساعت، فقط اگر دفتر عوض شده** (`LOCAL_EVERY_MINUTES`،
 *    کارِ جدای `tohid-auto-backup-local`)، به‌علاوهٔ نوبت‌های ۱۱ و ۲۳ (پایانِ
 *    روز) و بستنِ برنامهٔ کامپیوتر. فایلِ تکراری ساخته نمی‌شود.
 *  • نگه‌داری با `BackupRetention`: ۶ تای آخر + تازه‌ترینِ ۷ روزِ اخیر +
 *    تازه‌ترینِ ۴ هفتهٔ اخیر. به‌جای «۶ تای آخر»ِ پیشین آمده و هیچ نسخه‌ای
 *    را که آن قاعده نگه می‌داشت زودتر پاک نمی‌کند — فقط بیشتر می‌ماند.
 *    اگر خرابی دو روز یا دو هفته بعد دیده شود، نسخهٔ سالم هنوز هست.
 *  • پشتیبانِ ابری همان هر دوازده ساعت است و تغییری نکرده.
 *  ──────────────────────────────────────────────────────────────────
 */
object AutoBackup {

  private const val WORK = "tohid-auto-backup"
  private const val LOCAL_WORK = "tohid-auto-backup-local"
  private const val DIR = "auto-backups"

  /** نسخهٔ محلی هر چند دقیقه (فقط اگر دفتر عوض شده) — OPS-02: «هر ۱۵ تا ۳۰ دقیقه» */
  const val LOCAL_EVERY_MINUTES = 30L

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
    //  نسخهٔ محلیِ نیم‌ساعته: بی شرطِ باتری — فقط مقایسه و رونوشتِ یک فایل
    //  است، بی شبکه، و اگر دفتر عوض نشده هیچ نمی‌نویسد
    val local = PeriodicWorkRequestBuilder<LocalWorker>(LOCAL_EVERY_MINUTES, TimeUnit.MINUTES)
      .setInitialDelay(LOCAL_EVERY_MINUTES, TimeUnit.MINUTES)
      .build()
    runCatching {
      WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(LOCAL_WORK, ExistingPeriodicWorkPolicy.UPDATE, local)
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
    LocalSnapshots.list(dir(context)).map { Snapshot(it.file, it.at, it.bytes) }

  /**
   *  گرفتنِ یک نسخه — **فقط اگر دفتر با تازه‌ترین نسخه فرق دارد**؛ بعد هرس.
   *
   *  عمداً از خودِ فایلِ دفتر کپی می‌شود، نه از سریالایزِ دوباره: هم
   *  ارزان‌تر است، هم دقیقاً همان بایت‌هایی می‌ماند که برنامه می‌خواند.
   *  اگر دفتری نیست یا عوض نشده، null. خواندنِ دفتر جایگزین شدنش را نمی‌بندد
   *  (`LocalSnapshots.openShared`)، پس ثبتِ فروش هیچ‌وقت منتظرِ این نمی‌ماند.
   *
   *  ⚠️ نامِ فایل ساعت و ثانیه دارد (`auto-2026-10-17-143000.json`): با
   *  نسخهٔ نیم‌ساعته، نامِ «تاریخ + نوبتِ صبح/عصر»ِ پیشین نسخهٔ قبلیِ همان
   *  نوبت را رونویسی می‌کرد. نام‌های قدیمی (`…-b`/`…-sh`) هم همچنان نسخه‌اند.
   */
  fun take(context: Context, today: String): Result<File?> = runCatching {
    val ledger = File(context.filesDir, "shop-data.json")
    val time = java.text.SimpleDateFormat("HHmmss", java.util.Locale.US).format(java.util.Date())
    val made = LocalSnapshots.takeIfChanged(ledger, dir(context), "${LocalSnapshots.PREFIX}$today-$time${LocalSnapshots.SUFFIX}")
    prune(context)
    made
  }

  /** «۶ آخر + ۷ روزانه + ۴ هفتگی» به وقتِ همین دستگاه (`BackupRetention`) */
  private fun prune(context: Context) {
    LocalSnapshots.prune(dir(context), { at -> java.util.TimeZone.getDefault().getOffset(at) / 60_000 })
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
      //  SEC-01: دفترِ رمزدار باز می‌شود — پشتیبانِ بیرون‌رفته با کلیدِ دستگاه
      //  رمز نمی‌شود (بند ۱۰٫۳)؛ دفترِ خوانده‌نشده هم هیچ‌وقت «پشتیبان» نمی‌شود
      val ledgerJson = AtRest.readText(context, ledger)

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

  /** نسخهٔ محلیِ نیم‌ساعته — بی ابر، بی پوشهٔ قرینه؛ اگر دفتر عوض نشده، هیچ */
  class LocalWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
      runCatching { take(applicationContext, ir.vil3ntec.tohid.todayIso()) }
      return Result.success()
    }
  }

  class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
      runCatching { take(applicationContext, ir.vil3ntec.tohid.todayIso()) }
      //  نسخهٔ کامل (با عکس‌ها) در پوشه‌ای که صاحبِ دکان می‌بیند
      if (BackupMirror.enabled(applicationContext)) runCatching { BackupMirror.write(applicationContext) }
      //  ⚠️ بعد از نسخهٔ محلی، نه به‌جای آن. اگر ترتیب برعکس بود، یک
      //  خطای شبکه می‌توانست شبی را بی هیچ پشتیبانی بگذارد.
      runCatching { push(applicationContext) }
      return Result.success()
    }
  }
}
