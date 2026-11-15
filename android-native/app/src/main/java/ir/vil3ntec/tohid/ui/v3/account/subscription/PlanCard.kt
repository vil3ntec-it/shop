package ir.vil3ntec.tohid.ui.v3.account.subscription

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Type

/** برچسب‌های آزمون (`PlansCardsUiTest`) — سرِ کارت، کشوی جزئیات، دکمهٔ خرید */
internal object PlansTags {
  fun card(code: String) = "plan-$code"
  fun details(code: String) = "plan-details-$code"
  const val BUY = "plans-buy"
}

/**
 *  کارتِ بازشوندهٔ یک پلن در «اشتراک و قیمت‌ها»: سرِ کارت (نشانِ انتخاب، نام و نشانِ سرور، روزی چند و صرفه‌جویی، قیمتِ
 *  درشت با واحد، پیکان) و کشوی جزئیات (`PlanDetails`). زدنِ سرِ کارت = انتخاب و باز/بسته؛ هر بار فقط یک کارت باز است
 *  (حالتش در `PlansContent`).
 *
 *  حرکت، همه از واژه‌نامهٔ `MotionSpec` و زیرِ «کاهشِ حرکت» بی‌درنگ: باز و بسته شدن با فنرِ اندازه و محو، چرخشِ پیکان،
 *  حلقهٔ مرز و زمینه و سایهٔ کارتِ انتخاب‌شده، و فشردنِ لمسی. مقدارهای جان‌دار فقط درونِ `drawBehind` و `graphicsLayer`
 *  خوانده می‌شوند: هر قاب فقط دوباره کشیده می‌شود، نه دوباره ساخته، و هیچ شیئی در قاب ساخته نمی‌شود.
 */
@Composable
internal fun PlanCard(
  option: PlansModel.Option,
  saving: PlansModel.Saving?,
  currency: String,
  selected: Boolean,
  open: Boolean,
  onTap: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = PhoneV3.colors
  val density = LocalDensity.current
  val shape = remember { RoundedCornerShape(V3Space.featuredRadius) }
  val frame = remember(density) { with(density) { PlanFrame(Stroke(1.dp.toPx()), Stroke(2.dp.toPx()), V3Space.featuredRadius.toPx()) } }
  val fill = animateColorAsState(if (selected) PhoneV3Palette.selected(c) else c.card, MotionSpec.tweenOf(MotionTokens.MEDIUM), label = "planFill")
  val ring = animateFloatAsState(if (selected) 1f else 0f, MotionSpec.indicator(), label = "planRing")
  val lift = animateDpAsState(if (selected) 6.dp else 0.dp, MotionSpec.morph(), label = "planLift")
  val src = remember { MutableInteractionSource() }
  val down by src.collectIsPressedAsState()
  val press = animateFloatAsState(if (down && Motion.enabled) 0.985f else 1f, MotionSpec.press(), label = "planPress")
  val hairline = c.hairline
  val focus = PhoneV3Roles.focus(c)
  val shadow = c.ink.copy(alpha = 0.16f)
  Column(
    modifier
      .fillMaxWidth()
      .graphicsLayer {
        scaleX = press.value
        scaleY = press.value
        shadowElevation = lift.value.toPx().coerceAtLeast(0f)
        this.shape = shape
        clip = false
        ambientShadowColor = shadow
        spotShadowColor = shadow
      }
      .drawBehind { frame.draw(this, fill.value, hairline, focus, ring.value) },
  ) {
    PlanHeader(option, saving, currency, selected, open, src, onTap)
    if (!Motion.enabled) {
      if (open) PlanDetails(option, saving, currency)
    } else {
      val enter = remember { planEnter() }
      val exit = remember { planExit() }
      AnimatedVisibility(open, enter = enter, exit = exit, label = "planDrawer") { PlanDetails(option, saving, currency) }
    }
  }
}

/** مرزِ مویی و حلقهٔ انتخابِ کارت — قلم‌ها یک بار برای هر چگالی ساخته می‌شوند */
private class PlanFrame(val hair: Stroke, val ring: Stroke, val radiusPx: Float) {
  fun draw(scope: DrawScope, fill: Color, hairline: Color, focus: Color, ringAlpha: Float) {
    with(scope) {
      drawRoundRect(fill, cornerRadius = CornerRadius(radiusPx))
      inset(hair.width / 2) { drawRoundRect(hairline, cornerRadius = CornerRadius(radiusPx - hair.width / 2), style = hair) }
      val a = ringAlpha.coerceIn(0f, 1f)
      if (a > 0.01f) {
        inset(ring.width / 2) { drawRoundRect(focus, alpha = a, cornerRadius = CornerRadius(radiusPx - ring.width / 2), style = ring) }
      }
    }
  }
}

/** باز شدن: فنرِ نرمِ اندازه از بالا و محوِ ورود */
private fun planEnter(): EnterTransition =
  expandVertically(spring(dampingRatio = 0.82f, stiffness = 380f, visibilityThreshold = IntSize.VisibilityThreshold), expandFrom = Alignment.Top) +
    fadeIn(spring(dampingRatio = 1f, stiffness = 300f))

/** بسته شدن: فنرِ بی‌جهش (تندتر از باز شدن) و محوِ کوتاه */
private fun planExit(): ExitTransition =
  shrinkVertically(spring(dampingRatio = 1f, stiffness = 520f, visibilityThreshold = IntSize.VisibilityThreshold), shrinkTowards = Alignment.Top) +
    fadeOut(tween(MotionTokens.SHORT))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanHeader(
  option: PlansModel.Option,
  saving: PlansModel.Saving?,
  currency: String,
  selected: Boolean,
  open: Boolean,
  src: MutableInteractionSource,
  onTap: () -> Unit,
) {
  val c = PhoneV3.colors
  val haptic = LocalHapticFeedback.current
  val turn = animateFloatAsState(if (open) 180f else 0f, MotionSpec.indicator(), label = "planChevron")
  val dot = animateFloatAsState(if (selected) 1f else 0f, MotionSpec.press(), label = "planDot")
  Row(
    Modifier
      .fillMaxWidth()
      .heightIn(min = 72.dp)
      .testTag(PlansTags.card(option.code))
      .clickable(src, indication = null, role = Role.RadioButton, onClickLabel = if (open) StrAccount.hidePlanDetails else StrAccount.showPlanDetails) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onTap()
      }
      //  صفحه‌خوان: «انتخاب‌شده» و «باز/بسته» ِ کشو، نه فقط رنگِ مرز و چرخشِ پیکان
      .semantics {
        this.selected = selected
        stateDescription = if (open) StrA11y.expanded else StrA11y.collapsed
        if (open) collapse { onTap(); true } else expand { onTap(); true }
      }
      .padding(start = 14.dp, end = 10.dp, top = 14.dp, bottom = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioMark(dot)
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(option.title, style = V3Type.headline, color = c.ink, modifier = Modifier.align(Alignment.CenterVertically))
        if (option.badge.isNotBlank()) {
          PlanPill(option.badge, PhoneV3Roles.brandText(c), PhoneV3Palette.soft(c, PhoneV3Palette.Hue.BRAND), Modifier.align(Alignment.CenterVertically))
        }
      }
      Spacer(Modifier.height(4.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(StrAccount.perDay(money(option.perDay)), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.align(Alignment.CenterVertically))
        if (saving != null) {
          val ok = PhoneV3Roles.tone(c, V3Tone.SUCCESS)
          PlanPill(StrAccount.saveChip(saving.percent), ok.fg, ok.bg, Modifier.align(Alignment.CenterVertically))
        }
        PlansModel.discountText(option)?.let {
          PlanPill(it, PhoneV3Roles.brandText(c), PhoneV3Palette.soft(c, PhoneV3Palette.Hue.BRAND), Modifier.align(Alignment.CenterVertically))
        }
      }
    }
    Spacer(Modifier.width(10.dp))
    Column(horizontalAlignment = Alignment.End) {
      if (option.discounted) {
        Text(money(option.fullPrice.toDouble()), style = V3Type.footnote.copy(textDecoration = TextDecoration.LineThrough), color = c.inkMuted)
      }
      Text(money(option.price.toDouble()), style = V3Type.figure, color = c.ink)
      Text(currency, style = V3Type.caption, color = c.inkMuted)
    }
    Spacer(Modifier.width(4.dp))
    Icon(
      Icons.Outlined.KeyboardArrowDown,
      contentDescription = null,
      tint = c.chevron,
      modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = turn.value },
    )
  }
}

/** نشانِ رادیو: حلقه، و دایرهٔ پرِ برند با تیک که با فنر بزرگ می‌شود */
@Composable
private fun RadioMark(dot: State<Float>) {
  val c = PhoneV3.colors
  val on = PhoneV3Roles.button(c, V3ButtonKind.PRIMARY)
  val edge = PhoneV3Roles.inputBorder(c)
  val density = LocalDensity.current
  val stroke = remember(density) { with(density) { Stroke(2.dp.toPx()) } }
  Box(
    Modifier.size(22.dp).drawBehind {
      val r = size.minDimension / 2
      drawCircle(edge, radius = r - stroke.width / 2, style = stroke)
      val d = dot.value.coerceIn(0f, 1f)
      if (d > 0f) drawCircle(on.bg, radius = r * d)
    },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      Icons.Outlined.Check,
      contentDescription = null,
      tint = on.fg,
      modifier = Modifier.size(14.dp).graphicsLayer {
        val d = dot.value.coerceIn(0f, 1f)
        alpha = d
        scaleX = d
        scaleY = d
      },
    )
  }
}

/** برچسبِ گردِ کوچک: نشانِ سرور («پیشنهاد ما»)، صرفه‌جویی و تخفیف */
@Composable
internal fun PlanPill(text: String, fg: Color, bg: Color, modifier: Modifier = Modifier) {
  Text(
    text,
    style = V3Type.caption,
    color = fg,
    maxLines = 1,
    modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
  )
}
