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
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
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
 *  ⛔ تحویلِ سفارش ⇒ فروشِ عادی در همین دفتر ([post]، `OrderFulfillment`) و همگام‌سازیِ
 *  فوری. تحویلی که این دستگاه نزده (پیک، دستگاهِ دیگر، برنامه‌ای که پیش از ثبت بسته شد)
 *  را [reconcile] از `GET /shop/orders/handover` می‌گیرد — فقط با اجازهٔ `orders.manage`.
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

  /** تحویلی که فروشش این‌جا ثبت نشد (کمبود، قفلِ برنامه…) — صفحهٔ «سفارش‌ها» هشدار می‌دهد */
  data class Unposted(val orderId: String, val orderNo: Long, val message: String)
  val unposted = mutableStateOf<List<Unposted>>(emptyList())

  sealed interface Posted {
    data object Saved : Posted
    data object Already : Posted
    data class Failed(val message: String) : Posted
  }

  /** فاصلهٔ پرسیدنِ دوبارهٔ تحویل‌های بی‌فروش وقتی صف عوض نشده */
  const val HANDOVER_MS = 120_000L
  private const val PREFS = "tohid-orders"
  private const val SINCE = "handover_since"

  private val postLock = Mutex()
  @Volatile private var handoverRev = Long.MIN_VALUE
  @Volatile private var handoverAt = 0L
  @Volatile private var serverTime = 0L

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
    if (cfg == null) { counts.value = emptyMap(); rev.value = 0; unposted.value = emptyList() }
  }

  suspend fun pollSummary(context: Context) {
    if (!enabled) return
    repository(context).summary()
      .onSuccess {
        counts.value = it.counts
        rev.value = it.rev
        if (it.serverTime > 0) serverTime = it.serverTime
      }
      .onFailure { if (it.code == "orders_disabled") refreshConfig(context) }
  }

  /**
   *  لحظهٔ نخستین به‌کارگیریِ «فروشِ تحویل» روی این دستگاه (زمانِ سرور اگر هست) — تحویل‌های
   *  پیش از آن (که دکان شاید دستی در صندوق زده) دوباره فروخته نمی‌شوند.
   */
  fun handoverSince(context: Context): Long {
    val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val have = prefs.getLong(SINCE, 0L)
    if (have > 0) return have
    val now = if (serverTime > 0) serverTime else System.currentTimeMillis()
    prefs.edit().putLong(SINCE, now).apply()
    return now
  }

  /**
   *  فروشِ تحویلِ [order] (جزئیاتِ کامل با قلم‌ها) در همین دفتر و همگام‌سازیِ فوری. یکتا با
   *  شناسهٔ سفارش: تکرار یا دستگاهِ دیگر ⇒ [Posted.Already]. کمبود ⇒ [Posted.Failed] با پیامِ
   *  روشن (همان سیاستِ صندوق) — هرگز بی‌صدا نمی‌ماند.
   */
  suspend fun post(context: Context, store: ShopStore, order: OrderDto): Posted = postLock.withLock {
    val outcome: Posted = if (!store.loaded.value) Posted.Failed("دفترِ دکان هنوز باز نشده؛ کمی بعد دوباره بزنید") else {
      val r = OrderFulfillment.fulfill(
        store.data.value, order, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(),
        series = ir.vil3ntec.tohid.sync.DocSeries.current(context),
        seller = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault(""),
        blockExpired = ShopPolicy.blockExpired(context),
      )
      when (r) {
        is OrderFulfillment.Result.Already -> Posted.Already
        is OrderFulfillment.Result.Failed -> Posted.Failed(r.message)
        is OrderFulfillment.Result.Ok ->
          if (store.save(r.data)) Posted.Saved
          else Posted.Failed("فروشِ سفارش #${order.orderNo} روی دستگاه نوشته نشد (قفلِ برنامه؟)؛ دوباره بزنید")
      }
    }
    unposted.value = when (outcome) {
      is Posted.Failed -> unposted.value.filter { it.orderId != order.id } + Unposted(order.id, order.orderNo, outcome.message)
      else -> unposted.value.filter { it.orderId != order.id }
    }
    //  §۳۲.۲: موجودیِ تازه همین حالا به سرور، ویترین و دستگاه‌های دیگر برود
    if (outcome == Posted.Saved) ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
    outcome
  }

  /**
   *  همان [post] با شناسهٔ سفارش — جزئیات (قلم، واحد، روشِ پرداخت، نوعِ تحویل) تازه از سرور.
   *  درِ ورود برای «میزِ تحویل» (`core/delivery/HandoverSale.hook`، #337): دریافتِ حضوری یا
   *  «رسید»ِ پیک همین را صدا می‌زند؛ تکرار ⇒ [Posted.Already].
   */
  suspend fun postById(context: Context, store: ShopStore, orderId: String): Posted {
    OrderFulfillment.saleOf(store.data.value, orderId)?.let { return Posted.Already }
    return when (val r = repository(context).get(orderId)) {
      is ApiResult.Success -> post(context, store, r.value)
      is ApiResult.Failure -> Posted.Failed(r.error.userMessage)
    }
  }

  /** تحویل‌های بی‌فروشِ سرور ⇒ فروش در همین دفتر. فقط با `orders.manage`؛ بی تغییرِ صف کم‌پرسش. */
  suspend fun reconcile(context: Context, store: ShopStore, force: Boolean = false) {
    val cfg = config.value ?: return
    if (!cfg.permissions.manage || !store.loaded.value) return
    val now = System.currentTimeMillis()
    if (!force && rev.value == handoverRev && now - handoverAt < HANDOVER_MS) return
    handoverRev = rev.value
    handoverAt = now
    repository(context).handover(handoverSince(context)).onSuccess { page ->
      for (o in page.items) post(context, store, o)
    }
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
  fun Host(store: ShopStore) {
    val context = LocalContext.current
    var foreground by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { foreground = true }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { foreground = false }
    LaunchedEffect(foreground) {
      if (!foreground) return@LaunchedEffect
      if (!known || System.currentTimeMillis() - configAt > 60_000) refreshConfig(context)
      pollSummary(context)
      reconcile(context, store, force = true)
      while (isActive) {
        delay(POLL_MS)
        if (System.currentTimeMillis() - configAt > CONFIG_MS) refreshConfig(context)
        pollSummary(context)
        reconcile(context, store)
      }
    }
  }
}
