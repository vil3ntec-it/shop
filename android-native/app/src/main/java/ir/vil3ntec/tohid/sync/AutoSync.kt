package ir.vil3ntec.tohid.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 *  همگام‌سازی خودکار.
 *
 *  تا امروز یک دکمه در تنظیمات بود و بس: «همگام‌سازی حالا». یعنی دو گوشیِ
 *  یک دکان تا وقتی کسی آن دکمه را نزده بود از هم بی‌خبر بودند، و اگر
 *  گوشی گم می‌شد، هرچه از آخرین فشارِ دستیِ آن دکمه گذشته بود، رفته بود.
 *
 *  حالا خودش کار می‌کند:
 *    • یک بار وقتی برنامه باز می‌شود
 *    • بعد از هر تغییر در دفتر، با کمی مکث
 *
 *  آن مکث عمدی است. فروشنده‌ای که ده قلم در سبد می‌گذارد، ده تغییر
 *  می‌سازد؛ بدونِ مکث ده بار پشتِ سرِ هم به سرور وصل می‌شدیم. با مکث،
 *  همه‌ی آن‌ها یک بار فرستاده می‌شوند.
 *
 *  و هیچ‌وقت سرِ راهِ کاربر نمی‌ایستد: بی‌صدا اجرا می‌شود و اگر نشد،
 *  دفعهٔ بعد دوباره امتحان می‌کند. نبودنِ اینترنت خطا نیست.
 */
object AutoSync {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var pending: Job? = null

  /** الان در حال فرستادن است — برای نشان دادن در تنظیمات */
  var running by mutableStateOf(false)
    private set

  /** آخرین باری که واقعاً موفق شد */
  var lastOk by mutableStateOf(0L)
    private set

  /** پیامِ آخرین شکست، اگر بود */
  var lastError by mutableStateOf<String?>(null)
    private set

  /** مکث پس از تغییر — چند تغییرِ پشتِ سرِ هم یک بار فرستاده می‌شوند */
  private const val QUIET_MS = 4_000L

  /**
   *  آماده یعنی هم نشانی هست و هم حساب.
   *
   *  هر دو را نقطهٔ اتصال می‌داند؛ اینجا دیگر نه نشانی خوانده می‌شود نه
   *  توکن.
   */
  private fun ready(context: Context): Boolean = Backend.isReady(context)

  /**
   *  «چیزی عوض شد.»
   *
   *  هر بار که دفتر تغییر می‌کند صدا زده می‌شود. کارِ قبلی — اگر هنوز
   *  منتظر بود — لغو می‌شود و مکث از نو شروع می‌شود.
   */
  fun nudge(context: Context, store: ShopStore) {
    val app = context.applicationContext
    if (!ready(app)) return

    pending?.cancel()
    pending = scope.launch {
      delay(QUIET_MS)
      runOnce(app, store)
    }
  }

  /** همین حالا، بدونِ مکث — برای بازِ شدنِ برنامه */
  fun now(context: Context, store: ShopStore) {
    val app = context.applicationContext
    if (!ready(app)) return
    scope.launch { runOnce(app, store) }
  }

  private suspend fun runOnce(app: Context, store: ShopStore) {
    if (running) return
    if (!ready(app)) return
    if (!Backend.isOnline(app)) return   // نبودنِ اینترنت خطا نیست
    val state = SyncStore(app)

    running = true
    runCatching { Syncer(store, state, app).run() }
      .onSuccess {
        lastOk = System.currentTimeMillis()
        lastError = null
        // مجوزِ اشتراک هم همین‌جا تازه می‌شود؛ عمرش ده روز است و
        // اگر کاربر هیچ‌وقت دستی نزند، بی‌سروصدا تمام می‌شد
        runCatching { Syncer(store, state, app).refreshLicense(android.os.Build.MODEL ?: "گوشی") }
      }
      .onFailure { failure ->
        //  «نت نیست» را به کاربر نشان نمی‌دهیم: خطا نیست و دفعهٔ بعد
        //  خودش می‌رود. بقیه پیامِ فارسیِ خودشان را دارند.
        lastError = when (failure) {
          is ApiFailure.Offline -> null
          is ApiFailure -> failure.userMessage
          else -> failure.message
        }
      }
    running = false
  }
}
