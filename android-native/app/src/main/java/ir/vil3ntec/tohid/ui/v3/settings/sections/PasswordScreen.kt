package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Password
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Form
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/**
 *  `settings/security/password`: رمزِ فعلی، رمزِ تازه (≥ ۸) و تکرارش با سنجشِ درجا؛
 *  دکمهٔ چسبانِ «تغییر رمز» همان `auth.changePassword` را می‌زند. بیرون از حساب
 *  این بخش معنا ندارد ⇒ حالتِ خالی.
 */
@Composable
internal fun RouteScope.PasswordScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val title = SettingsLayout.labelOf("تغییر رمز حساب")
  val signedIn = remember { SyncStore(context).accessToken != null }
  if (!signedIn) {
    V3Scaffold(title = title, onBack = onBack) { pad ->
      Box(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {
        V3EmptyState(Icons.Outlined.Password, StrSettings.passwordSignedOut, body = StrSettings.serverPassword)
      }
    }
    return
  }
  val auth = remember { Backend.auth(context) }
  var current by remember { mutableStateOf("") }
  var fresh by remember { mutableStateOf("") }
  var repeat by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var failed by remember { mutableIntStateOf(0) }
  val check = PasswordModel.check(current, fresh, repeat)
  V3Form(
    title = title,
    primary = StrSettings.changePassword,
    onPrimary = {
      busy = true
      scope.launch {
        auth.changePassword(current, fresh)
          .onSuccess {
            current = ""; fresh = ""; repeat = ""
            Haptics.success(context)
            toasts.show(StrSettings.passwordChanged)
            onBack?.invoke()
          }
          .onFailure { failed++; toasts.show(it.userMessage) }
        busy = false
      }
    },
    onBack = onBack,
    primaryEnabled = check.ready,
    loading = busy,
    dirty = current.isNotEmpty() || fresh.isNotEmpty() || repeat.isNotEmpty(),
  ) {
    SettingsNote(StrSettings.serverPassword)
    //  رمزِ نادرست: لرزشِ کوتاهِ فرم (MOTION-APPLE) کنارِ پیام
    V3Section(null, Modifier.v3Shake(failed.takeIf { it > 0 }), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
      V3TextField(current, { current = it }, StrSettings.currentPassword, Modifier.fillMaxWidth(), kind = V3FieldKind.PASSWORD, required = true)
      Spacer(Modifier.height(14.dp))
      V3TextField(fresh, { fresh = it }, Str.newPassword, Modifier.fillMaxWidth(), kind = V3FieldKind.PASSWORD, helper = StrSettings.pwdMin8, error = check.freshError, required = true)
      Spacer(Modifier.height(14.dp))
      V3TextField(repeat, { repeat = it }, StrSettings.repeatPassword, Modifier.fillMaxWidth(), kind = V3FieldKind.PASSWORD, error = check.repeatError, required = true, imeAction = ImeAction.Done)
    }
  }
}
