package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCustomerPages
import ir.vil3ntec.tohid.core.i18n.StrDebtPages
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.perm.CreditLimitPerm
import ir.vil3ntec.tohid.data.CreditLimitGate
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.rememberCreditLimitRules

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
  fun save() {
    val limit = parseLimit(text)
    //  نوشتهٔ نادرست (NaN) را خودِ موتور با پیامِ خودش رد می‌کند
    val denied = if (limit?.isNaN() == true) null else CreditLimitGate.error(limitRules, debtor, limit)
    if (denied != null) onToast(denied)
    else when (val r = DebtorEngine.setCreditLimit(d, debtor.id, limit)) {
      is DebtorEngine.Result.Failed -> onToast(r.message)
      is DebtorEngine.Result.Ok -> onSaved(r.data)
    }
  }
  //  کامپیوتر (`claude/e-desk-d3c`): پنجره و دکمه‌های `ui/desk` — همان کادر (رقمِ لاتین و نگهبانِ اسکن)، همان دروازه و همان موتور
  val dc = ir.vil3ntec.tohid.ui.desk.Desk.colors
  ir.vil3ntec.tohid.ui.desk.DeskDialog(
    title = StrCustomerPages.creditLimitOf(debtor.name),
    onDismiss = onDismiss,
    confirm = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrKit.save, enabled = !limitField.locked, onClick = ::save),
    dismiss = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(Str.cancel, onClick = onDismiss),
  ) {
    if (!limitField.locked) {
      val shown = text.toDoubleOrNull()?.takeIf { it > 0 }?.let { "${ir.vil3ntec.tohid.money(it)} ؋" }
      DeskNumberField(text, { text = it }, StrMoney.creditLimitField, note = listOfNotNull(shown, StrDebtPages.limitNote).joinToString(" · "))
    }
    CreditLimitGate.helper(limitField)?.let {
      ir.vil3ntec.tohid.ui.desk.DeskDialogText(it, color = if (limitField.locked) dc.negative else null)
    }
  }
}
