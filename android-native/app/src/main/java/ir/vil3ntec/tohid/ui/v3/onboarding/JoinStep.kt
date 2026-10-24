package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3TextField

/**
 *  `auth?mode=join`: «پیوستن به دکان» با کدی که صاحبِ دکان می‌دهد — ایمیل و رمز لازم نیست.
 *  نام همان نامی است که صاحبِ دکان در فهرستِ کارمندان می‌بیند. کدِ پیک هم همین است (نقش را سرور
 *  می‌دهد)؛ «پیک هستید؟» صفحهٔ ورودِ پیک را با همین پیوستن باز می‌کند.
 */
@Composable
internal fun JoinStep(ui: AuthUi, env: AuthEnv, act: AuthActions, onBack: (() -> Unit)?) {
  val focus = LocalFocusManager.current
  GateBackdrop {
    GateFrame(
      title = StrAuth.joinShop,
      subtitle = StrAuth.staffCodeNote,
      onBack = onBack,
      footer = { GateLink(StrAuth.courierJoin, { act.edit { it.openCourier() } }) },
    ) {
      V3TextField(
        ui.name, { v -> act.edit { it.copy(name = v, error = null) } }, StrAuth.yourName,
        Modifier.fillMaxWidth(), placeholder = StrAuth.nameExample,
        onImeAction = { focus.moveFocus(FocusDirection.Next) },
      )
      FieldGap()
      V3TextField(
        ui.staffCode, { v -> act.edit { it.copy(staffCode = v.uppercase(), error = null) } }, Str.joinCode,
        Modifier.fillMaxWidth(),
        placeholder = StrAuth.joinCodeHint(StrAuth.ownerCodeHint),
        imeAction = ImeAction.Go, onImeAction = act::joinWithStaffCode,
      )
      GateMessages(ui.error, ui.note, ui.failures)
      Spacer(Modifier.height(18.dp))
      SubmitButton(StrAuth.join, enabled = !ui.busy && ui.staffCode.isNotBlank(), loading = ui.busy, done = env.done, onClick = act::joinWithStaffCode)
      if (!env.ready) {
        Spacer(Modifier.height(14.dp))
        V3Message(V3Tone.INFO, StrAuth.noServerAccounts)
      }
    }
  }
}
