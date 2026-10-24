package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet

/** هر بار رمز گذاشته یا برداشته شود یکی بالا می‌رود تا ردیف‌ها و صفحهٔ قفل تازه شوند */
internal object LockSignal {
  var version by mutableIntStateOf(0)
}

/**
 *  پیش‌نمایشِ عکس‌ها (`SettingsShots`): قفل را روشن نشان بده بی دست زدن به رمزِ
 *  واقعی؛ در برنامهٔ دستِ کاربر همیشه null است.
 */
internal object SettingsShotState {
  var lockOn: Boolean? = null
  /** صفحهٔ همگام‌سازی: «وارد شده» و خطای آخرین دور، برای عکسِ حالتِ خطا */
  var signedIn: Boolean? = null
  var syncError: String? = null
  /** پشتیبان: برگهٔ تأییدِ «پاک‌سازی کامل» باز */
  var wipeSheet: Boolean = false
}

/** رمزِ برنامه گذاشته شده؟ (`LockStore`) */
@Composable
internal fun rememberLockOn(): Boolean {
  val context = LocalContext.current
  val v = LockSignal.version
  val real = remember(v) { LockStore(context).enabled }
  return SettingsShotState.lockOn ?: real
}

/** تأییدِ «برداشتن رمز» — کارِ پاک‌کننده، پس برگهٔ تأیید (همان کارِ امروز پس از آن) */
@Composable
internal fun RemovePinSheet(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val toasts = LocalV3Toasts.current
  V3ConfirmSheet(
    title = StrSettings.removePinQ,
    message = StrSettings.removePinWarn,
    confirm = StrSettings.removePin,
    icon = Icons.Outlined.LockOpen,
    onConfirm = {
      LockStore(context).set("")
      LockSignal.version++
      toasts?.show(StrSettings.lockRemoved)
    },
    onDismiss = onDismiss,
  )
}
