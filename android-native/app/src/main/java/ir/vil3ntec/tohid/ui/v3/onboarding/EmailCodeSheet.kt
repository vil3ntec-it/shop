package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.onboarding.AuthRules
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.CodeLoginRepository
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** `auth/code` — برگهٔ «ورود با ایمیل»: ایمیل ⇐ شش رقم ⇐ ورود (`CodeLoginRepository`، بندِ ۲۱٫۴) */
@Composable
internal fun EmailCodeSheet(onClose: () -> Unit, onSignedIn: () -> Unit) {
  V3Sheet(onDismiss = onClose, title = StrAuth.emailSignIn, detents = false) { closer ->
    EmailCodeContent(onClose = { closer.close() }, onSignedIn = { closer.close(onSignedIn) })
  }
}

/** یک لحظه از برگه — پیام‌ها با رنگِ خطا یا بی‌رنگ، همان‌طور که پیش بود */
data class EmailCodeUi(
  val email: String = "",
  val requestId: String = "",
  val masked: String = "",
  val code: String = "",
  val message: String = "",
  val bad: Boolean = false,
  val busy: Boolean = false,
  val failures: Int = 0,
) {
  fun say(text: String, isBad: Boolean = false): EmailCodeUi =
    copy(message = text, bad = isBad, failures = if (isBad) failures + 1 else failures)
}

/**
 *  محتوای برگه (در `MySubscription` ِ پیشین هم همین، درونِ برگهٔ خودش). منطق عیناً همان
 *  `CodeLoginSheet`: درخواستِ کد، پرسیدنِ «ایمیل رفت یا نه» شش بار با فاصلهٔ ۲٫۵ ثانیه، شمارشِ
 *  معکوسِ ارسالِ دوباره، و پس از ورود بستنِ کلیدهای همگام‌سازی به همین حساب **پیش از** اولین
 *  همگام‌سازی. ⛔ کادرِ نشانیِ سرور این‌جا نیست و نباید بیاید.
 */
@Composable
fun EmailCodeContent(onClose: () -> Unit, onSignedIn: () -> Unit, showTitle: Boolean = false, initial: EmailCodeUi = EmailCodeUi()) {
  val context = LocalContext.current
  val repo = remember { Backend.codeLogin(context) }
  val scope = rememberCoroutineScope()
  var ui by remember { mutableStateOf(initial) }
  var resendAt by remember { mutableLongStateOf(0L) }
  var left by remember { mutableStateOf(0) }
  val focus = remember { FocusRequester() }
  val device = android.os.Build.MODEL ?: AuthRules.DEVICE_FALLBACK

  LaunchedEffect(resendAt) {
    while (resendAt > 0) {
      left = ((resendAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
      if (left == 0) break
      delay(500)
    }
  }

  suspend fun submit() {
    if (ui.code.length != AuthRules.CODE_LENGTH) { ui = ui.say(StrAuth.sixDigits, true); return }
    ui = ui.copy(busy = true).say(StrAuth.verifying)
    val out = repo.verify(requestId = ui.requestId, code = ui.code, deviceId = SyncStore(context).deviceUid, deviceName = device)
    ui = ui.copy(busy = false)
    out.onSuccess { session ->
      //  ⛔ کلیدهای همگام‌سازی به شناسهٔ همین حساب، پیش از اولین همگام‌سازی
      if (session.userId.isNotBlank()) SyncStateV1.setCurrentAccount(context, session.userId)
      SyncV1Engine.of(context).rebind()
      SyncV1Worker.now(context)
      ui = ui.say(StrAuth.signedIn)
      onSignedIn()
    }.onFailure { failure ->
      //  کدِ غلط ⇐ خانه‌ها خالی، تا شش بار پاک کردن لازم نباشد
      ui = ui.copy(code = "").say(failure.userMessage, true)
      runCatching { focus.requestFocus() }
    }
  }

  fun send() {
    val value = ui.email.trim()
    if (!value.contains('@') || !value.contains('.')) { ui = ui.say(StrAuth.emailInvalid, true); return }
    scope.launch {
      ui = ui.copy(busy = true).say(StrAuth.sendingDots)
      val out = repo.requestCode(value, device)
      ui = ui.copy(busy = false)
      out.onSuccess { r ->
        ui = ui.copy(requestId = r.requestId, masked = r.maskedEmail.ifBlank { value }).say("")
        resendAt = System.currentTimeMillis() + r.resendAfter * 1000L
        runCatching { focus.requestFocus() }
        //  «ایمیل رفت یا نرفت» — چند بار می‌پرسیم و بعد رها
        repeat(6) {
          delay(2500)
          if (ui.requestId.isBlank()) return@repeat
          repo.deliveryStatus(ui.requestId).onSuccess { st ->
            when (st.state) {
              "failed" -> ui = ui.say(StrAuth.emailFailed(st.reason), true)
              "sent" -> ui = ui.say(StrAuth.emailSent)
            }
          }
        }
      }.onFailure { ui = ui.say(it.userMessage, true) }
    }
  }

  fun resend() {
    scope.launch {
      repo.requestCode(ui.email.trim(), device)
        .onSuccess { resendAt = System.currentTimeMillis() + it.resendAfter * 1000L; ui = ui.say(StrAuth.codeResentDot) }
        .onFailure { ui = ui.say(it.userMessage, true) }
    }
  }

  EmailCodeBody(
    ui = ui, left = left, focus = focus, showTitle = showTitle,
    onEmail = { ui = ui.copy(email = it) },
    onCode = { d ->
      ui = ui.copy(code = d)
      if (d.length == AuthRules.CODE_LENGTH && !ui.busy) scope.launch { submit() }
    },
    onSend = ::send,
    onSubmit = { scope.launch { submit() } },
    onResend = ::resend,
    onOtherEmail = { ui = ui.copy(requestId = "", code = "").say("") },
    onClose = onClose,
  )
}

/** نمایشِ برگه، بی حالتِ خودش (عکس‌ها همین را می‌کشند) */
@Composable
internal fun EmailCodeBody(
  ui: EmailCodeUi,
  left: Int,
  focus: FocusRequester,
  showTitle: Boolean,
  onEmail: (String) -> Unit,
  onCode: (String) -> Unit,
  onSend: () -> Unit,
  onSubmit: () -> Unit,
  onResend: () -> Unit,
  onOtherEmail: () -> Unit,
  onClose: () -> Unit,
) {
  val c = PhoneV3.colors
  Column(Modifier.fillMaxWidth()) {
    if (showTitle) {
      Text(StrAuth.emailSignIn, style = V3Type.title, color = c.ink)
      Spacer(Modifier.height(8.dp))
    }
    Text(StrAuth.emailCodeNote, style = V3Type.callout, color = c.inkMuted)
    Spacer(Modifier.height(16.dp))
    V3PageSwitch(target = ui.requestId.isNotBlank(), depth = { if (it) 1 else 0 }) { sent ->
      Column(Modifier.fillMaxWidth()) {
        if (!sent) {
          V3TextField(
            ui.email, onEmail, StrAuth.email, Modifier.fillMaxWidth(),
            kind = V3FieldKind.EMAIL, placeholder = EMAIL_PLACEHOLDER, imeAction = ImeAction.Send, onImeAction = onSend,
          )
          Spacer(Modifier.height(16.dp))
          SubmitButton(StrAuth.sendCode, enabled = !ui.busy, loading = ui.busy, done = false, onClick = onSend)
        } else {
          Text(StrAuth.codeSentEnter(ui.masked), style = V3Type.callout, color = c.ink)
          Spacer(Modifier.height(12.dp))
          CodeCells(
            ui.code, onCode, focus, enabled = !ui.busy,
            shake = ui.failures.takeIf { ui.bad && it > 0 },
            normalize = CodeLoginRepository::digitsOnly,
          )
          Spacer(Modifier.height(10.dp))
          Text(
            if (left > 0) StrAuth.resendWait(left) else StrAuth.noCodeSpam,
            style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          )
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            GateLink(StrAuth.resendCodeAlt, onResend, enabled = left == 0 && !ui.busy)
          }
          Spacer(Modifier.height(6.dp))
          SubmitButton(Str.signIn, enabled = !ui.busy, loading = ui.busy, done = false, onClick = onSubmit)
          Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { V3TextButton(StrAuth.otherEmail, onOtherEmail) }
        }
      }
    }
    V3Collapse(visible = ui.message.isNotBlank()) {
      Column {
        Spacer(Modifier.height(10.dp))
        if (ui.bad) V3Message(V3Tone.ERROR, ui.message)
        else Text(ui.message, style = V3Type.footnote, color = c.inkMuted)
      }
    }
    Spacer(Modifier.height(8.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { V3TextButton(StrAuth.laterNoAccount, onClose) }
  }
}

private const val EMAIL_PLACEHOLDER = "you@example.com"
