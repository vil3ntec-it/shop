package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.orders.OrderStates

/**
 *  «سفارش‌های آنلاین» (خواستِ صاحب‌کار) — منطقِ خالصِ صف و کارتِ «آماده‌سازی و تحویل»؛ هر گذار همان ماشینِ وضعیتِ
 *  سرور (`OrderStates`) است و سرور دوباره می‌سنجد. بی Compose و بی شبکه (`OrdersHubModelTest`).
 */
object OrdersHubModel {

  /**
   *  برگه‌های صف به نامِ صاحب‌کار: جدید · در حالِ آماده‌سازی · آمادهٔ تحویل · در راه · تمام‌شده. «زمان‌بندی‌شده» فقط وقتی
   *  سفارشی دارد یا برگزیده است — هیچ سفارشی بی‌راه نمی‌ماند.
   */
  fun tabs(counts: Map<String, Int>, selected: String): List<String> =
    OrderStates.TABS.filter { it != SCHEDULED || (counts[it] ?: 0) > 0 || selected == it }

  /** خطِ اقلامِ کارت: نامِ تا سه قلمِ نخست و «و n قلمِ دیگر»؛ بی نام (سرورِ کهنه) ⇒ همان شمار */
  fun items(o: OrderDto): String {
    val names = o.itemsPreview.map { it.trim() }.filter { it.isNotEmpty() }
    if (names.isEmpty()) return StrOnline.items(o.itemsCount)
    return StrOrdersHub.itemsPreview(names.joinToString("، "), (o.itemsCount - names.size).coerceAtLeast(0))
  }

  fun courier(o: OrderDto): Boolean = o.fulfillment == "delivery"

  /** روشِ تحویلِ کارت: «با پیک» یا «خودش می‌آید» */
  fun method(o: OrderDto): String = if (courier(o)) StrOrdersHub.byCourier else StrOrdersHub.selfPickup

  /**
   *  کارتِ «آماده‌سازی و تحویل» ِ یک سفارش:
   *  @property photo «عکس برای مشتری» (گفت‌وگوی همین سفارش): چت روشن، شمارهٔ مشتری، سفارشِ هنوز باز
   *  @property pack گذارهای پیاپیِ «بسته شد» از همین وضعیت (قبول‌شده ⇒ جمع‌آوری ⇒ بسته؛ در جمع‌آوری ⇒ بسته)؛ null ⇒ نه
   *  @property packed بسته‌بندی شده است (برای تیکِ «بسته شد»)
   *  @property dispatch گذارِ تحویلِ پس از بسته‌بندی: `out_for_delivery` (پیک) یا `ready_for_pickup` (خودش می‌آید)
   */
  data class Prep(val photo: Boolean, val pack: List<String>?, val packed: Boolean, val dispatch: String?) {
    val isEmpty: Boolean get() = !photo && pack == null && !packed && dispatch == null
  }

  fun prep(o: OrderDto, perms: OrderPermsDto, chatOn: Boolean): Prep {
    val open = o.status in OPEN
    val photo = chatOn && open && o.customer.phoneMasked.isNotBlank()
    val can = { to: String -> to in OrderStates.actions(o.status, o.fulfillment, perms.manage, perms.refund) }
    val pack = when (o.status) {
      "accepted" -> listOf("picking", "packed").takeIf { can("picking") }
      "picking" -> listOf("packed").takeIf { can("packed") }
      else -> null
    }
    val packed = o.status in AFTER_PACK
    val dispatch = if (o.status == "packed") (if (courier(o)) "out_for_delivery" else "ready_for_pickup").takeIf(can) else null
    return Prep(photo, pack, packed, dispatch)
  }

  private const val SCHEDULED = "scheduled"
  private val OPEN = setOf("new", "accepted", "picking", "packed", "ready_for_pickup", "out_for_delivery", "delivery_failed", "retry_scheduled")
  private val AFTER_PACK = setOf("packed", "ready_for_pickup", "out_for_delivery", "delivered", "picked_up")
}
