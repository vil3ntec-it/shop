package ir.vil3ntec.tohid.ui.v3.online.orders

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.OrdersLive

/**
 *  حالِ مشترکِ مسیرهای سفارش (`orders`، `orders/{id}`، `orders/{id}/action`):
 *  آخرین نسخهٔ هر سفارش که دیده شد، و نشانِ «وضعیت جای دیگری عوض شد». صفحه و
 *  برگه‌ای که روی آن باز است هر دو از همین می‌خوانند، پس پس از هر گذار هر دو
 *  همان وضعیتِ تازه را نشان می‌دهند. منطقِ سفارش همان `OrdersRepository` است.
 */
internal object OrdersHub {
  val known = mutableStateMapOf<String, OrderDto>()
  val stale = mutableStateMapOf<String, Boolean>()
  /** با هر گذار یا سفارشِ تازه یکی بالا می‌رود ⇒ صف دوباره می‌خواند */
  val changed = mutableStateOf(0)

  fun put(o: OrderDto) { known[o.id] = o }

  fun repo(context: Context): OrdersRepository = OrdersLive.repository(context)

  suspend fun fetch(context: Context, id: String): ApiResult<OrderDto> =
    repo(context).get(id).onSuccess { put(it) }

  /**
   *  تحویل ⇒ فروش در همین دفتر (`OrderFulfillment`، همان سیاستِ موجودیِ صندوق): پیش از فرستادنِ
   *  گذار سنجیده می‌شود؛ کمبود ⇒ پیامِ روشن و گذار نمی‌رود، نه سفارشِ «تحویل‌شده» بی فروش.
   */
  fun preflight(context: Context, store: ShopStore, o: OrderDto, to: String): String? {
    val ledger = store.data.value
    if (to !in OrderFulfillment.HANDED_OVER || o.isTest || !OrdersLive.canRecord(OrdersLive.config.value, o)) return null
    if (OrderFulfillment.saleOf(ledger, o.id) != null) return null
    val dry = OrderFulfillment.fulfill(
      ledger, o, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), blockExpired = ShopPolicy.blockExpired(context),
    )
    return (dry as? OrderFulfillment.Result.Failed)?.let { StrOnline.handoverBlocked(it.message) }
  }

  /**
   *  فروشِ تحویل (یا لغوِ آن برای سفارشِ برگشته) — همیشه پس از پرسیدن از سرور
   *  (`OrdersLive.postById`)، یکتا با شناسهٔ سفارش. پیامی برای گفتن، یا null (پیش‌تر ثبت شده).
   */
  suspend fun postSale(context: Context, store: ShopStore, o: OrderDto): String? =
    when (val r = OrdersLive.postById(context, store, o.id)) {
      OrdersLive.Posted.Saved -> StrOnline.ffSaved(o.orderNo, o.status in OrderFulfillment.BACK_IN_SHOP)
      OrdersLive.Posted.Already -> null
      is OrdersLive.Posted.Skipped -> r.message
      is OrdersLive.Posted.Failed -> r.message
      is OrdersLive.Posted.Review -> r.message
    }

  /** «بررسی شد» ِ صاحب برای ردیفِ بررسیِ همین سفارش — برای همهٔ دستگاه‌ها */
  suspend fun ackReview(context: Context, o: OrderDto): String =
    OrdersLive.ackReview(context, o.id) ?: StrOnline.ffAcked(o.orderNo)

  /**
   *  یک گذار، درست مثلِ پیش: موفق ⇐ نسخهٔ تازه؛ `status_changed` ⇐ نشانِ کهنگی و
   *  خواندنِ دوباره. شمارش‌ها هر بار از سرور دوباره پرسیده می‌شوند. پس از گذار (یا اگر دستگاهِ
   *  دیگر یا پیک تحویل داد) فروشِ تحویل همین‌جا ثبت یا لغو می‌شود و پیامش به [say] می‌رود.
   */
  suspend fun transition(
    context: Context, store: ShopStore, o: OrderDto, to: String, reason: String = "", note: String = "", ready: Int? = null,
    say: (String) -> Unit = {},
  ): ApiResult<OrderDto> {
    val r = repo(context).transition(o.id, to, o.status, reason, note, ready)
    when (r) {
      is ApiResult.Success -> {
        put(r.value)
        stale.remove(o.id)
        val n = r.value
        if (n.status in OrderFulfillment.HANDED_OVER || n.status in OrderFulfillment.BACK_IN_SHOP) postSale(context, store, n)?.let(say)
      }
      is ApiResult.Failure -> if (r.error.code == "status_changed") {
        stale[o.id] = true
        fetch(context, o.id).onSuccess { n ->
          val ledger = store.data.value
          if (OrderFulfillment.needsSale(ledger, n, OrdersLive.handoverSince()) || OrderFulfillment.needsReverse(ledger, n)) postSale(context, store, n)?.let(say)
        }
      }
    }
    changed.value++
    OrdersLive.pollSummary(context)
    return r
  }

  suspend fun create(context: Context, key: String, body: OrderDraft.Body): ApiResult<OrderDto> =
    repo(context).create(key, body).onSuccess {
      put(it)
      changed.value++
      OrdersLive.pollSummary(context)
    }
}
