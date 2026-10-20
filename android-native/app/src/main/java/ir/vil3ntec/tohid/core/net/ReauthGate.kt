package ir.vil3ntec.tohid.core.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *  «رمز را دوباره بزنید» — یک جا برای همهٔ درخواست‌ها (بندِ ۲۱.۱، سرور: `lib/reauth.js`).
 *
 *  ── مسئله ──────────────────────────────────────────────────────────
 *  سرور کارهای حساس (گذاشتنِ رمز برای حسابِ بی‌رمز، افزودنِ شماره، مجوز
 *  و نقشِ کارمندان…) را فقط از نشستی می‌پذیرد که چند دقیقهٔ اخیر وارد
 *  شده یا تأییدِ دوباره زده؛ وگرنه ۴۰۳ با کدِ `reauth_required`. تا امروز
 *  فقط صفحهٔ مجوزها پنجرهٔ رمز داشت و بقیه ده دقیقه پس از ورود بی‌راهِ
 *  برگشت گیر می‌کردند.
 *
 *  ── قاعده ──────────────────────────────────────────────────────────
 *  `ApiClient` هر `reauth_required` را این‌جا می‌آورد. اگر صفحه‌ای
 *  «میزبان» است (`attach`)، یک پرسش (`asking`) می‌نشیند؛ میزبان پنجرهٔ
 *  رمز یا کد را نشان می‌دهد، `POST /auth/reauth` را می‌زند و با
 *  `Ask.done(true)` جواب می‌دهد — آن‌وقت `ApiClient` همان درخواست را
 *  **یک بار** دوباره می‌فرستد. پس هر مسیرِ حساسِ تازه بی کارِ اضافه همین
 *  را دارد.
 *
 *  • میزبانی نیست (کارِ پس‌زمینه) ⇒ بی‌درنگ `false`: همان خطای امروز.
 *  • چند درخواست با هم ⇒ یک پرسش؛ بقیه پشتِ قفل می‌مانند و اگر اولی
 *    تأیید شد، بی پرسشِ دوباره تکرار می‌شوند.
 *  • انصراف یا بسته شدنِ میزبان ⇒ `false` و خطای اصلی (پیامِ فارسیِ سرور).
 */
class ReauthGate {

  /** یک پرسشِ باز — میزبان با `done` جوابش را می‌دهد */
  class Ask internal constructor() {
    private val answer = CompletableDeferred<Boolean>()
    fun done(confirmed: Boolean) { answer.complete(confirmed) }
    internal suspend fun await(): Boolean = answer.await()
  }

  private val open = MutableStateFlow<Ask?>(null)

  /** پرسشی که همین حالا باید نشان داده شود — `null` یعنی هیچ */
  val asking: StateFlow<Ask?> = open.asStateFlow()

  private val lock = Mutex()
  private val hostsLock = Any()
  private var hosts = 0

  /** شمارِ تأییدهای موفق — تا درخواستِ پشتِ قفل بفهمد دیگری تأیید کرده */
  @Volatile private var confirmations = 0L

  /** صفحه‌ای که می‌تواند بپرسد؛ تابعِ برگشتی جدایش می‌کند */
  fun attach(): () -> Unit {
    synchronized(hostsLock) { hosts++ }
    var detached = false
    return {
      synchronized(hostsLock) {
        if (!detached) {
          detached = true
          hosts--
          //  آخرین میزبان رفت و پرسشی باز ماند ⇒ کسی نیست جواب دهد
          if (hosts == 0) open.value?.done(false)
        }
      }
    }
  }

  private fun hasHost(): Boolean = synchronized(hostsLock) { hosts > 0 }

  /**
   *  پرسیدن از کاربر. `true` فقط وقتی سرور تأییدِ دوباره را پذیرفت و
   *  درخواست باید یک بار دیگر برود.
   */
  suspend fun confirm(): Boolean {
    if (!hasHost()) return false
    val seen = confirmations
    return lock.withLock {
      //  همین حالا درخواستِ دیگری تأیید گرفت ⇒ نشست تازه است
      if (confirmations != seen) return@withLock true
      if (!hasHost()) return@withLock false
      val ask = Ask()
      open.value = ask
      //  میزبان درست در همین لحظه رفت ⇒ کسی نیست که جواب دهد
      if (!hasHost()) ask.done(false)
      val ok = try { ask.await() } finally { open.compareAndSet(ask, null) }
      if (ok) confirmations++
      ok
    }
  }

  companion object {
    /** کدِ خطای سرور — `lib/reauth.js` */
    const val CODE = "reauth_required"

    /**
     *  آیا این خطا یعنی «تأییدِ دوباره بخواه».
     *  خودِ مسیرهای `/auth/reauth` هرگز — وگرنه پرسش در پرسش.
     */
    fun applies(failure: ApiFailure, path: String): Boolean =
      failure.code == CODE && !path.startsWith(ApiEndpoints.Auth.REAUTH)
  }
}
