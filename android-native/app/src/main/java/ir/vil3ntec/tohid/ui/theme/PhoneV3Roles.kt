package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 *  نقش‌های رنگیِ کیتِ نسخهٔ ۳ (VILL3N، COLOR-SYSTEM §۷ دکمه‌ها، §۸ وضعیتِ سفارش،
 *  §۹ پیام‌ها، §۴/§۵ کادرِ ورودی) — کنارِ `PhoneV3Colors`، از روی همان روز/شب.
 *  مقدارها عیناً از فایلِ سیستمِ رنگ؛ جفت‌های شب که فایل به ما سپرده («define a dark
 *  equivalent») هم‌خانوادهٔ همان رنگ‌اند و کنتراستشان در `PhoneV3RolesContrastTest`
 *  سنجیده می‌شود. هیچ صفحه‌ای HEX نمی‌نویسد؛ همه از این‌جا.
 */

/** یک جفتِ رنگِ پیش‌زمینه/زمینه (+ مرز) */
data class V3Pair(val fg: Color, val bg: Color, val border: Color = bg)

/** §۷: پنج گونهٔ دکمه */
enum class V3ButtonKind { PRIMARY, SECONDARY, NEUTRAL, DANGER }

/** §۸: هفت وضعیتِ سفارش — یک معنی در همه‌جا (مشتری، دکان، پیک، مدیر) */
enum class V3Status { DELIVERED, IN_DELIVERY, AWAITING, CANCELLED, REVIEW, PREPARING, INACTIVE }

/** §۹: چهار گونهٔ پیام */
enum class V3Tone { SUCCESS, INFO, WARNING, ERROR }

object PhoneV3Roles {
  private fun dark(c: PhoneV3Colors) = c === PhoneV3Dark

  fun button(c: PhoneV3Colors, kind: V3ButtonKind): V3Pair = if (dark(c)) when (kind) {
    V3ButtonKind.PRIMARY -> V3Pair(Color(0xFF041B15), Color(0xFF19A985))
    V3ButtonKind.SECONDARY -> V3Pair(Color(0xFF6EE7D1), Color(0xFF162235), Color(0xFF40516A))
    V3ButtonKind.NEUTRAL -> V3Pair(Color(0xFFF1F5F9), Color(0xFF203149))
    V3ButtonKind.DANGER -> V3Pair(Color(0xFFFFFFFF), Color(0xFFDC2626))
  } else when (kind) {
    //  انحرافِ تأییدشده (design-v2 #285): سفید روی #108B81 فقط ۴٫۱۷:۱ است ⇒ primaryDeep
    V3ButtonKind.PRIMARY -> V3Pair(Color(0xFFFFFFFF), Color(0xFF08665F))
    V3ButtonKind.SECONDARY -> V3Pair(Color(0xFF08665F), Color(0xFFFFFFFF), Color(0xFFB8DCD5))
    V3ButtonKind.NEUTRAL -> V3Pair(Color(0xFF344256), Color(0xFFE8EDF3))
    V3ButtonKind.DANGER -> V3Pair(Color(0xFFFFFFFF), Color(0xFFDC2626))
  }

  /** §۷ «Disabled» — شب: سطحِ درونی با متنِ کم‌رنگ */
  fun disabled(c: PhoneV3Colors): V3Pair =
    if (dark(c)) V3Pair(Color(0xFF64748B), Color(0xFF1B2A3E)) else V3Pair(Color(0xFF94A3B8), Color(0xFFE2E8F0))

  fun status(c: PhoneV3Colors, s: V3Status): V3Pair = if (dark(c)) when (s) {
    V3Status.DELIVERED -> V3Pair(Color(0xFF86EFAC), Color(0xFF123C2B))
    V3Status.IN_DELIVERY -> V3Pair(Color(0xFF93C5FD), Color(0xFF172E54))
    V3Status.AWAITING -> V3Pair(Color(0xFFFCD34D), Color(0xFF3D2E0A))
    V3Status.CANCELLED -> V3Pair(Color(0xFFFCA5A5), Color(0xFF3F1A1F))
    V3Status.REVIEW -> V3Pair(Color(0xFFC4B5FD), Color(0xFF2E2350))
    V3Status.PREPARING -> V3Pair(Color(0xFF67E8F9), Color(0xFF0E3440))
    V3Status.INACTIVE -> V3Pair(Color(0xFFCBD5E1), Color(0xFF29374A))
  } else when (s) {
    V3Status.DELIVERED -> V3Pair(Color(0xFF15803D), Color(0xFFDCFCE7))
    V3Status.IN_DELIVERY -> V3Pair(Color(0xFF1D4ED8), Color(0xFFDBEAFE))
    V3Status.AWAITING -> V3Pair(Color(0xFF92400E), Color(0xFFFEF3C7))
    V3Status.CANCELLED -> V3Pair(Color(0xFFB91C1C), Color(0xFFFEE2E2))
    V3Status.REVIEW -> V3Pair(Color(0xFF6D28D9), Color(0xFFEDE9FE))
    V3Status.PREPARING -> V3Pair(Color(0xFF0E7490), Color(0xFFCFFAFE))
    V3Status.INACTIVE -> V3Pair(Color(0xFF475569), Color(0xFFE2E8F0))
  }

  /** §۹: متن/زمینه/مرزِ پیام — شدت هم‌اندازهٔ اهمیت */
  fun tone(c: PhoneV3Colors, t: V3Tone): V3Pair = when (t) {
    V3Tone.SUCCESS -> status(c, V3Status.DELIVERED).let { it.copy(border = if (dark(c)) Color(0xFF1F6B47) else Color(0xFFA7E8BF)) }
    V3Tone.INFO -> status(c, V3Status.IN_DELIVERY).let { it.copy(border = if (dark(c)) Color(0xFF2B4C86) else Color(0xFFBFD5FB)) }
    V3Tone.WARNING -> status(c, V3Status.AWAITING).let { it.copy(border = if (dark(c)) Color(0xFF6B5216) else Color(0xFFFCDB8A)) }
    V3Tone.ERROR -> status(c, V3Status.CANCELLED).let { it.copy(border = if (dark(c)) Color(0xFF7A2A31) else Color(0xFFFBC4C4)) }
  }

  /** §۴/§۵: مرزِ کادرِ ورودی (borderInput) و زمینه‌اش (سطحِ تعاملی) */
  fun inputBorder(c: PhoneV3Colors): Color = if (dark(c)) Color(0xFF40516A) else Color(0xFFC5D0DE)
  fun inputFill(c: PhoneV3Colors): Color = if (dark(c)) Color(0xFF203149) else Color(0xFFFFFFFF)
  /** حلقهٔ تمرکز = برند (iconActive در شب تا روی تیره خوانا باشد) */
  fun focus(c: PhoneV3Colors): Color = if (dark(c)) Color(0xFF6EE7D1) else Color(0xFF108B81)
  /** متنِ برند روی زمینهٔ صفحه (پیوند، برچسبِ فعال) — primaryDeep در روز */
  fun brandText(c: PhoneV3Colors): Color = if (dark(c)) Color(0xFF6EE7D1) else Color(0xFF08665F)
  fun errorText(c: PhoneV3Colors): Color = if (dark(c)) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
}

/** فاصله‌های ۸-نقطه‌ای و اندازه‌های کیت — §۱۱ (کارت ۱۲/۱۶، دکمه و کادر ۱۰) */
object V3Space {
  val xs = 4.dp
  val sm = 8.dp
  val md = 12.dp
  val lg = 16.dp
  val xl = 20.dp
  val xxl = 24.dp
  /** لبهٔ صفحه */
  val gutter = 16.dp
  /** گوشهٔ کارتِ معمولی و کارتِ برجسته */
  val cardRadius = 12.dp
  val featuredRadius = 16.dp
  /** گوشهٔ دکمه و کادرِ ورودی */
  val controlRadius = 10.dp
  /** بلندیِ دکمه و کادر — هدفِ لمسیِ ≥ ۴۸ */
  val control = 50.dp
  /** بلندیِ ردیفِ فهرست */
  val row = 60.dp
}
