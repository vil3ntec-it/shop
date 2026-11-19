package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.StrKit
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3ButtonSpec
import ir.vil3ntec.tohid.ui.theme.V3Space

/**
 *  دکمهٔ نسخهٔ ۳ — یک سلسله‌مراتب (`V3ButtonKind`: اصلی، دوم، خنثی، سوم، خطر) با همهٔ حالت‌ها و اندازه‌های `V3ButtonSpec`
 *  (بلندیِ ۴۸، گوشهٔ ۱۲): عادی، فشرده (پردهٔ ۱۰٪ روی زمینه + کوچک‌شدنِ فنری)، تمرکزِ صفحه‌کلید (حلقهٔ `focus` بیرونِ دکمه)،
 *  غیرفعال (جفتِ `disabled`، بی‌لمس) و «در حالِ کار» (`loading`: چرخندهٔ کوچک جای آیکن، برچسب می‌ماند تا پهنا نپرد، و زدنِ دوباره
 *  بی‌اثر). رنگ‌ها فقط از `PhoneV3Roles` و جابه‌جاییِ رنگ نرم. `haptic` برای کارهای کلیدی (ثبت، پرداخت).
 */
@Composable
fun V3Button(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  kind: V3ButtonKind = V3ButtonKind.PRIMARY,
  icon: ImageVector? = null,
  enabled: Boolean = true,
  loading: Boolean = false,
  haptic: Boolean = false,
  height: Dp = V3Space.control,
  pill: Boolean = false,
) {
  val c = PhoneV3.colors
  val src = remember { MutableInteractionSource() }
  val down by src.collectIsPressedAsState()
  val focused by src.collectIsFocusedAsState()
  val pair = if (enabled) PhoneV3Roles.button(c, kind) else PhoneV3Roles.disabled(c).let { if (kind == V3ButtonKind.TERTIARY) it.copy(bg = Color.Transparent) else it }
  val fill = if (down && enabled && !loading) PhoneV3Roles.pressed(c, kind) else pair.bg
  val bg by animateColorAsState(fill, MotionSpec.tweenOf(MotionTokens.SHORT), label = "btnBg")
  val fg by animateColorAsState(pair.fg, MotionSpec.tweenOf(MotionTokens.SHORT), label = "btnFg")
  val shape = if (pill) RoundedCornerShape(percent = 50) else RoundedCornerShape(V3ButtonSpec.radius)
  val focusRadius = if (pill) height / 2 else V3ButtonSpec.radius
  val ring = PhoneV3Roles.focus(c)
  Row(
    modifier
      .v3Pressable(enabled = enabled && !loading, label = text, role = Role.Button, haptic = haptic, interaction = src, onClick = onClick)
      .heightIn(min = height)
      .v3FocusRing(focused && enabled, ring, focusRadius)
      .clip(shape)
      .background(bg)
      .then(if (pair.border != pair.bg && enabled) Modifier.border(1.dp, pair.border, shape) else Modifier)
      .semantics {
        if (!enabled) disabled()
        if (loading) stateDescription = StrKit.working
      }
      .padding(horizontal = V3ButtonSpec.paddingH),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AnimatedContent(loading, transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) togetherWith fadeOut(MotionSpec.tweenOf(90)) }, label = "btnLoading") { busy ->
      when {
        busy -> Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
          V3LiveSpinner(Modifier.size(18.dp), color = fg, strokeWidth = 2.dp)
        }
        icon != null -> Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(V3ButtonSpec.icon))
        else -> Spacer(Modifier.height(0.dp))
      }
    }
    if (icon != null || loading) Spacer(Modifier.width(V3ButtonSpec.iconGap))
    Text(text, style = v3Text(15, FontWeight.SemiBold, 22), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/**
 *  حلقهٔ تمرکزِ صفحه‌کلید (`V3ButtonSpec.focusRing` با فاصلهٔ `focusGap`) بیرونِ مرزِ دکمه — پیش از `clip` بیاید تا بیرون کشیده شود.
 */
fun Modifier.v3FocusRing(on: Boolean, color: Color, radius: Dp = V3ButtonSpec.radius): Modifier =
  if (!on) this else drawWithContent {
    drawContent()
    val gap = (V3ButtonSpec.focusGap + V3ButtonSpec.focusRing / 2).toPx()
    drawRoundRect(
      color = color,
      topLeft = Offset(-gap, -gap),
      size = Size(size.width + gap * 2, size.height + gap * 2),
      cornerRadius = CornerRadius(radius.toPx() + gap),
      style = Stroke(V3ButtonSpec.focusRing.toPx()),
    )
  }

/**
 *  دکمهٔ سوم (`V3ButtonKind.TERTIARY`) در اندازهٔ فشرده — «انصراف»، «همه»: فقط نوشتهٔ برند بی زمینه؛ فشردن تهِ‌رنگِ برند را نشان
 *  می‌دهد و تمرکزِ صفحه‌کلید حلقه دارد. [danger] نوشتهٔ خطا (پاک کردن).
 */
@Composable
fun V3TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false, enabled: Boolean = true) {
  val c = PhoneV3.colors
  val src = remember { MutableInteractionSource() }
  val down by src.collectIsPressedAsState()
  val focused by src.collectIsFocusedAsState()
  val shape = RoundedCornerShape(8.dp)
  val tint = if (down && enabled) PhoneV3Roles.pressed(c, V3ButtonKind.TERTIARY) else Color.Transparent
  val bg by animateColorAsState(tint, MotionSpec.tweenOf(MotionTokens.SHORT), label = "textBtnBg")
  Text(
    text,
    style = v3Text(15, FontWeight.SemiBold, 22),
    color = when {
      !enabled -> c.inkMuted
      danger -> PhoneV3Roles.errorText(c)
      else -> PhoneV3Roles.button(c, V3ButtonKind.TERTIARY).fg
    },
    modifier = modifier
      .v3Pressable(enabled = enabled, label = text, interaction = src, onClick = onClick)
      .v3FocusRing(focused && enabled, PhoneV3Roles.focus(c), 8.dp)
      .clip(shape)
      .background(bg)
      .padding(horizontal = 8.dp, vertical = 6.dp),
  )
}
