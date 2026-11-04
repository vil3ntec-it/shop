package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.account.DeviceCap
import ir.vil3ntec.tohid.sync.LicenseGuard
import ir.vil3ntec.tohid.sync.SyncStore

/**
 *  O-09: هشدارِ «بیرون از سقفِ دستگاه» ِ همین دستگاهِ صاحب، از `SyncStore.deviceCapOver` (هر پاسخِ مجوز بازنویسی‌اش
 *  می‌کند؛ `Syncer`). فقط خواندنِ انبارِ سکو این‌جاست (مرزِ `*Android.kt`)؛ نوار در `ShellNotices.kt` (`DeviceCapNotice`).
 */
@Composable
internal fun rememberDeviceCapOver(): DeviceCap.Over? {
  val context = LocalContext.current
  return remember(LicenseGuard.changes.intValue) {
    runCatching { DeviceCap.decode(SyncStore(context).deviceCapOver) }.getOrNull()
  }
}
