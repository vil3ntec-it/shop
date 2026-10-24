package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import ir.vil3ntec.tohid.ui.v3.settings.cameraGranted

/** برگهٔ اسکنِ گروهِ فروش، به‌عنوانِ نتیجه (`nav.openForResult`) */
private val SCAN_TEST = RoutePaths.withQuery("scan", "for" to "test")

/**
 *  `settings/devices/camera`: اجازهٔ دوربین برای بارکدخوان — وضعیت، «درخواست
 *  دسترسی دوربین» و «آزمایش دوربین» که برگهٔ اسکن را باز می‌کند و کدِ خوانده‌شده را
 *  همین‌جا نشان می‌دهد.
 */
@Composable
internal fun RouteScope.CameraScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var granted by remember { mutableStateOf(cameraGranted(context)) }
  var result by remember { mutableStateOf<String?>(null) }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
    granted = ok
    toasts.show(if (ok) StrSettings.cameraReady else StrSettings.cameraDenied)
  }
  SettingsPageFrame(title = SettingsLayout.labelOf(SettingsLayout.CAMERA), onBack = onBack) {
    item(key = "status") {
      V3Message(
        if (granted) V3Tone.SUCCESS else V3Tone.WARNING,
        if (granted) StrSettings.permitted else StrSettings.notPermitted,
        Modifier.padding(top = 8.dp),
        body = StrSettings.cameraNeeded,
      )
    }
    item(key = "actions") {
      Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (!granted) {
          V3Button(StrSettings.requestCamera, { ask.launch(android.Manifest.permission.CAMERA) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.QrCodeScanner)
          Spacer(Modifier.height(10.dp))
        }
        V3Button(
          StrSettings.testCamera,
          { nav.openForResult(SCAN_TEST) { code -> if (!code.isNullOrBlank()) { Haptics.confirm(context); result = code } } },
          Modifier.fillMaxWidth(),
          kind = V3ButtonKind.SECONDARY,
          enabled = granted,
        )
      }
    }
    result?.let { code ->
      item(key = "result") { V3Message(V3Tone.SUCCESS, StrSettings.scanned(code, code.length), Modifier.padding(top = 16.dp)) }
    }
    item(key = "note") { SettingsNote(StrSettings.cameraTestNote) }
  }
}
