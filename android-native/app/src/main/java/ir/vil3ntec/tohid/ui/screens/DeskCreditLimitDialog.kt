package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCustomerPages
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.perm.CreditLimitPerm
import ir.vil3ntec.tohid.data.CreditLimitGate
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.rememberCreditLimitRules
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  سقفِ اعتبارِ پروندهٔ مشتریِ کامپیوتر (`DeskCustomersScreen`، v3 §22.2-A5) — `DebtorEngine.setCreditLimit`.
 *  CRM-03 / §۱۰.۱: با پرچمِ `shop.credit_limit_perm` و کاربری که «تغییرِ سقفِ اعتبار» (`customers.credit_limit`) ندارد،
 *  کادر بسته است و دلیلش دیده می‌شود، یا تا سقفِ نقش باز است و بیشتر یا «بی سقف» ذخیره نمی‌شود (`CreditLimitGate`، همان
 *  قاعدهٔ سرور). پرچمِ خاموش یا صاحبِ دکان ⇒ همان امروز.
 */
@Composable
internal fun DeskCreditLimitDialog(
  d: ShopData,
  debtor: Debtor,
  onToast: (String) -> Unit,
  onDismiss: () -> Unit,
  onSaved: (ShopData) -> Unit,
) {
  var text by remember { mutableStateOf(limitText(debtor.creditLimit)) }
  val limitRules = rememberCreditLimitRules()
  val limitField = remember(limitRules) { CreditLimitPerm.field(limitRules, existing = true) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrCustomerPages.creditLimitOf(debtor.name), color = Shop.colors.text) },
    text = {
      Column {
        if (!limitField.locked) CreditLimitField(text) { text = it }
        CreditLimitGate.helper(limitField)?.let {
          Text(it, style = MaterialTheme.typography.bodySmall, color = if (limitField.locked) Shop.colors.danger else Shop.colors.muted)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = !limitField.locked, onClick = {
        val limit = parseLimit(text)
        //  نوشتهٔ نادرست (NaN) را خودِ موتور با پیامِ خودش رد می‌کند
        val denied = if (limit?.isNaN() == true) null else CreditLimitGate.error(limitRules, debtor, limit)
        if (denied != null) onToast(denied)
        else when (val r = DebtorEngine.setCreditLimit(d, debtor.id, limit)) {
          is DebtorEngine.Result.Failed -> onToast(r.message)
          is DebtorEngine.Result.Ok -> onSaved(r.data)
        }
      }) { Text(StrKit.save) }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel) } },
  )
}
