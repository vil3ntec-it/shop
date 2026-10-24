package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import java.util.concurrent.TimeUnit

/**
 *  همگام‌سازی در پس‌زمینه — بندِ ۲۱٫۳.
 *
 *      دوره‌ای ۱۵ دقیقه  +  یک‌بارهٔ فوری پس از هر تغییر (با مکثِ ۵۰۰ms)
 *
 *  ── چرا WorkManager و نه یک حلقهٔ خودمان ─────────────────────────
 *  اندروید برنامهٔ بسته را نمی‌گذارد حلقه بزند. `WorkManager` تنها راهی
 *  است که Doze و «ذخیرهٔ باتری» را می‌فهمد و کار را وقتی شبکه هست
 *  اجرا می‌کند، نه وقتی ما می‌خواهیم.
 *
 *  ⚠️ **`setExpedited` فقط برای دستهٔ کوچکِ پس از یک تغییر** است. کارِ
 *  فوری سهمیهٔ محدودی دارد؛ ریختنِ همه‌چیز در آن یعنی اندروید کم‌کم
 *  همه‌شان را عقب می‌اندازد. کارِ سنگین (Snapshot، صفِ سه‌روزه) با کارِ
 *  دوره‌ای می‌رود.
 *
 *  ⚠️ `NetworkType.CONNECTED` یعنی بی نت اصلاً اجرا نمی‌شود — نه اینکه
 *  اجرا شود و خطا بدهد. خطای بی‌فایده باتری می‌خورد.
 */
class SyncV1Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val app = applicationContext
    if (!Backend.isReady(app)) return Result.success()
    //  حسابِ پیک دفترِ دکان ندارد (DLV-01): سرور ۴۰۳ می‌دهد، پس اصلاً نمی‌پرسیم
    if (ir.vil3ntec.tohid.data.ShopRole.isCourier(app)) return Result.success()

    val store = ShopStore(app)
    store.load()
    /*
     *  SEC-01: دفترِ رمزدارِ خوانده‌نشده ⇒ نه Pull، نه تفاضل: دفترِ حافظه خالی است و نشاندنِ
     *  opهای سرور روی آن یعنی رونویسیِ دفترِ واقعی. ولی صفِ opها رمزدار نیست — **فرستاده
     *  می‌شود** (بازبینیِ #331)، تا تغییرهای نرفته گم نشوند و «بازسازی از سرور» و خروج از حساب
     *  راه داشته باشند. با دفترِ ساده (پیش‌فرض) هرگز پیش نمی‌آید.
     */
    if (store.unreadable.value != null) {
      if (ir.vil3ntec.tohid.sync.v3.SyncChoice.isV3(app)) return Result.success()
      val engine = SyncV1Engine.of(app)
      engine.rebind()
      return if (engine.pushPending().isSuccess) Result.success() else Result.retry()
    }
    //  Sync v3 پشتِ تنظیمِ هر دستگاه (پیش‌فرض v1): همین کارگر، موتورِ دیگر
    if (ir.vil3ntec.tohid.sync.v3.SyncChoice.isV3(app)) {
      return if (ir.vil3ntec.tohid.sync.v3.SyncV3Runner.of(app).work(store)) Result.success() else Result.retry()
    }
    val engine = SyncV1Engine.of(app)
    engine.rebind()

    /*
     *  تپشِ هر پانزده دقیقه (بندِ ۲۰.۷) — همین‌جا، نه یک کارِ دومِ جدا.
     *  اشتراک، روزهای مانده و قابلیت‌ها کش می‌شوند و «اشتراکِ من» و
     *  قفلِ نرم از همان می‌خوانند.
     */
    runCatching {
      Backend.codeLogin(app).heartbeat().onSuccess { body ->
        HeartbeatCache(app).write(body)
        SoftLock.refresh(app)
      }
    }

    //  F0-03: «سری سند»ِ این دستگاه — یک بار، هنگامِ پیوستن؛ شکستش بی‌صداست
    runCatching { ir.vil3ntec.tohid.sync.DocSeries.ensure(app) }

    //  یک بار در عمرِ هر حساب روی این دستگاه؛ شکستش (نت) دورِ عادی را نمی‌گیرد
    runCatching {
      engine.prepare(read = { store.data.value }, write = { next -> store.applyFromServerNow(next) })
    }

    val outcome = engine.runOnce(
      read = { store.data.value },
      write = { next -> store.applyFromServerNow(next) },
    )
    return if (outcome.isSuccess) Result.success() else Result.retry()
  }

  companion object {
    private const val PERIODIC = "tohid-sync-v1-periodic"
    private const val NOW = "tohid-sync-v1-now"

    private val net = Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()

    /** کارِ دوره‌ایِ پانزده‌دقیقه‌ای — کمینهٔ خودِ اندروید. */
    fun schedule(context: Context) {
      val work = PeriodicWorkRequestBuilder<SyncV1Worker>(15, TimeUnit.MINUTES)
        .setConstraints(net)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
        .build()
      WorkManager.getInstance(context.applicationContext)
        .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, work)
    }

    /**
     *  «همین حالا» — پس از یک تغییر.
     *
     *  ⚠️ مکثِ نیم‌ثانیه‌ای همان Debounceِ بندِ ۲۰.۲ است: کسی که ده
     *  ردیفِ پشتِ سرِ هم می‌زند، یک درخواست می‌فرستد نه ده تا.
     *  `REPLACE` یعنی هر تغییرِ تازه مکث را از نو می‌شمارد.
     */
    fun now(context: Context) {
      val work = OneTimeWorkRequestBuilder<SyncV1Worker>()
        .setConstraints(net)
        .setInitialDelay(500, TimeUnit.MILLISECONDS)
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .build()
      WorkManager.getInstance(context.applicationContext)
        .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, work)
    }

    fun cancel(context: Context) {
      val wm = WorkManager.getInstance(context.applicationContext)
      wm.cancelUniqueWork(PERIODIC)
      wm.cancelUniqueWork(NOW)
    }
  }
}
