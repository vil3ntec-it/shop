package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrFields
import ir.vil3ntec.tohid.data.CrmPricingForm
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  CRM-08 / CRM-12 — پنجرهٔ «گروه، لیستِ قیمت و رضایتِ تبلیغ». فقط رابط: بی Context و بی نوشتن؛
 *  ذخیره با `onSave` (درِ نوشتن در `CrmPricingAndroid.kt`، منطق در `CrmPricingForm`).
 */
@Composable
internal fun CrmPricingDialog(d: ShopData, debtor: Debtor, onDismiss: () -> Unit, onSave: (CrmPricingForm.Input) -> Unit) {
  var input by remember(debtor.id) { mutableStateOf(CrmPricingForm.of(debtor)) }
  val missingSource = CrmPricingForm.needsSource(debtor, input)
  val last = remember(d, debtor.id) { CrmPricingForm.lastConsent(d, debtor.id) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text("${StrFields.crmPricingTitle} — ${debtor.name}", color = Shop.colors.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(
          value = input.group,
          onValueChange = { input = input.copy(group = it.take(CustomerCrm.MAX_GROUP_LENGTH)) },
          singleLine = true,
          label = { Text(StrFields.crmGroup) },
          placeholder = { Text(StrFields.crmGroupHint) },
          modifier = Modifier.fillMaxWidth(),
        )
        val hints = remember(d, input.group) { CrmPricingForm.suggestions(d, input.group) }
        if (hints.isNotEmpty()) {
          Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            hints.forEach { g -> FilterChip(selected = false, onClick = { input = input.copy(group = g) }, label = { Text(g) }) }
          }
        }
        Spacer(Modifier.height(12.dp))
        Text(StrFields.crmPriceList, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
        Spacer(Modifier.height(6.dp))
        SegmentedChoice(
          options = listOf(CustomerCrm.PRICE_RETAIL to StrFields.crmRetail, CustomerCrm.PRICE_WHOLESALE to StrFields.crmWholesale),
          selected = input.priceList,
          onSelect = { input = input.copy(priceList = it) },
        )
        Text(StrFields.crmPriceListNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
        Spacer(Modifier.height(12.dp))
        Text(StrFields.crmConsent, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
        Spacer(Modifier.height(6.dp))
        SegmentedChoice(
          options = listOf("" to StrFields.crmConsentUnasked, "yes" to StrFields.crmConsentYes, "no" to StrFields.crmConsentNo),
          selected = when (input.consent) { true -> "yes"; false -> "no"; null -> "" },
          onSelect = { input = input.copy(consent = when (it) { "yes" -> true; "no" -> false; else -> null }) },
        )
        if (input.consent == true && debtor.marketingConsent != true) {
          Spacer(Modifier.height(6.dp))
          OutlinedTextField(
            value = input.source,
            onValueChange = { input = input.copy(source = it.take(60)) },
            singleLine = true,
            label = { Text(StrFields.crmConsentSource) },
            isError = missingSource,
            supportingText = if (missingSource) { { Text(StrFields.crmConsentSourceNeeded) } } else null,
            modifier = Modifier.fillMaxWidth(),
          )
        }
        Text(StrFields.crmConsentNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
        last?.let {
          Spacer(Modifier.height(6.dp))
          Text(StrFields.crmLastConsent(formatDate(it.date)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
      }
    },
    confirmButton = { TextButton(enabled = !missingSource, onClick = { onSave(input) }) { Text(StrFields.crmSave) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel) } },
  )
}

/** خطِ کوتاهِ وضعیت برای دکمه: «گروه: همکار · قیمت: عمده · تبلیغ: بله» */
internal fun crmPricingSummary(debtor: Debtor): String = StrFields.crmSummary(
  debtor.group?.takeIf { it.isNotBlank() } ?: StrFields.crmNoGroup,
  if (CustomerCrm.priceListOf(debtor) == CustomerCrm.PRICE_WHOLESALE) StrFields.crmWholesale else StrFields.crmRetail,
  when (debtor.marketingConsent) { true -> StrFields.crmConsentYes; false -> StrFields.crmConsentNo; null -> StrFields.crmConsentUnasked },
)
