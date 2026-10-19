package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.pay.ShiftReport
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  صندوقِ شیفت با هستهٔ pay-core (PAY-11..15، 19؛ S12) — فقط با پرچمِ `ShopPolicy.payV2`.
 *  همان صندوقِ `index.html` (`#shift-panel` با پرچم): باز کردن با موجودی، ورود/خروجِ نقد با
 *  دلیل، کشو بی فروش با تأییدِ صاحب/مدیر، گزارشِ X، بستن با شمارشِ اسکناس و Z.
 *  شمارشِ کور: شاگرد (صندوقدار) مبلغِ مورد انتظار و اختلاف را نمی‌بیند؛ صاحب و مدیر می‌بینند.
 *  خاموش ⇒ این‌جا صدا زده نمی‌شود و همان کارتِ `CashShift`ِ امروز در `SalesHistory` است.
 */

private val MOVE_LABEL = mapOf(
  "deposit" to "واریز به دخل", "owner_withdrawal" to "برداشتِ مالک", "petty_expense" to "پرداختِ کوچک", "other" to "دیگر",
)

private fun afn(m: Long?): String = "${money(PayFlow.major(m ?: 0))} ؋"

@Composable
fun ShiftPanelV2(d: ShopData, store: ShopStore, me: String, toast: (String) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val staff = remember { ir.vil3ntec.tohid.data.ShopRole.isStaff(context) }
  val shift = remember(d) { CashShift.current(d) }
  val now = System.currentTimeMillis()
  val x = remember(d, shift) { shift?.let { PayFlow.report(d, it.open, null, now) } }

  var openDialog by remember { mutableStateOf(false) }
  var moveMode by remember { mutableStateOf<String?>(null) }
  var closeDialog by remember { mutableStateOf(false) }
  var history by remember { mutableStateOf(false) }
  var showReport by remember { mutableStateOf<ShiftReport?>(null) }

  fun save(row: AuditEntry, done: String) {
    scope.launch { if (store.save(d.copy(auditLog = d.auditLog + row))) toast(done) else toast("ذخیره نشد") }
  }

  Panel {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          if (shift == null) "صندوق بسته است" else "صندوق باز — از ${formatMillis(shift.open.createdAt)}",
          style = MaterialTheme.typography.titleSmall, color = Shop.colors.text,
        )
        when (val r = x) {
          null -> Unit
          is PayFlow.Outcome.Bad -> Text(PayFlow.message(r.problem), style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
          is PayFlow.Outcome.Ok -> {
            Text(
              if (staff) "شمارشِ کور: مبلغِ مورد انتظار پس از بستن به مدیر نشان داده می‌شود"
              else "نقدِ مورد انتظار در دخل: ${afn(r.value.report.expected?.get(PayFlow.CASH))}",
              style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
            )
            if (r.value.skipped.isNotEmpty()) {
              StatusPill("${r.value.skipped.size} خروجِ دفتر بیش از نقدِ دخل — بررسیِ مدیر", PillTone.WARNING)
            }
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      TextButton(onClick = { history = true }) { Text("سابقه") }
      if (shift != null) {
        OutlinedButton(onClick = { moveMode = "cash_out" }) { Text("ورود/خروجِ نقد") }
        OutlinedButton(onClick = { moveMode = "no_sale" }) { Text("کشو بی فروش") }
        OutlinedButton(onClick = { x?.valueOrNull()?.let { showReport = it.report } ?: toast("گزارش ساخته نشد") }) { Text("گزارش X") }
        Button(onClick = { closeDialog = true }) { Text("بستن صندوق") }
      } else {
        Button(onClick = { openDialog = true }) { Text("باز کردن") }
      }
    }
  }

  /* ── باز کردن با موجودی (PAY-11) — همان ردیفِ `shift_open`ِ امروز ── */
  if (openDialog) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { openDialog = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("باز کردنِ صندوق", color = Shop.colors.text) },
      text = {
        OutlinedTextField(
          value = amount, onValueChange = { amount = numericInput(it) }, label = { Text("پولِ اولِ صندوق") }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(onClick = {
          when (val r = CashShift.open(d, amount.toDoubleOrNull() ?: Double.NaN, me, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)) {
            is CashShift.Result.Failed -> toast(r.message)
            is CashShift.Result.Ok -> { scope.launch { store.save(r.data) }; toast("صندوق باز شد"); openDialog = false }
          }
        }) { Text("باز کن") }
      },
      dismissButton = { TextButton(onClick = { openDialog = false }) { Text("انصراف") } },
    )
  }

  /* ── ورود/خروجِ نقد با دلیل (PAY-12) و کشو بی فروش با تأیید (PAY-19) ── */
  val mode0 = moveMode
  if (mode0 != null && shift != null) {
    val sh = shift
    var mode by remember { mutableStateOf(mode0) }
    var reason by remember(mode) { mutableStateOf(ShiftCore.MOVE_REASONS[mode]?.first()) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val noSale = mode == "no_sale"
    AlertDialog(
      onDismissRequest = { moveMode = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(if (noSale) "باز کردنِ کشو بی فروش" else "ورود/خروجِ نقد", color = Shop.colors.text) },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          if (!noSale) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("cash_out" to "خروج از دخل", "cash_in" to "ورود به دخل").forEach { (k, l) ->
                if (k == mode) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(l) }
                else OutlinedButton(onClick = { mode = k }, modifier = Modifier.weight(1f)) { Text(l) }
              }
            }
            Spacer(Modifier.height(8.dp))
            Text("دلیل", style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              ShiftCore.MOVE_REASONS[mode].orEmpty().forEach { r ->
                if (r == reason) Button(onClick = {}) { Text(MOVE_LABEL[r] ?: r) }
                else OutlinedButton(onClick = { reason = r }) { Text(MOVE_LABEL[r] ?: r) }
              }
            }
            OutlinedTextField(
              value = amount, onValueChange = { amount = numericInput(it) }, label = { Text("مبلغ (افغانی)") }, singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
          }
          OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text(if (noSale) "دلیلِ باز کردنِ کشو" else "توضیح (برای «دیگر» لازم)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
          )
          if (noSale) {
            Spacer(Modifier.height(6.dp))
            Text(
              if (staff) "باز کردنِ کشو بی فروش تأییدِ صاحب یا مدیر می‌خواهد؛ با حسابِ صاحب یا مدیر وارد شوید."
              else "تأییدکننده: ${me.ifBlank { "همین حساب" }} — کشوِ برقی هنوز وصل نیست؛ این‌جا فقط با دلیل و تأیید ثبت می‌شود.",
              style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
            )
          }
          error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
        }
      },
      confirmButton = {
        TextButton(enabled = !(noSale && staff), onClick = {
          val m = if (noSale) PayFlow.Move("no_sale", reason = note, by = me, approvedBy = if (staff) "" else me.ifBlank { "صاحب/مدیر" })
          else PayFlow.Move(mode, amount, reason, note, by = me)
          when (val r = PayFlow.moveRow(d, sh.open, m, System.currentTimeMillis(), ::newId)) {
            is PayFlow.Outcome.Bad -> error = PayFlow.message(r.problem)
            is PayFlow.Outcome.Ok -> {
              val row = r.value
              val notes = if (noSale) "کشو بی فروش — ${note.trim()}" + (row.approvedBy?.let { " — تأیید: $it" } ?: "")
              else "${if (mode == "cash_in") "ورود به دخل" else "خروج از دخل"}: ${MOVE_LABEL[row.reason] ?: row.reason} — ${money(row.amount)} افغانی" +
                (row.note?.let { " — $it" } ?: "") + (if (me.isNotBlank()) " — $me" else "")
              save(row.copy(date = ir.vil3ntec.tohid.todayIso(), notes = notes), if (noSale) "باز کردنِ کشو ثبت شد" else "ثبت شد")
              moveMode = null
            }
          }
        }) { Text("ثبت") }
      },
      dismissButton = { TextButton(onClick = { moveMode = null }) { Text("انصراف") } },
    )
  }

  /* ── بستن با شمارشِ اسکناس (PAY-13، 14) ── */
  if (closeDialog && shift != null) {
    val counts = remember { mutableStateMapOf<String, String>() }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val parsed: Map<String, Long> = counts.mapNotNull { (k, v) -> v.trim().takeIf { it.isNotEmpty() }?.let { k to (it.toLongOrNull() ?: -1L) } }.toMap()
    AlertDialog(
      onDismissRequest = { closeDialog = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("بستنِ صندوق — شمارشِ اسکناس", color = Shop.colors.text) },
      text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
          Text(
            if (staff) "شمارشِ کور: اسکناس‌ها را بشمارید؛ مبلغِ مورد انتظار پس از بستن به مدیر نشان داده می‌شود."
            else "نقدِ مورد انتظار: ${x?.valueOrNull()?.let { afn(it.report.expected?.get(PayFlow.CASH)) } ?: "—"}",
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
          )
          Spacer(Modifier.height(6.dp))
          Pay.denominations(false).reversed().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              pair.forEach { note ->
                OutlinedTextField(
                  value = counts[note.toString()].orEmpty(),
                  onValueChange = { v -> counts[note.toString()] = v.filter { it.isDigit() } },
                  label = { Text("${money(PayFlow.major(note))} ×") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  modifier = Modifier.weight(1f),
                )
              }
            }
          }
          Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("نقدِ شمرده", style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
            Text(afn(PayFlow.countTotal(parsed)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
          }
          OutlinedTextField(
            value = reason, onValueChange = { reason = it }, label = { Text("دلیلِ اختلاف (بالای ۵۰ افغانی لازم است)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
          )
          error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
        }
      },
      confirmButton = {
        TextButton(onClick = {
          when (val r = PayFlow.closeRow(d, shift.open, PayFlow.Count(parsed, by = me, blind = staff, reason = reason), System.currentTimeMillis(), ::newId)) {
            is PayFlow.Outcome.Bad -> error = if (r.problem.code == "REASON_REQUIRED") "اختلافِ شمارش از آستانهٔ ۵۰ افغانی بیشتر است؛ دلیل بنویسید" else PayFlow.message(r.problem)
            is PayFlow.Outcome.Ok -> {
              val z = r.value.report
              val vc = z.variance?.get(PayFlow.CASH) ?: 0L
              val extra = listOf(reason.trim(), me.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
              val notes = "بستنِ صندوق: مورد انتظار ${money(PayFlow.major(z.expected?.get(PayFlow.CASH) ?: 0))}، شمرده ${money(r.value.row.amount)}، " +
                (if (vc == 0L) "بی اختلاف" else if (vc > 0) "اضافه ${money(PayFlow.major(vc))}" else "کسری ${money(PayFlow.major(-vc))}") +
                (if (extra.isNotEmpty()) " — $extra" else "")
              save(r.value.row.copy(date = ir.vil3ntec.tohid.todayIso(), notes = notes), "صندوق بسته شد")
              closeDialog = false
              showReport = z
            }
          }
        }) { Text("ببند") }
      },
      dismissButton = { TextButton(onClick = { closeDialog = false }) { Text("انصراف") } },
    )
  }

  /* ── گزارشِ X / Z (PAY-15) ── */
  showReport?.let { rep ->
    AlertDialog(
      onDismissRequest = { showReport = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(if (rep.kind == "Z") "گزارش Z (پایانِ شیفت)" else "گزارش X (میانِ شیفت)", color = Shop.colors.text) },
      text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) { ShiftReportLines(if (staff) ShiftCore.cashierView(rep) else rep) } },
      confirmButton = { TextButton(onClick = { showReport = null }) { Text("بستن") } },
    )
  }

  /* ── سابقه، با Zِ ذخیره‌شده ── */
  if (history) {
    val shifts = remember(d) { CashShift.list(d).take(60) }
    AlertDialog(
      onDismissRequest = { history = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text("سابقهٔ صندوق", color = Shop.colors.text) },
      text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
          if (shifts.isEmpty()) Text("هنوز صندوقی باز نشده.", color = Shop.colors.muted)
          shifts.forEach { sh ->
            Text(formatMillis(sh.open.createdAt) + " — " + sh.open.notes, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
            Text(
              sh.close?.let { formatMillis(it.createdAt) + " — " + it.notes } ?: "هنوز باز است",
              style = MaterialTheme.typography.labelSmall, color = if (sh.close == null) Shop.colors.warning else Shop.colors.muted,
            )
            sh.close?.z?.let { PayFlow.reportOf(it) }?.let { z ->
              TextButton(onClick = { history = false; showReport = z }) { Text("گزارش Z") }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Shop.colors.border)
          }
        }
      },
      confirmButton = { TextButton(onClick = { history = false }) { Text("بستن") } },
    )
  }
}

@Composable
private fun ShiftReportLines(rep: ShiftReport) {
  @Composable
  fun line(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, modifier = Modifier.weight(1f))
      Spacer(Modifier.width(8.dp))
      Text(value, style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall, color = Shop.colors.text)
    }
  }
  line("باز شد", formatMillis(rep.openedAt))
  line(if (rep.kind == "Z") "بسته شد" else "زمانِ گزارش", formatMillis(rep.at))
  rep.by?.let { line("کاربر", it) }
  line("اولِ صندوق", afn(rep.float))
  rep.methods.forEach { (id, m) ->
    if (m.sales != 0L) line("${PayFlow.methodName(id)} — فروش", afn(m.sales))
    if (m.receipts != 0L) line("${PayFlow.methodName(id)} — رسیدِ قرض‌داران", afn(m.receipts))
    if (m.refunds != 0L) line("${PayFlow.methodName(id)} — مرجوعی", "−" + afn(m.refunds))
  }
  line("ورود به دخل", afn(rep.moves.cashIn))
  line("خروج از دخل", "−" + afn(rep.moves.cashOut))
  line("کشو بی فروش", "${rep.events.noSale} بار")
  rep.expected?.forEach { (id, v) -> line("مورد انتظار — ${PayFlow.methodName(id)}", afn(v), id == PayFlow.CASH) }
  if (rep.kind == "Z") {
    rep.denominations?.entries?.sortedByDescending { it.key.toLongOrNull() ?: 0 }?.forEach { (k, n) ->
      line("${money(PayFlow.major(k.toLongOrNull() ?: 0))} × $n", afn((k.toLongOrNull() ?: 0) * n))
    }
    line("نقدِ شمرده", afn(rep.counted?.get(PayFlow.CASH)), true)
    rep.variance?.forEach { (id, v) ->
      line(
        "اختلاف — ${PayFlow.methodName(id)}",
        when { v == null -> "شمرده نشد"; v == 0L -> "بی اختلاف"; v > 0 -> "اضافه ${afn(v)}"; else -> "کسری ${afn(-v)}" },
      )
    }
    rep.reason?.let { line("دلیلِ اختلاف", it) }
    if (rep.needsReview == true) {
      Spacer(Modifier.height(4.dp))
      StatusPill("اختلاف خودکار اصلاح نمی‌شود — برای بررسیِ مدیر ثبت شد", PillTone.WARNING)
    }
  }
}
