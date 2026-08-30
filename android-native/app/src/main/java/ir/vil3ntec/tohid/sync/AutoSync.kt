package ir.vil3ntec.tohid.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.data.ShopStore
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

  private fun online(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }.getOrDefault(false)

  private fun ready(state: SyncStore): Boolean =
    state.serverUrl.isNotBlank() && !state.accessToken.isNullOrBlank()

  /**
   *  «چیزی عوض شد.»
   *
   *  هر بار که دفتر تغییر می‌کند صدا زده می‌شود. کارِ قبلی — اگر هنوز
   *  منتظر بود — لغو می‌شود و مکث از نو شروع می‌شود.
   */
  fun nudge(context: Context, store: ShopStore) {
    val app = context.applicationContext
    val state = SyncStore(app)
    if (!ready(state)) return

    pending?.cancel()
    pending = scope.launch {
      delay(QUIET_MS)
      runOnce(app, store)
    }
  }

  /** همین حالا، بدونِ مکث — برای بازِ شدنِ برنامه */
  fun now(context: Context, store: ShopStore) {
    val app = context.applicationContext
    val state = SyncStore(app)
    if (!ready(state)) return
    scope.launch { runOnce(app, store) }
  }

  private suspend fun runOnce(app: Context, store: ShopStore) {
    if (running) return
    val state = SyncStore(app)
    if (!ready(state)) return
    if (!online(app)) return          // نبودنِ اینترنت خطا نیست

    running = true
    runCatching { Syncer(store, state, app).run() }
      .onSuccess {
        lastOk = System.currentTimeMillis()
        lastError = null
        // مجوزِ اشتراک هم همین‌جا تازه می‌شود؛ عمرش ده روز است و
        // اگر کاربر هیچ‌وقت دستی نزند، بی‌سروصدا تمام می‌شد
        runCatching { Syncer(store, state, app).refreshLicense(android.os.Build.MODEL ?: "گوشی") }
      }
      .onFailure { lastError = (it as? ServerClient.ServerError)?.message ?: it.message }
    running = false
  }
}
