package androidx.work

import android.content.Context
import ir.vil3ntec.tohid.ios.jvm.System
import ir.vil3ntec.tohid.ios.work.WorkTiming
import ir.vil3ntec.tohid.ios.jvm.synchronized
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 *  shimِ iOS ِ WorkManager — همان کارِ `desktop/src/shim/kotlin/androidx/work/Work.kt`: کارهای دوره‌ای و
 *  یک‌باره **داخلِ همان فرآیند**، با همان کلاس‌های `CoroutineWorker` ِ اندروید، دست‌نخورده (همگام‌سازی
 *  `SyncV1Worker`، پشتیبانِ خودکار، دیدبان، یادآور).
 *
 *  قاعده‌های اندروید نگه داشته شده‌اند: نامِ یکتا، `KEEP` (کارِ زنده دست نمی‌خورد)، `UPDATE` (زمان‌بندی همان)،
 *  `REPLACE` (از نو)، `retry` با عقب‌نشینیِ نمایی، مکثِ آغازین. دو تفاوت با کامپیوتر، هر دو برای iOS:
 *   ۱) Kotlin/Native بازتاب ندارد ⇐ هر کلاسِ کارگر یک بار با سازنده‌اش ثبت می‌شود (`register`، در
 *      `ir.vil3ntec.tohid.ios.IosWork`). کارگرِ ثبت‌نشده اجرا نمی‌شود و نامش در گزارش می‌آید — نه «انجام شد» ِ بی‌صدا.
 *   ۲) iOS برنامه را در پس‌زمینه نگه نمی‌دارد: زمانِ آخرین اجرای هر کارِ دوره‌ای در NSUserDefaults می‌ماند
 *      (مثلِ پایگاهِ خودِ WorkManager)، پس باز کردنِ دوبارهٔ برنامه کارِ نارسیده را زود اجرا نمی‌کند و کارِ
 *      سررسیده را همان دم؛ `runDue` همین را برای `BGAppRefreshTask` (‎ios/IosBackground.kt‎) می‌کند.
 *  ⛔ قیدِ «نت هست» (`NetworkType.CONNECTED`) مثلِ کامپیوتر سنجیده نمی‌شود: کارِ بی‌نت `retry` می‌دهد.
 */
class WorkerParameters internal constructor(val tags: Set<String> = emptySet())

abstract class ListenableWorker(appContext: Context, @Suppress("UNUSED_PARAMETER") params: WorkerParameters) {
  val applicationContext: Context = appContext.applicationContext

  abstract class Result internal constructor() {
    internal class Success : Result()
    internal class Retry : Result()
    internal class Failure : Result()

    companion object {
      fun success(): Result = Success()
      fun retry(): Result = Retry()
      fun failure(): Result = Failure()
    }
  }
}

abstract class CoroutineWorker(appContext: Context, params: WorkerParameters) : ListenableWorker(appContext, params) {
  abstract suspend fun doWork(): Result
}

enum class NetworkType { NOT_REQUIRED, CONNECTED, UNMETERED }
enum class BackoffPolicy { EXPONENTIAL, LINEAR }
enum class ExistingPeriodicWorkPolicy { KEEP, REPLACE, UPDATE, CANCEL_AND_REENQUEUE }
enum class ExistingWorkPolicy { KEEP, REPLACE, APPEND, APPEND_OR_REPLACE }
enum class OutOfQuotaPolicy { RUN_AS_NON_EXPEDITED_WORK_REQUEST, DROP_WORK_REQUEST }

class Constraints private constructor() {
  class Builder {
    fun setRequiredNetworkType(t: NetworkType) = this
    fun setRequiresBatteryNotLow(b: Boolean) = this
    fun setRequiresCharging(b: Boolean) = this
    fun setRequiresStorageNotLow(b: Boolean) = this
    fun build() = Constraints()
  }

  companion object {
    val NONE = Constraints()
  }
}

open class WorkRequest internal constructor(
  internal val worker: KClass<out CoroutineWorker>,
  internal val initialDelayMs: Long,
  internal val periodMs: Long,
  internal val backoffMs: Long,
  internal val linear: Boolean,
  internal val tags: Set<String>,
)

class PeriodicWorkRequest internal constructor(w: KClass<out CoroutineWorker>, d: Long, p: Long, b: Long, l: Boolean, t: Set<String>) :
  WorkRequest(w, d, p, b, l, t)

class OneTimeWorkRequest internal constructor(w: KClass<out CoroutineWorker>, d: Long, b: Long, l: Boolean, t: Set<String>) :
  WorkRequest(w, d, 0, b, l, t)

abstract class WorkBuilder<B : WorkBuilder<B, R>, R : WorkRequest>(internal val worker: KClass<out CoroutineWorker>) {
  internal var delayMs = 0L
  //  همان پیش‌فرضِ WorkManager: نمایی، از ۳۰ ثانیه
  internal var backoffMs = 30_000L
  internal var linear = false
  internal val tags = LinkedHashSet<String>()

  @Suppress("UNCHECKED_CAST")
  private fun self() = this as B
  fun setInitialDelay(d: Long, unit: TimeUnit): B = self().also { delayMs = unit.toMillis(d) }
  fun setConstraints(c: Constraints): B = self()
  fun setBackoffCriteria(p: BackoffPolicy, d: Long, unit: TimeUnit): B = self().also {
    backoffMs = unit.toMillis(d).coerceAtLeast(WorkTiming.MIN_BACKOFF_MS)
    linear = p == BackoffPolicy.LINEAR
  }
  fun setExpedited(p: OutOfQuotaPolicy): B = self()
  fun addTag(t: String): B = self().also { tags += t }
  abstract fun build(): R
}

class PeriodicBuilder(w: KClass<out CoroutineWorker>, private val periodMs: Long) : WorkBuilder<PeriodicBuilder, PeriodicWorkRequest>(w) {
  override fun build() = PeriodicWorkRequest(worker, delayMs, periodMs, backoffMs, linear, tags.toSet())
}

class OneTimeBuilder(w: KClass<out CoroutineWorker>) : WorkBuilder<OneTimeBuilder, OneTimeWorkRequest>(w) {
  override fun build() = OneTimeWorkRequest(worker, delayMs, backoffMs, linear, tags.toSet())
}

inline fun <reified W : CoroutineWorker> PeriodicWorkRequestBuilder(interval: Long, unit: TimeUnit) =
  PeriodicBuilder(W::class, unit.toMillis(interval))

@Suppress("UNUSED_PARAMETER")
inline fun <reified W : CoroutineWorker> PeriodicWorkRequestBuilder(interval: Long, unit: TimeUnit, flex: Long, flexUnit: TimeUnit) =
  PeriodicBuilder(W::class, unit.toMillis(interval))

inline fun <reified W : CoroutineWorker> OneTimeWorkRequestBuilder() = OneTimeBuilder(W::class)

class WorkManager private constructor(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val jobs = HashMap<String, Job>()
  private val periodic = HashMap<String, PeriodicWorkRequest>()
  private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

  fun enqueueUniquePeriodicWork(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest) {
    synchronized(jobs) {
      if (policy == ExistingPeriodicWorkPolicy.KEEP && jobs[name]?.isActive == true) return
      jobs.remove(name)?.cancel()
      //  REPLACE/CANCEL_AND_REENQUEUE ⇐ کارِ تازه، همان دم (اندروید هم)؛ KEEP/UPDATE ⇐ زمان‌بندیِ پیشین می‌ماند
      if (policy == ExistingPeriodicWorkPolicy.REPLACE || policy == ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE) {
        prefs.edit().remove(name).apply()
      }
      periodic[name] = request
      jobs[name] = loop(name, request, firstDelay = request.initialDelayMs)
    }
  }

  /** حلقهٔ کارِ دوره‌ای: نخستین اجرا پس از `firstDelay` یا سررسید (هر کدام دیرتر)، سپس هر بازه */
  private fun loop(name: String, request: PeriodicWorkRequest, firstDelay: Long): Job = scope.launch {
    val period = WorkTiming.period(request.periodMs)
    delay(maxOf(firstDelay, dueIn(name, period)))
    while (isActive) {
      runPeriodic(name, request)
      delay(period)
    }
  }

  fun enqueueUniqueWork(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {
    synchronized(jobs) {
      if (policy == ExistingWorkPolicy.KEEP && jobs[name]?.isActive == true) return
      jobs.remove(name)?.cancel()
      jobs[name] = scope.launch { runWithRetry(request) }
    }
  }

  fun enqueue(request: WorkRequest) {
    scope.launch { runWithRetry(request) }
  }

  fun cancelUniqueWork(name: String) {
    synchronized(jobs) {
      jobs.remove(name)?.cancel()
      periodic.remove(name)
    }
  }

  fun cancelAllWorkByTag(tag: String) {
    synchronized(jobs) {
      periodic.filterValues { tag in it.tags }.keys.toList().forEach { name ->
        jobs.remove(name)?.cancel()
        periodic.remove(name)
      }
    }
  }

  /**
   *  `BGAppRefreshTask` ِ iOS: هر کارِ دوره‌ایِ ثبت‌شده‌ای که سررسیده یک بار، پشتِ سرِ هم (همان کلاسِ
   *  اندروید). زمانِ اجرا که iOS داده تمام شود، فراخواننده این coroutine را لغو می‌کند.
   *  @return شمارِ کارهای اجراشده
   */
  suspend fun runDue(): Int {
    val due = synchronized(jobs) { periodic.toList() }
      .filter { (name, r) -> dueIn(name, WorkTiming.period(r.periodMs)) == 0L }
    due.forEach { (name, r) ->
      //  حلقهٔ همان کار کنار می‌رود تا دو اجرای هم‌زمان نباشد؛ پس از اجرا با سررسیدِ تازه برمی‌گردد
      synchronized(jobs) { if (periodic[name] === r) jobs.remove(name)?.cancel() }
      runPeriodic(name, r)
      synchronized(jobs) { if (periodic[name] === r && jobs[name]?.isActive != true) jobs[name] = loop(name, r, firstDelay = 0L) }
    }
    return due.size
  }

  /** چند میلی‌ثانیه تا سررسیدِ کارِ دوره‌ای (۰ = همین حالا) */
  private fun dueIn(name: String, period: Long): Long = WorkTiming.dueIn(prefs.getLong(name, 0L), period, System.currentTimeMillis())

  private suspend fun runPeriodic(name: String, request: PeriodicWorkRequest) {
    val result = runOnce(request)
    if (result !is ListenableWorker.Result.Retry) {
      prefs.edit().putLong(name, System.currentTimeMillis()).apply()
    }
  }

  private suspend fun runWithRetry(request: WorkRequest) {
    delay(request.initialDelayMs)
    var backoff = request.backoffMs
    repeat(MAX_TRIES) {
      if (runOnce(request) !is ListenableWorker.Result.Retry) return
      delay(backoff)
      backoff = WorkTiming.nextBackoff(backoff, request.backoffMs, request.linear)
    }
  }

  private suspend fun runOnce(request: WorkRequest): ListenableWorker.Result {
    val make = synchronized(factories) { factories[request.worker] }
    if (make == null) {
      println("work ${request.worker.simpleName}: کارگرِ ثبت‌نشده در iOS (IosWork) — اجرا نشد")
      return ListenableWorker.Result.failure()
    }
    return try {
      make(context, WorkerParameters(request.tags)).doWork()
    } catch (e: kotlinx.coroutines.CancellationException) {
      throw e
    } catch (e: Throwable) {
      println("work ${request.worker.simpleName}: ${e.message}")
      ListenableWorker.Result.retry()
    }
  }

  companion object {
    private const val PREFS = "tohid-work"
    private const val MAX_TRIES = 6

    private val factories = HashMap<KClass<out CoroutineWorker>, (Context, WorkerParameters) -> CoroutineWorker>()
    private var instance: WorkManager? = null

    fun getInstance(context: Context): WorkManager =
      synchronized(this) { instance ?: WorkManager(context.applicationContext).also { instance = it } }

    /** سازندهٔ کلاسِ کارگر (جای بازتابِ اندروید/JVM) — یک بار، پیش از نخستین `enqueue` */
    fun <W : CoroutineWorker> register(worker: KClass<W>, make: (Context, WorkerParameters) -> W) {
      synchronized(factories) { factories[worker] = make }
    }
  }
}
