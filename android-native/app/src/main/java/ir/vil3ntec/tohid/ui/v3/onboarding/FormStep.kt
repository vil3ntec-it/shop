package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.onboarding.AuthRules
import ir.vil3ntec.tohid.core.onboarding.AuthUi
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Collapse
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconButton
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3SwipeAction
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.v3Shake

/**
 *  `auth?mode=signin|signup`: «خوش آمدید» با ایمیل و رمز، یا گامِ ۱ از ۳ ِ «ساخت حساب». کلیدِ
 *  دوتکهٔ بالای کارت راه را **در جا** عوض می‌کند: نام و تکرارِ رمز نرم باز و بسته می‌شوند. در ورود:
 *  «رمز را فراموش کرده‌اید؟» زیرِ رمز، «ورود با کد» فقط اگر سرور کد می‌فرستد، و ورودهای ذخیره‌شدهٔ
 *  همین گوشی بالای کارت (پر کردنِ ایمیل؛ رمز همیشه خواسته می‌شود).
 */
@Composable
internal fun FormStep(ui: AuthUi, env: AuthEnv, act: AuthActions, onBack: (() -> Unit)?) {
  val register = ui.register
  GateBackdrop {
    GateFrame(
      title = if (register) Str.createAccount else StrAuth.welcome,
      subtitle = if (register) StrAuth.registerSub else StrAuth.signInSub,
      onBack = onBack,
      stage = AuthRules.signupStage(ui),
      above = if (!register && env.saved.isNotEmpty()) ({ SavedSignIns(env, act) }) else null,
      footer = {
        GateLinks(StrAuth.haveStaffCode to { act.edit { it.openJoin() } }, StrAuth.continueWithout to act::skip)
      },
    ) {
      FormFields(ui, env, act)
    }
  }
}

@Composable
private fun ColumnScope.FormFields(ui: AuthUi, env: AuthEnv, act: AuthActions) {
  val focus = LocalFocusManager.current
  val register = ui.register
  val next: () -> Unit = { focus.moveFocus(FocusDirection.Next) }
  V3Segmented(listOf(Str.signIn, Str.createAccount), if (register) 1 else 0, { i -> act.edit { it.switchMode(i == 1) } })
  Spacer(Modifier.height(18.dp))
  V3Collapse(register) {
    Column {
      V3TextField(
        ui.name, { v -> act.edit { it.copy(name = v, error = null) } }, StrAuth.yourName,
        Modifier.fillMaxWidth(), placeholder = StrAuth.nameExample, onImeAction = next,
      )
      FieldGap()
    }
  }
  V3TextField(
    ui.email, { v -> act.edit { it.copy(email = v, error = null, emailIssue = null) } }, StrAuth.email,
    Modifier.fillMaxWidth().v3Shake(ui.failures.takeIf { ui.emailIssue != null && it > 0 }),
    kind = V3FieldKind.EMAIL, placeholder = EMAIL_EXAMPLE, error = ui.emailIssue?.text(), onImeAction = next,
  )
  FieldGap()
  DraftSecretField(
    ui.password, { v -> act.edit { it.copy(password = v, error = null, passwordIssue = null) } }, StrAuth.password,
    Modifier.fillMaxWidth().v3Shake(ui.failures.takeIf { ui.emailIssue == null && ui.passwordIssue != null && it > 0 }),
    placeholder = if (register) StrAuth.min8 else StrAuth.password,
    helper = if (register) StrAuth.min8 else null,
    error = ui.passwordIssue?.text(),
    imeAction = if (register) ImeAction.Next else ImeAction.Done,
    onImeAction = if (register) next else act::submitForm,
  )
  V3Collapse(!register) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
      V3TextButton(StrAuth.forgotPassword, { act.edit { it.openForgot() } })
    }
  }
  V3Collapse(register) {
    Column {
      FieldGap()
      val match = AuthRules.passwordsMatch(ui)
      DraftSecretField(
        ui.password2, { v -> act.edit { it.copy(password2 = v, error = null) } }, StrAuth.repeatPassword,
        Modifier.fillMaxWidth(),
        placeholder = StrAuth.samePasswordAgain,
        error = if (match == false) Str.passwordsDiffer else null,
        success = if (match == true) StrAuth.bothMatch else null,
        onImeAction = act::submitForm,
      )
    }
  }
  GateMessages(ui.error, ui.note, ui.failures)
  Spacer(Modifier.height(18.dp))
  SubmitButton(
    text = if (register) StrKit.continueLabel else StrAuth.signInToAccount,
    enabled = AuthRules.canSubmitForm(ui, env.ready),
    loading = ui.busy,
    done = env.done,
    onClick = act::submitForm,
  )
  if (!register && (env.otpReady || GOOGLE_LOGIN)) {
    OrDivider()
    if (env.otpReady) {
      V3Button(StrAuth.signInWithCode, act::requestOtp, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY, enabled = env.ready && !ui.busy, height = GATE_BUTTON)
    }
    if (GOOGLE_LOGIN) {
      if (env.otpReady) Spacer(Modifier.height(10.dp))
      GoogleButton(enabled = env.ready && !ui.busy, onClick = act::google)
    }
  }
  if (!env.ready) {
    Spacer(Modifier.height(14.dp))
    V3Message(V3Tone.INFO, StrAuth.noServerAccounts)
  }
}

/** «ورودهای ذخیره شده»: زدن ⇐ ایمیل پر می‌شود؛ ✕ یا کشیدن ⇐ از فهرستِ همین گوشی برداشته می‌شود */
@Composable
private fun SavedSignIns(env: AuthEnv, act: AuthActions) {
  V3Section(StrAuth.savedSignIns, padding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
    env.saved.forEachIndexed { i, entry ->
      key(entry.identifier) {
        if (i > 0) V3Divider(start = 64.dp)
        V3ListRow(
          title = entry.identifier,
          subtitle = entry.shop.takeIf { it.isNotBlank() }?.let { StrAuth.shopLabel(it) },
          icon = Icons.Outlined.Person,
          hue = V3Hue.SKY,
          chevron = false,
          trailing = { V3IconButton(Icons.Outlined.Close, StrAuth.removeSaved, size = 34.dp, iconSize = 16.dp) { act.forgetSaved(entry) } },
          endActions = listOf(V3SwipeAction(Icons.Outlined.DeleteOutline, StrAuth.removeSaved, V3ButtonKind.DANGER) { act.forgetSaved(entry) }),
          onClick = { act.pickSaved(entry) },
        )
      }
    }
  }
}

/** نمونهٔ نشانی در کادرِ خالی — نشانیِ ساختگی، نه نوشته */
private const val EMAIL_EXAMPLE = "name@gmail.com"
