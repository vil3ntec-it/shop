package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.debt.TillReceive
import ir.vil3ntec.tohid.core.money.MoneyInput
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  POS-39 — پنجرهٔ «دریافت از مشتری» ِ صندوق (F7). فقط نمایش و ورودی؛ ثبت با `TillReceivePref.record` ⇐
 *  `DebtEntry.record` روی دفترِ همین لحظهٔ دیسک، در صفحه. مبلغِ پیش‌فرض همهٔ بدهی تا پول (`MoneyInput.settleInput`)؛ بیش از بدهی
 *  همین‌جا «پیش‌پرداخت» گفته می‌شود و دکمه «ثبتِ پیش‌پرداخت» است — زدنش همان تأییدِ صریح است.
 */
@Composable
internal fun TillReceiveDialog(
  name: String,
  balanceMinor: Long,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSubmit: (amountMinor: Long?, notes: String, prepayConfirmed: Boolean) -> Unit,
  /** دروازهٔ اشتراک دورِ محتوای پنجره (دفترِ قرض بخشِ پولی است: `VipGate`)؛ قفل ⇒ کارتِ قفل در همین پنجره */
  gate: @Composable (content: @Composable () -> Unit) -> Unit = { it() },
) {
  var amountText by remember { mutableStateOf(MoneyInput.settleInput(PayFlow.major(TillReceive.suggestedMinor(balanceMinor))).orEmpty()) }
  var notes by remember { mutableStateOf("") }
  val amountMinor = ir.vil3ntec.tohid.parseNumber(amountText)?.let(PayFlow::minor)
  val verdict = TillReceive.judge(balanceMinor, amountMinor)
  val prepay = (verdict as? TillReceive.Verdict.Ok)?.prepayMinor ?: 0L
  fun show(m: Long) = money(PayFlow.major(m)) + " افغانی"

  Dialog(onDismissRequest = onDismiss) { gate {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(Radius.lg), modifier = Modifier.fillMaxWidth()) {
      Column(Modifier.padding(18.dp)) {
        Text("دریافت از «$name»", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        Spacer(Modifier.height(4.dp))
        Text(
          if (balanceMinor > 0) "بدهی: ${show(balanceMinor)}" else "بدهی ندارد" + if (balanceMinor < 0) " — پیش‌پرداخت: ${show(-balanceMinor)}" else "",
          style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted,
        )
        Spacer(Modifier.height(12.dp))
        AmountField(value = amountText, onValueChange = { amountText = it.take(16) }, label = "مبلغِ دریافتی")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = notes, onValueChange = { notes = it.take(200) }, label = { Text("شرح (اختیاری)") },
          singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        when (verdict) {
          is TillReceive.Verdict.Bad -> if (amountText.isNotBlank()) {
            Text(ir.vil3ntec.tohid.data.TillReceiveFlow.message(verdict.problem), style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
          }
          is TillReceive.Verdict.Ok -> Text(
            if (prepay > 0) "${show(prepay)} بیش از بدهی است و پیش‌پرداختِ مشتری ثبت می‌شود."
            else "مانده پس از دریافت: ${show(verdict.leftMinor)}",
            style = MaterialTheme.typography.labelSmall,
            color = if (prepay > 0) Shop.colors.warning else Shop.colors.muted,
          )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("انصراف") }
          Button(
            onClick = { onSubmit(amountMinor, notes, prepay > 0) },
            enabled = !busy && verdict is TillReceive.Verdict.Ok,
            modifier = Modifier.weight(1f),
          ) { Text(if (prepay > 0) "ثبتِ پیش‌پرداخت" else "ثبتِ دریافت") }
        }
      }
    }
  } }
}
