package ir.vil3ntec.tohid.ui.v3.settings.devices

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  بارکدخوان‌ها: USB، بلوتوث، بی‌سیم — همه خودشان را صفحه‌کلید معرفی می‌کنند. کادرِ
 *  آزمون: اسکن کنید و همان کد با شمارِ نویسه‌ها زیرش می‌آید.
 */
@Composable
internal fun ScannersCard(highlight: Boolean) {
  var test by remember { mutableStateOf("") }
  var last by remember { mutableStateOf<String?>(null) }
  DeviceCard(StrSettings.scanners, StrSettings.scannersTrouble, highlight, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
    Text(StrSettings.scannersNote, style = V3Type.footnote, color = PhoneV3.colors.inkMuted)
    Spacer(Modifier.height(12.dp))
    //  بارکدخوان پس از کد «Enter» می‌زند ⇐ خطِ تازه یعنی یک خواندنِ کامل
    V3TextField(
      test,
      { v -> if (v.contains('\n')) { last = v.trim().ifBlank { null }; test = "" } else test = v.take(64) },
      StrSettings.scannerTest,
      Modifier.fillMaxWidth(),
      singleLine = false,
      onImeAction = { last = test.trim().ifBlank { null }; test = "" },
    )
    last?.let {
      Text(StrSettings.scanned(it, it.length), style = V3Type.callout, color = PhoneV3Roles.toneIcon(PhoneV3.colors, ir.vil3ntec.tohid.ui.theme.V3Tone.SUCCESS), modifier = Modifier.padding(top = 8.dp))
    }
  }
}
