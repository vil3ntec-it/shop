package ir.vil3ntec.tohid.scan

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 *  عکس‌برداریِ راهنمایی‌شده روی کامپیوتر نیست: دوربینِ کالا گوشی است
 *  (`GuidedCapture.kt`ِ اندروید). `AVAILABLE = false` ⇒ دکمه‌اش اصلاً
 *  نشان داده نمی‌شود؛ این تابع فقط برای هم‌نامیِ کد است و همان دم می‌بندد.
 */
object GuidedCapture {
  const val AVAILABLE = false
}

@Composable
fun GuidedCaptureDialog(onDismiss: () -> Unit, onAccepted: (Uri) -> Unit) {
  LaunchedEffect(Unit) { onDismiss() }
}
