package androidx.work

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/*
 *  shimِ iOS ِ WorkManager — همان نام‌های `desktop/src/shim/kotlin/androidx/work/Work.kt`.
 *  ⛔ گامِ ۲: کارِ دوره‌ای و پس‌زمینه روی آیفون **زمان‌بندی نمی‌شود** (پشتیبانِ خودکار، همگام‌سازیِ دوره‌ای،
 *  یادآور): iOS برنامه را در پس‌زمینه نگه نمی‌دارد و `BGTaskScheduler` گامِ ۳ است (‎docs/IOS-PLAN-fa.md‎ §۵.۳).
 *  پس `enqueue*` فقط درخواست را می‌پذیرد و هیچ اجرا نمی‌کند — نه بی‌صدا «انجام شد».
 */
class WorkerParameters internal constructor()

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

open class WorkRequest internal constructor(internal val worker: KClass<out CoroutineWorker>)
class PeriodicWorkRequest internal constructor(w: KClass<out CoroutineWorker>) : WorkRequest(w)
class OneTimeWorkRequest internal constructor(w: KClass<out CoroutineWorker>) : WorkRequest(w)

abstract class WorkBuilder<B : WorkBuilder<B, R>, R : WorkRequest>(internal val worker: KClass<out CoroutineWorker>) {
  @Suppress("UNCHECKED_CAST")
  private fun self() = this as B
  fun setInitialDelay(d: Long, unit: TimeUnit): B = self()
  fun setConstraints(c: Constraints): B = self()
  fun setBackoffCriteria(p: BackoffPolicy, d: Long, unit: TimeUnit): B = self()
  fun setExpedited(p: OutOfQuotaPolicy): B = self()
  fun addTag(t: String): B = self()
  abstract fun build(): R
}

class PeriodicBuilder(w: KClass<out CoroutineWorker>) : WorkBuilder<PeriodicBuilder, PeriodicWorkRequest>(w) {
  override fun build() = PeriodicWorkRequest(worker)
}

class OneTimeBuilder(w: KClass<out CoroutineWorker>) : WorkBuilder<OneTimeBuilder, OneTimeWorkRequest>(w) {
  override fun build() = OneTimeWorkRequest(worker)
}

inline fun <reified W : CoroutineWorker> PeriodicWorkRequestBuilder(interval: Long, unit: TimeUnit) = PeriodicBuilder(W::class)
inline fun <reified W : CoroutineWorker> PeriodicWorkRequestBuilder(interval: Long, unit: TimeUnit, flex: Long, flexUnit: TimeUnit) = PeriodicBuilder(W::class)
inline fun <reified W : CoroutineWorker> OneTimeWorkRequestBuilder() = OneTimeBuilder(W::class)

class WorkManager private constructor() {
  fun enqueueUniquePeriodicWork(name: String, policy: ExistingPeriodicWorkPolicy, request: PeriodicWorkRequest) {}
  fun enqueueUniqueWork(name: String, policy: ExistingWorkPolicy, request: OneTimeWorkRequest) {}
  fun enqueue(request: WorkRequest) {}
  fun cancelUniqueWork(name: String) {}
  fun cancelAllWorkByTag(tag: String) {}

  companion object {
    private val one = WorkManager()
    fun getInstance(context: Context): WorkManager = one
  }
}
