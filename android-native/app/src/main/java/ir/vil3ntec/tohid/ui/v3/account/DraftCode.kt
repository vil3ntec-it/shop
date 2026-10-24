package ir.vil3ntec.tohid.ui.v3.account

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.vil3ntec.tohid.normalizeDigits
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Space
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.delay

/**
 *  کادرِ کدِ چندرقمی (کدِ اشتراکِ شش‌رقمی): خانه‌های جدا که با نوشتن پر می‌شوند،
 *  خانهٔ بعدی حلقهٔ برند دارد. یک کادرِ نوشتنِ پنهان پشتش است، پس چسباندن و
 *  صفحه‌کلیدِ عددی همان رفتارِ کادرِ معمولی را دارند؛ رقمِ فارسی لاتین می‌شود.
 *  همیشه چپ‌به‌راست (کد لاتین است). پیش‌نویسِ گروهِ حساب برای کیت (`V3CodeField`).
 */
@Composable
internal fun DraftCodeCells(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  length: Int = 6,
) {
  val c = PhoneV3.colors
  val source = remember { MutableInteractionSource() }
  val focused by source.collectIsFocusedAsState()
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BasicTextField(
      value = value,
      onValueChange = { raw -> onValueChange(normalizeDigits(raw).filter { it in '0'..'9' }.take(length)) },
      singleLine = true,
      interactionSource = source,
      cursorBrush = SolidColor(c.card.copy(alpha = 0f)),
      textStyle = V3Type.body.copy(color = c.card.copy(alpha = 0f)),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
      modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
      decorationBox = { inner ->
        Box {
          Box(Modifier.width(1.dp).height(1.dp)) { inner() }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(length) { i ->
              val ch = value.getOrNull(i)
              val next = focused && i == value.length.coerceAtMost(length - 1)
              val edge by animateColorAsState(
                when {
                  next -> PhoneV3Roles.focus(c)
                  ch != null -> PhoneV3Roles.brandText(c)
                  else -> PhoneV3Roles.inputBorder(c)
                },
                MotionSpec.tweenOf(MotionTokens.SHORT),
                label = "cell",
              )
              val shape = RoundedCornerShape(V3Space.controlRadius)
              Box(
                Modifier
                  .weight(1f)
                  .height(56.dp)
                  .clip(shape)
                  .background(PhoneV3Roles.inputFill(c))
                  .border(if (next) 2.dp else 1.dp, edge, shape),
                contentAlignment = Alignment.Center,
              ) {
                Text(ch?.toString().orEmpty(), style = V3Type.figure.copy(fontFamily = FontFamily.Monospace), color = c.ink)
              }
            }
          }
        }
      },
    )
  }
}

/**
 *  نمایشِ یک‌بارهٔ کد (کدِ پیوستن): نویسه‌های درشتِ تک‌پهنا که یکی‌یکی با ۳۰ms
 *  فاصله می‌چرخند و پیدا می‌شوند (پشتِ `Motion.enabled`). همیشه چپ‌به‌راست.
 */
@Composable
internal fun DraftCodeReveal(code: String, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(
      modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = code.toList().joinToString(" ") },
      horizontalArrangement = Arrangement.Center,
    ) {
      code.forEachIndexed { i, ch ->
        val p = remember(code) { Animatable(if (Motion.enabled) 0f else 1f) }
        LaunchedEffect(code) {
          if (p.value < 1f) {
            delay((i * MotionTokens.STAGGER_STEP).toLong())
            p.animateTo(1f, tween(MotionTokens.LONG, easing = MotionTokens.EmphasizedDecelerate))
          }
        }
        Text(
          ch.toString(),
          style = V3Type.figure.copy(fontFamily = FontFamily.Monospace, fontSize = 32.sp, fontWeight = FontWeight.Bold),
          color = c.ink,
          modifier = Modifier.graphicsLayer {
            alpha = p.value
            rotationX = (1f - p.value) * 90f
          },
        )
      }
    }
  }
}
