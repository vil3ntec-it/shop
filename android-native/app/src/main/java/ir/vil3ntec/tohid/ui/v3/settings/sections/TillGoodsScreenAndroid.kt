package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.TillGoodsPref
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.TILL_GOODS_NOTE
import ir.vil3ntec.tohid.ui.screens.TILL_GOODS_PART
import ir.vil3ntec.tohid.ui.screens.TILL_GOODS_PART_NO
import ir.vil3ntec.tohid.ui.screens.TILL_GOODS_PART_OK
import ir.vil3ntec.tohid.ui.screens.TILL_GOODS_QUICK
import ir.vil3ntec.tohid.ui.screens.TILL_GOODS_UNITS
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/** `settings/sales/till-goods`: ثبتِ سریع (S03) و واحدِ بزرگ با «بستهٔ کامل» (S05) — همان `TillGoodsSettingsWired` ِ رابطِ امروز */
@Composable
internal fun RouteScope.TillGoodsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var quick by remember { mutableStateOf(TillGoodsPref.quickRegister(context)) }
  var units by remember { mutableStateOf(TillGoodsPref.units(context)) }
  var whole by remember { mutableStateOf(TillGoodsPref.wholePacks(context)) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.TILL_GOODS),
    subtitle = if (quick || units) StrShell.switchOn else StrSettings.off,
    onBack = onBack,
  ) {
    item(key = "quick") {
      SettingsBlock {
        Text(TILL_GOODS_QUICK)
        V3Segmented(SWITCH, if (quick) 1 else 0, { quick = it == 1; TillGoodsPref.setQuickRegister(context, quick) })
      }
    }
    item(key = "units") {
      SettingsBlock {
        Text(TILL_GOODS_UNITS)
        V3Segmented(SWITCH, if (units) 1 else 0, { units = it == 1; TillGoodsPref.setUnits(context, units) })
      }
      if (units) SettingsBlock {
        Text(TILL_GOODS_PART)
        V3Segmented(listOf(TILL_GOODS_PART_OK, TILL_GOODS_PART_NO), if (whole) 1 else 0, { whole = it == 1; TillGoodsPref.setWholePacks(context, whole) })
      }
      SettingsNote(TILL_GOODS_NOTE)
      SettingsNote(StrShell.thisDeviceOnly)
    }
  }
}
