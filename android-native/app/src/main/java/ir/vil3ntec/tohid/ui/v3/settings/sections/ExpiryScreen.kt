package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/** `settings/sales/expiry`: «فروخته نشود | فقط هشدار» — همان `ShopPolicy.setBlockExpired` */
@Composable
internal fun RouteScope.ExpiryScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var block by remember { mutableStateOf(ShopPolicy.blockExpired(context)) }
  SettingsPageFrame(title = SettingsLayout.labelOf("کالای منقضی"), onBack = onBack) {
    item(key = "choice") {
      SettingsBlock {
        V3Segmented(
          options = listOf(StrSettings.dontSell, StrSettings.warnOnly),
          selected = if (block) 0 else 1,
          onSelect = {
            block = it == 0
            ShopPolicy.setBlockExpired(context, block)
          },
        )
      }
      SettingsNote(StrSettings.expiryNote)
    }
  }
}
