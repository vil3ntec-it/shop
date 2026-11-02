package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPlaces
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.AnomalyPref
import ir.vil3ntec.tohid.data.SmartForecastPref
import ir.vil3ntec.tohid.data.StockPlacesPref
import ir.vil3ntec.tohid.ui.screens.ANOMALY_NOTE
import ir.vil3ntec.tohid.ui.screens.SMART_FORECAST_NOTE
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/*
 *  تنظیم‌های انبار و گزارش که فقط در رابطِ امروز بودند (`StockPlacesSettingsWired`، `SmartForecastSettingsWired`،
 *  `AnomalySettingsWired` در `ui/legacy/SettingsLegacy.kt`) — همان کلید، همان پیش‌فرضِ خاموش و همان نوشتن
 *  (`SettingsPortTest`)؛ فقط رویِ کیت. هر سه کلیدِ همین دستگاه‌اند.
 */

/**
 *  `settings/sales/stock-places`: محل‌ها و انتقال (INV-20) و بسته‌بندیِ دوباره و ساختِ بسته (INV-21) — دو کلیدِ
 *  جدا، همان `StockPlacesPref.setPlaces` و `setRepack` ِ `StockPlacesSettingsWired`.
 */
@Composable
internal fun RouteScope.StockPlacesScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var places by remember { mutableStateOf(StockPlacesPref.places(context)) }
  var repack by remember { mutableStateOf(StockPlacesPref.repack(context)) }
  val options = listOf(StrPlaces.off, StrPlaces.on)
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.STOCK_PLACES),
    subtitle = if (places || repack) StrPlaces.settingsOn else StrPlaces.off,
    onBack = onBack,
  ) {
    item(key = "note") { SettingsNote(StrPlaces.settingsNote) }
    item(key = "places") {
      SettingsBlock {
        PartTitle(StrPlaces.placesSection)
        V3Segmented(options, if (places) 1 else 0, { places = it == 1; StockPlacesPref.setPlaces(context, places) })
      }
      SettingsNote(StrPlaces.placesSectionNote)
    }
    item(key = "repack") {
      SettingsBlock {
        PartTitle(StrPlaces.repackSection)
        V3Segmented(options, if (repack) 1 else 0, { repack = it == 1; StockPlacesPref.setRepack(context, repack) })
      }
      SettingsNote(StrPlaces.repackSectionNote)
    }
  }
}

/** سرخطِ یک کلید درونِ کارت، وقتی صفحه چند کلید دارد */
@Composable
internal fun PartTitle(text: String) {
  Text(text, style = V3Type.headline, color = PhoneV3.colors.ink, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
}

/** `settings/sales/forecast`: پیش‌بینیِ تقاضای قابلِ توضیح در «پیشنهادِ سفارش» (AI-07) */
@Composable
internal fun RouteScope.SmartForecastScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.SMART_FORECAST, onBack, SmartForecastPref.on(context), StrShell.switchOn, SMART_FORECAST_NOTE, deviceOnly = true) {
    SmartForecastPref.set(context, it)
  }
}

/** `settings/security/anomalies`: تشخیصِ ناهنجاری در زبانهٔ «ضدتقلب» ِ گزارشِ تازه (AI-08) */
@Composable
internal fun RouteScope.AnomalyScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.ANOMALY, onBack, AnomalyPref.on(context), StrShell.switchOn, ANOMALY_NOTE, deviceOnly = true) {
    AnomalyPref.set(context, it)
  }
}
