package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.i18n.StrKit
import kotlinx.coroutines.delay

/**
 *  CodeStep — کدِ شش‌رقمی، مشترکِ ثبت‌نام (گامِ ۲ از ۳)، بازیابیِ رمز (با رمزِ تازه) و ورود با کد.
 *  صفحه‌کلیدِ عددی خودش بالا می‌آید و با ششمین رقم خودش می‌فرستد (جز بازیابی). شمارشِ «ارسالِ
 *  دوباره» از مهلتِ نوشته‌شده روی گوشی می‌آید، پس چرخاندن، رفتن و آمدن یا بستنِ برنامه آن را از صفر
 *  شروع نمی‌کند — و یک کدِ اضافه از سقفِ نرخ نمی‌رود.
 */
@Composable
internal fun CodeStep(ui: AuthUi, env: AuthEnv, act: AuthActions, onBack: (() -> Unit)?, initialCode: String = "") {
  val reset = ui.purpose == CodePurpose.RESET
  val destination = ui.email.trim()
  var code by rememberSaveable { mutableStateOf(initialCode) }
  var fresh by rememberSaveable { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  var wait by remember { mutableIntStateOf(env.secondsLeft(destination)) }
  //  هر ثانیه از روی مهلتِ گوشی؛ «ارسالِ دوباره» مهلتِ تازه را پس از پاسخِ سرور می‌نویسد
  LaunchedEffect(destination) {
    while (true) {
      wait = env.secondsLeft(destination)
      delay(1000)
    }
  }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() }; keyboard?.show() }
  val submit = { if (AuthRules.canSubmitCode(code, fresh, ui.busy, reset)) act.submitCode(code, fresh) }
  GateBackdrop {
    GateFrame(
      title = if (reset) Str.newPassword else StrAuth.enterCode,
      subtitle = if (reset) StrAuth.codeSentReset(destination) else StrAuth.codeSent6(destination),
      onBack = onBack,
      stage = AuthRules.signupStage(ui),
      footer = { onBack?.let { GateLink(StrAuth.changeAddress, it) } },
    ) {
      CodeCells(
        code = code,
        onCode = { digits ->
          code = digits
          if (AuthRules.autoSubmit(digits, ui.busy, reset)) { keyboard?.hide(); act.submitCode(digits, "") }
        },
        focus = focus,
        enabled = !env.done,
        shake = ui.failures.takeIf { ui.error != null && it > 0 },
        onDone = submit,
      )
      GateMessages(ui.error, ui.note, shake = 0)
      if (reset) {
        FieldGap()
        DraftSecretField(fresh, { fresh = it }, Str.newPassword, Modifier.fillMaxWidth(), placeholder = StrAuth.newPasswordMin8, onImeAction = submit)
      }
      Spacer(Modifier.height(18.dp))
      SubmitButton(
        text = when (ui.purpose) {
          CodePurpose.RESET -> StrAuth.setPasswordSignIn
          CodePurpose.REGISTER -> StrKit.continueLabel
          CodePurpose.OTP -> Str.signIn
        },
        enabled = AuthRules.canSubmitCode(code, fresh, ui.busy, reset),
        loading = ui.busy,
        done = env.done,
        onClick = submit,
      )
      Spacer(Modifier.height(6.dp))
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        GateLink(
          if (wait > 0) StrAuth.resendIn(wait) else StrAuth.resendCode,
          act::resend,
          enabled = wait == 0 && !ui.busy,
        )
      }
    }
  }
}
