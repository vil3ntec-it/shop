package ir.vil3ntec.tohid.ui.v3.nav

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.scan.CameraScanner
import ir.vil3ntec.tohid.scan.ScanFeedback
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3Type

/** اسکنِ QR در هر حالت: `nav.openForResult(ScanPaths.QR) { متنِ خام -> … }` */
object ScanPaths {
  const val QR = "scan?kind=qr"
}

/**
 *  اسکنِ QR برای همهٔ حالت‌ها (دکان، پیک، خرید). `scan?kind=qr` از `scan` ِ بارکدِ فروش
 *  دقیق‌تر است، پس در دکان هم همین باز می‌شود و `scan` ِ فروش دست نمی‌خورد.
 *  فقط QR می‌خواند و متنِ خام را با `nav.finish(text)` برمی‌گرداند. کامپیوتر دوربین ندارد:
 *  «این‌جا دوربین نیست».
 */
val ScanModule = AppModule(
  id = "scan",
  title = { StrKit.scanQr },
  icon = Icons.Outlined.QrCodeScanner,
  modes = setOf(AppMode.SHOP, AppMode.COURIER, AppMode.SHOPPER),
  routes = listOf(
    V3Route(ScanPaths.QR, RouteKind.SHEET, { StrKit.scanQr }, chrome = Chrome.OWN) {
      if (desk) {
        V3Sheet(onDismiss = { back() }) {
          V3EmptyState(Icons.Outlined.QrCodeScanner, StrKit.noCameraTitle, body = StrKit.noCameraBody)
        }
      } else {
        QrScanSheet(onDismiss = { back() }, onText = { nav.finish(it) })
      }
    },
  ),
)

/** دوربینِ تمام‌صفحه فقط برای QR؛ خوانشِ اول پذیرفته می‌شود و برگه بسته می‌شود */
@Composable
private fun QrScanSheet(onDismiss: () -> Unit, onText: (String) -> Unit) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  var granted by remember {
    mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
  }
  var status by remember { mutableStateOf(StrKit.scanQrHint) }
  var taken by remember { mutableStateOf(false) }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
    granted = ok
    if (!ok) status = StrKit.cameraDenied
  }
  LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
  val bars = WindowInsets.systemBars.asPaddingValues()

  Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    Column(
      Modifier.fillMaxSize().background(c.bg)
        .padding(top = bars.calculateTopPadding(), bottom = bars.calculateBottomPadding())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(StrKit.scanQr, style = V3Type.title, color = c.ink, modifier = Modifier.weight(1f))
        V3TextButton(StrKit.cancel, onDismiss)
      }
      Box(
        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp)).background(if (granted) Color.Black else c.card),
        contentAlignment = Alignment.Center,
      ) {
        if (granted) {
          CameraScanner(
            onCode = { text ->
              if (!taken) {
                taken = true
                ScanFeedback.ok(context)
                onText(text)
              }
            },
            onStatus = { _, _ -> },
            modifier = Modifier.fillMaxSize(),
            qrOnly = true,
          )
        } else {
          Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(24.dp)) {
            Text(StrKit.cameraNeeded, style = V3Type.body, color = c.inkMuted, textAlign = TextAlign.Center)
            V3Button(StrKit.cameraAllow, onClick = { ask.launch(Manifest.permission.CAMERA) })
          }
        }
      }
      Text(status, style = V3Type.body, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
  }
}
