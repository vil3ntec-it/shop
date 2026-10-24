package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Skeleton
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/**
 *  `reauth` — «تأییدِ دوبارهٔ هویت» روی هر صفحه (`ReauthHost` بازش می‌کند). منطق عیناً همان
 *  `ReauthDialog` ِ پیشین: حساب رمز دارد یا نه از خودِ سرور (`hasPassword`)؛ حسابِ بی‌رمز یا «رمز ندارم»
 *  ⇒ کد همان لحظه به ایمیل (وگرنه شمارهٔ) خودِ حساب؛ «انصراف» یعنی «انجام نده». تا درخواست در راه
 *  است، زدنِ بیرون و کشیدن و «برگشت» برگه را نمی‌بندند. `onDone` دقیقاً یک بار صدا زده می‌شود.
 */
@Composable
fun ReauthSheet(onDone: (Boolean) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val auth = remember(context) { Backend.auth(context) }
  var proof by remember { mutableStateOf<ReauthProof?>(null) }
  var destination by remember { mutableStateOf("") }
  var secret by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<String?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var failures by remember { mutableIntStateOf(0) }
  val answered = remember { booleanArrayOf(false) }
  var confirmed by remember { mutableStateOf(false) }
  fun answer(ok: Boolean) { if (!answered[0]) { answered[0] = true; onDone(ok) } }

  LaunchedEffect(Unit) {
    val me = Backend.account(context).me().valueOrNull()?.user
    //  همان ترتیبِ سرور: ایمیل، وگرنه شماره
    destination = me?.email?.takeIf { it.isNotBlank() } ?: me?.phone.orEmpty()
    proof = ReauthRules.proofFor(known = me != null, hasPassword = me?.hasPassword == true)
  }
  fun sendCode() {
    busy = true
    error = null
    scope.launch {
      auth.requestReauthCode()
        .onSuccess { note = if (destination.isBlank()) StrAuth.codeSentShort else StrAuth.codeSentTo(destination) }
        .onFailure { error = it.userMessage }
      busy = false
    }
  }
  //  حسابِ بی‌رمز (یا «کد بفرست»): کد همان لحظه می‌رود — کاربر خودش کارِ حساس را زده
  LaunchedEffect(proof) { if (proof == ReauthProof.CODE) sendCode() }

  V3Sheet(onDismiss = { answer(confirmed) }, title = StrAuth.reauthTitle, dismissible = !busy, detents = false) { closer ->
    fun submit() {
      val mode = proof ?: return
      busy = true
      error = null
      scope.launch {
        val done: ApiResult<Unit> = if (mode == ReauthProof.PASSWORD) auth.reauthWithPassword(secret) else auth.reauthWithCode(secret)
        done
          .onSuccess { confirmed = true; closer.close { answer(true) } }
          .onFailure { error = it.userMessage; failures++ }
        busy = false
      }
    }
    ReauthBody(
      proof = proof, secret = secret, busy = busy, note = note, error = error, failures = failures,
      onSecret = { secret = if (proof == ReauthProof.CODE) ReauthRules.codeDigits(it) else it },
      onUseCode = { secret = ""; proof = ReauthProof.CODE },
      onResend = ::sendCode,
      onSubmit = ::submit,
      onCancel = { closer.close() },
    )
  }
}

/** بدنهٔ برگه، بی حالتِ خودش (عکس‌ها همین را با رمز یا کد می‌کشند) */
@Composable
internal fun ReauthBody(
  proof: ReauthProof?,
  secret: String,
  busy: Boolean,
  note: String?,
  error: String?,
  failures: Int,
  onSecret: (String) -> Unit,
  onUseCode: () -> Unit,
  onResend: () -> Unit,
  onSubmit: () -> Unit,
  onCancel: () -> Unit,
) {
  val c = PhoneV3.colors
  val ready = ReauthRules.canSubmit(busy, proof, secret)
  Column(Modifier.fillMaxWidth()) {
    Text(
      StrAuth.reauthWhy + " " + if (proof == ReauthProof.CODE) StrAuth.reauthEnterCode else StrAuth.reauthEnterPassword,
      style = V3Type.callout, color = c.inkMuted,
    )
    Spacer(Modifier.height(16.dp))
    when (proof) {
      null -> V3Skeleton(rows = 1)
      ReauthProof.PASSWORD -> {
        DraftSecretField(
          secret, onSecret, StrAuth.reauthPassword, Modifier.fillMaxWidth().v3Shake(failures.takeIf { error != null && it > 0 }),
          enabled = !busy, onImeAction = { if (ready) onSubmit() },
        )
        if (!busy) V3TextButton(StrAuth.noPasswordSendCode, onUseCode)
      }
      ReauthProof.CODE -> {
        note?.let { Text(it, style = V3Type.footnote, color = c.inkMuted) }
        Spacer(Modifier.height(8.dp))
        V3TextField(
          secret, onSecret, StrAuth.verifyCode, Modifier.fillMaxWidth().v3Shake(failures.takeIf { error != null && it > 0 }),
          kind = V3FieldKind.NUMBER, enabled = !busy, onImeAction = { if (ready) onSubmit() },
        )
        if (!busy) V3TextButton(StrAuth.resendCodeAlt, onResend)
      }
    }
    V3Collapse(error != null) { V3Message(V3Tone.ERROR, error.orEmpty(), Modifier.fillMaxWidth()) }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrKit.cancel, onCancel, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL, enabled = !busy)
      V3Button(StrAuth.confirm, onSubmit, Modifier.weight(1f), enabled = ready, loading = busy, haptic = true)
    }
  }
}
