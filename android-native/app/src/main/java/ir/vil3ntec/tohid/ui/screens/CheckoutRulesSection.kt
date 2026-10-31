package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.sale.CheckoutRules
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  POS-24 — بخشِ «قاعده‌های پایانِ فاکتور» در تنظیمات (حالت بیرون نگه داشته می‌شود؛ ذخیره با
 *  `CheckoutRulesPref` در `Settings`). خالی ⇒ «مشتری از مبلغِ X» خاموش؛ نوشتهٔ نادرست یا منفی ذخیره
 *  نمی‌شود و آخرین مقدارِ درست می‌ماند.
 */
@Composable
internal fun CheckoutRulesSection(rules: CheckoutRules, onChange: (CheckoutRules) -> Unit) {
  var fromText by remember { mutableStateOf(rules.customerFromMinor?.let(::afnText).orEmpty()) }
  fun valid(text: String): Double? = ir.vil3ntec.tohid.parseNumber(text)?.takeIf { it >= 0 }
  SettingsSection(
    icon = Icons.Filled.PointOfSale,
    title = StrSale.checkoutRulesTitle,
    subtitle = if (rules.active) StrShell.switchOn else StrShell.switchOff,
    tint = if (rules.active) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrSale.checkoutRulesNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    Text(StrSale.zeroTotalRule, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (rules.blockZeroTotal) "on" else "off",
      onSelect = { onChange(rules.copy(blockZeroTotal = it == "on")) },
    )
    Spacer(Modifier.height(8.dp))
    TohidTextField(
      fromText,
      { text ->
        fromText = text.take(14)
        if (text.isBlank()) onChange(rules.copy(customerFromMinor = null))
        else valid(text)?.let { onChange(rules.copy(customerFromMinor = ir.vil3ntec.tohid.data.PayFlow.minor(it))) }
      },
      label = StrSale.customerFromLabel,
      supporting = StrSale.customerFromHint,
      error = if (fromText.isNotBlank() && valid(fromText) == null) StrSale.badNumberShort else null,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
  }
}

/** پول ⇒ نوشتهٔ افغانی برای کادر: ۱۲۳۴۵۰ ⇒ «1234.5»، ۱۰۰۰۰۰ ⇒ «1000» */
private fun afnText(minor: Long): String {
  val whole = minor / 100
  val frac = minor % 100
  return if (frac == 0L) whole.toString() else whole.toString() + "." + frac.toString().padStart(2, '0').trimEnd('0')
}
