package ir.vil3ntec.tohid.ui.courier

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.courier.CourierChecks
import ir.vil3ntec.tohid.data.DeviceLocation
import kotlinx.coroutines.launch

/**
 *  مرزِ پلتفرمِ موقعیتِ پیک (DLV-17 «تحویل شد» نزدیکِ پین، DLV-04 مسیرِ پیشنهادی): پرسیدنِ اجازه و گرفتنِ یک
 *  نقطه از `DeviceLocation`. صفحه‌ها (`CourierChecksUi.kt`) فقط تابعِ برگشتی را صدا می‌زنند و `Point` یا null می‌گیرند.
 *
 *  `maxAgeMs`: آخرین نقطهٔ دستگاه از این کهنه‌تر نباشد (برای «تحویل شد» یک دقیقه — نه نقطهٔ دکان).
 *  `ask = false`: اگر اجازه نیست نمی‌پرسد و همان دم null می‌دهد (مسیر بی موقعیت هم ساخته می‌شود).
 */
@Composable
internal fun rememberCourierFix(maxAgeMs: Long, ask: Boolean, onFix: (CourierChecks.Point?) -> Unit): () -> Unit {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val deliver by rememberUpdatedState(onFix)
  fun look() {
    scope.launch {
      val fix = DeviceLocation.current(context, force = true, maxAgeMs = maxAgeMs)
      deliver(fix?.let { CourierChecks.Point(it.lat, it.lng) })
    }
  }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted.values.any { it }) look() else deliver(null)
  }
  return {
    when {
      DeviceLocation.granted(context) -> look()
      ask -> launcher.launch(DeviceLocation.PERMISSIONS)
      else -> deliver(null)
    }
  }
}
