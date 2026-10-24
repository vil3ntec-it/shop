package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Springs
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import ir.vil3ntec.tohid.ui.v3.v3Shake

/** کلیدِ گردِ ۷۲ (سطحِ درونی)؛ فاصلهٔ میانِ کلیدها */
private val KEY = 72.dp
private val KEY_GAP = 18.dp

/**
 *  نقطه‌های رمز: هر رقم یک نقطه با فنرِ شاد پر می‌شود؛ غلط ⇐ همه قرمز و لرزشِ کیت؛ درست ⇐ همه
 *  رنگِ برند. چپ‌به‌راست در هر زبانی (ترتیبِ رقم‌ها).
 */
@Composable
internal fun PinDots(length: Int, filled: Int, wrong: Boolean, shake: Any?, ok: Boolean = false) {
  val c = PhoneV3.colors
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(Modifier.v3Shake(shake), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      repeat(length) { i ->
        val on = i < filled || ok
        val scale by animateFloatAsState(if (on) 1f else 0.78f, V3Springs.playful(), label = "pinDot")
        val color by animateColorAsState(
          when {
            wrong -> PhoneV3Roles.errorText(c)
            on -> PhoneV3Roles.focus(c)
            else -> c.hairline
          },
          MotionSpec.tweenOf(MotionTokens.SHORT), label = "pinDotColor",
        )
        Box(Modifier.size(14.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(color))
      }
    }
  }
}

/**
 *  صفحه‌کلیدِ ۳×۴ ِ رمز (۱۲۳ از چپ در هر زبانی، مثلِ هر صفحه‌کلیدِ عددی). کلیدِ پایین‌آغاز اثرِ
 *  انگشت است وقتی گوشی دارد، پایین‌پایان «پاک کردن». صفحه‌کلیدِ سخت‌افزاری هم پذیرفته می‌شود
 *  (رقمِ لاتین/فارسی/عربی، Backspace و Delete) — روی کامپیوتر تنها راهِ طبیعی همین است.
 */
@Composable
internal fun PinPad(onDigit: (Char) -> Unit, onErase: () -> Unit, onFinger: (() -> Unit)?) {
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(
      Modifier
        .focusRequester(focus)
        .focusable()
        .onKeyEvent { e ->
          if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
          if (e.key == Key.Backspace || e.key == Key.Delete) { onErase(); return@onKeyEvent true }
          val d = PinRules.keyDigit(e.utf16CodePoint.toChar()) ?: return@onKeyEvent false
          onDigit(d)
          true
        },
      verticalArrangement = Arrangement.spacedBy(14.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      listOf("123", "456", "789").forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
          row.forEach { d -> DigitKey(d) { onDigit(d) } }
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(KEY_GAP)) {
        if (onFinger != null) IconKey(Icons.Outlined.Fingerprint, StrAuth.unlockFingerprint, onFinger) else Spacer(Modifier.size(KEY))
        DigitKey('0') { onDigit('0') }
        IconKey(Icons.AutoMirrored.Outlined.Backspace, StrKit.clear, onErase)
      }
    }
  }
}

@Composable
private fun DigitKey(d: Char, onClick: () -> Unit) {
  val c = PhoneV3.colors
  Box(
    Modifier.size(KEY).clip(CircleShape).background(c.tile).v3Pressable(label = d.toString(), scale = 0.92f, haptic = true, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(d.toString(), style = v3Text(28, FontWeight.Medium, 34), color = c.ink)
  }
}

@Composable
private fun IconKey(icon: ImageVector, label: String, onClick: () -> Unit) {
  val c = PhoneV3.colors
  Box(
    Modifier.size(KEY).clip(CircleShape).v3Pressable(label = label, scale = 0.92f, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = label, tint = c.icon, modifier = Modifier.size(26.dp))
  }
}
