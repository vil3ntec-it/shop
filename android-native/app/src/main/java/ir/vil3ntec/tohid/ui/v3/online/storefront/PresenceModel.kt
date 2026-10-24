package ir.vil3ntec.tohid.ui.v3.online.storefront

import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.repo.PresenceStatusDto

/**
 *  حالتِ خالصِ برگهٔ «وضعیت» ِ ویترین (آزمون: `PresenceModelTest`): پنج اعلامِ مالک، سنجشِ
 *  «آنلاین تا ساعتِ …» (همان `parseHm` و کمتر از ۲۴ ساعت)، و متن‌های کادرِ بزرگ از
 *  `status` ِ سرور — همان جمله‌هایی که مشتری در ویترین می‌بیند.
 */
object PresenceModel {

  /** گزینه‌های اعلام به ترتیبِ online.md */
  val MODES = listOf("online_until", "online", "offline", "closed", "auto")

  /** «20:30» ⇒ دقیقهٔ روز؛ نادرست ⇒ null */
  fun untilMinutes(text: String): Int? = StorefrontOwner.parseHm(text)?.takeIf { it < 24 * 60 }

  fun untilOf(st: PresenceStatusDto): String? = st.declaredUntil?.let { StorefrontOwner.clock(it, st.timezone) }

  /** عددِ بزرگِ کادر: «باز · آنلاین»، «آنلاین تا 20:30»، «بسته» … */
  fun headline(st: PresenceStatusDto): String = StrOnline.presenceShort(st.badge, untilOf(st))

  /** برچسبِ کادر: همان جملهٔ کاملِ ویترین */
  fun sentence(st: PresenceStatusDto): String = StrOnline.presenceBadge(st.badge, untilOf(st))

  /** زیرنویس: ساعتِ دکان و ساعتِ امروز */
  fun caption(st: PresenceStatusDto): String {
    val today = when {
      st.holiday -> StrOnline.todayHoliday
      st.today.isNotEmpty() -> StrOnline.todayHours(StorefrontOwner.formatDay(st.today.map { it[0] to it[1] }))
      st.hoursSet -> StrOnline.todayClosed
      else -> StrOnline.hoursNotSet
    }
    return StrOnline.shopClock(st.localTime) + " · " + today
  }

  fun connection(st: PresenceStatusDto): String =
    StrOnline.connection(st.lastConfirmedAt?.takeIf { it > 0 }?.let { StorefrontOwner.clock(it, st.timezone) })
}
