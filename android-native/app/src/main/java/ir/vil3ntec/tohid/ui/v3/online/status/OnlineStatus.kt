package ir.vil3ntec.tohid.ui.v3.online.status

import ir.vil3ntec.tohid.ui.components.common.OrderStatusTone
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.theme.V3Status

/**
 *  وضعیتِ سفارش و مأموریت ⇐ یکی از وضعیت‌های کیت (COLOR-SYSTEM §۸، THEME-V2: «در انتظارِ تأیید»
 *  بنفش و جدا از هشدارِ کهربایی ⇐ هشت معنا، هفت رنگ). نگاشت **یک جا**
 *  است — `OrderStatusTone` ِ پایه (همان که پیک و میزِ تحویلِ پیشین می‌خوانند)؛ این‌جا
 *  فقط نشانِ پایه به `V3Status` ِ کیت برگردانده می‌شود تا قرص‌های تازه همان رنگ را بگیرند.
 *  فقط نمایش است؛ ماشینِ وضعیت (`OrderStates`) دست نمی‌خورد. (آزمون: `DeliveryModelTest`)
 */
object OnlineStatus {

  fun of(tone: PillTone): V3Status = when (tone) {
    PillTone.SUCCESS -> V3Status.DELIVERED
    PillTone.INFO -> V3Status.IN_DELIVERY
    PillTone.AWAITING -> V3Status.AWAITING
    PillTone.WARNING -> V3Status.WARNING
    PillTone.DANGER -> V3Status.CANCELLED
    PillTone.REVIEW -> V3Status.REVIEW
    PillTone.PREPARING -> V3Status.PREPARING
    PillTone.NEUTRAL -> V3Status.INACTIVE
  }

  /** وضعیتِ سفارش (`OrderStates`) */
  fun order(status: String): V3Status = of(OrderStatusTone.order(status))

  /** وضعیتِ مأموریتِ تحویل (`delivery-states.js`) — همان معناها با نام‌های پیک */
  fun mission(status: String): V3Status = of(OrderStatusTone.courier(status))
}
