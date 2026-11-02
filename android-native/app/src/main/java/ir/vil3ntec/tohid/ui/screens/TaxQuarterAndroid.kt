package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.StrTaxQuarter
import ir.vil3ntec.tohid.core.report.QuarterTax
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.TaxQuarter
import ir.vil3ntec.tohid.data.TaxQuarterPref
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.v3.stock.rememberCsvExport
import kotlinx.coroutines.launch

/**
 *  RPT-10 — بخشِ «گزارشِ مالیاتِ سه‌ماهه» در تنظیماتِ گوشی و کامپیوتر (همان الگوی RPT-17 ِ «شماره‌های
 *  جاافتاده») و کارتِ یادآورِ سررسید روی خانه. مرزِ پلتفرم: خواندن/نوشتنِ `TaxQuarterPref`، ذخیرهٔ فایل
 *  و چاپ؛ حساب‌ها همه در `TaxQuarter` / `core/report/QuarterTax`. عنوانش در فهرستِ `DeskSettingsLegacy` هم هست.
 *  خاموش (پیش‌فرض) ⇒ فقط کلیدِ روشن‌کردن؛ کارتِ خانه کشیده نمی‌شود.
 */
@Composable
fun TaxQuarterSettingsWired(context: android.content.Context, d: ShopData, snackbar: SnackbarHostState) {
  val scope = rememberCoroutineScope()
  fun toast(t: String) { scope.launch { snackbar.showSnackbar(t) } }
  var on by remember { mutableStateOf(TaxQuarterPref.on(context)) }
  var cfg by remember { mutableStateOf(TaxQuarterPref.config(context)) }
  SettingsSection(
    icon = Icons.Filled.Receipt,
    title = StrTaxQuarter.settingsTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrTaxQuarter.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; TaxQuarterPref.setOn(context, on) },
    )
    if (!on) return@SettingsSection
    Spacer(Modifier.height(10.dp))
    TaxQuarterConfigForm(cfg) { next ->
      cfg = next.clamped()
      TaxQuarterPref.setConfig(context, cfg)
      toast(StrTaxQuarter.saved)
    }
    Spacer(Modifier.height(12.dp))
    TaxQuarterReportBody(d, cfg, onMessage = ::toast)
  }
}

@Composable
private fun TaxQuarterConfigForm(cfg: QuarterTax.Config, onSave: (QuarterTax.Config) -> Unit) {
  var start by remember(cfg) { mutableStateOf(cfg.startMonth) }
  var rate by remember(cfg) { mutableStateOf(if (cfg.rateBp == 0) "" else QuarterTax.rateText(cfg.rateBp)) }
  var dueDay by remember(cfg) { mutableStateOf("${cfg.dueDay}") }
  var dueMonths by remember(cfg) { mutableStateOf("${cfg.dueMonths}") }
  var remind by remember(cfg) { mutableStateOf("${cfg.remindDays}") }
  var menu by remember { mutableStateOf(false) }
  val lang = ir.vil3ntec.tohid.LocaleChoice.lang.code
  fun int(s: String): Int? = ir.vil3ntec.tohid.normalizeDigits(s).trim().toIntOrNull()
  val rateBp = if (rate.isBlank()) 0 else QuarterTax.parseRate(rate)

  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(StrTaxQuarter.startMonth, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
    Box {
      OutlinedButton(onClick = { menu = true }) { Text(SolarHijri.monthName(start, lang)) }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        for (m in 1..12) DropdownMenuItem(text = { Text(SolarHijri.monthName(m, lang)) }, onClick = { start = m; menu = false })
      }
    }
  }
  OutlinedTextField(
    value = rate, onValueChange = { rate = it.take(6) }, label = { Text(StrTaxQuarter.rate) }, singleLine = true,
    isError = rateBp == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
  )
  Text(if (rateBp == null) StrTaxQuarter.rateBad else if (rateBp == 0) StrTaxQuarter.noRate else "", style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
  Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    OutlinedTextField(dueDay, { dueDay = it.take(2) }, label = { Text(StrTaxQuarter.dueDay) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
    OutlinedTextField(dueMonths, { dueMonths = it.take(1) }, label = { Text(StrTaxQuarter.dueMonths) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
    OutlinedTextField(remind, { remind = it.take(2) }, label = { Text(StrTaxQuarter.remindDays) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
  }
  TextButton(
    enabled = rateBp != null,
    onClick = {
      onSave(
        QuarterTax.Config(
          startMonth = start,
          rateBp = rateBp ?: return@TextButton,
          dueDay = int(dueDay) ?: cfg.dueDay,
          dueMonths = int(dueMonths) ?: cfg.dueMonths,
          remindDays = int(remind) ?: cfg.remindDays,
        ),
      )
    },
  ) { Text(StrTaxQuarter.save) }
}

@Composable
private fun TaxQuarterReportBody(d: ShopData, cfg: QuarterTax.Config, onMessage: (String) -> Unit) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val today = ir.vil3ntec.tohid.todayIso()
  var quarter by remember(cfg.startMonth) { mutableStateOf(TaxQuarter.initial(today, cfg)) }
  val x = quarter ?: return
  val report = remember(d, cfg, x) { TaxQuarter.report(d, cfg, x) } ?: return
  val s = report.summary
  val lang = ir.vil3ntec.tohid.LocaleChoice.lang.code
  val csv = rememberCsvExport { ok -> onMessage(if (ok) StrTaxQuarter.exported else StrTaxQuarter.exportFailed) }
  fun sheet(): ReportCsv.Sheet = TaxQuarter.sheet(report, cfg.startMonth)

  Row(verticalAlignment = Alignment.CenterVertically) {
    TextButton(onClick = { quarter = QuarterTax.previous(x) }) { Text(StrTaxQuarter.prev) }
    Text(
      StrTaxQuarter.quarter(x.q, x.fiscalYear, TaxQuarter.monthsLabel(x, cfg.startMonth, lang)),
      Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
    )
    TextButton(onClick = { quarter = QuarterTax.next(x) }) { Text(StrTaxQuarter.next) }
  }
  fun afn(minor: Long) = "${ir.vil3ntec.tohid.money(minor / 100.0)} ؋"
  TaxLine(StrTaxQuarter.sales, StrTaxQuarter.count(s.salesCount), afn(s.salesMinor))
  TaxLine(StrTaxQuarter.returns, StrTaxQuarter.count(s.returnsCount), afn(s.returnsMinor))
  TaxLine(StrTaxQuarter.net, "", afn(s.netMinor), bold = true)
  TaxLine(StrTaxQuarter.rate, "", QuarterTax.rateText(s.rateBp))
  TaxLine(StrTaxQuarter.salesTax, "", afn(s.salesTaxMinor))
  TaxLine(StrTaxQuarter.returnsTax, "", afn(s.returnsTaxMinor))
  TaxLine(StrTaxQuarter.netTax, "", afn(s.netTaxMinor), bold = true)
  TaxLine(StrTaxQuarter.purchases, StrTaxQuarter.count(s.purchasesCount), afn(s.purchasesMinor))
  report.dueIso?.let { Text(StrTaxQuarter.due(ir.vil3ntec.tohid.formatDate(it)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted) }
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
    OutlinedButton(onClick = {
      val sh = sheet()
      if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) {
        val saved = ir.vil3ntec.tohid.ui.Platform.saveFile("${sh.name}.csv", ReportCsv.text(sh).toByteArray(Charsets.UTF_8))
        if (saved != null) onMessage(StrTaxQuarter.exported)
      } else csv(sh)
    }) { Text(StrTaxQuarter.export) }
    OutlinedButton(onClick = { val sh = sheet(); ir.vil3ntec.tohid.print.ReportPrint.print(context, sh, sh.name) }) { Text(StrTaxQuarter.print) }
  }
}

@Composable
private fun TaxLine(label: String, count: String, value: String, bold: Boolean = false) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else null)
    if (count.isNotEmpty()) Text(count, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted, modifier = Modifier.padding(horizontal = 8.dp))
    Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = if (bold) FontWeight.Bold else null)
  }
}

/**
 *  یادآورِ سررسید روی خانه (رابطِ امروز): فقط وقتی «گزارشِ مالیاتِ سه‌ماهه» روشن است و امروز در پنجرهٔ
 *  `remindDays` ِ پیش از سررسید است (`QuarterTax.reminder`)؛ لمس ⇒ تنظیمات. خاموش ⇒ هیچ چیز کشیده نمی‌شود.
 */
@Composable
fun TaxDueBanner(onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val on = remember { TaxQuarterPref.on(context) }
  if (!on) return
  val today = ir.vil3ntec.tohid.todayIso()
  val due = remember(today) { QuarterTax.reminder(today, TaxQuarterPref.config(context)) } ?: return
  Row(
    modifier
      .fillMaxWidth()
      .background(Shop.colors.warning.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
      .clickable { onOpen("settings") }
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Filled.Receipt, contentDescription = null, tint = Shop.colors.warning)
    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
      Text(
        if (due.daysLeft == 0) StrTaxQuarter.reminderToday else StrTaxQuarter.reminderTitle(due.daysLeft),
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text,
      )
      Text(StrTaxQuarter.due(ir.vil3ntec.tohid.formatDate(due.dueIso)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Text(StrTaxQuarter.open, style = MaterialTheme.typography.labelMedium, color = Shop.colors.primary)
  }
}
