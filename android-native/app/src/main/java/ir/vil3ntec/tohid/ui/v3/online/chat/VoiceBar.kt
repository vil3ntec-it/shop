package ir.vil3ntec.tohid.ui.v3.online.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.ChatMedia
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.delay

/**
 *  پیامِ صوتی (همان قاعده‌های `VoiceBar` ِ پیشین): اجازهٔ صریحِ میکروفون، **نگه‌داشتن** ⇐ ضبط و رها
 *  کردن ⇐ پایان، سقفِ ۵ دقیقه، پیش از فرستادن مدت و حجم (بزرگ‌تر از سقف ⇐ نمی‌رود) و «لغو». روی
 *  کامپیوتر رمزگذار نیست ⇐ دکمه‌ای نیست و یک جملهٔ کوتاه چرایش را می‌گوید.
 */
@Stable
internal class VoiceState(private val recorder: ChatMedia.Recorder?) {
  var recording by mutableStateOf(false)
  var elapsed by mutableLongStateOf(0L)
  var ready by mutableStateOf<Pair<ByteArray, Long>?>(null)

  fun start(onBanner: (Pair<String, Boolean>?) -> Unit) {
    val r = recorder ?: return
    runCatching { r.start() }
      .onSuccess { recording = true; ready = null; onBanner(null) }
      .onFailure { onBanner(StrOnline.ctRecFailed(it.message ?: "") to true) }
  }

  fun stop() {
    if (!recording) return
    ready = recorder?.stop()
    recording = false
  }

  fun cancel() { recorder?.cancel(); recording = false; ready = null }

  fun dispose() { recorder?.cancel() }

  fun tick() { elapsed = recorder?.elapsedMs ?: 0L }
}

@Composable
internal fun rememberVoiceState(): VoiceState {
  val context = LocalContext.current
  val state = remember { VoiceState(if (ChatMedia.voiceSupported) ChatMedia.Recorder(context) else null) }
  DisposableEffect(Unit) { onDispose { state.dispose() } }
  LaunchedEffect(state.recording) {
    while (state.recording) {
      state.tick()
      if (state.elapsed >= ChatMedia.VOICE_MAX_MS) state.stop()
      delay(TICK_MS)
    }
  }
  return state
}

/** دکمهٔ میکروفون: نگه‌داشتن ⇐ ضبط؛ بی اجازه ⇐ اول اجازه می‌خواهد */
@Composable
internal fun VoiceMic(state: VoiceState, enabled: Boolean, onBanner: (Pair<String, Boolean>?) -> Unit) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  val banner by rememberUpdatedState(onBanner)
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (!granted) banner(StrOnline.ctNoMic to true)
  }
  val pair = if (state.recording) PhoneV3Roles.button(c, V3ButtonKind.DANGER) else if (enabled) PhoneV3Roles.button(c, V3ButtonKind.NEUTRAL) else PhoneV3Roles.disabled(c)
  val pulse = if (state.recording && Motion.enabled) 1.12f else 1f
  Box(
    Modifier
      .size(46.dp)
      .graphicsLayer { scaleX = pulse; scaleY = pulse }
      .clip(CircleShape)
      .background(pair.bg)
      .semantics { contentDescription = StrOnline.ctHoldToRecord }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onPress = {
          val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
          if (!granted) { ask.launch(Manifest.permission.RECORD_AUDIO); return@detectTapGestures }
          Haptics.confirm(context)
          state.start(banner)
          tryAwaitRelease()
          state.stop()
        })
      },
    contentAlignment = Alignment.Center,
  ) { Icon(Icons.Outlined.Mic, contentDescription = null, tint = pair.fg, modifier = Modifier.size(22.dp)) }
}

/** ردیفِ بالای نوشتن: «در حالِ ضبط ۰:۱۲» یا «پیامِ صوتی ۰:۱۲ · ۴۰ کیلوبایت» با «فرستادنِ صدا»/«لغو» */
@Composable
internal fun VoiceStatus(state: VoiceState, limit: Long, enabled: Boolean, onSend: (ByteArray, Long) -> Unit) {
  val c = PhoneV3.colors
  val ready = state.ready
  when {
    !ChatMedia.voiceSupported -> Text(StrOnline.ctNoVoiceDesktop, style = V3Type.caption, color = c.inkMuted, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    state.recording -> Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(PhoneV3Roles.errorText(c)))
      Spacer(Modifier.width(8.dp))
      Text(StrOnline.ctRecording(ChatModel.mmss(state.elapsed)), style = V3Type.callout, color = PhoneV3Roles.errorText(c), modifier = Modifier.weight(1f))
      V3TextButton(StrKit.cancel, { state.cancel() }, danger = true)
    }
    ready != null -> {
      val (bytes, ms) = ready
      val big = limit > 0 && bytes.size > limit
      Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          StrOnline.ctVoiceReady(ChatModel.mmss(ms), StrOnline.kb(bytes.size.toLong())) + if (big) " — " + StrOnline.ctTooBig else "",
          style = V3Type.callout, color = if (big) PhoneV3Roles.errorText(c) else c.ink, modifier = Modifier.weight(1f),
        )
        V3TextButton(StrKit.cancel, { state.ready = null })
        V3Button(StrOnline.ctSendVoice, { onSend(bytes, ms); state.ready = null }, enabled = !big && enabled, height = 38.dp, haptic = true)
      }
    }
  }
}

private const val TICK_MS = 300L
