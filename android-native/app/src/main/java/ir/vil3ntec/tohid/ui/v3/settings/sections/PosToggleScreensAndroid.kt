package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrCustomerPrices
import ir.vil3ntec.tohid.core.i18n.StrPayFix
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.PayFixPref
import ir.vil3ntec.tohid.data.QuotePref
import ir.vil3ntec.tohid.ui.components.pos.CustomerPricePrefs
import ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/*
 *  سه کلیدِ صندوق که فقط در تنظیماتِ رابطِ امروز بودند (`PayFixSettingsWired`، `PriceListSettingsWired`،
 *  `QuoteSettingsWired` در رابطِ قدیمِ پاک‌شده) — همان کلید، همان پیش‌فرضِ خاموش و همان نوشتن
 *  (`SettingsPortTest`)، فقط رویِ کیت (`ToggleScreen` ِ `MoreToggleScreensAndroid.kt`). هر سه فقط همین دستگاه.
 */

/** `settings/sales/payment-fix`: اصلاحِ روشِ پرداخت پس از فروش (POS-40) */
@Composable
internal fun RouteScope.PayFixSettingsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.PAY_FIX, onBack, PayFixPref.on(context), StrShell.switchOn, StrPayFix.settingsNote, deviceOnly = true) {
    PayFixPref.set(context, it)
  }
}

/**
 *  `settings/sales/price-lists`: لیستِ قیمت در صندوق، خرده/عمده (POS-18)، و زیرش «قیمتِ خاصِ مشتری»
 *  (`CustomerPricePrefs`، پیش‌فرض خاموش) که فقط با همین لیستِ روشن اثر دارد.
 */
@Composable
internal fun RouteScope.PriceListSettingsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var on by remember { mutableStateOf(PriceListPrefs.on(context)) }
  var special by remember { mutableStateOf(CustomerPricePrefs.on(context)) }
  SettingsPageFrame(title = SettingsLayout.labelOf(SettingsLayout.PRICE_LIST), subtitle = if (on) StrShell.switchOn else StrSettings.off, onBack = onBack) {
    item(key = "choice") {
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { on = it == 1; PriceListPrefs.set(context, on) }) }
      SettingsNote(StrPos.priceListNote)
      SettingsNote(StrShell.thisDeviceOnly)
    }
    item(key = "special") {
      SettingsNote(StrCustomerPrices.settingsTitle)
      SettingsBlock { V3Segmented(SWITCH, if (special) 1 else 0, { special = it == 1; CustomerPricePrefs.set(context, special) }) }
      SettingsNote(StrCustomerPrices.settingsNote)
    }
  }
}

/** `settings/sales/quotes`: پیش‌فاکتور در صندوق (POS-25) */
@Composable
internal fun RouteScope.QuoteSettingsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.QUOTE, onBack, QuotePref.on(context), StrShell.switchOn, StrPos.quoteSettingsNote, deviceOnly = true) {
    QuotePref.set(context, it)
  }
}
