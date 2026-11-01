package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.onboarding.AuthUi
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  `auth?mode=forgot`: ایمیلِ حساب ⇐ کدِ شش‌رقمی (`CodeStep` با رمزِ تازه) ⇐ «گذاشتن رمز و ورود».
 *  کد فقط به ایمیلِ درست‌قالب می‌رود؛ پاسخِ سرور برای ایمیلِ بی‌حساب همان پاسخِ همیشگی است.
 */
@Composable
internal fun ForgotStep(ui: AuthUi, env: AuthEnv, act: AuthActions, onBack: (() -> Unit)?) {
  GateBackdrop {
    GateFrame(title = StrAuth.forgotTitle, subtitle = StrAuth.forgotSub, onBack = onBack) {
      V3TextField(
        ui.email, { v -> act.edit { it.copy(email = v, error = null, emailIssue = null) } }, StrAuth.email,
        Modifier.fillMaxWidth().v3Shake(ui.failures.takeIf { ui.emailIssue != null && it > 0 }),
        kind = V3FieldKind.EMAIL, error = ui.emailIssue?.text(),
        imeAction = ImeAction.Send, onImeAction = act::requestReset,
      )
      GateMessages(ui.error, ui.note, ui.failures)
      Spacer(Modifier.height(18.dp))
      SubmitButton(StrAuth.sendCode, enabled = env.ready && !ui.busy, loading = ui.busy, done = false, onClick = act::requestReset)
    }
  }
}
