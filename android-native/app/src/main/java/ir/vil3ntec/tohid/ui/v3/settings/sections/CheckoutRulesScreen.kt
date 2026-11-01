package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.sale.CheckoutRules
import ir.vil3ntec.tohid.data.CheckoutRulesPref
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame

/**
 *  `settings/sales/checkout-rules`: قاعده‌های پیش از پایانِ فاکتور (POS-24) — همان `CheckoutRulesPref` و همان
 *  رفتارِ `CheckoutRulesSection` ِ رابطِ امروز: «فاکتورِ صفر ثبت نشود» و «مشتری لازم است از مبلغِ X» (خالی ⇒ خاموش،
 *  ۰ ⇒ هر فاکتور). نوشتهٔ نادرست یا منفی ذخیره نمی‌شود و آخرین مقدارِ درست می‌ماند. پیش‌فرض خاموش.
 */
@Composable
internal fun RouteScope.CheckoutRulesScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  var rules by remember { mutableStateOf(CheckoutRulesPref.get(context)) }
  var fromText by remember { mutableStateOf(rules.customerFromMinor?.let(CheckoutRulesText::afn).orEmpty()) }
  fun save(next: CheckoutRules) {
    rules = next
    CheckoutRulesPref.set(context, next)
  }
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.CHECKOUT_RULES),
    subtitle = if (rules.active) StrShell.switchOn else StrShell.switchOff,
    onBack = onBack,
  ) {
    item(key = "note") { SettingsNote(StrSale.checkoutRulesNote) }
    item(key = "zero") {
      SettingsBlock {
        V3Segmented(
          options = listOf(StrShell.switchOff, StrShell.switchOn),
          selected = if (rules.blockZeroTotal) 1 else 0,
          onSelect = { save(rules.copy(blockZeroTotal = it == 1)) },
        )
      }
      SettingsNote(StrSale.zeroTotalRule)
    }
    item(key = "from") {
      val bad = fromText.isNotBlank() && CheckoutRulesText.valid(fromText) == null
      SettingsBlock {
        V3TextField(
          value = fromText,
          onValueChange = { text ->
            fromText = text.take(14)
            if (text.isBlank()) save(rules.copy(customerFromMinor = null))
            else CheckoutRulesText.valid(text)?.let { save(rules.copy(customerFromMinor = PayFlow.minor(it))) }
          },
          label = StrSale.customerFromLabel,
          modifier = Modifier.fillMaxWidth(),
          kind = V3FieldKind.MONEY,
          helper = StrSale.customerFromHint,
          error = if (bad) StrSale.badNumberShort else null,
          imeAction = ImeAction.Done,
        )
      }
    }
  }
}

/** نوشتهٔ کادرِ آستانه — همان قاعدهٔ `CheckoutRulesSection` (آزمون: `SettingsPortTest`) */
internal object CheckoutRulesText {
  /** عددِ نامنفی، یا null */
  fun valid(text: String): Double? = ir.vil3ntec.tohid.parseNumber(text)?.takeIf { it >= 0 }

  /** پول ⇒ نوشتهٔ افغانی برای کادر: ۱۲۳۴۵۰ ⇒ «1234.5»، ۱۰۰۰۰۰ ⇒ «1000» */
  fun afn(minor: Long): String {
    val whole = minor / 100
    val frac = minor % 100
    return if (frac == 0L) whole.toString() else whole.toString() + "." + frac.toString().padStart(2, '0').trimEnd('0')
  }
}
