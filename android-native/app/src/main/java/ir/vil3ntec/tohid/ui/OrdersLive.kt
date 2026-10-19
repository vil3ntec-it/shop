package ir.vil3ntec.tohid.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *  حالِ بخشِ «سفارش‌ها» برای کلِ برنامه (فاز ۴، ORD-01).
 *
 *  ⛔ پیش‌فرض خاموش: تا سرور (`GET /shop/orders/config`) نگوید `enabled`،
 *  `config` تهی است ⇒ نه ردیفی در منو، نه صفحه‌ای، نه پرسیدنِ شمارش.
 *  ⛔ صفِ زنده با پرسیدنِ `summary` است، **فقط** وقتی برنامه جلوی چشم است
 *  (ON_START تا ON_STOP)؛ سوکتِ تازه‌ای باز نمی‌شود. این جای اعلانِ فوریِ
 *  §۳۰ (FCM) را نمی‌گیرد و صفحه همین را صادقانه می‌گوید.
 *  ⛔ برگشت به جلو (§۳۰.۲ بندِ ۴): همان لحظه از سرور دوباره پرسیده می‌شود تا
 *  اعلانِ گم‌شده سفارشی را گم نکند.
 */
object OrdersLive {

  /** فاصلهٔ پرسیدنِ شمارش‌ها */
  const val POLL_MS = 20_000L

  /** هر چند وقت یک بار «روشن است؟» دوباره پرسیده شود */
  const val CONFIG_MS = 10 * 60_000L

  /** فقط وقتی بخش برای این دکان روشن است، تهی نیست */
  val config = mutableStateOf<OrdersConfigDto?>(null)
  val counts = mutableStateOf<Map<String, Int>>(emptyMap())

  /** نشانگرِ تغییرِ صف — صفحهٔ باز با عوض شدنش فهرست را تازه می‌کند */
  val rev = mutableStateOf(0L)

  /** سفارشی که باید باز شود — راهِ ورودِ اعلان (§۳۰.۱ بندِ ۵): جزئیات همیشه تازه از سرور */
  val pendingOrderId = mutableStateOf<String?>(null)

  val enabled: Boolean get() = config.value != null
  val newCount: Int get() = counts.value["new"] ?: 0

  @Volatile private var known = false
  @Volatile private var configAt = 0L
  private val lock = Mutex()
  @Volatile private var repo: OrdersRepository? = null

  fun repository(context: Context): OrdersRepository =
    repo ?: synchronized(this) { repo ?: OrdersRepository(Backend.api(context)).also { repo = it } }

  suspend fun refreshConfig(context: Context) {
    lock.withLock {
      configAt = System.currentTimeMillis()
      if (!Backend.isReady(context)) { apply(null); return@withLock }
      val r = repository(context).config()
      //  قطعِ گذرا بخشِ روشن را خاموش نمی‌کند
      val keep = r is ApiResult.Failure && r.error.retryable && known
      if (!keep) apply((r as? ApiResult.Success)?.value?.takeIf { it.enabled })
    }
  }

  private fun apply(cfg: OrdersConfigDto?) {
    known = true
    config.value = cfg
    if (cfg == null) { counts.value = emptyMap(); rev.value = 0 }
  }

  suspend fun pollSummary(context: Context) {
    if (!enabled) return
    repository(context).summary()
      .onSuccess {
        counts.value = it.counts
        rev.value = it.rev
      }
      .onFailure { if (it.code == "orders_disabled") refreshConfig(context) }
  }

  /** باز کردنِ یک سفارش با شناسه (برای اعلان)؛ اگر بخش خاموش است، هیچ */
  fun openOrder(id: String) {
    if (!enabled || id.isBlank()) return
    pendingOrderId.value = id
    AppNav.go("orders")
  }

  /**
   *  زمان‌سنجِ صف — یک بار در `AppRoot`. فقط بینِ ON_START و ON_STOP می‌دود.
   */
  @Composable
  fun Host() {
    val context = LocalContext.current
    var foreground by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { foreground = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { foreground = false }
    LaunchedEffect(foreground) {
      if (!foreground) return@LaunchedEffect
      if (!known || System.currentTimeMillis() - configAt > 60_000) refreshConfig(context)
      pollSummary(context)
      while (isActive) {
        delay(POLL_MS)
        if (System.currentTimeMillis() - configAt > CONFIG_MS) refreshConfig(context)
        pollSummary(context)
      }
    }
  }
}
