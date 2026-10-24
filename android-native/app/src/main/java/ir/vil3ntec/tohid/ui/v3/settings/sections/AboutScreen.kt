package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.rememberUpdateRepo
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/about` — «نسخه و به‌روزرسانی» (برای همه، کارمند هم): نشانِ برنامه، نسخه
 *  و شرح، کارتِ «به‌روزرسانی برنامه» (نسخهٔ بازار خودش را به‌روز نمی‌کند — قانونِ
 *  بازار) و «وضعیت» با شمارِ دفتر.
 */
@Composable
internal fun AboutScreen(data: ShopData, onBack: (() -> Unit)?) {
  val c = PhoneV3.colors
  SettingsPageFrame(title = SettingsLayout.labelOf(SettingsLayout.ABOUT_APP), onBack = onBack) {
    item(key = "mark") {
      Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        V3IconTile(Icons.Outlined.Storefront, hue = V3Hue.BRAND, size = 72.dp)
        Spacer(Modifier.height(10.dp))
        Text(Str.appName, style = V3Type.title, color = c.ink)
        Text(Str.version(BuildConfig.VERSION_NAME), style = V3Type.subhead, color = c.inkMuted)
        Spacer(Modifier.height(8.dp))
        Text(StrSettings.aboutText, style = V3Type.footnote, color = c.inkMuted, textAlign = TextAlign.Center)
      }
    }
    if (BuildConfig.STORE != "bazaar") {
      item(key = "update") {
        V3Section(StrNav.appUpdate, padding = PaddingValues(16.dp)) { UpdateCard(rememberUpdateRepo()) }
      }
    }
    item(key = "status") {
      V3Section(StrNav.status) {
        listOf(
          StrNav.versionLabel to BuildConfig.VERSION_NAME,
          StrNav.goods to data.products.size.toString(),
          Str.invoices to data.sales.size.toString(),
          Str.debtors to data.debtors.size.toString(),
          Str.expenses to data.expenses.size.toString(),
          StrNav.suppliers to data.suppliers.size.toString(),
        ).forEachIndexed { i, (label, value) ->
          if (i > 0) V3Divider(start = 22.dp)
          V3ListRow(label, value = value, tile = false, chevron = false)
        }
      }
    }
  }
}
