package ir.vil3ntec.tohid.ui.v3

import ir.vil3ntec.tohid.core.i18n.StrKit
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ir.vil3ntec.tohid.ui.theme.V3Space

/**
 *  دکمهٔ نسخهٔ ۳ — پنج گونهٔ §۷ (اصلی، دوم، خنثی، خطر، غیرفعال) با همهٔ حالت‌ها:
 *  عادی، فشرده (کوچک‌شدنِ فنری)، غیرفعال (رنگِ §۷ «Disabled»، بی‌لمس)، و
 *  «در حالِ کار» (`loading`: چرخندهٔ کوچک جای آیکن، برچسب می‌ماند تا پهنا نپرد،
 *  و زدنِ دوباره بی‌اثر). رنگ‌ها از `PhoneV3Roles` و جابه‌جاییِ رنگ نرم.
 *  `haptic` برای کارهای کلیدی (ثبت، پرداخت).
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
) {
  val c = PhoneV3.colors
  val pair = if (enabled) PhoneV3Roles.button(c, kind) else PhoneV3Roles.disabled(c)
  val bg by animateColorAsState(pair.bg, MotionSpec.tweenOf(MotionTokens.SHORT), label = "btnBg")
  val fg by animateColorAsState(pair.fg, MotionSpec.tweenOf(MotionTokens.SHORT), label = "btnFg")
  val shape = RoundedCornerShape(V3Space.controlRadius)
  Row(
    modifier
      .v3Pressable(enabled = enabled && !loading, label = text, role = Role.Button, haptic = haptic, onClick = onClick)
      .heightIn(min = height)
      .clip(shape)
      .background(bg)
      .then(if (pair.border != pair.bg && enabled) Modifier.border(1.dp, pair.border, shape) else Modifier)
      .semantics {
        if (!enabled) disabled()
        if (loading) stateDescription = StrKit.working
      }
      .padding(horizontal = 18.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    AnimatedContent(loading, transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)) togetherWith fadeOut(MotionSpec.tweenOf(90)) }, label = "btnLoading") { busy ->
      when {
        busy -> Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = fg, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        }
        icon != null -> Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        else -> Spacer(Modifier.height(0.dp))
      }
    }
    if (icon != null || loading) Spacer(Modifier.width(8.dp))
    Text(text, style = v3Text(15, FontWeight.SemiBold, 22), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/** دکمهٔ متنیِ برندرنگ («انصراف»، «همه») — بی زمینه، با همان فشردن */
@Composable
fun V3TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, danger: Boolean = false) {
  val c = PhoneV3.colors
  Text(
    text,
    style = v3Text(15, FontWeight.SemiBold, 22),
    color = if (danger) PhoneV3Roles.errorText(c) else PhoneV3Roles.brandText(c),
    modifier = modifier.v3Pressable(label = text, onClick = onClick).clip(RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
  )
}
