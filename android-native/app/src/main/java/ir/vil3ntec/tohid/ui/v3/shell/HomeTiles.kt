package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.ui.components.common.NavCatalog

/**
 *  میان‌برِ نخستِ خانه (خواستِ صاحب‌کار، ۱۴۰۵/۰۸/۱۲: «فروشِ تازه را بردار و سفارش‌های آنلاین را بگذار»): «سفارش‌های
 *  آنلاین» **همیشه** جای «فروشِ تازه» است. بخشِ سفارش برای همین دکان روشن (`OrdersLive.enabled`) ⇒ نشانِ زنده و صف؛
 *  خاموش ⇒ همان کاشی کم‌رنگ و بی نشان ([Orders.off]) که «تنظیمات ← سفارشِ آنلاین» («چرا نمی‌رسد؟» و راهِ روشن کردن) را
 *  باز می‌کند — پیش‌تر خاموشی کاشی را بی‌صدا به «فروشِ تازه» برمی‌گرداند و صاحب‌کار تغییری نمی‌دید.
 *  «هیچ کاری بی‌راه نماند»: وقتی کاشیِ فروش جا داد و نوارِ پایینِ همین کاربر هم «فروش»/«اسکن» ندارد، خانه دکمهٔ
 *  شناورِ «فروشِ تازه» نشان می‌دهد ([saleFab]؛ `V3ReachabilityTest`). خالص — بی Compose و بی android.
 */
object HomeTiles {

  /** نشانِ کاشی: تازه‌ها (با نقطهٔ تپنده) بر سفارش‌های در کار جلو می‌افتند؛ هیچ ⇒ بی نشان. [off]: بخش برای دکان خاموش است */
  data class Orders(val fresh: Int, val active: Int, val off: Boolean = false) {
    val pulse: Boolean get() = fresh > 0
    val badge: Int get() = if (fresh > 0) fresh else active
    val badgeText: String? get() = badge.takeIf { it > 0 }?.let { if (it > MAX_BADGE) "$MAX_BADGE+" else it.toString() }
  }

  const val MAX_BADGE = 99

  /** شمارش‌های صفِ سرور (`summary.counts`، کلید = برگه) ⇒ نشان؛ بخشِ خاموش ⇒ کاشیِ خاموش (شمارِ کهنه دیده نمی‌شود) */
  fun orders(enabled: Boolean, counts: Map<String, Int>): Orders {
    if (!enabled) return OFF
    val fresh = (counts["new"] ?: 0).coerceAtLeast(0)
    val active = OrderStates.OPEN_TABS.filter { it != "new" }.sumOf { (counts[it] ?: 0).coerceAtLeast(0) }
    return Orders(fresh, active)
  }

  private val OFF = Orders(0, 0, off = true)

  /** نامِ دسترس‌پذیرِ کاشی: خاموش ⇒ «هنوز روشن نیست»؛ شمارِ تازه‌ها، بی تازه شمارِ در-کارها، هیچ ⇒ همان نام */
  fun a11y(o: Orders): String = when {
    o.off -> StrOrdersHub.tileOff
    o.fresh > 0 -> StrOrdersHub.tileFresh(o.fresh)
    o.active > 0 -> StrOrdersHub.tileActive(o.active)
    else -> StrOrdersHub.tile
  }

  /** دکمهٔ شناورِ «فروشِ تازه» روی خانه: کاشیِ فروش رفته و نوار هم راهِ صندوق («فروش» یا «اسکن») ندارد */
  fun saleFab(ordersTile: Boolean, navIds: Collection<String>): Boolean =
    ordersTile && NavCatalog.SALE !in navIds && NavCatalog.SCAN !in navIds
}
