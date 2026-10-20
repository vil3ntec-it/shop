package ir.vil3ntec.tohid.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import ir.vil3ntec.tohid.core.delivery.HandedOverOrder
import ir.vil3ntec.tohid.core.delivery.HandoverSale
import ir.vil3ntec.tohid.core.delivery.HandoverSaleHook
import ir.vil3ntec.tohid.core.delivery.HandoverSaleResult
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

  /** تحویلی که فروشش این‌جا ثبت نشد (اجازه، قفلِ برنامه، دیسک…) — صفحهٔ «سفارش‌ها» هشدار می‌دهد */
  data class Unposted(val orderId: String, val orderNo: Long, val message: String)
  val unposted = mutableStateOf<List<Unposted>>(emptyList())

  /** فروش ثبت شد ولی کالا در دفتر کم بود (موجودی منفی) — INV-19: ثبت‌شده رد نمی‌شود، فقط گفته می‌شود */
  val shortfalls = mutableStateOf<List<Unposted>>(emptyList())

  sealed interface Posted {
    data object Saved : Posted
    data object Already : Posted
    /** این نقش/سرور نمی‌تواند — گذار رفت، ثبت با دستگاهِ توانا (صاحب/مدیر) */
    data class Skipped(val message: String) : Posted
    data class Failed(val message: String) : Posted
  }

  /** فاصلهٔ پرسیدنِ دوبارهٔ تحویل‌های بی‌فروش وقتی صف عوض نشده */
  const val HANDOVER_MS = 120_000L
  /** حداکثر صفحه در هر دورِ [reconcile] (۲۰ تا در هر صفحه) */
  const val HANDOVER_PAGES = 10
  const val SKIPPED_NOTE = "فروشِ این تحویل را دستگاهی با اجازهٔ ثبتِ فروش (صاحب یا مدیر) ثبت می‌کند؛ موجودی همان‌جا کم می‌شود."

  private val postLock = Mutex()
  @Volatile private var handoverRev = Long.MIN_VALUE
  @Volatile private var handoverAt = 0L

  /** سرور «فروشِ تحویل» را دارد و نقشِ این حساب می‌تواند فروشِ [o] را بنویسد (با تخفیف ⇒ اجازهٔ تخفیف) */
  fun canRecord(cfg: OrdersConfigDto?, o: OrderDto? = null): Boolean {
    if (cfg == null || !cfg.saleOnHandover || !cfg.permissions.recordSale) return false
    return o == null || o.discountMinor <= 0 || cfg.permissions.saleDiscount
  }

  /** لغوِ فروشِ تحویلِ برگشته — Sync v1 لغو را فقط با `sales.cancel` می‌پذیرد */
  fun canReverse(cfg: OrdersConfigDto?): Boolean =
    cfg != null && cfg.saleOnHandover && cfg.permissions.saleCancel

  /** کفِ سرور برای «تحویلِ تازه» — همهٔ دستگاه‌های دکان یکی (`order-ledger.FLOOR`) */
  fun handoverSince(): Long = config.value?.handoverSince ?: Long.MAX_VALUE

  private fun isoDay(at: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
    val d = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "${c.get(java.util.Calendar.YEAR)}-$m-$d"
  }

  private fun note(list: MutableState<List<Unposted>>, o: OrderDto, message: String?) {
    val rest = list.value.filter { it.orderId != o.id }
    list.value = if (message == null) rest else rest + Unposted(o.id, o.orderNo, message)
  }

  /**
   *  کارِ یک ردیفِ `GET /shop/orders/handover` در همین دفتر — `sale`: فروشِ تحویل با زمان و روزِ
   *  همان گذار؛ `reverse`: لغوِ فروشِ سفارشِ برگشته. خواندن و نوشتن با [ShopStore.update] زیرِ
   *  قفلِ موتور (همیشه روی تازه‌ترین دفتر)، و همگام‌سازیِ فوری. فقط ردیفی که **سرور** همین حالا
   *  گفته لازم است به این‌جا می‌رسد ([postById]، [reconcile]).
   */
  private suspend fun applyItem(context: Context, store: ShopStore, item: OrderDto): Posted = postLock.withLock {
    val cfg = config.value
    val reverse = item.action == "reverse"
    if (!store.loaded.value) return@withLock Posted.Failed("دفترِ دکان هنوز باز نشده؛ کمی بعد دوباره بزنید")
    if (if (reverse) !canReverse(cfg) else !canRecord(cfg, item)) return@withLock Posted.Skipped(SKIPPED_NOTE)
    val at = (if (reverse) item.returnedAt else OrderFulfillment.handedOverAt(item)) ?: System.currentTimeMillis()
    val day = isoDay(at)
    val seller = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("")
    val blockExpired = ShopPolicy.blockExpired(context)
    var outcome: Posted = Posted.Already
    var short: List<String> = emptyList()
    val wrote = store.update { cur ->
      val r = if (reverse) OrderFulfillment.reverse(cur, item, day, at)
      else OrderFulfillment.fulfill(cur, item, day, at, seller = seller, blockExpired = blockExpired, allowShortage = true)
      when (r) {
        is OrderFulfillment.Result.Ok -> { outcome = Posted.Saved; short = r.short; r.data }
        is OrderFulfillment.Result.Already -> { outcome = Posted.Already; null }
        is OrderFulfillment.Result.Failed -> { outcome = Posted.Failed(r.message); null }
      }
    }
    if (outcome == Posted.Saved && wrote != true) {
      outcome = Posted.Failed("فروشِ سفارش #${item.orderNo} روی دستگاه نوشته نشد (قفلِ برنامه؟)؛ دوباره بزنید")
    }
    note(unposted, item, (outcome as? Posted.Failed)?.message)
    if (outcome == Posted.Saved) {
      note(shortfalls, item, if (short.isEmpty()) null else "سفارش #${item.orderNo} ثبت شد ولی موجودی منفی شد: ${short.joinToString(" · ")}")
      //  §۳۲.۲: موجودیِ تازه همین حالا به سرور، ویترین و دستگاه‌های دیگر برود
      ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
    }
    outcome
  }

  /**
   *  ثبت (یا لغو) برای یک سفارش — **پس از پرسیدن از سرور** که هنوز لازم است
   *  (`handover?orderId=`): تحویلِ پیش از کفِ سرور، سفارشی که فروشش (`o-…`/`w-…`) در دفترِ
   *  همگام‌شده هست، یا برگشته‌ای که فروشش لغو شده ⇒ هیچ. پس از گذارِ همین دستگاه، پس از
   *  «وضعیت جای دیگری عوض شد»، و دکمهٔ «ثبتِ فروش» همه از همین در می‌روند.
   *  درِ ورود برای «میزِ تحویل» (`core/delivery/HandoverSale.hook`، #337).
   */
  suspend fun postById(context: Context, store: ShopStore, orderId: String): Posted {
    val cfg = config.value ?: return Posted.Skipped(SKIPPED_NOTE)
    if (!cfg.saleOnHandover) return Posted.Skipped(SKIPPED_NOTE)
    return when (val r = repository(context).handover(cfg.handoverSince, orderId = orderId)) {
      is ApiResult.Failure -> Posted.Failed(r.error.userMessage)
      is ApiResult.Success -> {
        val item = r.value.items.firstOrNull { it.id == orderId } ?: r.value.reversals.firstOrNull { it.id == orderId }
        if (item == null) Posted.Already else applyItem(context, store, item)
      }
    }
  }

  /**
   *  قلابِ «میزِ تحویل» (#337، `core/delivery/HandoverSale`): دریافتِ حضوری ⇒ همان [postById]
   *  (پرسیدن از سرور، یکتا با شناسهٔ سفارش). یک بار در [Host]؛ با `applicationContext`.
   *  Saved ⇒ Recorded("o-<سفارش>") · Already ⇒ AlreadyRecorded · Skipped/Failed ⇒ Failed(پیام).
   */
  fun installHandoverHook(context: Context, store: ShopStore) {
    val app = context.applicationContext
    HandoverSale.hook = object : HandoverSaleHook {
      override suspend fun record(order: HandedOverOrder): HandoverSaleResult =
        when (val r = postById(app, store, order.orderId)) {
          Posted.Saved -> HandoverSaleResult.Recorded(OrderFulfillment.saleIdOf(order.orderId))
          Posted.Already -> HandoverSaleResult.AlreadyRecorded(
            OrderFulfillment.saleOf(store.data.value, order.orderId)?.id ?: OrderFulfillment.saleIdOf(order.orderId)
          )
          is Posted.Skipped -> HandoverSaleResult.Failed(r.message)
          is Posted.Failed -> HandoverSaleResult.Failed(r.message)
        }
    }
  }

  /**
   *  تحویل‌های بی‌فروش و برگشته‌های لغونشدهٔ سرور ⇒ همین دفتر. فقط وقتی سرور توانا و نقشِ این
   *  حساب توانا است؛ صفحه‌به‌صفحه (`after`) تا سفارشِ گیرکرده جلوی بقیه را نگیرد؛ بی تغییرِ صف کم‌پرسش.
   */
  suspend fun reconcile(context: Context, store: ShopStore, force: Boolean = false) {
    val cfg = config.value ?: return
    if (!cfg.permissions.manage || !cfg.saleOnHandover || !store.loaded.value) return
    if (!canRecord(cfg) && !canReverse(cfg)) return
    val now = System.currentTimeMillis()
    if (!force && rev.value == handoverRev && now - handoverAt < HANDOVER_MS) return
    handoverRev = rev.value
    handoverAt = now
    var after: String? = null
    for (page in 0 until HANDOVER_PAGES) {
      val r = repository(context).handover(cfg.handoverSince, after = after)
      val p = (r as? ApiResult.Success)?.value ?: return
      for (o in p.items) applyItem(context, store, o)
      if (page == 0) for (o in p.reversals) applyItem(context, store, o)
      after = p.nextAfter ?: return
    }
  }

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
    if (cfg == null) { counts.value = emptyMap(); rev.value = 0; unposted.value = emptyList(); shortfalls.value = emptyList() }
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
  fun Host(store: ShopStore) {
    val context = LocalContext.current
    //  میزِ تحویل همین درِ ثبت را صدا می‌زند (#337)
    LaunchedEffect(store) { installHandoverHook(context, store) }
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
