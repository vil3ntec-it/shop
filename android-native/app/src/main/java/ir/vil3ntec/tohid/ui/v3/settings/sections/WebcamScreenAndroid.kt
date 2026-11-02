package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.hw.WebcamScan
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.scan.WebcamPlatform
import ir.vil3ntec.tohid.scan.WebcamPref
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/devices/webcam`: HW-08 — وب‌کمِ کامپیوتر به‌عنوانِ بارکدخوان. دستهٔ B (وابسته به دستگاهِ جانبی)، پیش‌فرض
 *  خاموش، فقط همین دستگاه؛ ردیفش فقط روی کامپیوتر (`SettingsLayout.PHONE_HIDDEN`). روشن ⇒ کادرِ اسکنِ کامپیوتر تصویرِ
 *  زندهٔ دوربینِ برگزیده را می‌خواند (`desktop/.../scan/BarcodeScannerDesk.kt`). دوربین‌ها از مرزِ سکو
 *  (`WebcamPlatform`)؛ گزینش با `WebcamScan.pickCamera` (کشیده‌شده ⇒ نخستینِ وصل).
 */
@Composable
internal fun RouteScope.WebcamScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val onState = remember { WebcamPref.load(context); WebcamPref.on }
  val on by onState
  val chosen by WebcamPref.camera
  val cameras = remember { WebcamPlatform.cameras() }
  val current = WebcamScan.pickCamera(cameras, chosen)
  SettingsPageFrame(title = SettingsLayout.labelOf(SettingsLayout.WEBCAM), subtitle = if (on) StrShell.switchOn else StrSettings.off, onBack = onBack) {
    item(key = "choice") {
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { WebcamPref.set(context, it == 1) }) }
      SettingsNote(StrDevices.webcamNote)
      if (!WebcamPlatform.supported) SettingsNote(StrDevices.webcamPhone)
      SettingsNote(StrShell.thisDeviceOnly)
    }
    if (WebcamPlatform.supported) {
      item(key = "cameras") {
        if (cameras.isEmpty()) {
          SettingsNote(StrDevices.webcamNone)
        } else {
          V3Section(StrDevices.webcamPick, footer = StrDevices.webcamTip) {
            cameras.forEachIndexed { i, cam ->
              if (i > 0) V3Divider(start = 22.dp)
              V3ListRow(
                cam.name.ifBlank { StrDevices.webcamFirst },
                tile = false,
                chevron = false,
                trailing = if (cam.id == current?.id) ({ Chosen() }) else null,
                onClick = { WebcamPref.choose(context, cam.id) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun Chosen() {
  Icon(Icons.Outlined.Check, contentDescription = StrSettings.chosen, tint = PhoneV3Roles.brandText(PhoneV3.colors), modifier = Modifier.size(20.dp))
}
