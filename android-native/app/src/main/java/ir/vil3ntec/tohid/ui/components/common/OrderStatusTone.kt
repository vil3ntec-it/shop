package ir.vil3ntec.tohid.ui.components.common

/**
 *  وضعیتِ سفارش و مأموریتِ پیک ⇐ یکی از هفت نشانِ VILL3N (§۸).
 *
 *  «وضعیتِ سفارش همه‌جا یک معنا دارد» — صفحهٔ سفارش‌ها، پیک، میزِ تحویل و ویترین
 *  همین دو تابع را صدا می‌زنند تا «در مسیر» در هیچ صفحه‌ای رنگِ دیگری نگیرد.
 *  فقط رنگ است: منطقِ وضعیت (`core/orders/OrderStates.kt`) دست نمی‌خورد.
 *  قرمز فقط برای لغو و رد است؛ «تازه» در انتظارِ تأییدِ دکان است (کهربایی).
 */
object OrderStatusTone {

  /** وضعیتِ سفارش (`OrderStates`) */
  fun order(status: String): PillTone = when (status) {
    "new", "return_requested", "scheduled" -> PillTone.WARNING
    "accepted", "picking", "packed", "preparing", "ready", "ready_for_pickup" -> PillTone.PREPARING
    "out_for_delivery", "on_the_way", "retry_scheduled" -> PillTone.INFO
    "delivered", "picked_up", "done" -> PillTone.SUCCESS
    "rejected", "auto_rejected", "cancelled", "cancelled_by_customer" -> PillTone.DANGER
    "delivery_failed", "returned_to_store" -> PillTone.REVIEW
    "restocked", "refunded_partial", "refunded_full" -> PillTone.NEUTRAL
    else -> PillTone.NEUTRAL
  }

  /** وضعیتِ مأموریتِ پیک (`CourierHome`) — همان معناها با نام‌های پیک */
  fun courier(status: String): PillTone = when (status) {
    "requested" -> PillTone.WARNING
    "assigned" -> PillTone.PREPARING
    "picked_up", "moving", "arrived" -> PillTone.INFO
    "delivered" -> PillTone.SUCCESS
    "rejected" -> PillTone.DANGER
    "failed", "returned" -> PillTone.REVIEW
    "withdrawn" -> PillTone.NEUTRAL
    else -> PillTone.NEUTRAL
  }
}
