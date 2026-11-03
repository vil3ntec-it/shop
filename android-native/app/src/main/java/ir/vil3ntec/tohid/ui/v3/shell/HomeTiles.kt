package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.ui.components.common.NavCatalog

/**
 *  میان‌برِ نخستِ خانه (خواستِ صاحب‌کار): «سفارش‌های آنلاین» با نشانِ زنده جای «فروشِ تازه» را می‌گیرد — فقط وقتی
 *  بخشِ سفارش برای همین دکان روشن است (`OrdersLive.enabled`)؛ وگرنه همان «فروشِ تازه» ِ پیشین (پیش‌فرضِ امن).
 *  «هیچ کاری بی‌راه نماند»: وقتی کاشیِ فروش جا داد و نوارِ پایینِ همین کاربر هم «فروش»/«اسکن» ندارد، خانه دکمهٔ
 *  شناورِ «فروشِ تازه» نشان می‌دهد ([saleFab]؛ `V3ReachabilityTest`). خالص — بی Compose و بی android.
 */
object HomeTiles {

  /** نشانِ کاشی: تازه‌ها (با نقطهٔ تپنده) بر سفارش‌های در کار جلو می‌افتند؛ هیچ ⇒ بی نشان */
  data class Orders(val fresh: Int, val active: Int) {
    val pulse: Boolean get() = fresh > 0
    val badge: Int get() = if (fresh > 0) fresh else active
    val badgeText: String? get() = badge.takeIf { it > 0 }?.let { if (it > MAX_BADGE) "$MAX_BADGE+" else it.toString() }
  }

  const val MAX_BADGE = 99

  /** شمارش‌های صفِ سرور (`summary.counts`، کلید = برگه) ⇒ نشان؛ بخشِ خاموش ⇒ null (کاشیِ فروش می‌ماند) */
  fun orders(enabled: Boolean, counts: Map<String, Int>): Orders? {
    if (!enabled) return null
    val fresh = (counts["new"] ?: 0).coerceAtLeast(0)
    val active = OrderStates.OPEN_TABS.filter { it != "new" }.sumOf { (counts[it] ?: 0).coerceAtLeast(0) }
    return Orders(fresh, active)
  }

  /** دکمهٔ شناورِ «فروشِ تازه» روی خانه: کاشیِ فروش رفته و نوار هم راهِ صندوق («فروش» یا «اسکن») ندارد */
  fun saleFab(ordersTile: Boolean, navIds: Collection<String>): Boolean =
    ordersTile && NavCatalog.SALE !in navIds && NavCatalog.SCAN !in navIds
}
