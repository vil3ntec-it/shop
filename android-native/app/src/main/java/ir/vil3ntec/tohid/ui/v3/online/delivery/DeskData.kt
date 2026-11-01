package ir.vil3ntec.tohid.ui.v3.online.delivery

import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeliveryText
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.ExternalCompany
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.OrdersRepository

/*
 *  دادهٔ میزِ تحویل و کمک‌های خالصِ فرم‌هایش — جابه‌جاشده از `ui/screens/DeliveryDesk.kt` و
 *  `DeliveryDialogs.kt` (بی هیچ تغییرِ منطق) تا صفحه‌های تازه و پیشین یک منبع داشته باشند.
 */

/** همهٔ داده‌ای که میز از سرور دارد — یک جا تا شمارش‌ها و برگه‌ها یکی باشند */
internal data class DeskData(
  val couriers: List<CourierRow> = emptyList(),
  val live: List<Mission> = emptyList(),
  val orders: List<OrderDto> = emptyList(),
  val companies: List<ExternalCompany> = emptyList(),
) {
  private val liveByOrder: Map<String, Mission> = live.associateBy { it.orderId }

  fun missionOf(orderId: String): Mission? = liveByOrder[orderId]

  /** سفارشِ ارسالیِ پذیرفته/در حالِ آماده‌سازی که هنوز مأموریتِ زنده ندارد */
  val toAssign: List<OrderDto> = orders.filter {
    it.fulfillment == "delivery" && it.status in ASSIGNABLE && it.id !in liveByOrder
  }.sortedWith(compareBy({ it.createdAt }, { it.orderNo }))

  /** سفارش‌های «دریافتِ حضوری» که هنوز تحویل نشده‌اند */
  val pickups: List<OrderDto> = orders.filter {
    it.fulfillment == "pickup" && it.status in PICKUP_OPEN
  }.sortedWith(compareBy({ it.createdAt }, { it.orderNo }))

  /** مأموریت‌های ارسال (نه دریافتِ حضوری) */
  val missions: List<Mission> = live.filter { it.mode != "pickup" }

  fun courierName(id: String): String = couriers.firstOrNull { it.id == id }?.name ?: "پیک"

  companion object {
    val ASSIGNABLE = setOf("accepted", "picking", "packed")
    val PICKUP_OPEN = setOf("accepted", "picking", "packed", "ready_for_pickup")
  }
}

/** چند صفحه از یک برگهٔ سفارش‌ها — میز سفارش‌های باز را یک‌جا می‌خواهد */
private suspend fun ordersOf(repo: OrdersRepository, tab: String, pages: Int = 4): ApiResult<List<OrderDto>> {
  val out = ArrayList<OrderDto>()
  var cursor: String? = null
  repeat(pages) {
    when (val r = repo.list(tab, cursor)) {
      is ApiResult.Failure -> return ApiResult.Failure(r.error)
      is ApiResult.Success -> {
        out += r.value.items
        cursor = r.value.nextCursor
      }
    }
    if (cursor == null) return ApiResult.Success(out)
  }
  return ApiResult.Success(out)
}

internal suspend fun loadDesk(repo: DeliveryRepository, orders: OrdersRepository, withCompanies: Boolean): ApiResult<DeskData> {
  val couriers = when (val r = repo.couriers()) { is ApiResult.Failure -> return ApiResult.Failure(r.error); is ApiResult.Success -> r.value }
  val live = when (val r = repo.board("live")) { is ApiResult.Failure -> return ApiResult.Failure(r.error); is ApiResult.Success -> r.value }
  val preparing = when (val r = ordersOf(orders, "preparing")) { is ApiResult.Failure -> return ApiResult.Failure(r.error); is ApiResult.Success -> r.value }
  val ready = when (val r = ordersOf(orders, "ready")) { is ApiResult.Failure -> return ApiResult.Failure(r.error); is ApiResult.Success -> r.value }
  //  فهرستِ شرکت‌ها کرایه دارد ⇒ فقط با `delivery.assign` (سرور هم همین را می‌خواهد)؛
  //  پیش‌فرضِ فرم است و نیامدنش میز را نمی‌بندد
  val companies = if (withCompanies) repo.companies().valueOrNull().orEmpty() else emptyList()
  return ApiResult.Success(DeskData(couriers, live, (preparing + ready).distinctBy { it.id }, companies))
}

/** «۱۲٬۵۰۰» یا «12500.5» ⇐ واحدِ کوچک؛ خالی ⇐ ۰؛ نامعتبر ⇐ null */
internal fun minorOf(text: String): Long? = if (text.isBlank()) 0L else OrderDraft.scaled(text, 2)

/** واحدِ کوچک ⇐ متنِ فیلد (بی جداکننده تا ویرایش ساده بماند) */
internal fun fieldOf(minor: Long): String =
  if (minor % 100 == 0L) (minor / 100).toString() else "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

internal fun reasonsOf(cfg: DeskConfig, key: String): List<String> =
  cfg.reasons[key]?.takeIf { it.isNotEmpty() } ?: DeliveryText.FALLBACK_REASONS[key].orEmpty()
