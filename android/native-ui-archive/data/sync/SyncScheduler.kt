package af.tohid.shop.data.sync

import android.content.Context
import androidx.work.*
import af.tohid.shop.TohidApp
import java.util.concurrent.TimeUnit

/**
 * همگام‌سازی پس‌زمینه.
 *
 * WorkManager خودش صبر می‌کند تا اینترنت وصل شود، پس اگر دکان کل روز
 * بدون نت باشد، به محض رسیدن اینترنت کار انجام می‌شود — حتی اگر برنامه بسته باشد.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TohidApp ?: return Result.success()
        if (!app.session.isLoggedIn() ||
            !af.tohid.shop.data.remote.ApiClient.isConfigured(app.session) ||
            app.session.shopId().isBlank()
        ) {
            return Result.success()
        }
        return try {
            app.sync.sync()
            Result.success()
        } catch (e: Exception) {
            // شکست شبکه: بعداً دوباره تلاش می‌شود؛ داده محلی دست‌نخورده می‌ماند
            if (runAttemptCount < 5) Result.retry() else Result.success()
        }
    }
}

object SyncScheduler {
    private const val PERIODIC = "tohid-sync-periodic"
    private const val ONE_SHOT = "tohid-sync-now"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun syncNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
    }
}
