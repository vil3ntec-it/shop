package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.core.orders.OrderText

/**
 *  حالتِ خالصِ صفِ سفارش‌ها و نوارِ کارِ هر سفارش — بی Compose، بی شبکه
 *  (آزمون: `OrdersModelTest`). دکمه‌ها فقط از `OrderStates.actions` می‌آیند؛
 *  این‌جا فقط چیده می‌شوند (کدام اصلی، کدام خطر، کدام برگه می‌خواهد).
 */
object OrdersModel {

  /** کارهای مجازِ یک سفارش برای این کاربر، چیده برای نوارِ پایین */
  data class Bar(val primary: String?, val danger: List<String>, val others: List<String>) {
    val isEmpty: Boolean get() = primary == null && danger.isEmpty() && others.isEmpty()
  }

  fun bar(o: OrderDto, perms: OrderPermsDto): Bar {
    val all = OrderStates.actions(o.status, o.fulfillment, perms.manage, perms.refund)
    val forward = all.filter { it !in OrderText.DANGER }
    return Bar(forward.firstOrNull(), all.filter { it in OrderText.DANGER }, forward.drop(1))
  }

  /** دکمهٔ کارتِ صف: گامِ بعدیِ رو به جلو (نه رد/لغو) */
  fun nextStep(o: OrderDto, perms: OrderPermsDto): String? = bar(o, perms).primary

  /** گذاری که پیش از فرستادن چیزی می‌پرسد (دلیل، زمانِ آماده، تأییدِ بازپرداخت) */
  fun needsSheet(to: String): Boolean =
    OrderStates.needsReason(to) || to == "accepted" || to in OrderStates.MONEY_TARGETS

  fun danger(to: String): Boolean = to in OrderText.DANGER

  /** برگهٔ آغازینِ صف از پیوند (`orders?tab=`)؛ ناشناخته ⇐ «جدید» */
  fun initialTab(query: String?): String = query?.takeIf { it in OrderStates.TABS } ?: "new"

  /**
   *  سفارش‌های تازه‌رسیده برای درخششِ «در انتظارِ تأیید»: فقط پس از بارِ نخست (پیش از آن
   *  همه «تازه»اند) و فقط آنچه در فهرستِ پیشین نبود.
   */
  fun arrivals(before: Set<String>?, now: List<OrderDto>): Set<String> =
    if (before == null) emptySet() else now.map { it.id }.filter { it !in before }.toSet()

  /** زمانِ محلی به تقویمِ خورشیدی: «7/25 14:05» */
  fun stamp(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val j = Jalali.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
    return "${j.month}/${j.day} " + clock(ms)
  }

  /** فقط ساعت: «18:30» */
  fun clock(ms: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    return cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0') + ":" +
      cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
  }

  /** زمانِ زمان‌دار: امروز ⇐ فقط ساعت؛ روزِ دیگر ⇐ تاریخ و ساعت */
  fun scheduleLabel(at: Long, now: Long): String {
    val a = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val b = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val sameDay = a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
      a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    return if (sameDay) clock(at) else stamp(at)
  }
}
