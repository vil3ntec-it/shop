package ir.vil3ntec.tohid.ui.v3.online.orders

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderDraft
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
   *  یک گذار، درست مثلِ پیش: موفق ⇐ نسخهٔ تازه؛ `status_changed` ⇐ نشانِ کهنگی و
   *  خواندنِ دوباره. شمارش‌ها هر بار از سرور دوباره پرسیده می‌شوند.
   */
  suspend fun transition(context: Context, o: OrderDto, to: String, reason: String = "", note: String = "", ready: Int? = null): ApiResult<OrderDto> {
    val r = repo(context).transition(o.id, to, o.status, reason, note, ready)
    when (r) {
      is ApiResult.Success -> { put(r.value); stale.remove(o.id) }
      is ApiResult.Failure -> if (r.error.code == "status_changed") {
        stale[o.id] = true
        fetch(context, o.id)
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
