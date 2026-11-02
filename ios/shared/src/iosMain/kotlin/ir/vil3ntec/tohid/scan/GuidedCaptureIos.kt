package ir.vil3ntec.tohid.scan

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** جایگزینِ iOS ِ `scan/GuidedCapture.kt` (دوربینِ راهنمای عکسِ کالا؛ همان ‎GuidedCaptureDesk‎): دوربینِ آیفون گامِ ۳ */
object GuidedCapture {
  const val AVAILABLE = false
}

@Composable
fun GuidedCaptureDialog(onDismiss: () -> Unit, onAccepted: (Uri) -> Unit) {
  LaunchedEffect(Unit) { onDismiss() }
}
