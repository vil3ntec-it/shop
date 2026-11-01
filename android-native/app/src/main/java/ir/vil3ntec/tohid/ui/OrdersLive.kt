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
import ir.vil3ntec.tohid.data.OrderNotify
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

  /**
   *  تحویلی که فروشش این‌جا ثبت نشد (اجازه، قفلِ برنامه، دیسک…) — صفحهٔ «سفارش‌ها» هشدار می‌دهد.
   *  [productIds]: فقط برای [shortfalls] — کالاهای منفی‌شده (هشدار با درست شدنِ موجودی می‌رود).
   */
  data class Unposted(
    val orderId: String,
    val orderNo: Long,
    val message: String,
    val productIds: List<String> = emptyList(),
    /** فقط برای [review]: `before_floor` یا `reverse_blocked` — همان نوعِ «بررسی شد» ِ سرور */
    val code: String = "",
  )
  val unposted = mutableStateOf<List<Unposted>>(emptyList())

  /** فروش ثبت شد ولی کالا در دفتر کم بود (موجودی منفی) — INV-19: ثبت‌شده رد نمی‌شود، فقط گفته می‌شود */
  val shortfalls = mutableStateOf<List<Unposted>>(emptyList())

  /**
   *  تحویل‌هایی که فروش یا لغوشان را **این حساب** نمی‌تواند بنویسد (پرچمِ سرورِ همان سفارش:
   *  اجازه یا سقفِ تخفیفِ همین کاربر) ⇒ منتظرِ دستگاهِ صاحب یا مدیر. فهرستِ «سفارش‌ها» می‌شماردشان.
   */
  val waiting = mutableStateOf<List<Unposted>>(emptyList())

  /**
   *  بررسیِ صاحب — کارِ خودکار انجام نشد و نباید بشود: سفارشِ پیش از کف بیرون‌رفته (`review` ِ سرور،
   *  [OrderFulfillment.REVIEW_BEFORE_FLOOR]) و برگشتهٔ دارای «مرجوعیِ» دستی ([OrderFulfillment.reverseBlock]).
   */
  val review = mutableStateOf<List<Unposted>>(emptyList())

  /**
   *  بازپرداختی که سندِ برگشتش در این دفتر ننشست (فروشِ تحویل هنوز نرسیده، یا پول به حسابِ مشتری
   *  می‌رفت) — صفحهٔ «سفارش‌ها» هشدار می‌دهد؛ دورِ بعدِ [reconcile] دوباره می‌کوشد.
   */
  val refundIssues = mutableStateOf<List<Unposted>>(emptyList())

  /** چند روز پس از کفِ دکان یادآوریِ «همهٔ دستگاه‌ها را به‌روز کنید» دیده می‌شود */
  const val ROLLOUT_NOTICE_MS = 30L * 24 * 3600 * 1000
  const val ROLLOUT_NOTE = "همهٔ دستگاه‌های دکان را به‌روز کنید؛ نسخهٔ قدیم فروشِ سفارش را دستی ثبت می‌کند"

  /**
   *  دورهٔ گذار: دستگاهی با نسخهٔ پیش از «فروشِ تحویل» هنوز فروشِ سفارش را دستی در صندوق می‌زند و
   *  این برنامه هم خودکار ⇒ دو فروش. سدِ نسخه نیست؛ فقط همین یادآوری تا [ROLLOUT_NOTICE_MS] پس از کف.
   */
  fun rolloutNotice(now: Long = System.currentTimeMillis()): Boolean {
    val since = config.value?.takeIf { it.saleOnHandover }?.handoverSince ?: return false
    return now - since < ROLLOUT_NOTICE_MS
  }

  sealed interface Posted {
    data object Saved : Posted
    data object Already : Posted
    /** این نقش/سرور نمی‌تواند — گذار رفت، ثبت با دستگاهِ توانا (صاحب/مدیر) */
    data class Skipped(val message: String) : Posted
    data class Failed(val message: String) : Posted
    /** کارِ خودکار نشد؛ صاحب باید ببیند ([review]) */
    data class Review(val message: String) : Posted
  }

  /** فاصلهٔ پرسیدنِ دوبارهٔ تحویل‌های بی‌فروش وقتی صف عوض نشده */
  const val HANDOVER_MS = 120_000L
  /** حداکثر صفحه در هر دورِ [reconcile] (۲۰ تا در هر صفحه) */
  const val HANDOVER_PAGES = 10
  const val SKIPPED_NOTE = "فروشِ این تحویل را دستگاهی با اجازهٔ ثبتِ فروش (صاحب یا مدیر) ثبت می‌کند؛ موجودی همان‌جا کم می‌شود."
  const val SKIPPED_REVERSE_NOTE = "لغوِ فروشِ این سفارشِ برگشته را دستگاهی با اجازهٔ لغوِ فروش (صاحب یا مدیر) ثبت می‌کند؛ موجودی همان‌جا برمی‌گردد."
  const val NO_FLOOR_NOTE = "سرور هنوز کفِ تحویلِ این دکان را نگفته؛ بخشِ «سفارش‌ها» را یک بار دوباره باز کنید."

  private val postLock = Mutex()
  @Volatile private var handoverRev = Long.MIN_VALUE
  @Volatile private var handoverAt = 0L

  /**
   *  سرور «فروشِ تحویل» را دارد و این حساب فروشِ [o] را می‌تواند بنویسد — ردیفِ `handover` با
   *  پرچمِ سرورِ همان سفارش (`recordable`: اجازهٔ مؤثر و سقفِ تخفیفِ همین کاربر)، وگرنه اجازهٔ
   *  کلیِ نقش ([OrderFulfillment.allowed]).
   */
  fun canRecord(cfg: OrdersConfigDto?, o: OrderDto? = null): Boolean {
    if (cfg == null || !cfg.saleOnHandover) return false
    return if (o == null) cfg.permissions.recordSale else OrderFulfillment.allowed(o.copy(action = "sale"), cfg.permissions)
  }

  /** لغوِ فروشِ تحویلِ برگشته — پرچمِ `reversible` ِ همان سفارش، وگرنه `sales.cancel` ِ نقش */
  fun canReverse(cfg: OrdersConfigDto?, o: OrderDto? = null): Boolean {
    if (cfg == null || !cfg.saleOnHandover) return false
    return if (o == null) cfg.permissions.saleCancel else OrderFulfillment.allowed(o.copy(action = "reverse"), cfg.permissions)
  }

  /** سندِ برگشتِ بازپرداخت — پرچمِ `refundable` ِ همان سفارش (`returns.create`)، وگرنه نوشتنِ فروش */
  fun canRefund(cfg: OrdersConfigDto?, o: OrderDto): Boolean {
    if (cfg == null || !cfg.saleOnHandover) return false
    return OrderFulfillment.refundAllowed(o, cfg.permissions)
  }

  /** این سفارش منتظرِ دستگاهِ صاحب یا مدیر است (این حساب نمی‌تواند) */
  fun isWaiting(orderId: String): Boolean = waiting.value.any { it.orderId == orderId }

  /** کفِ همین دکان روی سرور (`order-ledger.claimFloor`) — نیامده ⇒ هیچ تحویلی «تازه» نیست */
  fun handoverSince(): Long = config.value?.handoverSince ?: Long.MAX_VALUE

  private fun isoDay(at: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
    val d = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "${c.get(java.util.Calendar.YEAR)}-$m-$d"
  }

  private fun note(
    list: MutableState<List<Unposted>>,
    o: OrderDto,
    message: String?,
    productIds: List<String> = emptyList(),
    code: String = "",
  ) {
    val rest = list.value.filter { it.orderId != o.id }
    list.value = if (message == null) rest else rest + Unposted(o.id, o.orderNo, message, productIds, code)
  }

  /**
   *  «بررسی شد» ِ صاحب یا مدیر: سرور آن را در دفترِ ممیزی می‌نشاند و ردیف از فهرستِ **همهٔ**
   *  دستگاه‌ها می‌رود (دیگر هیچ کارِ خودکاری برایش نیست). ⇒ null یا پیامِ خطا.
   */
  suspend fun ackReview(context: Context, orderId: String): String? {
    val row = review.value.firstOrNull { it.orderId == orderId } ?: return null
    return when (val r = repository(context).reviewAck(orderId, row.code.ifBlank { "before_floor" })) {
      is ApiResult.Failure -> r.error.userMessage
      is ApiResult.Success -> {
        review.value = review.value.filter { it.orderId != orderId }
        null
      }
    }
  }

  /**
   *  هشدارهایی که دیگر راست نیستند: «ثبت نشد» وقتی فروشِ همان سفارش (از هر دستگاه) در دفتر آمد،
   *  «کمبود» وقتی موجودیِ آن کالاها دیگر منفی نیست (ورودِ انبار از هر دستگاه)، «منتظر» وقتی فروش آمد.
   */
  fun prune(d: ir.vil3ntec.tohid.data.ShopData) {
    unposted.value = unposted.value.filterNot { OrderFulfillment.settled(d, it.orderId) }
    waiting.value = waiting.value.filterNot { w ->
      if (w.message.endsWith(REVERSE_WAIT)) OrderFulfillment.saleOf(d, w.orderId)?.status == "cancelled"
      else if (w.message.endsWith(REFUND_WAIT)) OrderFulfillment.refundedKeys(d, w.orderId).isNotEmpty()
      else OrderFulfillment.settled(d, w.orderId)
    }
    refundIssues.value = refundIssues.value.filterNot { OrderFulfillment.refundedKeys(d, it.orderId).isNotEmpty() }
    shortfalls.value = shortfalls.value.filter { OrderFulfillment.stillShort(d, it.productIds) }
  }

  private const val SALE_WAIT = "ثبتِ فروشِ تحویل"
  private const val REVERSE_WAIT = "لغوِ فروشِ برگشته"
  private const val REFUND_WAIT = "سندِ برگشتِ بازپرداخت"
  const val SKIPPED_REFUND_NOTE = "سندِ برگشتِ این بازپرداخت را دستگاهی با اجازهٔ برگشتِ کالا (صاحب یا مدیر) ثبت می‌کند؛ گزارش و صندوق همان‌جا درست می‌شوند."

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
    if (item.action == "refund") {
      val by = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("")
      return@withLock applyRefund(store, cfg, item, by).also { if (it == Posted.Saved) ir.vil3ntec.tohid.sync.AutoSync.now(context, store) }
    }
    if (if (reverse) !canReverse(cfg, item) else !canRecord(cfg, item)) {
      //  پرچمِ سرورِ همین سفارش برای همین کاربر: منتظرِ دستگاهِ صاحب یا مدیر (فهرست می‌شماردش)
      note(waiting, item, "سفارش #${item.orderNo} — " + if (reverse) REVERSE_WAIT else SALE_WAIT)
      return@withLock Posted.Skipped(if (reverse) SKIPPED_REVERSE_NOTE else SKIPPED_NOTE)
    }
    note(waiting, item, null)
    val at = (if (reverse) item.returnedAt else OrderFulfillment.handedOverAt(item)) ?: System.currentTimeMillis()
    val day = isoDay(at)
    val seller = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("")
    val blockExpired = ShopPolicy.blockExpired(context)
    var outcome: Posted = Posted.Already
    var short: List<String> = emptyList()
    var shortIds: List<String> = emptyList()
    val wrote = store.update { cur ->
      val r = if (reverse) OrderFulfillment.reverse(cur, item, day, at)
      else OrderFulfillment.fulfill(cur, item, day, at, seller = seller, blockExpired = blockExpired, allowShortage = true)
      when (r) {
        is OrderFulfillment.Result.Ok -> { outcome = Posted.Saved; short = r.short; shortIds = r.shortIds; r.data }
        is OrderFulfillment.Result.Already -> { outcome = Posted.Already; null }
        is OrderFulfillment.Result.Failed -> { outcome = Posted.Failed(r.message); null }
        is OrderFulfillment.Result.Review -> { outcome = Posted.Review(r.message); null }
      }
    }
    note(review, item, (outcome as? Posted.Review)?.message, code = "reverse_blocked")
    if (outcome == Posted.Saved && wrote != true) {
      outcome = Posted.Failed("فروشِ سفارش #${item.orderNo} روی دستگاه نوشته نشد (قفلِ برنامه؟)؛ دوباره بزنید")
    }
    note(unposted, item, (outcome as? Posted.Failed)?.message)
    if (outcome == Posted.Saved) {
      note(shortfalls, item, if (short.isEmpty()) null else "سفارش #${item.orderNo} ثبت شد ولی موجودی منفی شد: ${short.joinToString(" · ")}", shortIds)
      //  §۳۲.۲: موجودیِ تازه همین حالا به سرور، ویترین و دستگاه‌های دیگر برود
      ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
    }
    outcome
  }

  /**
   *  بازپرداختِ سفارشِ تحویل‌شده ⇒ سندِ برگشتِ همان فروش ([OrderFulfillment.refund]) با زمان و روزِ
   *  همان بازپرداخت. کالا دست نمی‌خورد: «کالا به انبار برگشت؟» جداست ([restock]). زیرِ [postLock] (از [applyItem]).
   */
  private suspend fun applyRefund(store: ShopStore, cfg: OrdersConfigDto?, item: OrderDto, by: String): Posted {
    if (!canRefund(cfg, item)) {
      note(waiting, item, "سفارش #${item.orderNo} — $REFUND_WAIT")
      return Posted.Skipped(SKIPPED_REFUND_NOTE)
    }
    note(waiting, item, null)
    var outcome: Posted = Posted.Already
    val wrote = store.update { cur ->
      when (val r = OrderFulfillment.refund(cur, item, by) { isoDay(it) }) {
        is OrderFulfillment.Result.Ok -> { outcome = Posted.Saved; r.data }
        is OrderFulfillment.Result.Already -> null
        is OrderFulfillment.Result.Failed -> { outcome = Posted.Failed(r.message); null }
        is OrderFulfillment.Result.Review -> { outcome = Posted.Review(r.message); null }
      }
    }
    if (outcome == Posted.Saved && wrote != true) {
      outcome = Posted.Failed("سندِ برگشتِ سفارش #${item.orderNo} روی دستگاه نوشته نشد (قفلِ برنامه؟)؛ دوباره می‌کوشد")
    }
    note(refundIssues, item, (outcome as? Posted.Failed)?.message)
    return outcome
  }

  /** «کالا به انبار برگشت؟» ِ بازپرداخت‌های همین سفارش — فقط [chosen] به موجودی ⇒ null یا پیامِ خطا */
  suspend fun restock(context: Context, store: ShopStore, orderId: String, orderNo: Long, chosen: Set<Int>): String? = postLock.withLock {
    if (!store.loaded.value) return@withLock "دفترِ دکان هنوز باز نشده؛ کمی بعد دوباره بزنید"
    val by = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("")
    var error: String? = null
    val now = System.currentTimeMillis()
    val wrote = store.update { cur ->
      when (val r = OrderFulfillment.restock(cur, orderId, orderNo, chosen, by, isoDay(now), now)) {
        is OrderFulfillment.Result.Ok -> r.data
        is OrderFulfillment.Result.Failed -> { error = r.message; null }
        else -> null
      }
    }
    if (error == null && wrote == true) ir.vil3ntec.tohid.sync.AutoSync.now(context, store)
    error
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
    val since = cfg.handoverSince ?: return Posted.Skipped(NO_FLOOR_NOTE)
    return when (val r = repository(context).handover(since, orderId = orderId)) {
      is ApiResult.Failure -> Posted.Failed(r.error.userMessage)
      is ApiResult.Success -> {
        val item = r.value.items.firstOrNull { it.id == orderId } ?: r.value.reversals.firstOrNull { it.id == orderId }
          ?: r.value.refunds.firstOrNull { it.id == orderId }
        val held = r.value.review.firstOrNull { it.id == orderId }
        when {
          item != null -> applyItem(context, store, item)
          //  پیش از کف بیرون رفت ⇒ هرگز خودکار؛ صاحب می‌بیند
          held != null -> reviewOf(held)
          else -> Posted.Already
        }
      }
    }
  }

  /** ردیفِ `review` ِ سرور ⇒ فهرستِ بررسیِ صاحب، با دلیلِ همان کد */
  private fun reviewOf(o: OrderDto): Posted {
    val msg = "سفارش #${o.orderNo} — " + when (o.reviewCode) {
      "before_floor" -> OrderFulfillment.REVIEW_BEFORE_FLOOR
      else -> "کارِ خودکار نشد؛ بررسی کنید"
    }
    note(review, o, msg, code = o.reviewCode ?: "before_floor")
    return Posted.Review(msg)
  }

  /**
   *  قلابِ «میزِ تحویل» (#337، `core/delivery/HandoverSale`): دریافتِ حضوری ⇒ همان [postById]
   *  (پرسیدن از سرور، یکتا با شناسهٔ سفارش). یک بار در [Host]؛ با `applicationContext`.
   *  Review ⇒ NeedsReview(دلیل) · Saved ⇒ Recorded("o-<سفارش>") · Already ⇒ AlreadyRecorded · Skipped/Failed ⇒ Failed(پیام).
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
          //  بررسیِ صاحب: بی وعدهٔ «دوباره می‌کوشد»
          is Posted.Review -> HandoverSaleResult.NeedsReview(r.message)
        }
    }
  }

  /**
   *  تحویل‌های بی‌فروش و برگشته‌های لغونشدهٔ سرور ⇒ همین دفتر؛ هر ردیف با پرچمِ سرورِ خودش
   *  (`recordable`/`reversible`) — آن‌چه این حساب نمی‌تواند در [waiting] می‌ماند تا فهرست بشماردش
   *  (پس حتی دستگاهی که هیچ فروشی نمی‌نویسد هم می‌پرسد). صفحه‌به‌صفحه (`after`) تا سفارشِ گیرکرده
   *  جلوی بقیه را نگیرد؛ بی تغییرِ صف کم‌پرسش. دورِ کامل ⇒ [waiting] ِ تازه (ثبت‌شده‌ها جای دیگر می‌روند).
   */
  suspend fun reconcile(context: Context, store: ShopStore, force: Boolean = false) {
    val cfg = config.value ?: return
    if (!cfg.permissions.manage || !cfg.saleOnHandover || !store.loaded.value) return
    val since = cfg.handoverSince ?: return
    prune(store.data.value)
    val now = System.currentTimeMillis()
    if (!force && rev.value == handoverRev && now - handoverAt < HANDOVER_MS) return
    handoverRev = rev.value
    handoverAt = now
    val left = HashSet<String>()
    val held = HashSet<String>()
    val listed = HashSet<String>()
    var after: String? = null
    for (page in 0 until HANDOVER_PAGES) {
      val r = repository(context).handover(since, after = after)
      val p = (r as? ApiResult.Success)?.value ?: return
      for (o in p.items) {
        listed += o.id
        if (applyItem(context, store, o) is Posted.Skipped) left += o.id
      }
      if (page == 0) {
        for (o in p.reversals) {
          listed += o.id
          when (applyItem(context, store, o)) {
            is Posted.Skipped -> left += o.id
            is Posted.Review -> held += o.id
            else -> Unit
          }
        }
        for (o in p.review) { reviewOf(o); held += o.id }
        //  بازپرداختِ سفارشِ دارای فروش ⇒ سندِ برگشت (روز و صندوقِ همان بازپرداخت)
        for (o in p.refunds) {
          listed += o.id
          if (applyItem(context, store, o) is Posted.Skipped) left += o.id
        }
      }
      after = p.nextAfter
      if (after == null) {
        //  همهٔ صفحه‌ها دیده شد ⇒ آن‌چه دیگر در فهرستِ سرور نیست (جای دیگر ثبت شد) می‌رود
        waiting.value = waiting.value.filter { it.orderId in left }
        review.value = review.value.filter { it.orderId in held }
        unposted.value = unposted.value.filter { it.orderId in listed }
        refundIssues.value = refundIssues.value.filter { it.orderId in listed }
        prune(store.data.value)
        return
      }
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
    if (cfg != null) deferredOpen?.let { deferredOpen = null; openOrder(it) }
    if (cfg == null) {
      counts.value = emptyMap(); rev.value = 0
      unposted.value = emptyList(); shortfalls.value = emptyList(); waiting.value = emptyList(); review.value = emptyList()
      refundIssues.value = emptyList()
      OrderAlertsLive.reset()
    }
  }

  suspend fun pollSummary(context: Context) {
    if (!enabled) return
    repository(context).summary()
      .onSuccess {
        counts.value = it.counts
        rev.value = it.rev
        OrderAlertsLive.onSummary(context, it)   // ORD-18 هشدارِ زمان‌بندی‌شده، ORD-03 «وصل است»
      }
      .onFailure { OrderAlertsLive.onFailure(it); if (it.code == "orders_disabled") refreshConfig(context) }
  }

  /** باز کردنِ یک سفارش با شناسه (برای اعلان)؛ اگر بخش خاموش است، هیچ */
  fun openOrder(id: String) {
    if (!enabled || id.isBlank()) return
    pendingOrderId.value = id
    AppNav.go("orders")
  }

  /** سفارشِ اعلانِ لمس‌شده (M6) — اگر «روشن است؟» هنوز نرسیده، پس از رسیدنش باز می‌شود */
  @Volatile private var deferredOpen: String? = null

  fun openFromNotice(id: String) {
    if (id.isBlank()) return
    if (enabled) openOrder(id) else deferredOpen = id
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
      //  M6: رسیدِ «باز شد» ِ اعلان‌های لمس‌شده و اعلانِ سفارشِ تازه (پرچمِ `shop.order_notify`)
      if (enabled) OrderNotify.tick(context)
      while (isActive) {
        delay(POLL_MS)
        if (System.currentTimeMillis() - configAt > CONFIG_MS) refreshConfig(context)
        pollSummary(context)
        reconcile(context, store)
        if (enabled) OrderNotify.tick(context)
      }
    }
  }
}
