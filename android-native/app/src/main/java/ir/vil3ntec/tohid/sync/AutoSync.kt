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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 *  همگام‌سازی خودکار — دفترِ دکان همیشه روی سرور.
 *
 *  قرار این است: هر تغییری که در هر حساب می‌افتد، همان‌جا روی سرور
 *  بنشیند و در حسابِ خودِ همان شخص ذخیره شود؛ و هر چه روی سرور هست،
 *  روی هر دستگاهی که با آن حساب وارد شده دیده شود.
 *
 *  چهار راه دارد که با هم آن قرار را نگه می‌دارند:
 *
 *    ۱) **پس از هر تغییر** — با مکثی کوتاه، تا ده قلمِ یک سبد ده بار
 *       پشتِ سرِ هم فرستاده نشوند.
 *    ۲) **سرِ باز شدنِ برنامه و بعد از هر ورود** — تا کسی که تازه نصب
 *       کرده و وارد شده، دفترِ خودش را همان لحظه پایین بگیرد.
 *    ۳) **هر چند دقیقه تا وقتی برنامه باز است** — تا تغییرِ شریکی که
 *       روی گوشیِ دیگری کار می‌کند دیده شود.
 *    ۴) **تلاشِ دوباره پس از شکست** — با فاصلهٔ رشدیابنده.
 *
 *  ── سه سوراخی که اینجا بسته شد ─────────────────────────────────────
 *  • بعد از ورود هیچ همگام‌سازی‌ای صدا زده نمی‌شد. کسی که تازه نصب
 *    کرده بود و وارد می‌شد، **دکانِ خالی** می‌دید تا برنامه را ببندد و
 *    دوباره باز کند.
 *  • شکست، تلاشِ دوباره نداشت. تغییری که در جای بی‌آنتن ثبت می‌شد تا
 *    تغییرِ بعدی یا ری‌استارت روی گوشی می‌ماند.
 *  • تا کاربر خودش چیزی عوض نمی‌کرد، تغییرِ دیگران گرفته نمی‌شد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  همهٔ این‌ها بی‌صداست: نبودنِ اینترنت خطا نیست و سرِ راهِ کاربر
 *  نمی‌ایستد. دفترِ محلی همیشه کار می‌کند؛ سرور فقط جایی است که
 *  دیر یا زود به آن می‌رسد.
 */
object AutoSync {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private var pending: Job? = null   // مکثِ پس از تغییر
  private var retrying: Job? = null  // تلاشِ دوباره پس از شکست
  private var polling: Job? = null   // گرفتنِ تغییرهای دیگران

  /** الان در حال فرستادن است — برای نشان دادن در تنظیمات */
  var running by mutableStateOf(false)
    private set

  /** آخرین باری که واقعاً موفق شد */
  var lastOk by mutableStateOf(0L)
    private set

  /** پیامِ آخرین شکست، اگر بود */
  var lastError by mutableStateOf<String?>(null)
    private set

  /** تغییرِ محلی هست که هنوز نرفته */
  @Volatile private var unsent = false

  /** شمارهٔ تلاشِ ناموفقِ پیاپی — پایهٔ فاصلهٔ تلاشِ بعدی */
  @Volatile private var attempt = 0

  /**
   *  شروعِ پنجرهٔ مکثِ فعلی.
   *
   *  بدونِ این، کسی که پیوسته تایپ می‌کند هر بار مکث را از نو شروع
   *  می‌کرد و فرستادن تا وقتی دست از کار بکشد عقب می‌افتاد.
   */
  @Volatile private var windowStart = 0L

  /** هم نشانی هست، هم حساب — یعنی اصلاً می‌شود به سرور زد */
  private fun ready(context: Context): Boolean = Backend.isReady(context)

  /* ------------------------------ راه‌ها ------------------------------ */

  /**
   *  «چیزی عوض شد.»
   *
   *  هر بار که دفتر تغییر می‌کند صدا زده می‌شود.
   */
  fun nudge(context: Context, store: ShopStore) {
    val app = context.applicationContext
    if (!ready(app)) return

    unsent = true
    val now = System.currentTimeMillis()
    if (windowStart == 0L) windowStart = now

    //  مکثِ عادی، مگر اینکه از شروعِ این پنجره زیادی گذشته باشد
    val wait = SyncSchedule.waitAfterChange(windowStart, now)

    pending?.cancel()
    pending = scope.launch {
      delay(wait)
      windowStart = 0L
      runOnce(app, store)
    }
  }

  /** همین حالا — برای بازِ شدنِ برنامه و برای بعد از ورود */
  fun now(context: Context, store: ShopStore) {
    val app = context.applicationContext
    if (!ready(app)) return
    scope.launch { runOnce(app, store) }
  }

  /**
   *  گرفتنِ تغییرهای دیگران، تا وقتی برنامه جلوی چشم است.
   *
   *  در پس‌زمینه خاموش می‌شود: نه باتری می‌خورد و نه دیتا. وقتی برنامه
   *  برمی‌گردد، `now` یک بار همه‌چیز را تازه می‌کند.
   */
  fun startPolling(context: Context, store: ShopStore) {
    val app = context.applicationContext
    polling?.cancel()
    polling = scope.launch {
      while (isActive) {
        delay(SyncSchedule.POLL_MS)
        if (ready(app)) runOnce(app, store)
      }
    }
  }

  fun stopPolling() {
    polling?.cancel()
    polling = null
  }

  /* ------------------------------ اجرا ------------------------------ */

  private suspend fun runOnce(app: Context, store: ShopStore) {
    if (running) return          // یکی در حال اجراست؛ `unsent` سرِ جایش می‌ماند
    if (!ready(app)) return

    //  نت که نیست، رفتن سراغِ شبکه فقط انتظارِ بی‌فایده است — ولی اگر
    //  چیزی برای فرستادن مانده، بعداً دوباره امتحان می‌کنیم
    if (!Backend.isOnline(app)) {
      scheduleRetry(app, store)
      return
    }

    val state = SyncStore(app)
    running = true
    val outcome = runCatching { Syncer(store, state, app).run() }
    running = false

    outcome
      .onSuccess {
        unsent = false
        attempt = 0
        lastOk = System.currentTimeMillis()
        lastError = null
        retrying?.cancel()
        retrying = null
        //  مجوزِ اشتراک هم همین‌جا تازه می‌شود؛ عمرش ده روز است و اگر
        //  کاربر هیچ‌وقت دستی نزند، بی‌سروصدا تمام می‌شد
        runCatching { Syncer(store, state, app).refreshLicense(android.os.Build.MODEL ?: "گوشی") }
      }
      .onFailure { failure ->
        //  «نت نیست» را به کاربر نشان نمی‌دهیم: خطا نیست و خودش دوباره
        //  می‌رود. بقیه پیامِ فارسیِ خودشان را دارند.
        lastError = when (failure) {
          is ApiFailure.Offline -> null
          is ApiFailure -> failure.userMessage
          else -> failure.message
        }
        scheduleRetry(app, store)
      }
  }

  /**
   *  تلاشِ دوباره، با فاصله‌ای که هر بار بلندتر می‌شود.
   *
   *  فقط وقتی معنی دارد که چیزی برای فرستادن مانده باشد یا آخرین تلاش
   *  شکست خورده باشد — وگرنه شبکه را بی‌دلیل بیدار نمی‌کنیم.
   *
   *  نشستِ تمام‌شده تلاشِ دوباره ندارد: تا کاربر دوباره وارد نشود،
   *  هزار بار امتحان کردن هم جواب نمی‌دهد.
   */
  private fun scheduleRetry(app: Context, store: ShopStore) {
    if (!SyncSchedule.shouldRetry(unsent, lastError != null, ready(app))) return

    retrying?.cancel()
    val wait = SyncSchedule.backoffFor(attempt)
    attempt++
    retrying = scope.launch {
      delay(wait)
      runOnce(app, store)
    }
  }
}
