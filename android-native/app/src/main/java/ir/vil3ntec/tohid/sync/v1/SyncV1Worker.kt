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
import ir.vil3ntec.tohid.core.sync.SyncOutage
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.delay
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
    //  OPS-07: حالتِ آموزشی ⇒ هیچ دورِ همگام‌سازی، هیچ دفتری خوانده یا نوشته نمی‌شود (دورِ بعدی پس از خروج)
    if (!ir.vil3ntec.tohid.data.TrainingMode.networkAllowed()) return Result.success()
    //  درِ ورودِ فرآیند: «سرورِ بعدی» پیش از هر دفتر و درخواستی (`ServerSwitchAndroid`)
    ir.vil3ntec.tohid.platform.ServerSwitchAndroid.applyPending(app)
    if (!Backend.isReady(app)) return Result.success()
    //  حسابِ پیک دفترِ دکان ندارد (DLV-01): سرور ۴۰۳ می‌دهد، پس اصلاً نمی‌پرسیم
    if (ir.vil3ntec.tohid.data.ShopRole.isCourier(app)) return Result.success()
    //  SYN-12: هجومِ پس از وصلِ دوباره — نخستین دورِ خودکار پس از برگشتنِ شبکهٔ خودِ دستگاه با درنگِ کران‌دار (`ReconnectGate`)
    if (!Backend.isOnline(app)) reconnect.offline() else reconnect.beforeRound().let { if (it > 0) delay(it) }
    //  تنظیماتِ دکان (`shop.settings_sync`، پیش‌فرض خاموش): تغییرِ صاحب/مدیر روی همین دستگاه با همین دور می‌رود
    //  (حداکثر هر دقیقه یک بار؛ جدا از دفتر و بی اثر بر نتیجهٔ این دور)
    ir.vil3ntec.tohid.data.ShopSettingsSync.kick(app)

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
      val done = ir.vil3ntec.tohid.sync.v3.SyncV3Runner.of(app).work(store)
      sweepAfterPull(store)
      noteOffline()
      return if (done) Result.success() else Result.retry()
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
    //  SEC-02: زنجیرهٔ هشِ اسنادِ همین سری (پرچمِ خاموشِ shop.doc_chain ⇒ فقط پرسشِ پرچم؛ شکستش بی‌صدا)
    runCatching { ir.vil3ntec.tohid.sync.platform.DocChainSync.cycle(app, store.data.value) }

    //  بایگانیِ کهنهٔ همین حساب (پیش از «یک درِ ورود») باز شده ⇒ اول بازیابی (سرور برنده، ردیف‌های تنها درج)؛
    //  نشد ⇒ هیچ دورِ دیگری: صفِ این کلید شاید DELETE ِ دفترِ دیگری دارد (`SyncV1Engine.recoverArchived`)
    if (engine.recoveryPending()) {
      val ok = runCatching {
        engine.recoverArchived(read = { store.ledgerNow() }, write = { next -> store.applyFromServerNow(next) })
      }.isSuccess
      if (!ok) return Result.retry()
    }

    //  DATA-06: حالِ این حساب از پشتیبانِ گوشیِ دیگر آمد ⇒ پیش از هر هم‌ترازی و Pull یک بار «بازسازی از سرور» (صف اول)؛
    //  نشد ⇒ هیچ چیزی عوض نشده، دورِ بعد دوباره (`V1Replica.rebuildRestored`)
    if (V1Replica.restoredPending(app) && !runCatching { V1Replica.rebuildRestored(app, store) }.getOrDefault(false)) {
      return Result.retry()
    }

    //  یک بار در عمرِ هر حساب روی این دستگاه؛ شکستش (نت) دورِ عادی را نمی‌گیرد
    //  ⛔ `read` دفترِ همین لحظهٔ دیسک است (`ledgerNow`)، نه دفتری که این نمونه هنگامِ `load` خواند:
    //  نشاندنِ opها روی آن فروشی را که صفحه در این فاصله ثبت کرد از دیسک پاک می‌کرد
    runCatching {
      engine.prepare(read = { store.ledgerNow() }, write = { next -> store.applyFromServerNow(next) })
    }

    val outcome = engine.runOnce(
      read = { store.ledgerNow() },
      write = { next -> store.applyFromServerNow(next) },
    )
    sweepAfterPull(store)
    //  STF-09: سرِ زنجیرهٔ سابقهٔ همین دستگاه پس از هر دور، فقط وقتی عوض شده — ردیف‌های هنوز نرفته هم (بریدنشان روی
    //  سرور دیده می‌شود). پرچمِ خاموشِ shop.doc_chain ⇒ هیچ؛ شکستش بی‌صدا و دورِ بعد دوباره (`AuditAnchorSync`)
    runCatching { ir.vil3ntec.tohid.sync.platform.AuditAnchorSync.cycle(app) }
    noteOffline()
    return if (outcome.isSuccess) Result.success() else Result.retry()
  }

  /** SYN-12: دور شبکهٔ خودِ دستگاه را نیافت (حکمِ موتور «آفلاین») ⇒ دورِ خودکارِ پس از برگشتنش پخش می‌شود */
  private fun noteOffline() {
    if (SyncV1Engine.of(applicationContext).status.value.outage == SyncOutage.Kind.OFFLINE) reconnect.offline()
  }

  /**
   *  CRM-10: پس از Pull، ردیف‌هایی که هنوز به مشتریِ ادغام‌شده اشاره می‌کنند به ماندگار
   *  (`ShopStore.sweepMerged`: پشتِ تنظیمِ خاموشِ «گزارش‌های مشتری»، زیرِ قفلِ دفتر). اگر
   *  چیزی نوشت، op ِ آن با همان `SyncV1Worker.now` ِ هر ذخیره در دورِ بعد می‌رود؛ دورِ بعد
   *  دیگر کاری ندارد. شکستش دورِ همگام‌سازی را نمی‌گیرد.
   */
  private suspend fun sweepAfterPull(store: ShopStore) {
    store.sweepMergedQuietly()
    //  سندِ ردشدهٔ همین دستگاه که صاحب «کنار گذاشت»/«اصلاح کرد» ⇒ بیرون از دفترِ شمرده (`data/SetAside`)؛ شکستش دور را نمی‌گیرد
    runCatching { ir.vil3ntec.tohid.data.SetAsideSync.afterSync(applicationContext, store) }
  }

  companion object {
    private const val PERIODIC = "tohid-sync-v1-periodic"
    private const val NOW = "tohid-sync-v1-now"

    /** SYN-12: درِ هجومِ پس از وصلِ دوباره، در حافظهٔ همین فرآیند (`ReconnectGate`)؛ «همگام‌سازی حالا» پیش از [now] `userAsked` می‌زند */
    internal val reconnect = ReconnectGate()

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
      if (!ir.vil3ntec.tohid.data.TrainingMode.networkAllowed()) return
      if (!Backend.isOnline(context)) reconnect.offline()   // SYN-12: تغییرِ بی‌نت ⇒ دورش پس از برگشتنِ شبکه پخش می‌شود
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
