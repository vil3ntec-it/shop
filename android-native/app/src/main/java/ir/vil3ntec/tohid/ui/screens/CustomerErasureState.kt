package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.crm.CustomerErasure
import ir.vil3ntec.tohid.core.i18n.StrErasure
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.data.repo.CustomerErasureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 *  حالِ «حذفِ دادهٔ این مشتری» (SEC-10) — یک حالت برای هر دو رابط (`CustomerErasurePanel` ِ رابطِ امروز و
 *  `CustomerErasureV3`)؛ گام‌ها و شرط‌ها در `core/crm/CustomerErasure`. ترتیب همیشه: پیش‌نمایش ⇒ تأییدِ سخت ⇒
 *  تأییدِ دوبارهٔ هویت (همان `ReauthGate` ِ برنامه) ⇒ اجرا. انصرافِ هویت ⇒ هیچ درخواستی نمی‌رود.
 */

/** دکمه فقط برای صاحبِ دکان و پس از پاسخِ روشنِ سرور به پرسشِ بی‌اثر (`CustomerErasure.probe`) */
object CustomerErasureLive {
  const val TTL_MS = 30 * 60_000L
  val gate = ProbeGate(TTL_MS)

  suspend fun ensure(src: CustomerErasureSource, now: Long = System.currentTimeMillis()) {
    if (!gate.due(now)) return
    src.probe()
      .onSuccess { gate.record(false, now) }   // سرورِ ناشناخته‌ای که پرسشِ بی هدف را پذیرفت — پنهان
      .onFailure { f -> gate.record(CustomerErasure.probeSays(f.code, serverRefused(f), f is ApiFailure.Invalid), now) }
  }
}

/** «سرور گفت نه» ِ این در: خاموش (۴۰۴)، غیرِ صاحب (۴۰۳ `owner_only`) — نه تأییدِ هویت و نه سفارشِ باز */
private fun erasureRefused(f: ApiFailure): Boolean =
  (f is ApiFailure.NotFound) || (f is ApiFailure.Forbidden && f.code != CustomerErasure.REAUTH)

/**
 *  گام‌های یک حذف برای یک شماره. [reauth] همان پرسشِ برنامه (`ApiClient.reauth.confirm()`): برگهٔ رمز یا کد را
 *  نشان می‌دهد و فقط پس از پذیرشِ سرور `true` است.
 */
@Stable
internal class CustomerErasureController(
  private val src: CustomerErasureSource,
  val phone: String,
  private val reauth: suspend () -> Boolean,
  private val scope: CoroutineScope,
  private val clock: () -> Long = System::currentTimeMillis,
) {
  var step by mutableStateOf<CustomerErasure.Step>(CustomerErasure.Step.Idle)
    private set

  /** در حالِ کار (پیش‌نمایش، هویت یا اجرا) ⇒ برگه بسته نمی‌شود */
  val busy: Boolean
    get() = step.let { it is CustomerErasure.Step.Previewing || it is CustomerErasure.Step.Reauth || it is CustomerErasure.Step.Erasing }

  /** پیش‌نمایش (`dryRun`) — هیچ نوشتنی؛ از نو هم (پس از خطا یا پایان) */
  fun preview(): Job? {
    val next = CustomerErasure.start(step) ?: return null
    step = next
    return scope.launch {
      src.erase(phone, dryRun = true)
        .onSuccess { step = CustomerErasure.previewed(it) }
        .onFailure { f -> failed(f) }
    }
  }

  /** «ادامه» ⇒ تأییدِ سخت؛ سفارشِ باز یا هیچ چیزی برای پاک کردن ⇒ نه */
  fun proceed() {
    CustomerErasure.toConfirm(step)?.let { step = it }
  }

  fun type(text: String) {
    step = CustomerErasure.typed(step, text)
  }

  val confirmOk: Boolean
    get() = (step as? CustomerErasure.Step.Confirm)?.let { CustomerErasure.confirmOk(it.typed, phone) } == true

  /** «حذف کن»: فقط از تأییدِ درست ⇒ تأییدِ هویت ⇒ اجرا */
  fun erase(): Job? {
    val asked = CustomerErasure.askReauth(step, phone) ?: return null
    step = asked
    return scope.launch {
      val ok = reauth()
      step = CustomerErasure.reauthed(step, ok, StrErasure.reauthCancelled)
      if (step !is CustomerErasure.Step.Erasing) return@launch
      src.erase(phone, dryRun = false)
        .onSuccess { step = CustomerErasure.erased(step, it) }
        .onFailure { f ->
          if (erasureRefused(f)) CustomerErasureLive.gate.hide(clock())
          step = CustomerErasure.eraseFailed(step, f.userMessage)
        }
    }
  }

  /** برگشت به آغاز (بستنِ برگه، جز در میانِ کار) */
  fun reset() {
    if (!busy) step = CustomerErasure.Step.Idle
  }

  private fun failed(f: ApiFailure) {
    if (erasureRefused(f)) CustomerErasureLive.gate.hide(clock())
    step = CustomerErasure.Step.Failed(f.userMessage)
  }
}

/** خط‌های شمارش (فقط ناصفر) — هر دو رابط */
internal fun erasureCountLines(r: CustomerErasure.Result): List<String> =
  r.counts.map { (k, n) -> StrErasure.count(StrErasure.label(k), n.toString()) }

/** «چه می‌ماند» — همان سه بندِ ثابت، از جمله محدودیتِ دفترِ دستگاه (#787) */
internal val erasureKeptLines: List<String>
  get() = listOf(StrErasure.keptFinance, StrErasure.keptDevice, StrErasure.keptOther)
