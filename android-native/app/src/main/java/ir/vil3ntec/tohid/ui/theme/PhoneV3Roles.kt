package ir.vil3ntec.tohid.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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

/**
 *  §۷: یک سلسله‌مراتبِ دکمه در همهٔ برنامه (۶۹ p3) — هر صفحه از همین می‌گیرد، هیچ تمی رنگِ جدا به دکمه نمی‌دهد:
 *   • [PRIMARY] اصلی: پُرِ برند با نوشتهٔ سفید — یک کارِ اصلی در هر صفحه/برگه (و دکمهٔ شناور، `V3Fab`)؛
 *   • [SECONDARY] دوم: تهِ‌رنگِ برند با نوشتهٔ برند، بی خطِ دور («پیش‌نویس»، «بعدی»)؛
 *   • [NEUTRAL] خنثی: تهِ‌رنگِ خاکستریِ همان تم («انصراف» کنارِ کارِ اصلی)؛
 *   • [TERTIARY] سوم: فقط نوشتهٔ برند، بی زمینه (`V3TextButton` همین است)؛
 *   • [DANGER] خطر: عقیقِ برند با نوشتهٔ سفید — فقط کارِ پاک‌کننده.
 *  غیرفعال (`PhoneV3Roles.disabled`) جدا. اندازه‌ها در [V3ButtonSpec]؛ قاعده‌ها در `ButtonTokensTest`.
 */
enum class V3ButtonKind { PRIMARY, SECONDARY, NEUTRAL, TERTIARY, DANGER }

/**
 *  اندازه و حالت‌های دکمه — یک جا برای همه (۶۹ p3). گوشی: بلندیِ ۴۸، گوشهٔ ۱۲، دکمهٔ شناور ۵۶ با گوشهٔ ۱۶، تراشه ۳۸ (قرصی)،
 *  قرصِ کارِ ردیف ۳۲ (ناحیهٔ لمس ۴۰). فشردن (و کوچک‌شدنِ فنری): زمینهٔ پُر با نوشتهٔ روشن ⇐ پردهٔ سیاهِ ۱۲٪ (نوشتهٔ سفید خوانا
 *  می‌ماند)، زمینهٔ تهِ‌رنگ با نوشتهٔ تیره ⇐ ۱۰٪ ِ رنگِ نوشته، دکمهٔ بی زمینه ⇐ تهِ‌رنگِ برند؛ تمرکزِ صفحه‌کلید: حلقهٔ ۲dp ِ `focus` با
 *  ۲dp فاصله بیرونِ دکمه؛ غیرفعال: جفتِ `disabled` و بی‌لمس.
 */
object V3ButtonSpec {
  val height = 48.dp
  val compactHeight = 40.dp
  val radius = 12.dp
  val paddingH = 20.dp
  val icon = 20.dp
  val iconGap = 8.dp
  val fabHeight = 56.dp
  val fabRadius = 16.dp
  val chipHeight = 38.dp
  val pillHeight = 32.dp
  val pillTouch = 40.dp
  val focusRing = 2.dp
  val focusGap = 2.dp
  /** پردهٔ فشردنِ زمینهٔ پُر (نوشتهٔ روشن) و تهِ‌رنگ (نوشتهٔ تیره) */
  const val PRESSED_FILL = 0.12f
  const val PRESSED_TONAL = 0.10f

  /** زمینهٔ فشردهٔ دکمه‌ای با زمینهٔ [bg] و نوشتهٔ [fg]؛ زمینهٔ شفاف (دکمهٔ سوم) ⇐ [tint] */
  fun pressed(bg: Color, fg: Color, tint: Color): Color = when {
    bg.alpha == 0f -> tint
    SectionColors.luminance(fg) > 0.5 -> Color.Black.copy(alpha = PRESSED_FILL).compositeOver(bg)
    else -> fg.copy(alpha = PRESSED_TONAL).compositeOver(bg)
  }
}

/** §۸: هفت وضعیتِ سفارش — یک معنی در همه‌جا (مشتری، دکان، پیک، مدیر) */
enum class V3Status { DELIVERED, IN_DELIVERY, AWAITING, CANCELLED, REVIEW, PREPARING, INACTIVE, WARNING }

/** §۹: چهار گونهٔ پیام */
enum class V3Tone { SUCCESS, INFO, WARNING, ERROR }

object PhoneV3Roles {
  private fun dark(c: PhoneV3Colors) = c.source.isDark

  /** تنها منبعِ رنگ `ShopColors` است — این‌جا هیچ HEX ِ تازه‌ای نیست (`PhoneV3TokensTest`) */
  private fun shop(c: PhoneV3Colors): ShopColors = c.source

  private fun pair(t: ButtonTone) = V3Pair(fg = t.text, bg = t.bg, border = t.border)
  private fun pair(t: StatusTone) = V3Pair(fg = t.fg, bg = t.bg)
  private fun pair(t: MessageTone) = V3Pair(fg = t.text, bg = t.bg, border = t.border)

  /**
   *  دکمه‌ها (§۷، [V3ButtonKind]): همه از جفت‌های `ShopColors.button` ِ تمِ جاری؛ دکمهٔ سوم زمینهٔ شفاف و نوشتهٔ برند (`brandText`).
   */
  fun button(c: PhoneV3Colors, kind: V3ButtonKind): V3Pair = shop(c).button.let { b ->
    when (kind) {
      V3ButtonKind.PRIMARY -> pair(b.primary)
      V3ButtonKind.SECONDARY -> pair(b.secondary)
      V3ButtonKind.NEUTRAL -> pair(b.neutral)
      V3ButtonKind.TERTIARY -> V3Pair(fg = shop(c).brandText, bg = Color.Transparent, border = Color.Transparent)
      V3ButtonKind.DANGER -> pair(b.danger)
    }
  }

  /** زمینهٔ فشردهٔ دکمهٔ [kind] ([V3ButtonSpec.pressed]) */
  fun pressed(c: PhoneV3Colors, kind: V3ButtonKind): Color =
    button(c, kind).let { V3ButtonSpec.pressed(it.bg, it.fg, shop(c).surfaceSelected) }

  fun disabled(c: PhoneV3Colors): V3Pair = pair(shop(c).button.disabled)

  /** هفت وضعیتِ سفارش (§۸) — همان معنا همه‌جا (`OrderStatusTone`، پیک، ویترین) */
  fun status(c: PhoneV3Colors, s: V3Status): V3Pair = shop(c).status.let { t ->
    when (s) {
      V3Status.DELIVERED -> pair(t.delivered)
      V3Status.IN_DELIVERY -> pair(t.inDelivery)
      V3Status.AWAITING -> pair(t.awaitingApproval)
      V3Status.WARNING -> pair(t.warning)
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
  /** گوشهٔ دکمه و کادرِ ورودی ([V3ButtonSpec.radius]) */
  val controlRadius = V3ButtonSpec.radius
  /** بلندیِ دکمه و کادر — هدفِ لمسیِ ۴۸ ([V3ButtonSpec.height]) */
  val control = V3ButtonSpec.height
  /** بلندیِ ردیفِ فهرست */
  val row = 60.dp
}
