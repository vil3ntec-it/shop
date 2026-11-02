package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrPriceCheck
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrTill
import ir.vil3ntec.tohid.core.i18n.StrTouch
import ir.vil3ntec.tohid.data.TillUser
import ir.vil3ntec.tohid.ui.components.keys.KeyHelpPref
import ir.vil3ntec.tohid.ui.components.staff.TillUsersBlock
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import ir.vil3ntec.tohid.data.DocGapsPref
import ir.vil3ntec.tohid.data.ReportKeysPref
import ir.vil3ntec.tohid.ui.components.pos.PosMoreKeysPrefs
import ir.vil3ntec.tohid.ui.components.pos.PosPriceCheckPrefs
import ir.vil3ntec.tohid.ui.components.pos.PosSearchPrefs
import ir.vil3ntec.tohid.ui.components.pos.PosTouchPrefs
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope

/*
 *  کلیدهای فقط‌کامپیوترِ رابطِ امروز (`PosSearchSettingsWired` با `PosPriceCheckSettingsWired`،
 *  `PosTouchSettingsWired`، `PosMoreKeysSettingsWired`، `ReportKeysSettingsWired` — در رابطِ قدیمِ پاک‌شده پشتِ
 *  `Platform.DESKTOP`) — همان کلید، همان پیش‌فرضِ خاموش و همان نوشتن (`SettingsPortTest`). مثلِ رابطِ امروز گوشی
 *  ردیفشان را ندارد (`SettingsLayout.PHONE_HIDDEN`). «شماره‌های جاافتاده» (`DocGapsSettingsWired`) از RPT-17 زبانهٔ
 *  گوشی هم دارد، پس در هر دو هست.
 */

/** `settings/sales/smart-search`: جست‌وجوی مقاومِ صندوق (POS-04) */
@Composable
internal fun RouteScope.PosSearchScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { PosSearchPrefs.load(context); PosSearchPrefs.on.value }
  ToggleScreen(SettingsLayout.POS_SEARCH, onBack, on, StrShell.switchOn, StrPos.smartSearchNote) { PosSearchPrefs.set(context, it) }
}

/** `settings/sales/price-check`: قیمت‌پرسی در صندوق (POS-15) */
@Composable
internal fun RouteScope.PosPriceCheckScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { PosPriceCheckPrefs.load(context); PosPriceCheckPrefs.on.value }
  ToggleScreen(SettingsLayout.POS_PRICE_CHECK, onBack, on, StrShell.switchOn, StrPriceCheck.note) { PosPriceCheckPrefs.set(context, it) }
}

/** `settings/sales/touch-mode`: حالتِ لمسیِ صندوق (POS-31) */
@Composable
internal fun RouteScope.PosTouchScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { PosTouchPrefs.load(context); PosTouchPrefs.on.value }
  ToggleScreen(SettingsLayout.POS_TOUCH, onBack, on, StrShell.switchOn, StrTouch.note) { PosTouchPrefs.setOn(context, it) }
}

/** `settings/sales/more-keys`: کلیدهای بیشترِ صندوق */
@Composable
internal fun RouteScope.PosMoreKeysScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { PosMoreKeysPrefs.load(context); PosMoreKeysPrefs.on.value }
  ToggleScreen(SettingsLayout.POS_MORE_KEYS, onBack, on, StrShell.switchOn, StrPos.moreKeysNote) { PosMoreKeysPrefs.set(context, it) }
}

/** `settings/sales/report-keys`: کلیدهای دوره در گزارش‌ها (RPT-13) */
@Composable
internal fun RouteScope.ReportKeysScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.REPORT_KEYS, onBack, ReportKeysPref.on(context), StrHome.reportKeysOn, StrHome.reportKeysNote) {
    ReportKeysPref.setOn(context, it)
  }
}

/** `settings/sales/doc-gaps`: شماره‌های جاافتادهٔ اسناد (RPT-17) */
@Composable
internal fun RouteScope.DocGapsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.DOC_GAPS, onBack, DocGapsPref.on(context), StrReports.docGapsOn, StrReports.docGapsNote) {
    DocGapsPref.setOn(context, it)
  }
}

/** `settings/sales/key-help`: راهنمای کلیدِ هر صفحه از keymap.json و برچسب‌های Ctrl (S50) */
@Composable
internal fun RouteScope.KeyHelpScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { KeyHelpPref.load(context); KeyHelpPref.on.value }
  ToggleScreen(SettingsLayout.KEY_HELP, onBack, on, StrShell.switchOn, StrTill.keyHelpNote) { KeyHelpPref.set(context, it) }
}

/** `settings/sales/till-switch`: تعویضِ کاربر روی صندوق با PIN ِ شخصی (S46) — روشن/خاموش و کاربرانِ PIN‌دار */
@Composable
internal fun RouteScope.TillSwitchScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var on by remember { TillUser.load(context); mutableStateOf(TillUser.on.value) }
  SettingsPageFrame(title = SettingsLayout.labelOf(SettingsLayout.TILL_SWITCH), subtitle = if (on) StrShell.switchOn else StrSettings.off, onBack = onBack) {
    item(key = "choice") {
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { on = it == 1; TillUser.set(context, on) }) }
      SettingsNote(StrTill.switchNote)
    }
    if (on) item(key = "users") { SettingsBlock { Column { TillUsersBlock() } } }
  }
}
