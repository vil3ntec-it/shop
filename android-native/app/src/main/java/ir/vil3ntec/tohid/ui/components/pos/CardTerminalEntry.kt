package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.i18n.StrPay
import ir.vil3ntec.tohid.core.terminal.ManualCardTerminal
import ir.vil3ntec.tohid.core.terminal.TerminalRequest
import ir.vil3ntec.tohid.core.terminal.TerminalResult
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.pay.HesabPaySettle
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 *  کارت‌خوان (POS ِ بانکی) با آداپتورِ دستی — `core/terminal/CardTerminal.kt`.
 *
 *  صندوقدار مبلغِ باقی‌مانده را روی کارت‌خوان می‌زند و شمارهٔ تأیید/مرجعِ رسیدش را این‌جا می‌نویسد؛ نتیجه
 *  **همان ردیفِ ثبتِ دستیِ پنلِ ترکیبی** است (PAY-06: `afpay_card`، `pending`، `verification: manual`) و
 *  فروش مثلِ همیشه با «ثبتِ فروش» ِ همان پنل بسته می‌شود — راهِ دومی برای ثبت ساخته نمی‌شود.
 *  فقط با پنلِ ترکیبی (`payV2`، جایی که ثبتِ دستیِ PAY-06 هست) و همان پرچمِ سرورِ درگاه‌ها.
 *  «برنامه به برنامه» تا سندِ کارت‌خوان نرسیده نیست (`VendorAppTerminal`).
 */
@Composable
internal fun CardTerminalEntry(
  saleKey: String?,
  total: Double,
  panel: PayPanelState,
  customerId: String?,
  enabled: Boolean,
  busy: Boolean,
) {
  val due = HesabPaySettle.due(total, panel.draft(total, customerId))
  if (saleKey == null || due !is HesabPaySettle.Due.Amount) return
  val scope = rememberCoroutineScope()
  var ask by remember { mutableStateOf<Pair<TerminalRequest, CompletableDeferred<ManualCardTerminal.Answer>>?>(null) }
  var note by remember { mutableStateOf<String?>(null) }
  val terminal = remember {
    ManualCardTerminal { req ->
      val answer = CompletableDeferred<ManualCardTerminal.Answer>()
      ask = req to answer
      try { answer.await() } finally { ask = null }
    }
  }

  Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
    OutlinedButton(
      onClick = {
        note = null
        scope.launch {
          when (val r = terminal.requestPayment(due.minor, saleKey)) {
            is TerminalResult.Approved -> {
              panel.rows += PayFlow.DraftRow(CARD_METHOD, ManualCardTerminal.refText(r), plain(due.minor))
              note = StrPay.terminalAdded
            }
            is TerminalResult.Declined -> note = StrPay.terminalDeclinedNote
            TerminalResult.Cancelled -> note = null
            is TerminalResult.Unavailable -> note = null
          }
        }
      },
      enabled = enabled && !busy && ask == null,
      modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
    ) { Text(StrPay.cardTerminal) }
    note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted) }
  }

  ask?.let { (req, answer) -> ManualTerminalDialog(req) { answer.complete(it) } }
}

/** روشِ کارتِ جدولِ PAY-06 (`Pay.DEFAULT_METHODS`) */
private const val CARD_METHOD = "afpay_card"

/** مبلغِ کادرِ پنل: همان عددِ سادهٔ افغانی (`PayFlow.amountOf` می‌خواند) */
private fun plain(minor: Long): String =
  if (minor % 100 == 0L) (minor / 100).toString() else "${minor / 100}.${(minor % 100).toString().padStart(2, '0')}"

@Composable
private fun ManualTerminalDialog(req: TerminalRequest, onAnswer: (ManualCardTerminal.Answer) -> Unit) {
  var ref by remember { mutableStateOf("") }
  var last4 by remember { mutableStateOf("") }
  var tried by remember { mutableStateOf(false) }
  val refOk = ManualCardTerminal.reference(ref) != null
  val digits = ManualCardTerminal.digits(last4)
  val cardOk = digits.isEmpty() || (digits.length == 4 && digits.all { it in '0'..'9' })
  LaunchedEffect(req) { tried = false }

  Dialog(onDismissRequest = { onAnswer(ManualCardTerminal.Answer.Cancelled) }) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 520.dp)) {
      Column(Modifier.padding(18.dp).verticalScroll(rememberScrollState())) {
        Text(StrPay.cardTerminal, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Text("${amountText(req.amountMinor)} ؋", fontSize = 40.sp, color = Shop.colors.primary, fontWeight = FontWeight.Bold)
        Text(StrPay.terminalCharge, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = ref,
          onValueChange = { ref = it.take(40) },
          label = { Text(StrPay.terminalRef) },
          singleLine = true,
          isError = tried && !refOk,
          supportingText = if (tried && !refOk) { { Text(StrPay.terminalBadRef) } } else null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = last4,
          onValueChange = { last4 = it.take(4) },
          label = { Text(StrPay.terminalLast4) },
          singleLine = true,
          isError = !cardOk,
          supportingText = if (!cardOk) { { Text(StrPay.terminalBadCard) } } else null,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = { onAnswer(ManualCardTerminal.Answer.Cancelled) }) { Text(StrPay.cancel) }
          OutlinedButton(onClick = { onAnswer(ManualCardTerminal.Answer.Declined()) }, modifier = Modifier.weight(1f)) { Text(StrPay.terminalDeclined) }
          Button(
            onClick = {
              tried = true
              if (refOk && cardOk) onAnswer(ManualCardTerminal.Answer.Approved(ref, digits.ifEmpty { null }))
            },
            modifier = Modifier.weight(1f),
          ) { Text(StrPay.terminalApprove) }
        }
      }
    }
  }
}
