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
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.pay.ShiftReport
import ir.vil3ntec.tohid.core.pay.ShiftSlip
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.print.HwPages
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrShift

/*
 *  صندوقِ شیفت با هستهٔ pay-core (PAY-11..15، 19؛ S12) — فقط با پرچمِ `ShopPolicy.payV2`.
 *  گوشی و کامپیوتر («تاریخچهٔ فروش»): باز کردن با موجودی، ورود/خروجِ نقد با
 *  دلیل، کشو بی فروش با تأییدِ صاحب/مدیر، گزارشِ X، بستن با شمارشِ اسکناس و Z.
 *  شمارشِ کور: شاگرد (صندوقدار) مبلغِ مورد انتظار و اختلاف را نمی‌بیند؛ صاحب و مدیر می‌بینند —
 *  در X ِ میانِ شیفت، در Z و در سابقهٔ هر بستن (کور یا مدیر، `PayFlow.closeNote`) هم (`ShiftSlip.forViewer`، PAY-13): در نمای او فروش، رسید،
 *  مرجوعی و ورود/خروجِ نقد فقط با شمار می‌آیند، و بستنِ کور هرگز به‌خاطرِ اختلاف رد نمی‌شود (بی پیشگو).
 *  PAY-16: در بستن، هر روشِ غیرنقدی که در شیفت گردش داشت کادرِ شمارشِ خودش را دارد (`ShiftSlip.countable`).
 *  PAY-15: X و Z از یک فهرستِ ردیف (`ShiftSlip.rows`) هم روی صفحه می‌آیند هم روی چاپگرِ فاکتور.
 *  خاموش ⇒ این‌جا صدا زده نمی‌شود و همان کارتِ `CashShift`ِ امروز در `SalesHistory` است.
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrShift` (LOC-06)؛ یادداشتِ سابقه و «تأیید» ِ ذخیره‌شده داده‌اند و فارسی می‌مانند.
 */

/** نامِ دلیل در یادداشتِ سابقه (داده، همیشه فارسی)؛ برای صفحه [moveLabel] */
private val MOVE_LABEL = mapOf(
  "deposit" to "واریز به دخل", "owner_withdrawal" to "برداشتِ مالک", "petty_expense" to "پرداختِ کوچک", "other" to "دیگر",  // داده: یادداشتِ سابقهٔ صندوق
)

/** نامِ دلیلِ ورود/خروج روی دکمه، به زبانِ صفحه */
private fun moveLabel(reason: String): String = when (reason) {
  "deposit" -> StrShift.moveDeposit
  "owner_withdrawal" -> StrShift.moveOwnerWithdrawal
  "petty_expense" -> StrShift.movePettyExpense
  "other" -> StrShift.moveOther
  else -> reason
}

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
    scope.launch { if (store.save(d.copy(auditLog = d.auditLog + row))) toast(done) else toast(StrShift.saveFailed) }
  }

  /** PAY-15 — چاپِ همان نمایی که روی صفحه است، روی چاپگرِ فاکتور و از صفِ چاپ */
  fun printReport(rep: ShiftReport) {
    val printer = PrinterRegistry.defaultSaved(context) ?: return toast(StrShift.noReceiptPrinter)
    val title = reportTitle(rep)
    scope.launch {
      val bytes = withContext(Dispatchers.IO) {
        runCatching {
          val p = printer.profile()
          val lines = listOf(HwPages.Line(title, HwPages.Style.TITLE)) +
            ShiftSlip.rows(rep).map { r -> slipLine(r).let { (l, v) -> HwPages.reportLine(if (v.isEmpty()) l else "$l: $v", r.strong) } }
          EscPos.document(HwPages.text(context, p.paperDots, lines), p)
        }
      }.getOrElse { toast(StrShift.reportNotPrinted(it.message ?: Str.unknownReason)); return@launch }
      toast(PrintSpooler.submitAndWait(context, PrintQueue.Kind.REPORT, printer.link, title, bytes, done = StrShift.reportPrinted))
    }
  }

  Panel {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          if (shift == null) StrShift.registerClosed else StrShift.registerOpenSince(formatMillis(shift.open.createdAt)),
          style = MaterialTheme.typography.titleSmall, color = Shop.colors.text,
        )
        when (val r = x) {
          null -> Unit
          is PayFlow.Outcome.Bad -> Text(PayFlow.message(r.problem), style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
          is PayFlow.Outcome.Ok -> {
            Text(
              if (staff) StrShift.blindNote
              else StrShift.expectedInDrawer(afn(r.value.report.expected?.get(PayFlow.CASH))),
              style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
            )
            if (r.value.skipped.isNotEmpty()) {
              StatusPill(StrShift.skippedOuts(r.value.skipped.size), PillTone.WARNING)
            }
          }
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      TextButton(onClick = { history = true }) { Text(StrShift.history) }
      if (shift != null) {
        OutlinedButton(onClick = { moveMode = "cash_out" }) { Text(StrShift.cashInOut) }
        OutlinedButton(onClick = { moveMode = "no_sale" }) { Text(StrShift.noSale) }
        OutlinedButton(onClick = { x?.valueOrNull()?.let { showReport = it.report } ?: toast(StrShift.reportFailed) }) { Text(StrShift.xReport) }
        Button(onClick = { closeDialog = true }) { Text(StrShift.closeRegister) }
      } else {
        Button(onClick = { openDialog = true }) { Text(StrShift.openRegister) }
      }
    }
  }

  /* ── باز کردن با موجودی (PAY-11) — همان ردیفِ `shift_open`ِ امروز ── */
  if (openDialog) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { openDialog = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrShift.openRegisterTitle, color = Shop.colors.text) },
      text = {
        OutlinedTextField(
          value = amount, onValueChange = { amount = numericInput(it) }, label = { Text(StrShift.openingCash) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(onClick = {
          when (val r = CashShift.open(d, amount.toDoubleOrNull() ?: Double.NaN, me, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), ::newId)) {
            is CashShift.Result.Failed -> toast(r.message)
            is CashShift.Result.Ok -> { scope.launch { store.save(r.data) }; toast(StrShift.registerOpened); openDialog = false }
          }
        }) { Text(StrShift.openIt) }
      },
      dismissButton = { TextButton(onClick = { openDialog = false }) { Text(Str.cancel) } },
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
    //  PAY-19: کشوی وصل به چاپگرِ پیش‌فرض (HW-03) — اگر هست، «ثبت» واقعاً بازش می‌کند
    val drawer = remember { PrinterRegistry.defaultSaved(context)?.takeIf { it.drawer } }
    AlertDialog(
      onDismissRequest = { moveMode = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(if (noSale) StrShift.noSaleTitle else StrShift.cashInOut, color = Shop.colors.text) },
      text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          if (!noSale) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              listOf("cash_out" to StrShift.outOfDrawer, "cash_in" to StrShift.intoDrawer).forEach { (k, l) ->
                if (k == mode) Button(onClick = {}, modifier = Modifier.weight(1f)) { Text(l) }
                else OutlinedButton(onClick = { mode = k }, modifier = Modifier.weight(1f)) { Text(l) }
              }
            }
            Spacer(Modifier.height(8.dp))
            Text(StrShift.reason, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              ShiftCore.MOVE_REASONS[mode].orEmpty().forEach { r ->
                if (r == reason) Button(onClick = {}) { Text(moveLabel(r)) }
                else OutlinedButton(onClick = { reason = r }) { Text(moveLabel(r)) }
              }
            }
            OutlinedTextField(
              value = amount, onValueChange = { amount = numericInput(it) }, label = { Text(StrShift.amountAfn) }, singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
            )
          }
          OutlinedTextField(
            value = note, onValueChange = { note = it },
            label = { Text(if (noSale) StrShift.noSaleReason else StrShift.noteForOther) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
          )
          if (noSale) {
            Spacer(Modifier.height(6.dp))
            Text(
              if (staff) StrShift.noSaleNeedsApproval
              else if (drawer != null) StrShift.noSaleApproverDrawer(me.ifBlank { StrShift.thisAccount }, drawer.name)
              else StrShift.noSaleApprover(me.ifBlank { StrShift.thisAccount }),
              style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
            )
          }
          error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
        }
      },
      confirmButton = {
        TextButton(enabled = !(noSale && staff), onClick = move@{
          //  PAY-19: همان راهِ «باز کردنِ کشو» ِ دستگاه‌ها (`PrintSpooler.openDrawer`): مجوزِ drawer.open، دلیل، و ردیفِ
          //  no_sale ِ همین صندوق فقط وقتی ضربه به چاپگر رسید — نه ردیفی بی کشو، نه کشویی بی ردیف
          if (noSale && drawer != null) {
            if (note.isBlank()) { error = PayFlow.message(PayFlow.Problem("REASON_REQUIRED")); return@move }
            scope.launch { toast(PrintSpooler.openDrawer(context, drawer, note)) }
            moveMode = null
            return@move
          }
          //  تأییدکننده نامِ همین کاربر است؛ بی نام «policy» (سیاستِ دکان اجازه داده)، هرگز «صاحب/مدیر»ِ ساختگی — همان HW-03
          val m = if (noSale) PayFlow.Move("no_sale", reason = note, by = me, approvedBy = if (staff) "" else me.ifBlank { "policy" })
          else PayFlow.Move(mode, amount, reason, note, by = me)
          when (val r = PayFlow.moveRow(d, sh.open, m, System.currentTimeMillis(), ::newId)) {
            is PayFlow.Outcome.Bad -> error = PayFlow.message(r.problem)
            is PayFlow.Outcome.Ok -> {
              val row = r.value
              val notes = if (noSale) "کشو بی فروش — ${note.trim()}" + (row.approvedBy?.let { " — تأیید: $it" } ?: "")  // داده: یادداشتِ سابقهٔ صندوق
              else "${if (mode == "cash_in") "ورود به دخل" else "خروج از دخل"}: ${MOVE_LABEL[row.reason] ?: row.reason} — ${money(row.amount)} افغانی" +  // داده: یادداشتِ سابقهٔ صندوق
                (row.note?.let { " — $it" } ?: "") + (if (me.isNotBlank()) " — $me" else "")
              save(row.copy(date = ir.vil3ntec.tohid.todayIso(), notes = notes), if (noSale) StrShift.noSaleRecorded else StrShift.recorded)
              moveMode = null
            }
          }
        }) { Text(StrShift.record) }
      },
      dismissButton = { TextButton(onClick = { moveMode = null }) { Text(Str.cancel) } },
    )
  }

  /* ── بستن با شمارشِ اسکناس (PAY-13، 14) ── */
  if (closeDialog && shift != null) {
    val counts = remember { mutableStateMapOf<String, String>() }
    //  PAY-16: شمرده‌شدهٔ هر روشِ غیرنقد (جمعِ رسیدهای دستگاهِ کارت یا کیف پول)؛ خالی = شمرده نشد
    val methodTexts = remember { mutableStateMapOf<String, String>() }
    val full = x?.valueOrNull()?.report
    val methods = remember(full) { full?.let(ShiftSlip::countable).orEmpty() }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val parsed: Map<String, Long> = counts.mapNotNull { (k, v) -> v.trim().takeIf { it.isNotEmpty() }?.let { k to (it.toLongOrNull() ?: -1L) } }.toMap()
    AlertDialog(
      onDismissRequest = { closeDialog = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrShift.closeCountTitle, color = Shop.colors.text) },
      text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
          Text(
            if (staff) StrShift.blindCountNote
            else StrShift.expectedCash(x?.valueOrNull()?.let { afn(it.report.expected?.get(PayFlow.CASH)) } ?: "—"),
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
            Text(StrShift.countedCash, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
            Text(afn(PayFlow.countTotal(parsed)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text)
          }
          if (methods.isNotEmpty()) {
            Text(
              StrShift.methodCountsNote,
              style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
            )
            methods.forEach { id ->
              OutlinedTextField(
                value = methodTexts[id].orEmpty(),
                onValueChange = { methodTexts[id] = numericInput(it) },
                label = {
                  Text(PayFlow.methodName(id) + if (staff) "" else StrShift.expectedSuffix(afn(full?.expected?.get(id))))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
          OutlinedTextField(
            value = reason, onValueChange = { reason = it },
            //  ⛔ کور: نه آستانه، نه «لازم است» — بستنِ کور به‌خاطرِ اختلاف رد نمی‌شود
            label = { Text(if (staff) StrShift.noteForManager else StrShift.varianceReasonLabel) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
          )
          error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger) }
        }
      },
      confirmButton = {
        TextButton(onClick = close@{
          val mc = when (val m = ShiftSlip.methodCounts(methodTexts, methods)) {
            is PayResult.Ok -> m.value
            is PayResult.Err -> { error = StrShift.badMethodCount(PayFlow.methodName(m.method.orEmpty())); return@close }
          }
          val count = PayFlow.Count(parsed, methodCounts = mc, by = me, blind = staff, reason = reason)
          when (val r = PayFlow.closeRow(d, shift.open, count, System.currentTimeMillis(), ::newId)) {
            is PayFlow.Outcome.Bad -> error = if (r.problem.code == "REASON_REQUIRED" && !staff) StrShift.varianceReasonRequired else PayFlow.message(r.problem)
            is PayFlow.Outcome.Ok -> {
              val z = r.value.report
              val vc = z.variance?.get(PayFlow.CASH) ?: 0L
              val extra = listOf(reason.trim(), me.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
              //  PAY-13: متنِ سابقه را همه می‌بینند؛ بستنِ کور در آن مورد انتظار و اختلاف را نمی‌نویسد (Zِ کامل برای مدیر هست)
              val notes = (if (staff) "بستنِ صندوق (شمارشِ کور): شمرده ${money(r.value.row.amount)}"  // داده: یادداشتِ سابقهٔ صندوق
              else "بستنِ صندوق: مورد انتظار ${money(PayFlow.major(z.expected?.get(PayFlow.CASH) ?: 0))}، شمرده ${money(r.value.row.amount)}، " +  // داده: یادداشتِ سابقهٔ صندوق
                (if (vc == 0L) "بی اختلاف" else if (vc > 0) "اضافه ${money(PayFlow.major(vc))}" else "کسری ${money(PayFlow.major(-vc))}")) +  // داده: یادداشتِ سابقهٔ صندوق
                (if (extra.isNotEmpty()) " — $extra" else "")
              save(r.value.row.copy(date = ir.vil3ntec.tohid.todayIso(), notes = notes), StrShift.registerClosedNow)
              closeDialog = false
              showReport = z
            }
          }
        }) { Text(StrShift.closeIt) }
      },
      dismissButton = { TextButton(onClick = { closeDialog = false }) { Text(Str.cancel) } },
    )
  }

  /* ── گزارشِ X / Z (PAY-15) ── */
  showReport?.let { rep ->
    //  PAY-13: شاگرد نه در X و نه در Z مورد انتظار و اختلاف را می‌بیند؛ چاپ هم همین نماست
    val seen = ShiftSlip.forViewer(rep, seesExpected = !staff)
    AlertDialog(
      onDismissRequest = { showReport = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(reportTitle(rep), color = Shop.colors.text) },
      text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) { ShiftReportLines(seen) } },
      confirmButton = { TextButton(onClick = { showReport = null }) { Text(Str.close) } },
      dismissButton = { TextButton(onClick = { printReport(seen) }) { Text(StrShift.print) } },
    )
  }

  /* ── سابقه، با Zِ ذخیره‌شده ── */
  if (history) {
    val shifts = remember(d) { CashShift.list(d).take(60) }
    AlertDialog(
      onDismissRequest = { history = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrShift.registerHistory, color = Shop.colors.text) },
      text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
          if (shifts.isEmpty()) Text(StrShift.noShiftsYet, color = Shop.colors.muted)
          shifts.forEach { sh ->
            Text(formatMillis(sh.open.createdAt) + " — " + sh.open.notes, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
            Text(
              //  ⛔ PAY-13: شاگرد متنِ بستن (مدیر یا قدیمی: «مورد انتظار» و «اختلاف») را نمی‌بیند — فقط شمرده‌ها
              sh.close?.let { formatMillis(it.createdAt) + " — " + PayFlow.closeNote(it, seesExpected = !staff) } ?: StrShift.stillOpen,
              style = MaterialTheme.typography.labelSmall, color = if (sh.close == null) Shop.colors.warning else Shop.colors.muted,
            )
            sh.close?.z?.let { PayFlow.reportOf(it) }?.let { z ->
              //  PAY-14: اختلاف خودکار اصلاح نمی‌شود؛ مدیر از همین فهرست می‌بیند کدام بستن بررسی می‌خواهد
              if (!staff && z.needsReview == true) StatusPill(StrShift.needsReview, PillTone.WARNING)
              TextButton(onClick = { history = false; showReport = z }) { Text(StrShift.zReport) }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Shop.colors.border)
          }
        }
      },
      confirmButton = { TextButton(onClick = { history = false }) { Text(Str.close) } },
    )
  }
}

private fun reportTitle(rep: ShiftReport) = if (rep.kind == "Z") StrShift.zReportTitle else StrShift.xReportTitle

/** PAY-15 — برچسب و مقدارِ یک ردیفِ گزارش؛ صفحه و کاغذ هر دو همین را می‌گیرند */
private fun slipLine(r: ShiftSlip.Row): Pair<String, String> {
  val name = PayFlow.methodName(r.method.orEmpty())
  //  نمای کور (`ShiftSlip.forViewer`): فروش، رسید، مرجوعی و ورود/خروج بی روش و بی مبلغ، فقط شمار
  if (r.amount == null && r.count != null) countLine(r)?.let { return it }
  return when (r.kind) {
    ShiftSlip.Kind.OPENED -> StrShift.opened to formatMillis(r.amount ?: 0)
    ShiftSlip.Kind.AT -> StrShift.reportTime to formatMillis(r.amount ?: 0)
    ShiftSlip.Kind.CLOSED -> StrShift.closed to formatMillis(r.amount ?: 0)
    ShiftSlip.Kind.BY -> StrShift.user to r.text.orEmpty()
    ShiftSlip.Kind.FLOAT -> StrShift.openingFloat to afn(r.amount)
    ShiftSlip.Kind.SALES -> StrShift.methodSales(name) to afn(r.amount)
    ShiftSlip.Kind.RECEIPTS -> StrShift.methodReceipts(name) to afn(r.amount)
    ShiftSlip.Kind.REFUNDS -> StrShift.methodRefunds(name) to "−" + afn(r.amount)
    ShiftSlip.Kind.CASH_IN -> StrShift.intoDrawer to afn(r.amount)
    ShiftSlip.Kind.CASH_OUT -> StrShift.outOfDrawer to "−" + afn(r.amount)
    ShiftSlip.Kind.NO_SALE -> StrShift.noSale to StrShift.times(r.count ?: 0)
    ShiftSlip.Kind.EXPECTED -> StrShift.expectedOf(name) to afn(r.amount)
    ShiftSlip.Kind.DENOMINATION -> "${money(PayFlow.major(r.note ?: 0))} × ${r.count ?: 0}" to afn(r.amount)
    ShiftSlip.Kind.COUNTED -> (if (r.method == PayFlow.CASH) StrShift.countedCash else StrShift.countedOf(name)) to afn(r.amount)
    ShiftSlip.Kind.VARIANCE -> StrShift.varianceOf(name) to r.amount.let { v ->
      when { v == null -> StrShift.notCounted; v == 0L -> StrShift.noDifference; v > 0 -> StrShift.over(afn(v)); else -> StrShift.shortBy(afn(-v)) }
    }
    ShiftSlip.Kind.REASON -> StrShift.varianceReason to r.text.orEmpty()
    ShiftSlip.Kind.NEEDS_REVIEW -> StrShift.notAutoFixed to ""
  }
}

private fun countLine(r: ShiftSlip.Row): Pair<String, String>? {
  val label = when (r.kind) {
    ShiftSlip.Kind.SALES -> StrShift.sales
    ShiftSlip.Kind.RECEIPTS -> StrShift.receipts
    ShiftSlip.Kind.REFUNDS -> StrShift.refunds
    ShiftSlip.Kind.CASH_IN -> StrShift.intoDrawer
    ShiftSlip.Kind.CASH_OUT -> StrShift.outOfDrawer
    else -> return null
  }
  return label to StrShift.times(r.count ?: 0)
}

@Composable
private fun ShiftReportLines(rep: ShiftReport) {
  ShiftSlip.rows(rep).forEach { r ->
    val (label, value) = slipLine(r)
    if (r.kind == ShiftSlip.Kind.NEEDS_REVIEW) {
      Spacer(Modifier.height(4.dp))
      StatusPill(label, PillTone.WARNING)
      return@forEach
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(label, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted, modifier = Modifier.weight(1f))
      Spacer(Modifier.width(8.dp))
      Text(value, style = if (r.strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall, color = Shop.colors.text)
    }
  }
}
