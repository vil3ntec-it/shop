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

  /**
   *  چند تغییرِ محلی هنوز روی سرور ننشسته.
   *
   *  این عدد چیزی است که نقطهٔ بالای صفحه نشان می‌دهد. تا دیروز هیچ
   *  صفحه‌ای وضعیت را نشان نمی‌داد: فروشنده‌ای که در زیرزمینِ بی‌آنتن کار
   *  می‌کرد نمی‌دانست ۳۰ فروشش هنوز روی گوشی است.
   */
  var pendingCount by mutableStateOf(0)
    private set

  /**
   *  آخرین تغییرهایی که سرور قبول نکرد و نسخهٔ او جایشان نشست.
   *
   *  خالی نگه داشتنِ این، همان «بی‌صدا گم شدن» بود. حالا صفحهٔ تنظیمات و
   *  نقطهٔ وضعیت می‌گویند چند مورد اعمال نشد، و کاربر می‌داند باید نگاهی
   *  بیندازد.
   */
  var lastRejected by mutableStateOf(0)
    private set

  /** پیامِ فارسیِ آخرین تعارض، برای نشان دادن یک‌باره */
  var rejectionNote by mutableStateOf<String?>(null)
    private set

  /**
   *  سرور گفت این حساب دکانی ندارد.
   *
   *  ── چه چیزی را می‌بندد ────────────────────────────────────────────
   *  سرور بدونِ دکان هیچ داده‌ای نمی‌گیرد و نمی‌دهد و مجوزِ آزمایشی هم
   *  صادر نمی‌کند. برنامه دکان را فقط **یک بار**، هنگام باز شدن، از سرور
   *  می‌پرسید؛ اگر آن یک بار به هر دلیلی جواب نمی‌گرفت (نت نبود، سرور
   *  قدیمی بود، یا کاربر بعدش وارد شد) دیگر هیچ‌وقت نمی‌پرسید.
   *
   *  نتیجه‌اش همان چیزی شد که گزارش شد: حساب هست، سرور وصل است، ولی
   *  همگام‌سازی هر بار شکست می‌خورد و ۶۱ تغییر روی گوشی مانده — و هیچ
   *  راهی روی صفحه برای ساختنِ دکان نیست، چون آن صفحه فقط یک دربانِ
   *  یک‌بارمصرف بود.
   *
   *  حالا خودِ همگام‌سازی خبر می‌دهد: هر بار سرور `no_shop` بدهد، این
   *  درست می‌شود و `AppRoot` صفحهٔ ساختِ دکان را باز می‌کند. سرور
   *  می‌گوید چه کم است، برنامه همان را می‌پرسد.
   *  ──────────────────────────────────────────────────────────────────
   */
  var needsShop by mutableStateOf(false)
    private set

  /** بعد از اینکه کاربر پیام را دید */
  fun clearRejectionNote() {
    rejectionNote = null
  }

  /** یکی از سه حالتِ نقطه */
  enum class Health { OK, WAITING, FAILED }

  val health: Health
    get() = when {
      lastError != null -> Health.FAILED
      running || pendingCount > 0 -> Health.WAITING
      else -> Health.OK
    }

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
    val outcome = runCatching {
      Syncer(store, state, app).apply { onCollected = { pendingCount = it } }.run()
    }
    running = false

    outcome
      .onSuccess { done ->
        unsent = false
        attempt = 0
        pendingCount = 0
        lastOk = System.currentTimeMillis()
        lastError = null
        needsShop = false
        //  تعارض‌ها خطا نیستند — همگام‌سازی موفق بوده — ولی کاربر باید
        //  بداند کدام تغییرش اعمال نشد
        lastRejected = done.rejected.size
        rejectionNote = if (done.rejected.isEmpty()) null else conflictNote(done.rejected)
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
        //  «دکان نداری» تنها شکستی است که کاربر می‌تواند خودش درستش کند
        if ((failure as? ApiFailure)?.code == "no_shop") needsShop = true
        scheduleRetry(app, store)
      }
  }

  /**
   *  پیامِ فارسیِ تعارض‌ها.
   *
   *  دو حالت جدا می‌شوند چون کاری که کاربر باید بکند فرق دارد: یکی
   *  «شریکت زودتر عوض کرده» است و آن یکی «اجازه‌ات نمی‌رسد».
   */
  private fun conflictNote(
    rejected: List<ir.vil3ntec.tohid.data.repo.SyncRepository.Conflict>,
  ): String {
    val stale = rejected.count { it.reason == "stale" }
    val denied = rejected.count { it.reason == "delete_not_allowed" }
    val parts = buildList {
      if (stale > 0) add("$stale مورد چون نسخهٔ تازه‌تری روی سرور بود")
      if (denied > 0) add("$denied مورد چون اجازهٔ حذفش را نداشتید")
      val other = rejected.size - stale - denied
      if (other > 0) add("$other مورد دیگر")
    }
    return "${rejected.size} تغییر اعمال نشد: ${parts.joinToString("، ")}. " +
      "نسخهٔ سرور جایش نشست."
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
