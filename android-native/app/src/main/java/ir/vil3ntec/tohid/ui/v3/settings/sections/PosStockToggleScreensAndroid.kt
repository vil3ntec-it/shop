package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrCount
import ir.vil3ntec.tohid.core.i18n.StrDeliver
import ir.vil3ntec.tohid.core.i18n.StrGift
import ir.vil3ntec.tohid.core.i18n.StrLabel
import ir.vil3ntec.tohid.core.i18n.StrProduct
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.StrShrink
import ir.vil3ntec.tohid.core.i18n.StrSpecialOrder
import ir.vil3ntec.tohid.core.i18n.StrWhy
import ir.vil3ntec.tohid.data.CountToolsPref
import ir.vil3ntec.tohid.data.LabelToolsPref
import ir.vil3ntec.tohid.data.ShrinkReportPref
import ir.vil3ntec.tohid.data.SpecialOrderPref
import ir.vil3ntec.tohid.ui.components.pos.DeliverLaterPrefs
import ir.vil3ntec.tohid.ui.components.pos.GiftReceiptPrefs
import ir.vil3ntec.tohid.ui.components.pos.PriceWhyPrefs
import ir.vil3ntec.tohid.ui.components.product.ProductInfoPrefs
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/*
 *  کلیدهای صندوق و کالا که فقط در رابطِ امروز بودند (`GiftReceiptSettingsWired`، `DeliverLaterSettingsWired`،
 *  `SpecialOrderSettingsWired`، `PriceWhySettingsWired`، `ShrinkReportSettingsWired`، `CountToolsSettingsWired`،
 *  `LabelToolsSettingsWired`، `ProductInfoSettingsWired` در `ui/legacy/SettingsLegacy.kt`) — همان کلید، همان پیش‌فرضِ
 *  خاموش و همان نوشتن (`SettingsPortTest`)؛ فقط رویِ کیت (`ToggleScreen`). توضیح‌ها همان جدولِ سه‌زبانهٔ بخش‌ها.
 */

/** `settings/sales/gift-receipt`: رسیدِ هدیهٔ بی قیمت (POS-34) */
@Composable
internal fun RouteScope.GiftReceiptScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { GiftReceiptPrefs.load(context); GiftReceiptPrefs.on.value }
  ToggleScreen(SettingsLayout.GIFT_RECEIPT, onBack, on, StrShell.switchOn, StrGift.note) { GiftReceiptPrefs.set(context, it) }
}

/** `settings/sales/deliver-later`: فروش با تحویلِ بعدی (POS-38) */
@Composable
internal fun RouteScope.DeliverLaterScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { DeliverLaterPrefs.load(context); DeliverLaterPrefs.on.value }
  ToggleScreen(SettingsLayout.DELIVER_LATER, onBack, on, StrShell.switchOn, StrDeliver.note) { DeliverLaterPrefs.set(context, it) }
}

/** `settings/sales/special-orders`: سفارشِ ویژه با پیش‌پرداخت (POS-26) */
@Composable
internal fun RouteScope.SpecialOrderScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.SPECIAL_ORDER, onBack, SpecialOrderPref.on(context), StrShell.switchOn, StrSpecialOrder.settingsNote, deviceOnly = true) {
    SpecialOrderPref.set(context, it)
  }
}

/** `settings/sales/price-why`: «چرا این قیمت؟» در صندوق (POS-22) */
@Composable
internal fun RouteScope.PriceWhyScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { PriceWhyPrefs.load(context); PriceWhyPrefs.on.value }
  ToggleScreen(SettingsLayout.PRICE_WHY, onBack, on, StrShell.switchOn, StrWhy.note) { PriceWhyPrefs.set(context, it) }
}

/** `settings/sales/shrink-report`: گزارشِ ضایعات و کسری (INV-17) */
@Composable
internal fun RouteScope.ShrinkReportScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.SHRINK_REPORT, onBack, ShrinkReportPref.on(context), StrShrink.settingsOn, StrShrink.settingsNote) {
    ShrinkReportPref.set(context, it)
  }
}

/** `settings/sales/labels`: برچسب و بارکدِ داخلی (PRD-23، PRD-05) */
@Composable
internal fun RouteScope.LabelToolsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.LABEL_TOOLS, onBack, LabelToolsPref.on(context), StrLabel.settingsOn, StrLabel.settingsNote) {
    LabelToolsPref.set(context, it)
  }
}

/** `settings/sales/product-info`: کد، نام‌های دیگر و محلِ قفسهٔ کالا (PRD-02، PRD-26) */
@Composable
internal fun RouteScope.ProductInfoScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { ProductInfoPrefs.load(context); ProductInfoPrefs.on.value }
  ToggleScreen(SettingsLayout.PRODUCT_INFO, onBack, on, StrShell.switchOn, StrProduct.note) { ProductInfoPrefs.set(context, it) }
}

/**
 *  `settings/sales/stock-count`: انبارگردانیِ تازه (INV-06/07/08) — همان `CountToolsPref.setOn`؛ روشن ⇒ کلیدِ دومِ
 *  «شمارشِ کور» (`setBlind`)، مثلِ `CountToolsSettings` ِ رابطِ امروز.
 */
@Composable
internal fun RouteScope.CountToolsScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var on by remember { mutableStateOf(CountToolsPref.on(context)) }
  var blind by remember { mutableStateOf(CountToolsPref.blind(context)) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.COUNT_TOOLS),
    subtitle = if (on) StrCount.settingsOn else StrCount.off,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { on = it == 1; CountToolsPref.setOn(context, on) }) }
      SettingsNote(StrCount.settingsNote)
    }
    if (on) {
      item(key = "blind") {
        SettingsBlock {
          PartTitle(StrCount.blindTitle)
          V3Segmented(SWITCH, if (blind) 1 else 0, { blind = it == 1; CountToolsPref.setBlind(context, blind) })
        }
        SettingsNote(StrCount.blindNote)
      }
    }
  }
}
