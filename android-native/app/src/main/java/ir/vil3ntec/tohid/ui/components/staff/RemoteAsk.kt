package ir.vil3ntec.tohid.ui.components.staff

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrRemoteApproval
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.perm.ApprovalSlip
import ir.vil3ntec.tohid.core.perm.RemoteApproval
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.ApprovalRequestsApi
import ir.vil3ntec.tohid.data.repo.ApprovalRequestsRepository
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 *  «پرسیدن از مدیر» ِ یک برگهٔ تأیید (STF-02) — حالِ مشترکِ `ApprovalSheet` (گوشیِ نسخهٔ ۳ و همهٔ مسیرهای v3 ِ کامپیوتر)
 *  و `ApprovalDialog` (صندوق و برگشتِ کامپیوتر). قاعده‌ها در `core/perm/RemoteApproval`؛ این‌جا رفت‌وبرگشت و انتظار.
 *
 *  ⛔ پیش‌فرض خاموش: تا `/config` ِ سرور `enabled` نگوید (پرچمِ `shop.approval_requests`)، یا بی اینترنت، [offered] نه است
 *     و برگه دقیقاً همان امروز است (فقط PIN ِ همین دستگاه).
 *  ⛔ پرسیدن فقط تا وقتی برگه باز است و آرام (`RemoteApproval.pollDelayMs`)؛ بستنِ برگه درخواستِ باز را پس می‌گیرد.
 *  ⛔ تأیید فقط ارجاع است (`RemoteApproval.slipOf`): سرور هنگامِ همگام‌سازی آن را دوباره می‌سنجد و یک بار مصرف می‌کند.
 */
class RemoteAsk internal constructor(
  private val repo: ApprovalRequestsApi?,
  val need: ApprovalSlip.Need,
  private val shopId: String,
  /** تصمیمِ خودِ کاربر برای همین نیاز (`PermCore.evaluate`) */
  private val decision: String,
  private val now: () -> Long = System::currentTimeMillis,
  /** انتظارِ میانِ دو پرسش (آزمون‌ها بی ساعتِ واقعی) */
  private val sleep: suspend (Long) -> Unit = { delay(it) },
  /** تأییدِ این کار برای این نقش فقط از Sync v3 می‌نشیند (`RemoteApproval.needsV3`) */
  private val v3Only: Boolean = false,
  /** همین دستگاه با Sync v3 همگام می‌شود (`SyncChoice.isV3`) */
  private val deviceV3: Boolean = true,
) {
  var config by mutableStateOf<RemoteApproval.Config?>(null)
    private set
  /** null ⇒ هنوز نپرسیده */
  var wait by mutableStateOf<RemoteApproval.Wait?>(null)
    private set
  var request by mutableStateOf<RemoteApproval.Request?>(null)
    private set
  var message by mutableStateOf<String?>(null)
    private set
  /** ثانیه‌های مانده — با هر دورِ انتظار تازه می‌شود */
  var secondsLeft by mutableStateOf(0L)
    private set
  private var netErrors = 0
  private var key: String? = null
  /** «پس گرفتن» یا بستنِ برگه ⇒ حلقهٔ [run] در نخستین فرصت می‌ایستد */
  @Volatile private var stopped = false

  val offered: Boolean get() = repo != null && shopId.isNotBlank() && RemoteApproval.offered(config, decision, need.perm) &&
    RemoteApproval.lands(config, v3Only, deviceV3)
  /** [run] در کار است (دکمه ⇒ «پس گرفتن»)؛ پس از ایست، حتی «بی اینترنت»، دکمهٔ «دوباره بپرس» برمی‌گردد */
  var active by mutableStateOf(false)
    private set

  /** یک بار هنگامِ باز شدن؛ خطا یا خاموش ⇒ [offered] نه */
  suspend fun load() {
    if (repo == null || decision != "approve" || need.perm !in RemoteApproval.PERMS) return
    //  دستگاهِ v1 و کارِ فقط-v3 ⇒ نه پرسیدنِ سرور، نه دکمه
    if (v3Only && !deviceV3) return
    config = (repo.config() as? ApiResult.Success)?.value
  }

  /**
   *  ساختن (همان کلید برای تکرار) و انتظار تا پاسخِ پایانی؛ تأیید ⇒ [onApproved] با برگهٔ ارجاع. کوروتین را صدازننده نگه
   *  می‌دارد (بستنِ برگه ⇒ لغو و [withdraw]).
   */
  suspend fun run(reason: String, onApproved: (ApprovalSlip.Slip, RemoteApproval.Request) -> Unit) {
    val r = repo ?: return
    if (active) return
    active = true
    stopped = false
    try {
      loop(r, reason, onApproved)
    } finally {
      active = false
    }
  }

  private suspend fun loop(r: ApprovalRequestsApi, reason: String, onApproved: (ApprovalSlip.Slip, RemoteApproval.Request) -> Unit) {
    //  پرسشِ پیشین تمام شد (رد، گذشت، پس‌گرفته…) ⇒ درخواستِ تازه با کلیدِ تازه؛ «بی اینترنت» ِ پیش از ساختن ⇒ همان کلید
    if (request != null) { request = null; key = null }
    val k = key ?: RemoteApproval.newKey().also { key = it }
    val body = RemoteApproval.body(k, need, reason) ?: run { wait = RemoteApproval.Wait.FAILED; message = StrRemoteApproval.failed; return }
    message = null
    netErrors = 0
    wait = RemoteApproval.Wait.SENDING
    //  ساختن: خطای گذرا ⇒ با همان کلید دوباره (بی‌خطر)؛ چهار بارِ پیاپی ⇒ «بی اینترنت» و ایست تا «دوباره بپرس»
    while (request == null) {
      when (val out = r.create(body)) {
        is ApiResult.Success -> {
          request = out.value
          netErrors = 0
          //  در همین فاصله پس گرفته شد ⇒ درخواستِ تازه‌ساخته هم پس گرفته شود
          if (stopped) { cancelOnServer(r, out.value.id); return }
        }
        is ApiResult.Failure -> {
          if (!out.error.retryable) {
            wait = RemoteApproval.waitOfError(out.error.code) ?: RemoteApproval.Wait.FAILED
            message = out.error.userMessage
            return
          }
          netErrors++
          wait = RemoteApproval.waitOf(null, now(), netErrors)
          if (netErrors > 3) { wait = RemoteApproval.Wait.OFFLINE; message = StrRemoteApproval.offline; return }
          sleep(RemoteApproval.pollDelayMs(netErrors, now(), 0L))
          if (stopped) return
        }
      }
    }
    var attempt = 0
    while (true) {
      val cur = request ?: return
      secondsLeft = RemoteApproval.secondsLeft(cur, now())
      wait = RemoteApproval.waitOf(cur, now(), netErrors)
      if (RemoteApproval.done(wait!!)) break
      sleep(RemoteApproval.pollDelayMs(attempt++, now(), cur.expiresAt))
      if (stopped) return
      when (val out = r.get(cur.id)) {
        is ApiResult.Success -> { request = out.value; netErrors = 0 }
        is ApiResult.Failure -> if (out.error.retryable) netErrors++ else {
          wait = RemoteApproval.waitOfError(out.error.code) ?: RemoteApproval.Wait.FAILED
          message = out.error.userMessage
          return
        }
      }
      if (stopped) return
    }
    val done = request ?: return
    message = statusText()
    if (wait == RemoteApproval.Wait.APPROVED) RemoteApproval.slipOf(shopId, done)?.let { onApproved(it, done) }
  }

  private fun cancelOnServer(r: ApprovalRequestsApi, id: String) {
    background.launch { withTimeoutOrNull(5_000L) { r.cancel(id) } }
  }

  /** پس گرفتنِ درخواستِ باز (بستنِ برگه، «پس گرفتن») — بی‌انتظار؛ شکستش بی‌اثر (عمرش خودش تمام می‌شود) */
  fun withdraw() {
    if (!active) return
    stopped = true
    val r = repo ?: return
    if (wait == RemoteApproval.Wait.APPROVED) return
    request?.id?.let { cancelOnServer(r, it) }
    wait = RemoteApproval.Wait.CANCELLED
    message = StrRemoteApproval.cancelled
  }

  /** متنِ حالِ کنونی برای برگه */
  fun statusText(): String? = when (wait) {
    null -> null
    RemoteApproval.Wait.SENDING -> StrRemoteApproval.sending
    RemoteApproval.Wait.WAITING -> StrRemoteApproval.waiting(clock(secondsLeft))
    RemoteApproval.Wait.OFFLINE -> StrRemoteApproval.offline
    RemoteApproval.Wait.APPROVED -> request?.deciderName?.ifBlank { null }?.let(StrRemoteApproval::approved) ?: StrRemoteApproval.approvedAnon
    RemoteApproval.Wait.DENIED -> request?.note?.ifBlank { null }?.let(StrRemoteApproval::deniedNote) ?: StrRemoteApproval.denied
    RemoteApproval.Wait.EXPIRED -> StrRemoteApproval.expired
    RemoteApproval.Wait.CANCELLED -> StrRemoteApproval.cancelled
    RemoteApproval.Wait.FAILED -> message ?: StrRemoteApproval.failed
  }

  companion object {
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun clock(seconds: Long): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    fun of(context: Context, need: ApprovalSlip.Need, shopId: String): RemoteAsk {
      val ready = runCatching { Backend.isReady(context) }.getOrDefault(false)
      val req = buildMap {
        need.pctBp?.let { put("pct", it / 100.0) }
        need.amount?.let { put("amount", it.toDouble()) }
      }
      val decision = runCatching { ShopRole.decide(context, need.perm, req.ifEmpty { null }).decision }.getOrDefault("deny")
      val v3Only = runCatching { RemoteApproval.needsV3(ShopRole.current(context), need.perm) }.getOrDefault(true)
      val deviceV3 = runCatching { ir.vil3ntec.tohid.sync.v3.SyncChoice.isV3(context) }.getOrDefault(false)
      return RemoteAsk(if (ready) ApprovalRequestsRepository(Backend.api(context)) else null, need, shopId, decision, v3Only = v3Only, deviceV3 = deviceV3)
    }
  }
}

/** همان [RemoteAsk] برای یک برگه — `/config` یک بار، پس از باز شدن */
@Composable
fun rememberRemoteAsk(need: ApprovalSlip.Need, shopId: String): RemoteAsk {
  val context = LocalContext.current
  val state = remember(need, shopId) { RemoteAsk.of(context, need, shopId) }
  LaunchedEffect(state) { runCatching { state.load() } }
  //  برگه بسته شد و هنوز منتظر بود ⇒ درخواست پس گرفته شود تا مدیر بیهوده نبیند
  DisposableEffect(state) { onDispose { state.withdraw() } }
  return state
}
