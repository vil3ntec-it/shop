package androidx.work

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/*
 *  WorkManager روی کامپیوتر — کارهای دوره‌ای داخلِ همان فرآیند.
 *
 *  برنامهٔ کامپیوتر وقتی پنجره بسته می‌شود در سینیِ سیستم می‌ماند
 *  (`Main.kt`)، پس همگام‌سازیِ پانزده‌دقیقه‌ای، پشتیبانِ دوازده‌ساعته،
 *  دیدبان و یادآوری همان‌طور که روی گوشی می‌دوند این‌جا هم می‌دوند —
 *  همان کلاس‌های `CoroutineWorker`ِ اندروید، دست‌نخورده.
 *
 *  ⚠️ قاعده‌های اندروید نگه داشته شده‌اند: نامِ یکتا، `KEEP` (کارِ زنده
 *  دست نمی‌خورد)، `UPDATE`/`REPLACE` (از نو)، و `retry` با عقب‌نشینیِ
 *  نمایی.
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
  companion object { val NONE = Constraints() }
}

open class WorkRequest internal constructor(
  internal val worker: Class<out CoroutineWorker>,
  internal val initialDelayMs: Long,
  internal val periodMs: Long,
  internal val backoffMs: Long,
)

class PeriodicWorkRequest internal constructor(w: Class<out CoroutineWorker>, d: Long, p: Long, b: Long) : WorkRequest(w, d, p, b)
class OneTimeWorkRequest internal constructor(w: Class<out CoroutineWorker>, d: Long, b: Long) : WorkRequest(w, d, 0, b)

abstract class WorkBuilder<B : WorkBuilder<B, R>, R : WorkRequest>(internal val worker: Class<out CoroutineWorker>) {
  internal var delayMs = 0L
  internal var backoffMs = 30_000L
  @Suppress("UNCHECKED_CAST") private fun self() = this as B
  fun setInitialDelay(d: Long, unit: TimeUnit): B = self().also { delayMs = unit.toMillis(d) }
  fun setConstraints(c: Constraints): B = self()
  fun setBackoffCriteria(p: BackoffPolicy, d: Long, unit: TimeUnit): B = self().also { backoffMs = unit.toMillis(d) }
  fun setExpedited(p: OutOfQuotaPolicy): B = self()
  fun addTag(t: String): B = self()
  abstract fun build(): R
}

class PeriodicBuilder(w: Class<out CoroutineWorker>, private val periodMs: Long) :
  WorkBuilder<PeriodicBuilder, PeriodicWorkRequest>(w) {
  override fun build() = PeriodicWorkRequest(worker, delayMs, periodMs, backoffMs)
}

class OneTimeBuilder(w: Class<out CoroutineWorker>) : WorkBuilder<OneTimeBuilder, OneTimeWorkRequest>(w) {
  override fun build() = OneTimeWorkRequest(worker, delayMs, backoffMs)
}

inline fun <reified W : CoroutineWorker> PeriodicWorkRequestBuilder(interval: Long, unit: TimeUnit) =
  PeriodicBuilder(W::class.java, unit.toMillis(interval))

inline fun <reified W : CoroutineWorker> OneTimeWorkRequestBuilder() = OneTimeBuilder(W::class.java)

class WorkManager private constructor(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val jobs = HashMap<String, Job>()

  fun enqueueUniquePeriodicWork(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest) =
    synchronized(jobs) {
      if (policy == ExistingPeriodicWorkPolicy.KEEP && jobs[name]?.isActive == true) return@synchronized
      jobs.remove(name)?.cancel()
      jobs[name] = scope.launch {
        delay(request.initialDelayMs)
        while (isActive) {
          runOnce(request)
          delay(request.periodMs.coerceAtLeast(60_000))
        }
      }
    }

  fun enqueueUniqueWork(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) =
    synchronized(jobs) {
      if (policy == ExistingWorkPolicy.KEEP && jobs[name]?.isActive == true) return@synchronized
      jobs.remove(name)?.cancel()
      jobs[name] = scope.launch {
        delay(request.initialDelayMs)
        var backoff = request.backoffMs
        repeat(6) {
          if (runOnce(request) !is ListenableWorker.Result.Retry) return@launch
          delay(backoff)
          backoff = (backoff * 2).coerceAtMost(15 * 60_000L)
        }
      }
    }

  fun cancelUniqueWork(name: String) = synchronized(jobs) { jobs.remove(name)?.cancel() }

  private suspend fun runOnce(request: WorkRequest): ListenableWorker.Result = runCatching {
    val worker = request.worker
      .getConstructor(Context::class.java, WorkerParameters::class.java)
      .newInstance(context, WorkerParameters())
    worker.doWork()
  }.getOrElse { e ->
    System.err.println("work ${request.worker.simpleName}: ${e.message}")
    ListenableWorker.Result.retry()
  }

  companion object {
    @Volatile private var instance: WorkManager? = null
    fun getInstance(context: Context): WorkManager =
      instance ?: synchronized(this) { instance ?: WorkManager(context.applicationContext).also { instance = it } }
  }
}
