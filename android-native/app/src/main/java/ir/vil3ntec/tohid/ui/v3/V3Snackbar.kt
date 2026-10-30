package ir.vil3ntec.tohid.ui.v3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Tone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** پیامِ کوتاه با گونه — متنِ ساده (`showSnackbar(text)`ِ صفحه‌های قدیم) گونه‌اش را از متن می‌گیرد */
class V3SnackVisuals(
  override val message: String,
  val tone: V3Tone,
  override val actionLabel: String? = null,
  override val withDismissAction: Boolean = false,
  override val duration: SnackbarDuration = if (actionLabel != null) SnackbarDuration.Long else SnackbarDuration.Short,
) : SnackbarVisuals

/**
 *  پیام‌های کوتاهِ برنامه: `show(متن، گونه)` و `undo(متن) { برگرداندن }`.
 *  پوسته یکی می‌سازد و با `LocalV3Toasts` به همهٔ صفحه‌ها می‌دهد؛ همان
 *  `SnackbarHostState`ِ صفحه‌های قدیم پشتش است، پس پیامِ قدیم و تازه یک‌جا و
 *  یکی‌یکی می‌آیند. «برگرداندن» فقط جایی که موتور کارِ وارونه دارد (ORDER.md).
 */
@Stable
class V3Toasts(val host: SnackbarHostState, private val scope: CoroutineScope) {
  fun show(text: String, tone: V3Tone = toneOf(text)) {
    scope.launch { host.showSnackbar(V3SnackVisuals(text, tone)) }
  }

  fun undo(text: String, onUndo: () -> Unit) {
    scope.launch {
      host.currentSnackbarData?.dismiss()
      if (host.showSnackbar(V3SnackVisuals(text, V3Tone.SUCCESS, StrKit.undo)) == SnackbarResult.ActionPerformed) onUndo()
    }
  }

  companion object {
    fun toneOf(text: String): V3Tone = when (I18n.toneOf(text)) {
      I18n.Tone.ERROR -> V3Tone.ERROR
      I18n.Tone.SUCCESS -> V3Tone.SUCCESS
      I18n.Tone.INFO -> V3Tone.INFO
    }
  }
}

val LocalV3Toasts = staticCompositionLocalOf<V3Toasts?> { null }

@Composable
fun rememberV3Toasts(host: SnackbarHostState): V3Toasts {
  val scope = rememberCoroutineScope()
  return remember(host) { V3Toasts(host, scope) }
}

/**
 *  جای پیام‌ها: قرصِ تیرهٔ وارونه (`black`) بالای صفحه، نقطهٔ رنگیِ گونه کنارِ
 *  متن (رنگ تنها نیست: متن خودش می‌گوید)، و کارِ برندرنگِ اختیاری.
 */
@Composable
fun V3SnackbarHost(host: SnackbarHostState, modifier: Modifier = Modifier) {
  val c = PhoneV3.colors
  SnackbarHost(host, modifier) { data ->
    val tone = (data.visuals as? V3SnackVisuals)?.tone ?: V3Toasts.toneOf(data.visuals.message)
    AnimatedVisibility(
      visible = true,
      enter = slideInVertically(MotionSpec.sheet()) { -it } + fadeIn(MotionSpec.tweenOf(MotionTokens.SHORT)),
      exit = slideOutVertically(MotionSpec.sheet()) { -it } + fadeOut(MotionSpec.tweenOf(90)),
    ) {
      Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.TopCenter) {
        Row(
          Modifier
            .widthIn(max = 560.dp)
            .heightIn(min = 48.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = c.black.copy(alpha = 0.3f), spotColor = c.black.copy(alpha = 0.3f))
            .clip(RoundedCornerShape(16.dp))
            .background(c.black)
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(Modifier.size(8.dp).clip(CircleShape).background(dot(tone)))
          Spacer(Modifier.width(10.dp))
          Text(data.visuals.message, style = V3Type.callout, color = c.onBlack, modifier = Modifier.weight(1f, fill = false))
          val action = data.visuals.actionLabel
          if (action != null) {
            Spacer(Modifier.width(8.dp))
            Text(
              action,
              style = V3Type.callout,
              color = PhoneV3Palette.iconActive(ir.vil3ntec.tohid.ui.theme.PhoneV3Dark),
              modifier = Modifier.clip(RoundedCornerShape(10.dp)).v3Pressable(label = action) { data.performAction() }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
      }
    }
  }
}

/** نقطهٔ گونه روی سطحِ تیره — رنگ‌های روشنِ شب تا روی وارونه دیده شوند */
private fun dot(tone: V3Tone) = PhoneV3Roles.tone(ir.vil3ntec.tohid.ui.theme.PhoneV3Dark, tone).fg
