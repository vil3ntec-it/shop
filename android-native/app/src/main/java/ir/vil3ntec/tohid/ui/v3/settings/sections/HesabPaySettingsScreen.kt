package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.gateway.HesabAccountView
import ir.vil3ntec.tohid.ui.screens.HesabPayAccountSettings
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.hesabPayStatusLine
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/sales/hesabpay`: «حساب‌پیِ دکان» (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱؛ دستهٔ B، زیرِ «پیشرفته») — همان صفحهٔ
 *  رابطِ امروز (`HesabPayAccountSettings`)؛ فقط صاحبِ دکان (`rememberSettingsApi(ownerOnly = true)`)، رمزِ جدای
 *  حساب‌پی و همهٔ سنجش‌ها در سرور. کارتِ «این چه می‌کند؟» را خودِ قاب از `SettingsHelp.example` می‌کشد.
 */
@Composable
internal fun RouteScope.HesabPayAccountScreen(onBack: (() -> Unit)?) {
  val api = rememberSettingsApi(ownerOnly = true)
  var view by remember { mutableStateOf<HesabAccountView?>(null) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.HESAB_SHOP),
    subtitle = if (api == null) null else hesabPayStatusLine(view),
    onBack = onBack,
  ) {
    item(key = "hesabpay") {
      SettingsBlock { HesabPayAccountSettings(ready = api != null, toast = { toasts.show(it) }, onView = { view = it }) }
    }
  }
}
