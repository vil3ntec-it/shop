package ir.vil3ntec.tohid.core.shopper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** پلهٔ پیشرفتِ سفارش برای خریدار — برچسبش را صفحه از جدولِ زبان می‌گیرد. */
enum class OrderStage { PLACED, ACCEPTED, PREPARING, READY_FOR_PICKUP, ON_THE_WAY, PICKED_UP, DELIVERED, STOPPED }

enum class StepState { DONE, NOW, TODO, STOP }

/** یک پله: کی رسید (اگر رسید) و الان کجاست؛ `status` فقط برای پلهٔ «پایان» (رد/لغو) */
data class OrderStep(val stage: OrderStage, val state: StepState, val at: Long?, val status: String = "")

/**
 *  منطقِ خالصِ پیگیریِ سفارش — هم‌زادِ `K.orderSteps` ِ وب (`server/public/kit/kit.js`)
 *  تا خریدار در برنامه و وب یک چیز ببیند.
 */
object ShopperOrders {

  /** پایان‌یافته: پیگیریِ زنده می‌ایستد (همان `FINAL` ِ ویترینِ وب) */
  val FINAL: Set<String> = setOf(
    "rejected", "auto_rejected", "cancelled_by_customer", "cancelled", "delivered", "picked_up",
    "restocked", "refunded_full", "refunded_partial",
  )

  /** سفارشی که به خریدار نرسید — پله‌های بعدی نیست، فقط همان پایان */
  val STOP: Set<String> = setOf("rejected", "auto_rejected", "cancelled_by_customer", "cancelled", "returned_to_store", "restocked")

  /** پیگیریِ زنده هر بیست ثانیه، فقط جلوی چشم و تا سفارش تمام نشده */
  const val POLL_MS = 20_000L

  fun isFinal(status: String): Boolean = status in FINAL

  private data class Stage(val stage: OrderStage, val keys: List<String>)

  fun steps(order: PublicOrder): List<OrderStep> {
    val pickup = order.fulfillment == ShopperCheckout.PICKUP
    val after = listOf("return_requested", "refunded_partial", "refunded_full")
    val stages = listOf(
      Stage(OrderStage.PLACED, listOf("new")),
      Stage(OrderStage.ACCEPTED, listOf("accepted")),
      Stage(OrderStage.PREPARING, listOf("picking", "packed")),
      if (pickup) Stage(OrderStage.READY_FOR_PICKUP, listOf("ready_for_pickup"))
      else Stage(OrderStage.ON_THE_WAY, listOf("out_for_delivery", "delivery_failed", "retry_scheduled")),
      if (pickup) Stage(OrderStage.PICKED_UP, listOf("picked_up") + after)
      else Stage(OrderStage.DELIVERED, listOf("delivered") + after),
    )
    val at = order.timeline.associate { it.status to it.at }
    val stopped = order.status in STOP
    var idx = 0
    stages.forEachIndexed { i, s -> if (s.keys.any { at.containsKey(it) || order.status == it }) idx = maxOf(idx, i) }
    val out = stages.mapIndexed { i, s ->
      val time = s.keys.lastOrNull { at.containsKey(it) }?.let { at[it] }
      val state = when {
        i < idx -> StepState.DONE
        i == idx && (stopped || order.status == "delivered" || order.status == "picked_up") -> StepState.DONE
        i == idx -> StepState.NOW
        else -> StepState.TODO
      }
      OrderStep(s.stage, state, time)
    }
    if (!stopped) return out
    return out.take(idx + 1) + OrderStep(OrderStage.STOPPED, StepState.STOP, at[order.status] ?: order.updatedAt, order.status)
  }
}

/** یک سفارشِ همین دستگاه — برای «سفارش‌های من» حتی بی تأییدِ شماره */
@Serializable
data class SavedOrder(
  val slug: String,
  val token: String,
  val orderNo: Long = 0,
  val storeTitle: String = "",
  val totalMinor: Long = 0,
  val createdAt: Long = 0,
  /** آخرین وضعیتی که دیده شد */
  val status: String = "",
)

/**
 *  سفارش‌های ثبت‌شده از همین دستگاه (همان `store-orders` ِ وب، ولی برای همهٔ
 *  دکان‌ها). با بلیتِ شماره، «سفارش‌های من» ِ سرور هم رویش می‌آید (`merge`).
 */
class ShopperOrderBook(private val storage: ShopperStorage) {
  private val serializer = ListSerializer(SavedOrder.serializer())
  private val book = MutableStateFlow(storage.load(ShopperStorage.ORDERS, serializer).orEmpty())

  val state: StateFlow<List<SavedOrder>> = book.asStateFlow()

  @Synchronized
  fun remember(slug: String, storeTitle: String, order: PublicOrder) {
    val entry = SavedOrder(slug, order.token, order.orderNo, storeTitle, order.totalMinor, order.createdAt, order.status)
    write(listOf(entry) + book.value.filterNot { it.token == order.token })
  }

  /** وضعیتِ تازه‌ای که پیگیری دید */
  @Synchronized
  fun touch(token: String, status: String) {
    if (book.value.none { it.token == token && it.status != status }) return
    write(book.value.map { if (it.token == token) it.copy(status = status) else it })
  }

  /** سفارش‌های همین دستگاه + سفارش‌های همین شماره از سرور؛ تازه‌ترین اول، بی تکرار */
  fun merge(server: List<MarketOrder>): List<SavedOrder> {
    val fromServer = server.filter { it.token.isNotBlank() && it.store.slug.isNotBlank() }.map {
      SavedOrder(it.store.slug, it.token, it.orderNo, it.store.title, it.totalMinor, it.createdAt, it.status)
    }
    val seen = fromServer.map { it.token }.toSet()
    return (fromServer + book.value.filterNot { it.token in seen }).sortedByDescending { it.createdAt }
  }

  private fun write(list: List<SavedOrder>) {
    val capped = list.take(MAX)
    book.value = capped
    storage.save(ShopperStorage.ORDERS, serializer, capped)
  }

  private companion object {
    const val MAX = 50
  }
}
