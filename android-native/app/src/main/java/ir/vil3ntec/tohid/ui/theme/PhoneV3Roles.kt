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

  /** تنها منبعِ رنگ `ShopColors` است — این‌جا هیچ HEX ِ تازه‌ای نیست (`PhoneV3TokensTest`) */
  private fun shop(c: PhoneV3Colors): ShopColors = if (dark(c)) DarkColors else LightColors

  private fun pair(t: ButtonTone) = V3Pair(fg = t.text, bg = t.bg, border = t.border)
  private fun pair(t: StatusTone) = V3Pair(fg = t.fg, bg = t.bg)
  private fun pair(t: MessageTone) = V3Pair(fg = t.text, bg = t.bg, border = t.border)

  /**
   *  دکمه‌ها (§۷). دکمهٔ اصلیِ روز `brandDeep` با متنِ سفید است — انحرافِ تأییدشدهٔ design-v2
   *  (#108B81 با سفید فقط ۴٫۱۷:۱) که خودِ `ShopColors` دارد.
   */
  fun button(c: PhoneV3Colors, kind: V3ButtonKind): V3Pair = shop(c).button.let { b ->
    when (kind) {
      V3ButtonKind.PRIMARY -> pair(b.primary)
      V3ButtonKind.SECONDARY -> pair(b.secondary)
      V3ButtonKind.NEUTRAL -> pair(b.neutral)
      V3ButtonKind.DANGER -> pair(b.danger)
    }
  }

  fun disabled(c: PhoneV3Colors): V3Pair = pair(shop(c).button.disabled)

  /** هفت وضعیتِ سفارش (§۸) — همان معنا همه‌جا (`OrderStatusTone`، پیک، ویترین) */
  fun status(c: PhoneV3Colors, s: V3Status): V3Pair = shop(c).status.let { t ->
    when (s) {
      V3Status.DELIVERED -> pair(t.delivered)
      V3Status.IN_DELIVERY -> pair(t.inDelivery)
      V3Status.AWAITING -> pair(t.awaitingApproval)
      V3Status.CANCELLED -> pair(t.cancelled)
      V3Status.REVIEW -> pair(t.needsReview)
      V3Status.PREPARING -> pair(t.preparing)
      V3Status.INACTIVE -> pair(t.inactive)
    }
  }

  /** چهار پیام (§۹): متن، زمینه و مرز */
  fun tone(c: PhoneV3Colors, t: V3Tone): V3Pair = shop(c).message.let { m ->
    when (t) {
      V3Tone.SUCCESS -> pair(m.success)
      V3Tone.INFO -> pair(m.info)
      V3Tone.WARNING -> pair(m.warning)
      V3Tone.ERROR -> pair(m.error)
    }
  }

  /** آیکنِ پیام (هشدارِ روز #B45309 — انحرافِ تأییدشده، در خودِ `ShopColors`) */
  fun toneIcon(c: PhoneV3Colors, t: V3Tone): Color = shop(c).message.let { m ->
    when (t) {
      V3Tone.SUCCESS -> m.success.icon
      V3Tone.INFO -> m.info.icon
      V3Tone.WARNING -> m.warning.icon
      V3Tone.ERROR -> m.error.icon
    }
  }

  /** قرمزِ کارِ پاک‌کننده روی سطحِ تیره (سبد، کارتِ کششی) */
  val dangerOnInverse: Color get() = DarkColors.status.cancelled.fg

  fun inputBorder(c: PhoneV3Colors): Color = shop(c).borderInput
  fun inputFill(c: PhoneV3Colors): Color = if (dark(c)) shop(c).surfaceElevated else shop(c).surface
  fun focus(c: PhoneV3Colors): Color = shop(c).focus
  fun brandText(c: PhoneV3Colors): Color = shop(c).brandText
  fun errorText(c: PhoneV3Colors): Color = shop(c).status.cancelled.fg
}

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
