package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
 *  شماره از هر دستگاه و هر ویترین (S138).
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
    when (val r = api.myOrders()) {
      is ApiResult.Success -> flow.value = OrdersState(orders = book.merge(r.value), verified = true)
      is ApiResult.Failure -> {
        //  بلیت در سرور باطل شده (خروج از همه‌جا / پاک کردنِ حساب) ⇒ همین‌جا هم
        val revoked = r.error is ApiFailure.Unauthorized
        if (revoked) tickets.forget()
        flow.value = OrdersState(orders = book.merge(emptyList()), verified = !revoked, failure = r.error.takeUnless { revoked })
      }
    }
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
}

/**
 *  رسید و پیگیریِ یک سفارش (CHK-20/22، S136/S137). صفحه تا `live` است هر
 *  `ShopperOrders.POLL_MS` یک بار `load` را می‌زند — فقط وقتی جلوی چشم است.
 */
class ShopperTrackModel(
  private val api: ShopperApi,
  private val book: ShopperOrderBook,
  val slug: String,
  val token: String,
) {
  private val flow = MutableStateFlow(TrackState())
  val state: StateFlow<TrackState> = flow.asStateFlow()

  suspend fun load() {
    flow.update { it.copy(loading = it.order == null, failure = null) }
    when (val r = api.track(slug, token)) {
      is ApiResult.Success -> show(r.value)
      is ApiResult.Failure -> flow.update {
        val gone = r.error is ApiFailure.NotFound
        //  سفارشِ پیدا‌شده با خطای شبکهٔ بعدی پاک نمی‌شود
        it.copy(loading = false, notFound = gone && it.order == null, failure = r.error)
      }
    }
  }

  /** لغوِ رایگان تا پیش از قبولِ دکان؛ بعدش `cannot_cancel` */
  suspend fun cancel(): Boolean {
    if (flow.value.order?.canCancel != true || flow.value.cancelling) return false
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

  private fun show(order: PublicOrder) {
    book.touch(order.token, order.status)
    flow.update { it.copy(loading = false, order = order, steps = ShopperOrders.steps(order), failure = null, notFound = false) }
  }
}
