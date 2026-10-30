package ir.vil3ntec.tohid.ui.v3.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.SettingsEntry
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** پرچمِ ماژولِ نمونه — پیش‌فرض خاموش (`ModuleFlags`) */
const val EXAMPLE_FLAG = "module.example"

/**
 *  ماژولِ نمونه — نشان می‌دهد «سیستمِ تازه» با **یک پوشه و یک خط** (`V3Modules.ALL`)
 *  به همه‌جا وصل می‌شود: ردیفی در «بیشتر»، صفحه‌ای در تنظیمات، و مسیرها با
 *  پیوندِ `vill3n://app/example`. پشتِ پرچمِ خاموش
 *  است، پس کاربر هیچ‌کدام را نمی‌بیند (`docs/MODULES-fa.md`).
 */
val ExampleModule = AppModule(
  id = "example",
  title = { StrKit.exampleTitle },
  icon = Icons.Outlined.Extension,
  flag = EXAMPLE_FLAG,
  entries = listOf(
    ModuleEntry("example", { StrKit.exampleTitle }, "example", Icons.Outlined.Extension, setOf(Place.MORE), MoreGroup.SHOP, V3Hue.PURPLE, order = 99),
  ),
  settings = listOf(
    SettingsEntry({ StrKit.exampleTitle }, "settings/example", Icons.Outlined.Extension, SettingsLayout.GENERAL),
  ),
  routes = listOf(
    V3Route("example", RouteKind.PAGE, { StrKit.exampleTitle }, chrome = Chrome.OWN) { ExampleScreen() },
    V3Route("settings/example", RouteKind.PAGE, { StrKit.exampleTitle }, chrome = Chrome.OWN) { ExampleScreen() },
  ),
)

@Composable
private fun RouteScope.ExampleScreen() {
  V3Scaffold(title = StrKit.exampleTitle, onBack = { back() }) { pad ->
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding())) {
      V3EmptyState(Icons.Outlined.Extension, StrKit.exampleTitle, body = StrKit.exampleBody)
      V3Section(title = args.path) {
        args.all.forEach { (k, v) -> V3ListRow(title = k, value = v, chevron = false) }
      }
    }
  }
}
