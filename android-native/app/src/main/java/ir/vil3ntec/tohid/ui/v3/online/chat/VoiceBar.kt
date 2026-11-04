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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.chat.VoiceFlow
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
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
 *
 *  دسترس‌پذیری (WCAG 2.1 AA ‏2.1.1/2.5.1/4.1.3): نگه‌داشتن برای صفحه‌خوان شدنی نیست، پس همان ضبط با **زدن** هم
 *  هست — «دوبار بزنید» و کارهای سفارشیِ «شروعِ ضبط» / «پایانِ ضبط و فرستادن» / «لغو»؛ در ضبطِ زدنی ردیفِ ضبط
 *  «فرستادنِ صدا» ِ دیدنی هم دارد. حالت‌ها و گذرها `VoiceFlow` ِ هسته است؛ ناحیهٔ زندهٔ درونِ دکمه فقط با گذرِ حالت
 *  حرف می‌زند، نه با تیکِ زمان.
 */
@Stable
internal class VoiceState(private val recorder: ChatMedia.Recorder?) {
  var phase by mutableStateOf(VoiceFlow.Phase.IDLE)
    private set
  var elapsed by mutableLongStateOf(0L)
  var ready by mutableStateOf<Pair<ByteArray, Long>?>(null)
    private set

  /** آنچه ناحیهٔ زنده می‌گوید؛ پس از فرستادن/لغو چند ثانیه می‌ماند و پاک می‌شود */
  var say by mutableStateOf<VoiceFlow.Say?>(null)

  val recording: Boolean get() = phase.recording

  /** فشار (`tap = false`، نگه‌داشتن) یا «شروعِ ضبط» (`tap = true`، زدن/صفحه‌خوان) */
  fun start(tap: Boolean, onBanner: (Pair<String, Boolean>?) -> Unit) {
    val r = recorder ?: return
    val s = VoiceFlow.next(phase, if (tap) VoiceFlow.Input.START else VoiceFlow.Input.PRESS)
    if (s.effect != VoiceFlow.Effect.START) return
    runCatching { r.start() }
      .onSuccess { phase = s.phase; ready = null; say = s.say; onBanner(null) }
      .onFailure { onBanner(StrOnline.ctRecFailed(it.message ?: "") to true) }
  }

  /** رها کردنِ انگشت: فقط ضبطِ نگه‌داشتنی را می‌بندد */
  fun release() = on(VoiceFlow.Input.RELEASE)

  fun limitReached() = on(VoiceFlow.Input.LIMIT)

  /** «پایانِ ضبط و فرستادن»: اگر جا شد می‌رود، وگرنه ردیفِ پیش از فرستادن با دلیل */
  fun finish(limit: Long, canWrite: Boolean, onSend: (ByteArray, Long) -> Unit) = on(VoiceFlow.Input.FINISH, limit, canWrite, onSend)

  fun cancel() = on(VoiceFlow.Input.CANCEL)

  fun send(onSend: (ByteArray, Long) -> Unit) = on(VoiceFlow.Input.SEND, onSend = onSend)

  private fun on(i: VoiceFlow.Input, limit: Long = 0, canWrite: Boolean = false, onSend: (ByteArray, Long) -> Unit = { _, _ -> }) {
    var s = VoiceFlow.next(phase, i)
    var got = ready
    when (s.effect) {
      VoiceFlow.Effect.NONE, VoiceFlow.Effect.START -> Unit
      VoiceFlow.Effect.STOP, VoiceFlow.Effect.STOP_SEND -> {
        got = recorder?.stop()
        s = VoiceFlow.settle(s.effect == VoiceFlow.Effect.STOP_SEND, got?.first?.size?.toLong(), limit, canWrite)
      }
      VoiceFlow.Effect.SEND, VoiceFlow.Effect.DISCARD -> Unit
      VoiceFlow.Effect.CANCEL -> recorder?.cancel()
    }
    if (s.effect == VoiceFlow.Effect.NONE && s.phase == phase && s.say == null) return
    phase = s.phase
    ready = if (s.phase == VoiceFlow.Phase.READY) got else null
    if (s.say != null) say = s.say
    if (s.effect == VoiceFlow.Effect.SEND) got?.let { (bytes, ms) -> onSend(bytes, ms) }
  }

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
      if (state.elapsed >= ChatMedia.VOICE_MAX_MS) state.limitReached()
      delay(TICK_MS)
    }
  }
  LaunchedEffect(state.say, state.phase) {
    if (state.say != null && state.phase == VoiceFlow.Phase.IDLE) { delay(SAY_MS); state.say = null }
  }
  return state
}

/**
 *  دکمهٔ میکروفون: نگه‌داشتن ⇐ ضبط؛ بی اجازه ⇐ اول اجازه می‌خواهد. برای صفحه‌خوان «دوبار بزنید» و کارهای سفارشی
 *  (`VoiceFlow.actions`) همان ضبط را با زدن می‌کنند؛ اجازه‌ای که از زدن خواسته شد پس از «اجازه» خودش ضبط را آغاز می‌کند.
 */
@Composable
internal fun VoiceMic(state: VoiceState, enabled: Boolean, limit: Long, onBanner: (Pair<String, Boolean>?) -> Unit, onSend: (ByteArray, Long) -> Unit) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  val banner by rememberUpdatedState(onBanner)
  val send by rememberUpdatedState(onSend)
  var askedByTap by remember { mutableStateOf(false) }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (!granted) banner(StrOnline.ctNoMic to true)
    else if (askedByTap) { Haptics.confirm(context); state.start(tap = true, banner) }
    askedByTap = false
  }
  fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
  fun act(a: VoiceFlow.Action) = when (a) {
    VoiceFlow.Action.START -> if (granted()) { Haptics.confirm(context); state.start(tap = true, banner) } else { askedByTap = true; ask.launch(Manifest.permission.RECORD_AUDIO) }
    VoiceFlow.Action.FINISH -> state.finish(limit, enabled, send)
    VoiceFlow.Action.CANCEL -> state.cancel()
  }
  val actions = VoiceFlow.actions(state.phase)
  val pair = if (state.recording) PhoneV3Roles.button(c, V3ButtonKind.DANGER) else if (enabled) PhoneV3Roles.button(c, V3ButtonKind.NEUTRAL) else PhoneV3Roles.disabled(c)
  val pulse = if (state.recording && Motion.enabled) 1.12f else 1f
  Box(
    Modifier
      .size(46.dp)
      .graphicsLayer { scaleX = pulse; scaleY = pulse }
      .clip(CircleShape)
      .background(pair.bg)
      .semantics {
        contentDescription = StrOnline.ctHoldToRecord
        if (enabled) {
          onClick(actionLabel(actions.first())) { act(actions.first()); true }
          customActions = actions.map { a -> CustomAccessibilityAction(actionLabel(a)) { act(a); true } }
        } else {
          disabled()
        }
      }
      .pointerInput(enabled) {
        if (!enabled) return@pointerInput
        detectTapGestures(onPress = {
          if (!granted()) { askedByTap = false; ask.launch(Manifest.permission.RECORD_AUDIO); return@detectTapGestures }
          if (!state.recording) Haptics.confirm(context)
          state.start(tap = false, banner)
          tryAwaitRelease()
          state.release()
        })
      },
    contentAlignment = Alignment.Center,
  ) {
    Icon(Icons.Outlined.Mic, contentDescription = null, tint = pair.fg, modifier = Modifier.size(22.dp))
    //  ناحیهٔ زنده: همیشه هست (پیدا شدنِ گرهٔ تازه خوانده نمی‌شود)، و فقط با گذرِ حالت عوض می‌شود
    val said = state.say?.let(::sayText)
    Box(Modifier.matchParentSize().semantics { liveRegion = LiveRegionMode.Polite; if (said != null) contentDescription = said })
  }
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
      //  ضبطِ زدنی انگشتی روی دکمه ندارد که رها شود: پایان و فرستادن همین‌جا هم دیده می‌شود
      if (state.phase == VoiceFlow.Phase.TAP) V3Button(StrOnline.ctSendVoice, { state.finish(limit, enabled, onSend) }, height = 38.dp, haptic = true)
    }
    ready != null -> {
      val (bytes, ms) = ready
      val big = VoiceFlow.tooBig(bytes.size.toLong(), limit)
      Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          StrOnline.ctVoiceReady(ChatModel.mmss(ms), OnlineFmt.kb(bytes.size.toLong())) + if (big) " — " + StrOnline.ctTooBig else "",
          style = V3Type.callout, color = if (big) PhoneV3Roles.errorText(c) else c.ink, modifier = Modifier.weight(1f),
        )
        V3TextButton(StrKit.cancel, { state.cancel() })
        V3Button(StrOnline.ctSendVoice, { state.send(onSend) }, enabled = !big && enabled, height = 38.dp, haptic = true)
      }
    }
  }
}

private fun actionLabel(a: VoiceFlow.Action): String = when (a) {
  VoiceFlow.Action.START -> StrA11y.recStart
  VoiceFlow.Action.FINISH -> StrA11y.recFinish
  VoiceFlow.Action.CANCEL -> StrKit.cancel
}

private fun sayText(s: VoiceFlow.Say): String = when (s) {
  VoiceFlow.Say.RECORDING -> StrA11y.recOn
  VoiceFlow.Say.READY -> StrA11y.recReady
  VoiceFlow.Say.SENT -> StrA11y.recSent
  VoiceFlow.Say.CANCELLED -> StrA11y.recCancelled
  VoiceFlow.Say.TOO_BIG -> StrA11y.recTooBig
  VoiceFlow.Say.EMPTY -> StrA11y.recEmpty
}

private const val TICK_MS = 300L

/** پیامِ «فرستاده شد/لغو شد» ِ صفحه‌خوان پس از این پاک می‌شود تا خواندنِ دکمه کهنه نشود */
private const val SAY_MS = 4_000L
