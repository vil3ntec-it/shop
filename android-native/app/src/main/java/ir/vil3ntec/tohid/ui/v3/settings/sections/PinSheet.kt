package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable

/**
 *  برگهٔ رمزِ برنامه (`settings/security/app-lock/pin`): شش خانهٔ نقطه، صفحه‌کلیدِ
 *  عددی، «همان رمز را دوباره بزنید»، و اگر دو رمز یکی نبود لرزش و شروعِ دوباره.
 *  رمز با همان `LockStore.set` ذخیره می‌شود.
 */
@Composable
internal fun PinSheet(onDone: () -> Unit) {
  val context = LocalContext.current
  val toasts = LocalV3Toasts.current
  val had = remember { LockStore(context).enabled }
  var entry by remember { mutableStateOf(PinEntry()) }
  val shake = remember { Animatable(0f) }
  LaunchedEffect(entry.mismatch) {
    if (entry.mismatch > 0) {
      Haptics.confirm(context)
      if (Motion.enabled) shake.animateTo(0f, keyframes { durationMillis = 360; -14f at 60; 12f at 120; -8f at 180; 6f at 240; 0f at 300 })
    }
  }
  V3Sheet(onDismiss = onDone, title = StrSettings.appPin) { closer ->
    LaunchedEffect(entry.done) {
      val pin = entry.done ?: return@LaunchedEffect
      LockStore(context).set(pin)
      LockSignal.version++
      Haptics.success(context)
      toasts?.show(if (had) StrSettings.pinChanged else StrSettings.lockTurnedOn)
      closer.close()
    }
    val prompt = when {
      entry.confirming -> StrSettings.pinRepeat
      entry.mismatch > 0 -> Str.passwordsDiffer
      else -> StrSettings.pinNote
    }
    AnimatedContent(prompt, transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) }, label = "pinPrompt") { text ->
      Text(
        text,
        style = V3Type.callout,
        color = if (entry.mismatch > 0 && !entry.confirming) PhoneV3Roles.errorText(PhoneV3.colors) else PhoneV3.colors.inkMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
      )
    }
    Spacer(Modifier.height(20.dp))
    PinDots(entry.typed.length, Modifier.align(Alignment.CenterHorizontally).graphicsLayer { translationX = shake.value * density })
    Spacer(Modifier.height(24.dp))
    DraftPinPad(onDigit = { entry = PinModel.type(entry, it) }, onErase = { entry = PinModel.erase(entry) })
  }
}

/** شش خانه؛ پُرشده‌ها با رنگِ برند */
@Composable
private fun PinDots(filled: Int, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  //  رمز همیشه از چپ به راست پُر می‌شود، مثلِ صفحه‌کلید
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
      repeat(PinModel.LENGTH) { i ->
        val on = i < filled
        val fill by animateColorAsState(if (on) PhoneV3Roles.focus(c) else c.card, MotionSpec.tweenOf(MotionTokens.SHORT), label = "pinDot")
        Box(
          Modifier.size(16.dp).clip(CircleShape).background(fill)
            .border(1.5.dp, if (on) PhoneV3Roles.focus(c) else PhoneV3Roles.inputBorder(c), CircleShape),
        )
      }
    }
  }
}

/**
 *  صفحه‌کلیدِ عددیِ رمز (۳×۴). جزءِ تازه، فعلاً در همین بسته (`Draft`) — برای
 *  بردن به کیت در PR نوشته شده.
 */
@Composable
internal fun DraftPinPad(onDigit: (Char) -> Unit, onErase: () -> Unit) {
  val keys = listOf("123", "456", "789", " 0<")
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      keys.forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          row.forEach { k ->
            Box(Modifier.weight(1f).height(56.dp)) {
              when (k) {
                ' ' -> Unit
                '<' -> PadKey(Modifier, StrSettings.erase, onClick = onErase) {
                  Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = StrSettings.erase, tint = PhoneV3.colors.icon)
                }
                else -> PadKey(Modifier, k.toString(), onClick = { onDigit(k) }) {
                  Text(k.toString(), style = V3Type.title, color = PhoneV3.colors.ink)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PadKey(modifier: Modifier, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
  val c = PhoneV3.colors
  val pair = PhoneV3Roles.button(c, V3ButtonKind.NEUTRAL)
  Box(
    modifier
      .fillMaxWidth()
      .height(56.dp)
      .v3Pressable(label = label, scale = 0.92f, onClick = onClick)
      .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
      .background(pair.bg)
      .padding(4.dp),
    contentAlignment = Alignment.Center,
  ) { content() }
}
