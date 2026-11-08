package ir.vil3ntec.tohid.ios

import android.content.Intent
import android.content.IosContext
import ir.vil3ntec.tohid.AppStart
import androidx.work.WorkManager
import ir.vil3ntec.tohid.data.AutoBackup
import ir.vil3ntec.tohid.data.Reminders
import ir.vil3ntec.tohid.data.ShopChatSync
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.Watchman
import ir.vil3ntec.tohid.ios.identity.IosDeviceIdentity
import ir.vil3ntec.tohid.sync.v1.LiveSocket
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationProtectedDataDidBecomeAvailable
import platform.UIKit.UIApplicationWillResignActiveNotification

/*
 *  کارهای «برنامه» ِ آیفون که در اندروید کارِ `MainActivity`، مانیفست و WorkManager ِ سیستم است — همه با
 *  همان کدِ مشترک؛ این فایل فقط آن‌ها را به رخدادهای iOS وصل می‌کند.
 */

/** همان `LocalContext` ِ اندروید که Activity است (`context as? FragmentActivity` ⇐ Face ID در قفلِ برنامه) */
object IosActivity : androidx.fragment.app.FragmentActivity()

/**
 *  کارگرهای WorkManager ِ اندروید (همان کلاس‌ها، دست‌نخورده) — Kotlin/Native بازتاب ندارد، پس سازنده‌ها این‌جا
 *  یک بار ثبت می‌شوند (‎androidx/work/Work.kt‎). همان چهار کاری که کامپیوتر هم داخلِ فرآیند می‌دواند.
 */
object IosWork {
  private var done = false

  fun install() {
    if (done) return
    done = true
    WorkManager.register(SyncV1Worker::class) { c, p -> SyncV1Worker(c, p) }
    WorkManager.register(Watchman.Worker::class) { c, p -> Watchman.Worker(c, p) }
    WorkManager.register(AutoBackup.Worker::class) { c, p -> AutoBackup.Worker(c, p) }
    WorkManager.register(AutoBackup.LocalWorker::class) { c, p -> AutoBackup.LocalWorker(c, p) }
    WorkManager.register(Reminders.DailyWorker::class) { c, p -> Reminders.DailyWorker(c, p) }
  }
}

/**
 *  همگام‌سازی و سوکتِ زنده — همان `AppStart.startSync` ِ `MainActivity.onCreate` (و `desktop/…/Main.kt`): کارِ
 *  دوره‌ایِ پانزده‌دقیقه‌ای (`KEEP`) و سوکتِ «چیزی عوض شد» که Pull را به همان کارگر می‌سپارد. حالتِ پیک ⇐ هیچ.
 */
object IosSync {
  private var live: LiveSocket? = null

  fun start() {
    live = AppStart.startSync(IosContext)
  }

  /**
   *  بازگشت از پس‌زمینه. اندروید در پس‌زمینه همگام می‌کند و سوکتش زنده می‌ماند؛ iOS برنامه را نگه نمی‌دارد،
   *  پس همان کاری که آن دو می‌کردند همین لحظه: یک دورِ همگام‌سازی و وصلِ دوبارهٔ سوکت (اگر افتاده باشد).
   */
  fun resumed() {
    if (ShopRole.courierMode.value) return
    runCatching { SyncV1Worker.now(IosContext) }
    runCatching { live?.connect() }
  }
}

/** پیوندِ `vill3n://app/…` و لمسِ اعلان — همان `AppStart` ِ `MainActivity` (پس از بالا آمدنِ دفتر) */
object IosLinks {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  fun open(url: String) {
    IosBoot.ready()
    AppStart.openLink(IosContext, url)
  }

  fun openNotice(intent: Intent) {
    IosBoot.ready()
    AppStart.openNotice(IosContext, intent, scope)
  }
}

/**
 *  `BGAppRefreshTask`: iOS هر از گاهی (کمینهٔ ۱۵ دقیقه، به صلاحدیدِ خودش) برنامه را چند ثانیه در پس‌زمینه بیدار
 *  می‌کند ⇐ همان کارگرِ همگام‌سازی یک دور (`WorkManager.runDue`). شناسه در ‎Info.plist‎
 *  (`BGTaskSchedulerPermittedIdentifiers`). کارِ نیمه‌تمام با پایانِ مهلت لغو می‌شود — مثلِ توقفِ کار در اندروید.
 */
object IosBackground {
  const val REFRESH = "ir.vil3ntec.tohid.refresh"
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  fun register() {
    runCatching {
      BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(REFRESH, usingQueue = null) { task ->
        (task as? BGAppRefreshTask)?.let(::run)
      }
    }
  }

  fun schedule() {
    runCatching {
      val request = BGAppRefreshTaskRequest(identifier = REFRESH)
      request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(15 * 60.0)
      BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)
    }
  }

  private fun run(task: BGTask) {
    schedule()
    IosWork.install()
    val job = scope.launch {
      val ok = runCatching {
        if (!ShopRole.isCourier(IosContext)) SyncV1Worker.schedule(IosContext)
        WorkManager.getInstance(IosContext).runDue()
      }.isSuccess
      task.setTaskCompletedWithSuccess(ok)
    }
    task.expirationHandler = {
      job.cancel()
      task.setTaskCompletedWithSuccess(false)
    }
  }
}

/** رخدادهای چرخهٔ برنامه — همان `onResume`/`onPause` ِ `MainActivity` (چتِ دکان: «خوانده شد» فقط جلوی چشم) */
object IosLifecycle {
  private var wasBackground = false
  private val observers = ArrayList<Any>()

  fun install() {
    val center = NSNotificationCenter.defaultCenter
    val main = NSOperationQueue.mainQueue
    observers += center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, main) { _ ->
      //  هویتِ دستگاه اگر هنگامِ آغاز نسنجیده ماند (دستگاه قفل بود) — پیش از همگام‌سازیِ بازگشت
      IosDeviceIdentity.check()
      ShopChatSync.foreground = true
      IosNotifications.refresh()
      if (wasBackground) {
        wasBackground = false
        if (IosBoot.started) IosSync.resumed()
      }
      //  توکنِ APNs پس از ورود (روزی یک بار برای هر حساب؛ ‎IosPush.kt‎)
      if (IosBoot.started) IosPush.upload()
    }
    //  نخستین بازگشایی پس از روشن شدن: کلیددان و NSUserDefaults تازه خوانا شده‌اند
    observers += center.addObserverForName(UIApplicationProtectedDataDidBecomeAvailable, null, main) { _ ->
      IosDeviceIdentity.check()
    }
    observers += center.addObserverForName(UIApplicationWillResignActiveNotification, null, main) { _ ->
      ShopChatSync.foreground = false
    }
    observers += center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, main) { _ ->
      wasBackground = true
      IosBackground.schedule()
    }
  }
}
