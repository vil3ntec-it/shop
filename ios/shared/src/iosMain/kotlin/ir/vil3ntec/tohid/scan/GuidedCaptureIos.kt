package ir.vil3ntec.tohid.scan

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** جایگزینِ iOS ِ `scan/GuidedCapture.kt` (دوربینِ راهنمای CameraX؛ همان ‎GuidedCaptureDesk‎): نیست ⇐ دکمه‌اش پنهان؛ عکسِ کالا با دوربینِ iOS (`TakePicture`) */
object GuidedCapture {
  const val AVAILABLE = false
}

@Composable
fun GuidedCaptureDialog(onDismiss: () -> Unit, onAccepted: (Uri) -> Unit) {
  LaunchedEffect(Unit) { onDismiss() }
}
