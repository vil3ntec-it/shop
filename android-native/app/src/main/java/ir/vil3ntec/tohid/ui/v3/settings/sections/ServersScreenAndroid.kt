package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.platform.ServersController
import ir.vil3ntec.tohid.ui.screens.ServersSettings
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/data/servers`: «سرورها» — وصل به سرورِ دیگر (شبکهٔ دکان، خانه، اینترنت) با سنجاقِ هویت و دادهٔ جدا برای
 *  هر سرور (`docs/SERVERS-fa.md`)؛ همان بخشِ `ServersSettings` ِ تنظیماتِ پیشین، فقط صاحبِ دکان. کلاسِ B: پیش‌فرض
 *  خاموش (`app.multi_server` ِ همین دستگاه).
 */
@Composable
internal fun RouteScope.ServersScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val servers = remember(context) { ServersController(context) }
  SettingsPageFrame(title = SettingsLayout.labelOf(SettingsLayout.SERVERS), onBack = onBack) {
    item(key = "servers") {
      if (!servers.canManage) SettingsNote(StrReach.serversOwnerOnly)
      else ServersSettings(servers) { toasts.show(it) }
    }
  }
}
