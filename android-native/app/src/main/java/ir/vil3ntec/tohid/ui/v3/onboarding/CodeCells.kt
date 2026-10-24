package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  شش خانهٔ کد (CodeStep). کادرِ واقعی یکی و نامرئی است و خانه‌ها فقط نمایشِ همان‌اند — پس پرشِ
 *  خودکار، پاک کردن و **چسباندنِ کلِ کد** (از پیامک یا ایمیل) خودبه‌خود کار می‌کند. رقمِ فارسی و عربی
 *  لاتین می‌شود (`normalize`) و رقم‌ها با قلمِ برنامه همان رقمِ انتخابیِ دکان دیده می‌شوند.
 *
 *  ⚠️ خانه‌ها در هر زبانی چپ‌به‌راست‌اند: کدِ شش‌رقمی از چپ خوانده می‌شود. سه حال: خالیِ منتظر (نقطهٔ
 *  کم‌رنگ)، پُر، و خانهٔ نوبت (حلقهٔ ۲dpِ برند و کمی بزرگ‌تر). خطا ⇐ لرزشِ کیت.
 */
@Composable
internal fun CodeCells(
  code: String,
  onCode: (String) -> Unit,
  focus: FocusRequester,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  shake: Any? = null,
  normalize: (String) -> String = AuthRules::codeDigits,
  onDone: (() -> Unit)? = null,
) {
  Box(modifier.fillMaxWidth().v3Shake(shake)) {
    BasicTextField(
      value = code,
      onValueChange = { onCode(normalize(it).take(AuthRules.CODE_LENGTH)) },
      enabled = enabled,
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
      keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
      cursorBrush = SolidColor(Color.Transparent),
      textStyle = v3Text(16).copy(color = Color.Transparent),
      modifier = Modifier
        .matchParentSize()
        .alpha(0.01f)
        .focusRequester(focus)
        .semantics { contentDescription = StrAuth.codeCells },
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(AuthRules.CODE_LENGTH) { i ->
          CodeCell(digit = code.getOrNull(i)?.toString().orEmpty(), active = enabled && i == code.length, modifier = Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun CodeCell(digit: String, active: Boolean, modifier: Modifier) {
  val c = PhoneV3.colors
  val filled = digit.isNotEmpty()
  val edge by animateColorAsState(
    when {
      active -> PhoneV3Roles.focus(c)
      filled -> PhoneV3Roles.brandText(c).copy(alpha = 0.45f)
      else -> PhoneV3Roles.inputBorder(c)
    },
    MotionSpec.tweenOf(MotionTokens.SHORT), label = "cellEdge",
  )
  val width by animateDpAsState(if (active) 2.dp else 1.dp, MotionSpec.tweenOf(MotionTokens.SHORT), label = "cellW")
  val grow by animateFloatAsState(if (active) 1.04f else 1f, V3Springs.ui(), label = "cellGrow")
  val pop by animateFloatAsState(if (filled) 1f else 0.6f, V3Springs.playful(), label = "digitPop")
  val shape = RoundedCornerShape(V3Space.controlRadius + 2.dp)
  Box(
    modifier
      .height(56.dp)
      .graphicsLayer { scaleX = grow; scaleY = grow }
      .clip(shape)
      .background(PhoneV3Roles.inputFill(c))
      .border(width, edge, shape),
    contentAlignment = Alignment.Center,
  ) {
    if (filled) {
      Text(digit, style = v3Text(24, FontWeight.Bold, 30), color = c.ink, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop })
    } else {
      Box(Modifier.size(6.dp).clip(CircleShape).background(c.hairline))
    }
  }
}
