package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.v3.online.status.OnlineStatus

/**
 *  حالتِ خالصِ جزئیاتِ سفارش (آزمون: `OrderDetailModelTest`): ردیف‌های خطِ زمانی با
 *  همان متن‌های پیشین (ثبت شد / نقد / «از ← به»، کننده، دلیل، آماده تا، یادداشت)، و
 *  پیوندِ نقشهٔ پین. هیچ چیز حساب نمی‌شود که سرور نداده باشد.
 */
object OrderDetailModel {

  data class Step(
    val title: String,
    val who: String,
    val at: Long,
    val reason: String? = null,
    val readyBy: String? = null,
    val note: String? = null,
    /** رنگِ نقطه: وضعیتِ مقصد (ثبت و نقد ⇐ null، خنثی) */
    val status: V3Status? = null,
  )

  fun timeline(o: OrderDto): List<Step> = o.events.map(::step)

  fun step(e: OrderEventDto): Step = Step(
    title = when (e.kind) {
      "created" -> StrOnline.evCreated
      "cod" -> StrOnline.evCod(StrOnline.cod(e.toStatus))
      else -> StrOnline.statusMove(StrOnline.orderStatus(e.fromStatus), StrOnline.orderStatus(e.toStatus))
    },
    who = when (e.actorType) {
      "system" -> StrOnline.actorSystem
      "customer" -> StrOnline.customer
      else -> StrOnline.actorStaff
    },
    at = e.at,
    reason = e.reason.takeIf { it.isNotEmpty() }?.let(StrOnline::orderReason),
    readyBy = e.detail.readyAt?.let(OrdersModel::stamp),
    note = e.note.takeIf { it.isNotEmpty() },
    status = if (e.kind == "created" || e.kind == "cod" || e.toStatus.isEmpty()) null else OnlineStatus.order(e.toStatus),
  )

  /** پین روی نقشه ⇐ پیوندِ `geo:` (برنامهٔ نقشهٔ گوشی)؛ بی پین ⇐ null */
  fun mapUri(o: OrderDto): String? {
    val lat = o.address.lat ?: return null
    val lng = o.address.lng ?: return null
    return "geo:$lat,$lng?q=$lat,$lng"
  }

  /** شمارهٔ کامل فقط وقتی سرور داده (`orders.contact`) */
  fun fullPhone(o: OrderDto): String? = o.customer.phone?.takeIf { o.canContact && it.isNotEmpty() }
}
