package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

data class OrdersState(
  val loading: Boolean = false,
  val orders: List<SavedOrder> = emptyList(),
  /** خطای خواندنِ سرور — فهرستِ همین دستگاه باز هم نشان داده می‌شود */
  val failure: ApiFailure? = null,
  /** بی بلیتِ شماره فقط سفارش‌های همین دستگاه دیده می‌شود */
  val verified: Boolean = false,
)

/**
 *  «سفارش‌های من»: سفارش‌های همین دستگاه، و با شمارهٔ تأییدشده سفارش‌های همین
 *  شماره از هر دستگاه و هر ویترین (S138). بازارچه خاموش (پیش‌فرضِ سرور) ⇒ از
 *  ویترینِ هر دکانی که این دستگاه از آن سفارش داده است.
 */
class ShopperOrdersModel(
  private val api: ShopperApi,
  private val tickets: ShopperTickets,
  private val book: ShopperOrderBook,
) {
  private val flow = MutableStateFlow(OrdersState(orders = book.merge(emptyList())))
  val state: StateFlow<OrdersState> = flow.asStateFlow()

  suspend fun load() {
    if (tickets.current() == null) {
      flow.value = OrdersState(orders = book.merge(emptyList()), verified = false)
      return
    }
    flow.update { it.copy(loading = true, failure = null) }
    var r = api.myOrders()
    if (r is ApiResult.Failure && r.error is ApiFailure.NotFound) r = perStore()
    when (r) {
      is ApiResult.Success -> flow.value = OrdersState(orders = book.merge(r.value), verified = true)
      is ApiResult.Failure -> {
        //  فقط «بلیت باطل است» (خروج از همه‌جا / پاک کردنِ حساب) بلیتِ این دستگاه را پاک می‌کند
        val revoked = revoked(r.error)
        if (revoked) tickets.forget()
        flow.value = OrdersState(orders = book.merge(emptyList()), verified = !revoked, failure = r.error.takeUnless { revoked })
      }
    }
  }

  /** «سفارش‌های من» از ویترینِ هر دکانِ دفترِ همین دستگاه؛ ویترینِ بسته کنار می‌رود */
  private suspend fun perStore(): ApiResult<List<MarketOrder>> {
    val shops = book.state.value.distinctBy { it.slug }.take(MAX_STORES)
    val out = mutableListOf<MarketOrder>()
    for (shop in shops) {
      when (val r = api.storeOrders(shop.slug)) {
        is ApiResult.Success -> out += r.value.map { it.copy(store = StoreRef(shop.slug, shop.storeTitle)) }
        is ApiResult.Failure -> if (r.error !is ApiFailure.NotFound) return r
      }
    }
    return ApiResult.Success(out)
  }

  private fun revoked(f: ApiFailure): Boolean = f is ApiFailure.Unauthorized && f.code == VERIFICATION_REQUIRED

  companion object {
    const val VERIFICATION_REQUIRED = "verification_required"

    /** سقفِ درخواست‌های جایگزین وقتی بازارچه خاموش است */
    const val MAX_STORES = 10
  }
}

data class TrackState(
  val loading: Boolean = true,
  val order: PublicOrder? = null,
  val steps: List<OrderStep> = emptyList(),
  val failure: ApiFailure? = null,
  val notFound: Boolean = false,
  val cancelling: Boolean = false,
  val cancelFailure: ApiFailure? = null,
) {
  /** پیگیریِ زنده تا وقتی سفارش تمام نشده */
  val live: Boolean get() = order != null && !ShopperOrders.isFinal(order.status)

  /** باز هم بپرس: تمام نشده، یا هنوز چیزی نیامده و «پیدا نشد» هم نیست (شبکه) */
  val keepFollowing: Boolean get() = !notFound && (order == null || live)
}

/**
 *  رسید و پیگیریِ یک سفارش (CHK-20/22، S136/S137).
 *
 *  ⛔ پاسخِ کهنه روی تازه نمی‌نشیند: سفارشی که `updatedAt` اش از آن‌چه نشان
 *  داده‌ایم قدیمی‌تر است کنار می‌رود (پرسشِ دوره‌ای که پیش از «لغو» رفته بود،
 *  «لغو شد» را به «تازه» برنمی‌گرداند) و خطای پرسشِ کهنه هم.
 */
class ShopperTrackModel(
  private val api: ShopperApi,
  private val book: ShopperOrderBook,
  val slug: String,
  val token: String,
) {
  private val flow = MutableStateFlow(TrackState())
  val state: StateFlow<TrackState> = flow.asStateFlow()

  private val gen = AtomicInteger()

  suspend fun load() {
    val mine = gen.incrementAndGet()
    flow.update { it.copy(loading = it.order == null, failure = null) }
    when (val r = api.track(slug, token)) {
      is ApiResult.Success -> show(r.value)
      is ApiResult.Failure -> if (mine == gen.get()) flow.update {
        val gone = r.error is ApiFailure.NotFound
        //  سفارشِ پیدا‌شده با خطای شبکهٔ بعدی پاک نمی‌شود
        it.copy(loading = false, notFound = gone && it.order == null, failure = r.error)
      }
    }
  }

  /**
   *  پیگیریِ زنده: همین حالا، بعد هر `ShopperOrders.POLL_MS` تا وقتی سفارش تمام
   *  نشده. صفحه این را در کوروتینِ خودش (مثلاً `LaunchedEffect`) اجرا می‌کند:
   *  صفحه که رفت، کوروتین لغو می‌شود و پرسش هم می‌ایستد.
   *  @param pause انتظار میانِ دو پرسش — آزمون‌ها ساعتِ ساختگی می‌دهند
   */
  suspend fun follow(pause: suspend (Long) -> Unit = { delay(it) }) {
    load()
    while (flow.value.keepFollowing) {
      pause(ShopperOrders.POLL_MS)
      load()
    }
  }

  /** لغوِ رایگان تا پیش از قبولِ دکان؛ بعدش `cannot_cancel` */
  suspend fun cancel(): Boolean {
    if (flow.value.order?.canCancel != true || flow.value.cancelling) return false
    gen.incrementAndGet()   // خطای پرسشِ دوره‌ایِ پیش از لغو دیگر نشان داده نمی‌شود
    flow.update { it.copy(cancelling = true, cancelFailure = null) }
    return when (val r = api.cancel(slug, token)) {
      is ApiResult.Success -> { show(r.value); flow.update { it.copy(cancelling = false) }; true }
      is ApiResult.Failure -> {
        flow.update { it.copy(cancelling = false, cancelFailure = r.error) }
        //  دکان همین حالا قبول کرده ⇒ وضعیتِ تازه را نشان بده
        if (r.error.code == "cannot_cancel") load()
        false
      }
    }
  }

  /** «خریدِ دوباره» با قیمتِ امروز ⇒ سبدِ همین دکان. @return شمارِ قلم‌های افزوده — یا null */
  suspend fun reorder(carts: ShopperCarts, shop: CartShop): Int? =
    api.reorder(slug, token).valueOrNull()?.let { carts.fillFromReorder(shop, it) }

  @Synchronized
  private fun show(order: PublicOrder) {
    val shown = flow.value.order
    if (shown != null && order.updatedAt < shown.updatedAt) return
    book.touch(order.token, order.status)
    flow.update { it.copy(loading = false, order = order, steps = ShopperOrders.steps(order), failure = null, notFound = false) }
  }
}
