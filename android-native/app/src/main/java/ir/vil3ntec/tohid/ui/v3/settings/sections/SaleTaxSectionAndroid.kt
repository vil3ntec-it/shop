package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSaleTax
import ir.vil3ntec.tohid.core.product.ProductAlternatives
import ir.vil3ntec.tohid.core.sale.SaleTax
import ir.vil3ntec.tohid.data.SaleTaxPref
import ir.vil3ntec.tohid.data.SaleTaxWiring
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote

/**
 *  POS-37 — بخشِ «مالیاتِ فاکتور» در صفحهٔ `settings/sales/auto-fees` (هزینه‌هایی که خودکار به فاکتور افزوده می‌شوند): حالت
 *  (خاموش / روی کلِ فاکتور / هر ردیف جدا)، نرخِ پایه به درصد (بی پیش‌فرض، D-11) و در حالتِ ردیف نرخِ کالاها («کالا، کالا =
 *  درصد»، همان یابندهٔ نام/بارکدِ هزینهٔ خودکار). ذخیره فقط وقتی همه درست‌اند (`SaleTaxPref.set`، کلیدِ دکان). تا پرچمِ سرورِ
 *  `shop.sale_tax` روشن نیست یادداشتِ «سرور هنوز روشن نکرده» دیده می‌شود و صندوق بی مالیات می‌فروشد.
 */
@Composable
internal fun SaleTaxSection(d: ShopData) {
  val context = LocalContext.current
  val stored = remember { SaleTaxPref.config(context) }
  var mode by remember { mutableStateOf(stored?.mode) }
  var rateText by remember { mutableStateOf(stored?.rateBp?.let(::percentText).orEmpty()) }
  val names = remember(d.products) { d.products.associate { it.id to it.name } }
  var ratesText by remember { mutableStateOf(stored?.rates.orEmpty().let { SaleTax.formatRates(it, { id -> names[id] ?: id }, ::percentText) }) }
  var saved by remember { mutableStateOf(false) }
  val resolve = remember(d.products) {
    ir.vil3ntec.tohid.ui.components.pos.resolver(d.products.map { ProductAlternatives.Ref(it.id, it.name, it.barcodes) })
  }
  val rate = if (rateText.isBlank()) 0 else SaleTax.rateOf(rateText)
  val parsed = SaleTax.parseRates(if (mode == SaleTax.Mode.LINE) ratesText else "", resolve)
  val ratesError = parsed.problems.firstOrNull()?.second?.let {
    when (it) {
      SaleTax.Problem.BAD_RATE -> StrSaleTax.badRateLine
      SaleTax.Problem.UNKNOWN_ITEM -> StrSaleTax.badItem
      SaleTax.Problem.TOO_MANY -> StrSaleTax.tooMany
      SaleTax.Problem.BAD_LINE -> StrSaleTax.badLine
    }
  }
  val modes = listOf(null, SaleTax.Mode.INVOICE, SaleTax.Mode.LINE)
  SettingsBlock {
    V3Segmented(
      options = listOf(StrSaleTax.modeOff, StrSaleTax.modeInvoice, StrSaleTax.modeLine),
      selected = modes.indexOf(mode).coerceAtLeast(0),
      onSelect = { mode = modes[it]; saved = false },
      modifier = Modifier.fillMaxWidth(),
    )
    if (mode != null) {
      V3TextField(
        value = rateText,
        onValueChange = { rateText = it.take(12); saved = false },
        label = StrSaleTax.rate,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        error = if (rate == null) StrSaleTax.rateBad else null,
      )
    }
    if (mode == SaleTax.Mode.LINE) {
      V3TextField(
        value = ratesText,
        onValueChange = { ratesText = it.take(SaleTax.MAX_RATES * 80); saved = false },
        label = StrSaleTax.rates,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        error = ratesError,
        singleLine = false,
      )
    }
    V3Button(
      text = StrSaleTax.save,
      onClick = {
        SaleTaxPref.set(context, mode?.let { SaleTax.Config(it, rate ?: 0, parsed.rates) })
        saved = true
      },
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
      kind = V3ButtonKind.SECONDARY,
      enabled = rate != null && ratesError == null,
    )
  }
  SettingsNote(StrSaleTax.note)
  if (!SaleTaxWiring.serverFlag) SettingsNote(StrSaleTax.serverOff)
  if (saved) SettingsNote(StrSaleTax.saved)
}

/** صدمِ درصد ⇒ «۱۰» یا «۵٫۵» (رقمِ لاتین؛ کادر رقمِ فارسی را هم می‌خواند) */
private fun percentText(bp: Int): String {
  val whole = bp / 100
  val frac = bp % 100
  return if (frac == 0) "$whole" else "$whole." + frac.toString().padStart(2, '0').trimEnd('0')
}
