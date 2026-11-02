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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
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
import ir.vil3ntec.tohid.core.i18n.StrSafe
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SafeBook
import ir.vil3ntec.tohid.data.SafeBookPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.sync.v1.Ulid
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/*
 *  گاوصندوق (PAY-18) در «تاریخچهٔ فروش» ِ گوشی و کامپیوتر، زیرِ کارتِ صندوق — فقط با `SafeBookPref` ِ همین دستگاه
 *  (پیش‌فرض خاموش ⇒ این‌جا صدا زده نمی‌شود). همهٔ قاعده در `core/safe/SafeCore` و `data/SafeBook`؛ این‌جا فقط کادر.
 *  هر ثبت فقط از `SafeBookPref.record` (دفترِ همین لحظهٔ دیسک، یک بار برای شناسه‌ای که پنجره هنگامِ باز شدن می‌سازد).
 *  مجوز همان سرور (`safe-ledger.js`): شمارش، ورود، خروج، به بانک و به دخل `safe.move` می‌خواهند؛ «از دخل به گاوصندوق»
 *  فقط نوشتنِ `safeEntries`. بی `safe.move` مانده و تاریخچه هم دیده نمی‌شوند.
 */

private fun safeAfn(m: Long): String = "${money(PayFlow.major(m))} ؋"

private fun kindLabel(kind: String): String = when (kind) {
  SafeCore.COUNT -> StrSafe.kindCount
  SafeCore.IN -> StrSafe.kindIn
  SafeCore.OUT -> StrSafe.kindOut
  SafeCore.FROM_DRAWER -> StrSafe.kindFromDrawer
  SafeCore.TO_DRAWER -> StrSafe.kindToDrawer
  SafeCore.TO_BANK -> StrSafe.kindToBank
  else -> kind
}

private fun reasonLabel(reason: String): String = when (reason) {
  "deposit" -> StrSafe.reasonDeposit
  "owner_withdrawal" -> StrSafe.reasonOwnerWithdrawal
  "petty_expense" -> StrSafe.reasonPettyExpense
  SafeCore.OTHER -> StrSafe.reasonOther
  else -> reason
}

private fun dialogTitle(kind: String): String = when (kind) {
  SafeCore.COUNT -> StrSafe.countTitle
  SafeCore.IN -> StrSafe.moneyIn
  SafeCore.OUT -> StrSafe.moneyOut
  SafeCore.TO_BANK -> StrSafe.toBank
  SafeCore.FROM_DRAWER -> StrSafe.fromDrawer
  else -> StrSafe.toDrawer
}

/** پیامِ سه‌زبانهٔ کدِ رد؛ کدِ ناشناخته ⇒ همان پیامِ `SafeBook`/`PayFlow` */
private fun errorText(f: SafeBook.Result.Failed): String = when (f.code) {
  SafeCore.Code.REASON_REQUIRED -> f.varianceMinor?.let {
    StrSafe.varianceNeedsReason(it > 0, safeAfn(kotlin.math.abs(it)), safeAfn(Pay.DEFAULT_VARIANCE_THRESHOLD))
  } ?: StrSafe.reasonRequired
  SafeCore.Code.INSUFFICIENT_FUNDS -> StrSafe.insufficientFunds
  "INSUFFICIENT_CASH" -> StrSafe.insufficientCash
  SafeCore.Code.INVALID_AMOUNT -> StrSafe.invalidAmount
  SafeCore.Code.NOTE_REQUIRED -> StrSafe.noteRequired
  SafeCore.Code.BAG_REQUIRED -> StrSafe.bagRequired
  SafeCore.Code.BAD_REASON -> StrSafe.badReason
  SafeCore.Code.TEXT_TOO_LONG -> StrSafe.tooLong
  SafeCore.Code.ID_CONFLICT -> StrSafe.idConflict
  SafeCore.Code.INVALID_TIME -> StrSafe.badTime
  SafeBook.SHIFT_CLOSED -> StrSafe.shiftClosed
  else -> f.message
}

@Composable
fun SafePanel(d: ShopData, store: ShopStore, me: String, toast: (String) -> Unit) {
  val context = LocalContext.current
  val canMove = remember { ShopRole.allows(context, "safe.move") }
  val canWrite = remember { ShopRole.canWrite(context, "safeEntries") }
  if (!canWrite) return
  val view = remember(d) { SafeBook.view(d) }
  val shift = remember(d) { CashShift.current(d) }
  var mode by remember { mutableStateOf<String?>(null) }
  var history by remember { mutableStateOf(false) }

  Panel {
    Text(StrSafe.title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
    if (canMove) {
      Text(StrSafe.balance(safeAfn(view.balanceMinor)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
      Text(
        view.lastCount?.let { StrSafe.lastCount(formatMillis(it.entry.at)) } ?: StrSafe.notCounted,
        style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (view.negative) StatusPill(StrSafe.negative, PillTone.DANGER)
        if (view.needsReview.isNotEmpty()) StatusPill(StrSafe.needsReview(view.needsReview.size), PillTone.WARNING)
        if (view.invalid.isNotEmpty()) StatusPill(StrSafe.invalid(view.invalid.size), PillTone.WARNING)
      }
    } else {
      Text(StrSafe.staffNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      if (canMove) {
        TextButton(onClick = { history = true }) { Text(StrSafe.history) }
        OutlinedButton(onClick = { mode = SafeCore.COUNT }) { Text(StrSafe.count) }
        OutlinedButton(onClick = { mode = SafeCore.IN }) { Text(StrSafe.moneyIn) }
        OutlinedButton(onClick = { mode = SafeCore.OUT }) { Text(StrSafe.moneyOut) }
        OutlinedButton(onClick = { mode = SafeCore.TO_BANK }) { Text(StrSafe.toBank) }
      }
      if (shift != null) {
        OutlinedButton(onClick = { mode = SafeCore.FROM_DRAWER }) { Text(StrSafe.fromDrawer) }
        if (canMove) OutlinedButton(onClick = { mode = SafeCore.TO_DRAWER }) { Text(StrSafe.toDrawer) }
      }
    }
    if (shift == null) Text(StrSafe.transferNeedsShift, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
  }
  Spacer(Modifier.height(12.dp))

  mode?.let { k -> SafeMoveDialog(k, view.balanceMinor, store, me, toast) { mode = null } }
  if (history) SafeHistoryDialog(view) { history = false }
}

/** یک پنجره برای هر شش نوع؛ شناسهٔ ردیف (و ردیفِ جفتِ صندوق) هنگامِ باز شدن ساخته می‌شود ⇒ دو ضربه یک ثبت */
@Composable
private fun SafeMoveDialog(kind: String, balanceMinor: Long, store: ShopStore, me: String, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val scope = rememberCoroutineScope()
  val key = remember { Ulid.next() }
  val auditKey = remember { Ulid.next() }
  val reasons = SafeCore.REASONS[kind].orEmpty()
  var reason by remember { mutableStateOf(reasons.firstOrNull()) }
  var amount by remember { mutableStateOf("") }
  var note by remember { mutableStateOf("") }
  var bag by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val isCount = kind == SafeCore.COUNT
  val minor = CashKeys.cashMinor(amount)

  fun submit() {
    if (busy) return
    if (amount.isBlank()) { error = StrSafe.amountRequired; return }
    val m = minor ?: run { error = StrSafe.invalidAmount; return }
    val now = System.currentTimeMillis()
    val today = ir.vil3ntec.tohid.todayIso()
    val text = note.trim().ifEmpty { null }
    busy = true
    scope.launch {
      val r = SafeBookPref.record(store, key) { cur, k ->
        when (kind) {
          SafeCore.COUNT -> SafeBook.count(cur, m, text, me, now) { k }
          SafeCore.IN -> SafeBook.deposit(cur, m, reason.orEmpty(), text, me, now) { k }
          SafeCore.OUT -> SafeBook.withdraw(cur, m, reason.orEmpty(), text, me, now) { k }
          SafeCore.TO_BANK -> SafeBook.toBank(cur, m, bag, text, me, now) { k }
          else -> SafeBook.transfer(cur, kind, m, me, today, now, k, auditKey)
        }
      }
      busy = false
      when (r) {
        is SafeBookPref.Saved.Ok -> { toast(if (r.again) StrSafe.already else StrSafe.saved); onDismiss() }
        is SafeBookPref.Saved.Bad -> error = errorText(r.failed)
        SafeBookPref.Saved.NotSaved -> error = StrSafe.notSaved
      }
    }
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(dialogTitle(kind), color = Shop.colors.text) },
    text = {
      Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        if (reasons.isNotEmpty()) {
          Text(StrSafe.reason, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            reasons.forEach { r ->
              if (r == reason) Button(onClick = {}) { Text(reasonLabel(r)) }
              else OutlinedButton(onClick = { reason = r; error = null }) { Text(reasonLabel(r)) }
            }
          }
        }
        OutlinedTextField(
          value = amount, onValueChange = { amount = numericInput(it); error = null },
          label = { Text(if (isCount) StrSafe.counted else StrSafe.amount) }, singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        if (isCount) {
          Text(StrSafe.bookBalance(safeAfn(balanceMinor)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          val v = minor?.takeIf { amount.isNotBlank() }?.let { it - balanceMinor }
          if (v != null && v != 0L) {
            Text(
              if (v > 0) StrSafe.over(safeAfn(v)) else StrSafe.short(safeAfn(-v)),
              style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning,
            )
          }
        }
        if (kind == SafeCore.TO_BANK) {
          OutlinedTextField(
            value = bag, onValueChange = { bag = it.take(SafeCore.MAX_BAG); error = null }, label = { Text(StrSafe.bagNo) },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
          )
        }
        if (kind != SafeCore.FROM_DRAWER && kind != SafeCore.TO_DRAWER) {
          OutlinedTextField(
            value = note, onValueChange = { note = it.take(SafeCore.MAX_TEXT); error = null },
            label = {
              Text(
                when (kind) {
                  SafeCore.COUNT -> StrSafe.varianceReason
                  SafeCore.TO_BANK -> StrSafe.bankNote
                  else -> StrSafe.noteForOther
                },
              )
            },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
          )
        } else {
          Spacer(Modifier.height(6.dp))
          Text(StrSafe.transferNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger, modifier = Modifier.padding(top = 6.dp)) }
      }
    },
    confirmButton = { TextButton(enabled = !busy, onClick = ::submit) { Text(StrSafe.record) } },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrSafe.cancel) } },
  )
}

/** تاریخچه، تازه‌ترین اول — تا [HISTORY_MAX] ردیف */
@Composable
private fun SafeHistoryDialog(view: SafeCore.View, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrSafe.historyTitle, color = Shop.colors.text) },
    text = {
      Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        if (view.history.isEmpty()) Text(StrSafe.empty, color = Shop.colors.muted)
        view.history.take(HISTORY_MAX).forEachIndexed { i, line ->
          if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Shop.colors.border)
          SafeHistoryLine(line)
        }
        if (view.history.size > HISTORY_MAX) {
          Text(StrSafe.more(view.history.size - HISTORY_MAX), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(StrSafe.close) } },
  )
}

private const val HISTORY_MAX = 200

@Composable
private fun SafeHistoryLine(line: SafeCore.Line) {
  val e = line.entry
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(kindLabel(e.kind), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text, modifier = Modifier.weight(1f))
    val amount = if (e.kind == SafeCore.COUNT) safeAfn(e.amountMinor) else (if (line.deltaMinor > 0) "+" else "−") + safeAfn(e.amountMinor)
    Text(amount, style = MaterialTheme.typography.bodyMedium, color = if (line.deltaMinor < 0 && e.kind != SafeCore.COUNT) Shop.colors.danger else Shop.colors.text)
  }
  val details = listOfNotNull(
    formatMillis(e.at),
    e.by,
    e.reason?.let { if (e.kind == SafeCore.COUNT) it else reasonLabel(it) },
    e.note,
    e.bagNo?.let { StrSafe.bag(it) },
    if (e.kind == SafeCore.COUNT) line.varianceMinor?.let { v -> if (v > 0) StrSafe.over(safeAfn(v)) else if (v < 0) StrSafe.short(safeAfn(-v)) else null } ?: StrSafe.startCount else null,
  )
  Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
  Text(StrSafe.after(safeAfn(line.balanceMinor)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
  if (line.late) StatusPill(StrSafe.late, PillTone.WARNING)
}

/** بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) — یک خط در `SettingsLegacy` کنارِ تنظیم‌های صندوق */
@Composable
fun SafeBookSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(SafeBookPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.AccountBalance,
    title = StrSafe.settingsTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrSafe.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; SafeBookPref.set(context, on) },
    )
  }
}
