package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.LocaleText
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.i18n.StrCustomerCard
import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.core.i18n.StrLayaway
import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrLoyalty
import ir.vil3ntec.tohid.core.i18n.StrSafe
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.StrTaxQuarter
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.core.product.ProductAlternatives
import ir.vil3ntec.tohid.core.sale.AutoFees
import ir.vil3ntec.tohid.data.AutoFeesPref
import ir.vil3ntec.tohid.core.report.QuarterTax
import ir.vil3ntec.tohid.data.CustomerCardPref
import ir.vil3ntec.tohid.data.GiftCardPref
import ir.vil3ntec.tohid.data.LayawayPref
import ir.vil3ntec.tohid.data.LinePolicyPrefs
import ir.vil3ntec.tohid.data.LoyaltyPref
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.SafeBookPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.TaxQuarter
import ir.vil3ntec.tohid.data.TaxQuarterPref
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3PickerRow
import ir.vil3ntec.tohid.ui.v3.V3PickerSheet
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import ir.vil3ntec.tohid.ui.v3.stock.rememberCsvExport

/*
 *  تنظیم‌های پول و صندوق که فقط در رابطِ امروز بودند (`GiftCardSettingsWired`، `LayawaySettingsWired`،
 *  `SafeBookSettingsWired`، `CustomerCardSettingsWired`، `LoyaltySettingsWired`، `TaxQuarterSettingsWired` در
 *  رابطِ قدیمِ پاک‌شده) — همان کلید، همان پیش‌فرضِ خاموش، همان نوشتن و همان سنجش (`SettingsPortTest`)؛
 *  فقط رویِ کیت (`ToggleScreen`، `V3TextField`). همه فقط همین دستگاه.
 */

/** `settings/sales/gift-cards`: کارتِ هدیه و اعتبارِ فروشگاه (PAY-24) */
@Composable
internal fun RouteScope.GiftCardScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(
    SettingsLayout.GIFT_CARD, onBack, GiftCardPref.on(context), StrShell.switchOn, StrGiftCard.settingsNote,
    deviceOnly = true, more = listOf(StrGiftCard.needsPayPanel),
  ) { GiftCardPref.set(context, it) }
}

/** `settings/sales/layaway`: فروشِ امانی و قسطی (POS-27) */
@Composable
internal fun RouteScope.LayawayScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.LAYAWAY, onBack, LayawayPref.on(context), StrShell.switchOn, StrLayaway.settingsNote, deviceOnly = true) {
    LayawayPref.set(context, it)
  }
}

/** `settings/sales/safe`: گاوصندوق در «تاریخچهٔ فروش» (PAY-18) */
@Composable
internal fun RouteScope.SafeBookScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.SAFE_BOOK, onBack, SafeBookPref.on(context), StrShell.switchOn, StrSafe.settingsNote, deviceOnly = true) {
    SafeBookPref.set(context, it)
  }
}

/** `settings/sales/customer-cards`: کارت یا بارکدِ مشتری (CRM-16) */
@Composable
internal fun RouteScope.CustomerCardScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(
    SettingsLayout.CUSTOMER_CARD, onBack, CustomerCardPref.on(context), StrShell.switchOn, StrCustomerCard.settingsNote, deviceOnly = true,
  ) { CustomerCardPref.setOn(context, it) }
}

/**
 *  `settings/sales/loyalty`: امتیازِ وفاداری و سطحِ مشتری (PRM-13) — روشن/خاموش برای همین دستگاه؛ قاعده فقط صاحب
 *  یا مدیر (همان `!ShopRole.isStaff` ِ رابطِ امروز؛ شاگرد کادرها را بسته می‌بیند) و فقط قاعدهٔ درست نوشته می‌شود.
 */
@Composable
internal fun RouteScope.LoyaltyScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var on by remember { mutableStateOf(LoyaltyPref.on(context)) }
  val may = remember { !ShopRole.isStaff(context) }
  val rule0 = remember { LoyaltyPref.rule(context) }
  var every by remember { mutableStateOf(rule0.everyAfn.toString()) }
  var point by remember { mutableStateOf(rule0.pointAfn.toString()) }
  var share by remember { mutableStateOf(rule0.maxSharePct.toString()) }
  var silver by remember { mutableStateOf(rule0.silverAfn.toString()) }
  var gold by remember { mutableStateOf(rule0.goldAfn.toString()) }
  var note by remember { mutableStateOf<String?>(null) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.LOYALTY),
    subtitle = if (on) StrShell.switchOn else StrSettings.off,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { on = it == 1; LoyaltyPref.set(context, on) }) }
      SettingsNote(StrLoyalty.settingsNote)
      SettingsNote(StrShell.thisDeviceOnly)
    }
    item(key = "rule") {
      SettingsNote(StrLoyalty.tiersNote)
      if (!may) SettingsNote(StrLoyalty.ownerOnly)
      SettingsBlock {
        LoyaltyField(StrLoyalty.everyAfn, every, may) { every = it }
        LoyaltyField(StrLoyalty.pointAfn, point, may) { point = it }
        LoyaltyField(StrLoyalty.maxShare, share, may) { share = it }
        LoyaltyField(StrLoyalty.silverAfn, silver, may) { silver = it }
        LoyaltyField(StrLoyalty.goldAfn, gold, may) { gold = it }
        if (may) {
          V3Button(
            text = StrLoyalty.save,
            onClick = { note = if (LoyaltyPref.setRule(context, LoyaltyRuleText.rule(every, point, share, silver, gold))) StrLoyalty.saved else StrLoyalty.badRule },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            kind = V3ButtonKind.SECONDARY,
          )
        }
      }
      note?.let { SettingsNote(it) }
    }
    //  PRM-13: قاعدهٔ یکسانِ دکان (`LoyaltyShop`) و انقضای امتیاز — همان جزءِ رابطِ امروز، همان نوشتن‌ها
    item(key = "shop") { ir.vil3ntec.tohid.ui.components.pos.LoyaltyShopSettings(context) }
  }
}

@Composable
private fun LoyaltyField(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
  V3TextField(
    value = value,
    onValueChange = { onChange(LoyaltyRuleText.field(it)) },
    label = label,
    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    kind = V3FieldKind.NUMBER,
    enabled = enabled,
  )
}

/**
 *  `settings/sales/tax-quarter`: گزارشِ مالیاتِ سه‌ماهه و یادآورِ سررسید (RPT-10) — همان `TaxQuarterPref.setOn` و
 *  `setConfig`؛ روشن ⇒ فرمِ نرخ و سررسید (همان سنجشِ `TaxQuarterText`) و پیش‌نمایشِ سه‌ماهه با خروجیِ CSV و چاپ.
 */
@Composable
internal fun RouteScope.TaxQuarterScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var on by remember { mutableStateOf(TaxQuarterPref.on(context)) }
  var cfg by remember { mutableStateOf(TaxQuarterPref.config(context)) }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.TAX_QUARTER),
    subtitle = if (on) StrShell.switchOn else StrSettings.off,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { on = it == 1; TaxQuarterPref.setOn(context, on) }) }
      SettingsNote(StrTaxQuarter.settingsNote)
      SettingsNote(StrShell.thisDeviceOnly)
    }
    if (on) {
      item(key = "config") {
        TaxQuarterConfigBlock(cfg) { next ->
          cfg = next
          TaxQuarterPref.setConfig(context, next)
          toasts.show(StrTaxQuarter.saved)
        }
      }
      item(key = "report") { TaxQuarterReportBlock(data, cfg) { toasts.show(it) } }
    }
  }
}

@Composable
private fun TaxQuarterConfigBlock(cfg: QuarterTax.Config, onSave: (QuarterTax.Config) -> Unit) {
  var start by remember(cfg) { mutableStateOf(cfg.startMonth) }
  var rate by remember(cfg) { mutableStateOf(TaxQuarterText.rateField(cfg.rateBp)) }
  var dueDay by remember(cfg) { mutableStateOf("${cfg.dueDay}") }
  var dueMonths by remember(cfg) { mutableStateOf("${cfg.dueMonths}") }
  var remind by remember(cfg) { mutableStateOf("${cfg.remindDays}") }
  var pickMonth by remember { mutableStateOf(false) }
  val lang = LocaleChoice.lang.code
  val months = (1..12).map { SolarHijri.monthName(it, lang) }
  val rateBp = TaxQuarterText.rateBp(rate)
  V3Section(null) { V3PickerRow(StrTaxQuarter.startMonth, months[(start - 1).coerceIn(0, 11)], onClick = { pickMonth = true }) }
  SettingsBlock {
    V3TextField(
      value = rate,
      onValueChange = { rate = it.take(6) },
      label = StrTaxQuarter.rate,
      modifier = Modifier.fillMaxWidth(),
      kind = V3FieldKind.NUMBER,
      helper = if (rateBp == 0) StrTaxQuarter.noRate else null,
      error = if (rateBp == null) StrTaxQuarter.rateBad else null,
    )
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      V3TextField(dueDay, { dueDay = it.take(2) }, StrTaxQuarter.dueDay, Modifier.weight(1f), kind = V3FieldKind.NUMBER)
      V3TextField(dueMonths, { dueMonths = it.take(1) }, StrTaxQuarter.dueMonths, Modifier.weight(1f), kind = V3FieldKind.NUMBER)
      V3TextField(remind, { remind = it.take(2) }, StrTaxQuarter.remindDays, Modifier.weight(1f), kind = V3FieldKind.NUMBER)
    }
    V3Button(
      text = StrTaxQuarter.save,
      onClick = { TaxQuarterText.config(start, rate, dueDay, dueMonths, remind, cfg)?.let(onSave) },
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      kind = V3ButtonKind.SECONDARY,
      enabled = rateBp != null,
    )
  }
  if (pickMonth) {
    V3PickerSheet(
      title = StrTaxQuarter.startMonth,
      options = months,
      selected = months[(start - 1).coerceIn(0, 11)],
      onPick = { name -> months.indexOf(name).takeIf { it >= 0 }?.let { start = it + 1 }; pickMonth = false },
      onDismiss = { pickMonth = false },
      allowNew = false,
    )
  }
}

/** پیش‌نمایشِ سه‌ماهه — همان عددهای `TaxQuarter.report` و همان برگهٔ CSV و چاپِ رابطِ امروز */
@Composable
private fun TaxQuarterReportBlock(d: ShopData, cfg: QuarterTax.Config, onMessage: (String) -> Unit) {
  val context = LocalContext.current
  val today = ir.vil3ntec.tohid.todayIso()
  var quarter by remember(cfg.startMonth) { mutableStateOf(TaxQuarter.initial(today, cfg)) }
  val x = quarter ?: return
  val report = remember(d, cfg, x) { TaxQuarter.report(d, cfg, x) } ?: return
  val s = report.summary
  val lang = LocaleChoice.lang.code
  val csv = rememberCsvExport { ok -> onMessage(if (ok) StrTaxQuarter.exported else StrTaxQuarter.exportFailed) }
  fun sheet(): ReportCsv.Sheet = TaxQuarter.sheet(report, cfg.startMonth)
  val money = { minor: Long -> LocaleText.afghani(minor / 100.0, LocaleChoice.screenDigits, LocaleChoice.symbol) }
  SettingsBlock {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      V3TextButton(StrTaxQuarter.prev, { quarter = QuarterTax.previous(x) })
      Text(
        StrTaxQuarter.quarter(x.q, x.fiscalYear, TaxQuarter.monthsLabel(x, cfg.startMonth, lang)),
        Modifier.weight(1f),
        style = V3Type.subhead,
        color = PhoneV3.colors.ink,
        textAlign = TextAlign.Center,
      )
      V3TextButton(StrTaxQuarter.next, { quarter = QuarterTax.next(x) })
    }
    TaxLine(StrTaxQuarter.sales, StrTaxQuarter.count(s.salesCount), money(s.salesMinor))
    TaxLine(StrTaxQuarter.returns, StrTaxQuarter.count(s.returnsCount), money(s.returnsMinor))
    TaxLine(StrTaxQuarter.net, "", money(s.netMinor), bold = true)
    TaxLine(StrTaxQuarter.rate, "", QuarterTax.rateText(s.rateBp))
    TaxLine(StrTaxQuarter.salesTax, "", money(s.salesTaxMinor))
    TaxLine(StrTaxQuarter.returnsTax, "", money(s.returnsTaxMinor))
    TaxLine(StrTaxQuarter.netTax, "", money(s.netTaxMinor), bold = true)
    TaxLine(StrTaxQuarter.purchases, StrTaxQuarter.count(s.purchasesCount), money(s.purchasesMinor))
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      V3Button(
        text = StrTaxQuarter.export,
        onClick = {
          val sh = sheet()
          if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
            val saved = ir.vil3ntec.tohid.ui.Platform.saveFile("${sh.name}.csv", ReportCsv.text(sh).toByteArray(Charsets.UTF_8))
            if (saved != null) onMessage(StrTaxQuarter.exported)
          } else csv(sh)
        },
        modifier = Modifier.weight(1f),
        kind = V3ButtonKind.NEUTRAL,
      )
      V3Button(
        text = StrTaxQuarter.print,
        onClick = { val sh = sheet(); ir.vil3ntec.tohid.print.ReportPrint.print(context, sh, sh.name) },
        modifier = Modifier.weight(1f),
        kind = V3ButtonKind.NEUTRAL,
      )
    }
  }
  report.dueIso?.let { SettingsNote(StrTaxQuarter.due(ir.vil3ntec.tohid.formatDate(it))) }
}

@Composable
private fun TaxLine(label: String, count: String, value: String, bold: Boolean = false) {
  val c = PhoneV3.colors
  val weight = if (bold) FontWeight.Bold else null
  Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = V3Type.callout, color = c.ink, fontWeight = weight)
    if (count.isNotEmpty()) Text(count, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(horizontal = 8.dp))
    Text(value, style = V3Type.callout, color = c.ink, fontWeight = weight)
  }
}

/**
 *  `settings/sales/line-pricing`: قیمت و تخفیفِ ردیف (POS-08/10/20/21) — همان `LinePolicyPrefs.setOn`،
 *  `setDirection` و `setFreeLineCapMinor` ِ `LinePricingSettingsWired`؛ روشن ⇒ سوی تغییرِ قیمت و سقفِ ردیفِ رایگان
 *  (همان سنجشِ `LinePricingText`). فقط همین دستگاه، پیش‌فرض خاموش.
 */
@Composable
internal fun RouteScope.LinePricingScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var on by remember { mutableStateOf(LinePolicyPrefs.on(context)) }
  var direction by remember { mutableStateOf(LinePolicyPrefs.direction(context)) }
  var capText by remember { mutableStateOf(LinePricingText.capField(LinePolicyPrefs.freeLineCapMinor(context))) }
  val directions = LinePricing.Direction.entries
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.LINE_PRICING),
    subtitle = if (on) StrLine.sectionOn else StrShell.switchOff,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock { V3Segmented(SWITCH, if (on) 1 else 0, { on = it == 1; LinePolicyPrefs.setOn(context, on) }) }
      SettingsNote(StrLine.note)
    }
    if (on) {
      item(key = "direction") {
        SettingsBlock {
          PartTitle(StrLine.direction)
          V3Segmented(directions.map(::directionLabel), directions.indexOf(direction).coerceAtLeast(0), { i ->
            direction = directions[i]
            LinePolicyPrefs.setDirection(context, direction)
          })
        }
      }
      item(key = "cap") {
        SettingsBlock {
          V3TextField(
            value = capText,
            onValueChange = { text ->
              capText = text
              (LinePricingText.cap(text) as? LinePricingText.Cap.Write)?.let { LinePolicyPrefs.setFreeLineCapMinor(context, it.minor) }
            },
            label = StrLine.freeCap,
            modifier = Modifier.fillMaxWidth(),
            kind = V3FieldKind.MONEY,
            error = if (LinePricingText.cap(capText) == LinePricingText.Cap.Skip) StrSale.badNumberShort else null,
          )
        }
        SettingsNote(StrLine.freeCapNote)
      }
    }
  }
}

private fun directionLabel(d: LinePricing.Direction): String = when (d) {
  LinePricing.Direction.BOTH -> StrLine.dirBoth
  LinePricing.Direction.UP -> StrLine.dirUp
  LinePricing.Direction.DOWN -> StrLine.dirDown
  LinePricing.Direction.NONE -> StrLine.dirNone
}

/**
 *  `settings/sales/auto-fees`: هزینهٔ خودکارِ فاکتور (POS-36) — همان `AutoFeesSettingsWired`: کادرِ چندخطی با همان
 *  `AutoFees.parse` و همان یابندهٔ نام/بارکد (`resolver`)، ایرادِ نخستین خط زیرِ کادر، و ذخیره فقط وقتی همه درست‌اند
 *  (`AutoFeesPref.set`). همین دستگاه؛ پیش‌فرض بی قاعده.
 */
@Composable
internal fun RouteScope.AutoFeesScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val d = data
  var rules by remember { mutableStateOf(AutoFeesPref.rules(context)) }
  val names = remember(d.products) { d.products.associate { it.id to it.name } }
  fun shown(r: List<AutoFees.Rule>) = AutoFees.format(r, { names[it] ?: it }, { ir.vil3ntec.tohid.qty(it / 100.0) })
  var text by remember(rules) { mutableStateOf(shown(rules)) }
  var saved by remember { mutableStateOf(false) }
  val resolve = remember(d.products) {
    ir.vil3ntec.tohid.ui.components.pos.resolver(d.products.map { ProductAlternatives.Ref(it.id, it.name, it.barcodes) })
  }
  val parsed = AutoFees.parse(text, resolve)
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.AUTO_FEES),
    subtitle = if (rules.isEmpty()) StrShell.switchOff else StrSale.feesCount(ir.vil3ntec.tohid.plain(rules.size)),
    onBack = onBack,
  ) {
    item(key = "note") { SettingsNote(StrSale.feesNote) }
    item(key = "rules") {
      SettingsBlock {
        V3TextField(
          value = text,
          onValueChange = { text = it.take(AutoFees.MAX_RULES * 120); saved = false },
          label = StrSale.feesTitle,
          modifier = Modifier.fillMaxWidth(),
          error = parsed.problems.firstOrNull()?.let { StrSale.feesBad(it.first) },
          singleLine = false,
        )
        V3Button(
          text = StrSale.feesSave,
          onClick = { rules = parsed.rules; AutoFeesPref.set(context, parsed.rules); saved = true },
          modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
          kind = V3ButtonKind.SECONDARY,
          enabled = parsed.problems.isEmpty(),
        )
      }
      if (saved) SettingsNote(StrSale.feesSaved)
    }
    item(key = "sale-tax") { SaleTaxSection(d) } // POS-37: مالیاتِ فاکتور، همان «افزوده به فاکتور»
  }
}
