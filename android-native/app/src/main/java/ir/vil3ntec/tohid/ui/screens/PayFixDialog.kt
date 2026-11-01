package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrPayFix
import ir.vil3ntec.tohid.core.money.MoneyInput
import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.data.PayFixFlow
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.ui.theme.Shop

/** آنچه پنجره می‌فرستد؛ سنجش و ثبت با `PayFixHost` (`PayFixFlow.build` ⇐ `PayFixPref.record`) */
data class PayFixDraft(val from: String, val to: String, val amountMinor: Long, val reason: String, val ref: String)

/**
 *  POS-40 — پنجرهٔ «اصلاحِ روشِ پرداخت» ِ فاکتورهای گذشته (گوشی و کامپیوتر، `SalesHistoryScreen`). فقط نمایش و
 *  ورودی: پرداختِ همین لحظه (پس از اصلاح‌های پیشین)، روشِ پیشین و تازه، مبلغ (رقم‌به‌رقم، بی Double:
 *  `CashKeys.cashMinor`)، مرجعِ روشِ مرجع‌دار و دلیلِ اجباری، و فهرستِ اصلاح‌های پیشینِ همین فاکتور.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayFixDialog(
  d: ShopData,
  sale: Sale,
  needsApproval: Boolean,
  busy: Boolean,
  error: String?,
  onSubmit: (PayFixDraft) -> Unit,
  onDismiss: () -> Unit,
) {
  val now = remember(d, sale.id) { PayFixFlow.current(d, sale).rows }
  val history = remember(d, sale.id) { PayFixFlow.history(d, sale.id) }
  val sources = now.filter { it.method != Pay.ACCOUNT }
  var from by remember(sale.id) { mutableStateOf(sources.firstOrNull()?.method.orEmpty()) }
  val targets = PayFixFlow.targets().filter { it.id != from }
  var to by remember(sale.id) { mutableStateOf("") }
  fun left(m: String) = now.filter { it.method == m }.sumOf { it.amount }
  var amount by remember(sale.id) { mutableStateOf(MoneyInput.ofMinor(left(from)).orEmpty()) }
  var ref by remember(sale.id) { mutableStateOf("") }
  var reason by remember(sale.id) { mutableStateOf("") }
  val needsRef = PayFixFlow.targets().find { it.id == to }?.needsRef == true

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrPayFix.title(sale.invoiceLabel() ?: "0")) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(StrPayFix.body, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        Text(StrPayFix.paidNow, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
        now.forEach { Text(StrPayFix.row(PayFlow.methodName(it.method), money(PayFlow.major(it.amount))), style = MaterialTheme.typography.bodyMedium) }
        if (history.isNotEmpty()) {
          Spacer(Modifier.height(8.dp))
          Text(StrPayFix.history, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          history.forEach { f ->
            Text(
              StrPayFix.historyRow(PayFlow.methodName(f.from), PayFlow.methodName(f.to), money(PayFlow.major(f.amount)), f.by),
              style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
            )
          }
        }
        Spacer(Modifier.height(10.dp))
        Text(StrPayFix.from, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          sources.forEach { r ->
            FilterChip(
              selected = r.method == from, enabled = !busy,
              onClick = { from = r.method; amount = MoneyInput.ofMinor(r.amount).orEmpty(); if (to == r.method) to = "" },
              label = { Text(PayFlow.methodName(r.method)) },
            )
          }
        }
        Spacer(Modifier.height(6.dp))
        Text(StrPayFix.to, style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          targets.forEach { m ->
            FilterChip(selected = m.id == to, enabled = !busy, onClick = { to = m.id }, label = { Text(m.name) })
          }
        }
        OutlinedTextField(
          value = amount, onValueChange = { amount = numericInput(it) }, label = { Text(StrPayFix.amount) }, singleLine = true,
          enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        if (needsRef) {
          OutlinedTextField(
            value = ref, onValueChange = { ref = it.replace('\n', ' ') }, label = { Text(StrPayFix.ref) }, singleLine = true,
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
          )
        }
        OutlinedTextField(
          value = reason, onValueChange = { reason = it.replace('\n', ' ') }, label = { Text(StrPayFix.reason) }, singleLine = true,
          enabled = !busy, modifier = Modifier.fillMaxWidth(),
        )
        if (needsApproval) {
          Spacer(Modifier.height(6.dp))
          Text(StrPayFix.needsApproval, style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
        }
        error?.let {
          Spacer(Modifier.height(6.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
        }
      }
    },
    confirmButton = {
      TextButton(enabled = !busy && from.isNotEmpty() && to.isNotEmpty(), onClick = {
        //  متنِ نامعتبر ⇒ ۰ ⇒ ردِ «مبلغ درست نیست» ِ هسته؛ هیچ Doubleی خوانده نمی‌شود
        onSubmit(PayFixDraft(from, to, CashKeys.cashMinor(amount) ?: 0L, reason, if (needsRef) ref else ""))
      }) { Text(StrPayFix.save) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrPayFix.cancel) } },
  )
}
