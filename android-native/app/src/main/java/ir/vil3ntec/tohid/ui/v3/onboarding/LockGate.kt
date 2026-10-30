package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.onboarding.PinEntry
import ir.vil3ntec.tohid.core.onboarding.PinRules
import ir.vil3ntec.tohid.core.onboarding.PinSetup
import ir.vil3ntec.tohid.data.Fingerprint
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.delay

/**
 *  `lock` — دروازهٔ قفلِ برنامه، پیش از هر چیز. اثرِ انگشت میان‌بُر است نه جایگزین: به‌محضِ باز شدن
 *  یک بار خودش می‌پرسد و انصراف ⇐ صفحه‌کلید سرِ جایش؛ گوشیِ بی حسگر کلیدش را نمی‌بیند. رمزِ غلط ⇐
 *  نقطه‌ها قرمز و لرزان، پیامِ «رمز درست نیست» و پس از ۷۰۰ms دوباره عادی. قفلِ بی‌کاری همین را باز
 *  می‌کند و پیوندِ نگه‌داشته پس از باز شدن می‌آید (پوسته).
 */
@Composable
fun LockGate(onUnlocked: () -> Unit) {
  val context = LocalContext.current
  val lock = remember { LockStore(context) }
  val length = remember { lock.length }
  var entry by remember { mutableStateOf(PinEntry(length)) }
  var wrong by remember { mutableStateOf(false) }
  var wrongs by remember { mutableIntStateOf(0) }
  var opened by remember { mutableStateOf(false) }
  val activity = context as? androidx.fragment.app.FragmentActivity
  val canFinger = remember(activity) { activity != null && Fingerprint.available(context) }
  var asked by rememberSaveable { mutableStateOf(false) }
  val unlock = { opened = true; onUnlocked() }
  fun askFinger() { activity?.let { Fingerprint.ask(it, onOk = unlock) } }
  LaunchedEffect(canFinger) { if (canFinger && !asked) { asked = true; askFinger() } }
  LaunchedEffect(wrong) { if (wrong) { delay(PinRules.WRONG_HOLD_MS); wrong = false } }

  LockBody(
    title = StrAuth.lockTitle,
    subtitle = if (wrong) StrAuth.lockWrong else StrAuth.lockSub,
    error = wrong,
    length = length,
    filled = entry.digits.length,
    shake = wrongs.takeIf { it > 0 },
    ok = opened,
    onDigit = { d ->
      val next = entry.press(d)
      entry = next
      if (next.full) {
        if (lock.matches(next.digits)) unlock()
        else { wrong = true; wrongs++; entry = next.clear() }
      }
    },
    onErase = { entry = entry.erase() },
    onFinger = if (canFinger) ::askFinger else null,
  )
}

/**
 *  `lock/setup` — یک بار پس از ساختنِ حساب: «یک رمز شش رقمی بگذارید» ⇐ «همان رمز را دوباره بزنید».
 *  یکی نبود ⇐ «دو بار یکی نبود — از اول». اجباری نیست: «فعلاً نه» همان `onDone` است (پوسته
 *  `lockDeclined` را می‌نویسد، مثلِ پیش).
 */
@Composable
fun LockSetupGate(onDone: () -> Unit) {
  val context = LocalContext.current
  val lock = remember { LockStore(context) }
  var setup by remember { mutableStateOf(PinSetup(LockStore.NEW_PIN_LEN)) }
  var mismatches by remember { mutableIntStateOf(0) }
  LockBody(
    title = if (setup.confirming) StrAuth.lockAgain else StrAuth.lockSetFirst,
    subtitle = if (setup.mismatch) StrAuth.lockMismatch else StrAuth.lockSetupNote,
    error = setup.mismatch,
    length = setup.length,
    filled = setup.shown.length,
    shake = mismatches.takeIf { it > 0 },
    onDigit = { d ->
      val (next, pin) = setup.press(d)
      if (next.mismatch && setup.confirming && !next.confirming) mismatches++
      setup = next
      if (pin != null) { lock.set(pin); onDone() }
    },
    onErase = { setup = setup.erase() },
    onFinger = null,
    footer = { GateLink(StrAuth.notNow, onDone) },
  )
}

/** نمایشِ هر دو دروازهٔ قفل، بی حالتِ خودش (عکس‌ها همین را می‌کشند) */
@Composable
internal fun LockBody(
  title: String,
  subtitle: String,
  error: Boolean,
  length: Int,
  filled: Int,
  shake: Any?,
  onDigit: (Char) -> Unit,
  onErase: () -> Unit,
  onFinger: (() -> Unit)?,
  ok: Boolean = false,
  footer: (@Composable () -> Unit)? = null,
) {
  val c = PhoneV3.colors
  val stagger = rememberV3Stagger()
  GateBackdrop {
    Box(
      Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Column(Modifier.v3Stagger(stagger, "head", 0), horizontalAlignment = Alignment.CenterHorizontally) {
          BrandMark()
          Spacer(Modifier.height(18.dp))
          Text(title, style = V3Type.largeTitle, color = c.ink, textAlign = TextAlign.Center)
          Spacer(Modifier.height(6.dp))
          Text(
            subtitle,
            style = V3Type.body,
            color = if (error) PhoneV3Roles.errorText(c) else c.inkMuted,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(28.dp))
          PinDots(length, filled, wrong = error, shake = shake, ok = ok)
        }
        Spacer(Modifier.height(36.dp))
        Box(Modifier.v3Stagger(stagger, "pad", 1)) { PinPad(onDigit, onErase, onFinger) }
        if (footer != null) {
          Spacer(Modifier.height(16.dp))
          footer()
        }
      }
    }
  }
}
