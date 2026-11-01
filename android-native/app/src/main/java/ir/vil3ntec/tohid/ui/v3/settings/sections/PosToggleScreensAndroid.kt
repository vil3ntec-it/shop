package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrPayFix
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.PayFixPref
import ir.vil3ntec.tohid.data.QuotePref
import ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/*
 *  سه کلیدِ صندوق که فقط در تنظیماتِ رابطِ امروز بودند (`PayFixSettingsWired`، `PriceListSettingsWired`،
 *  `QuoteSettingsWired` در `ui/legacy/SettingsLegacy.kt`) — همان کلید، همان پیش‌فرضِ خاموش و همان نوشتن
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

/** `settings/sales/price-lists`: لیستِ قیمت در صندوق، خرده/عمده (POS-18) */
@Composable
internal fun RouteScope.PriceListSettingsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.PRICE_LIST, onBack, PriceListPrefs.on(context), StrShell.switchOn, StrPos.priceListNote, deviceOnly = true) {
    PriceListPrefs.set(context, it)
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
