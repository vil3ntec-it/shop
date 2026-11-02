package ir.vil3ntec.tohid.scan

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/*
 *  جایگزینِ iOS ِ `scan/BarcodeScanner.kt` (همان API؛ کامپیوتر: ‎BarcodeScannerDesk‎).
 *  اسکنِ دوربینِ آیفون (VisionKit `DataScannerViewController`) گامِ ۳ است. تا آن روز این کادر فقط می‌گوید
 *  دوربین هنوز نیست؛ بارکدخوانِ بلوتوثی (HID) همان‌جا کار می‌کند، چون صفحهٔ فروش ورودیِ صفحه‌کلید را
 *  خودش می‌گیرد (`Modifier.hidScanner` در کدِ مشترک).
 */
@Composable
fun CameraScanner(
  onCode: (String) -> Unit,
  onStatus: (String, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  isKnown: (String) -> Boolean = { false },
  @Suppress("UNUSED_PARAMETER") qr: Boolean = false,
  @Suppress("UNUSED_PARAMETER") qrOnly: Boolean = false,
) {
  val status by rememberUpdatedState(onStatus)
  LaunchedEffect(Unit) { status(NOT_YET, false) }
  Box(modifier.background(Color(0xFF0E1116)), contentAlignment = Alignment.Center) {
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
      Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(56.dp))
      Spacer(Modifier.height(10.dp))
      Text(NOT_YET, color = Color.White, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
    }
  }
}

private const val NOT_YET = "اسکن با دوربینِ آیفون به‌زودی — بارکدخوانِ بلوتوثی یا نوشتنِ کد کار می‌کند"

/** همان قاعدهٔ اندروید: یک کد در یک بازهٔ کوتاه یک بار */
class ScanGate(private val windowMs: Long = 1200) {
  private var lastCode: String? = null
  private var lastAt = 0L

  fun accept(code: String, now: Long = ir.vil3ntec.tohid.ios.jvm.System.currentTimeMillis()): Boolean {
    if (code == lastCode && now - lastAt < windowMs) return false
    lastCode = code
    lastAt = now
    return true
  }

  fun reset() {
    lastCode = null
    lastAt = 0
  }
}

/** بازخوردِ اسکن با لرزشِ خودِ iOS (بوقِ اندروید ⇐ «درست» و «ناشناخته») */
object ScanFeedback {
  fun ok(context: Context) = UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
  fun unknown(context: Context) = UINotificationFeedbackGenerator().notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeWarning)
  fun release() {}
}
