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
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.core.safe.SafeCurrency
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SafeBook
import ir.vil3ntec.tohid.data.SafeBookPref
import ir.vil3ntec.tohid.data.SafeCurrencies
import ir.vil3ntec.tohid.data.SafeCurrencyPref
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
import ir.vil3ntec.tohid.ui.v3.shell.SafeCurrencyChoice
import kotlinx.coroutines.launch

/*
 *  گاوصندوق (PAY-18) در «تاریخچهٔ فروش» ِ گوشی و کامپیوتر، زیرِ کارتِ صندوق — فقط با `SafeBookPref` ِ همین دستگاه
 *  (پیش‌فرض خاموش ⇒ این‌جا صدا زده نمی‌شود). همهٔ قاعده در `core/safe/SafeCore` و `data/SafeBook`؛ این‌جا فقط کادر.
 *  هر ثبت فقط از `SafeBookPref.record` (دفترِ همین لحظهٔ دیسک، یک بار برای شناسه‌ای که پنجره هنگامِ باز شدن می‌سازد).
 *  مجوز همان سرور (`safe-ledger.js`): شمارش، ورود، خروج، به بانک و به دخل `safe.move` می‌خواهند؛ «از دخل به گاوصندوق»
 *  فقط نوشتنِ `safeEntries`. بی `safe.move` مانده و تاریخچه هم دیده نمی‌شوند.
 *  ارز (خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴): هر ارز دفترِ جدای خودش است (`SafeBook.views`)؛ بیش از یک ارز ⇒ ماندهٔ هر ارز در
 *  فهرستِ فشرده (`SafeBalanceList`)، کادرِ «ارز» کنارِ مبلغِ شمارش/ورود/خروج/بانک (`SafeCurrencyButton`؛ پیش‌فرض آخرین
 *  ارزِ همین دستگاه). انتقال با دخل همیشه افغانی است. هیچ تبدیل و هیچ جمعی میانِ دو ارز.
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

/** پیامِ سه‌زبانهٔ کدِ رد؛ کدِ ناشناخته ⇒ همان پیامِ `SafeBook`/`PayFlow` (برگهٔ «+» ِ خانه هم همین را می‌گیرد) */
internal fun safeErrorText(f: SafeBook.Result.Failed): String = when (f.code) {
  SafeCore.Code.REASON_REQUIRED -> f.varianceMinor?.let {
    //  آستانه فقط افغانی است؛ در ارزِ دیگر هر اختلافی دلیل می‌خواهد (`SafeCore.thresholdFor`)
    if (f.currency != SafeCurrency.AFN) StrSafeCur.varianceNeedsReason(it > 0, SafeCurrencies.format(kotlin.math.abs(it), f.currency))
    else StrSafe.varianceNeedsReason(it > 0, safeAfn(kotlin.math.abs(it)), safeAfn(Pay.DEFAULT_VARIANCE_THRESHOLD))
  } ?: StrSafe.reasonRequired
  SafeCore.Code.BAD_CURRENCY -> StrSafeCur.badCurrency
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
  val views = remember(d) { SafeBook.views(d) }
  val view = views.getValue(SafeCurrency.AFN)
  val names = remember(views) { SafeCurrencyPref.custom(context).let { c -> views.keys.associateWith { SafeCurrencies.name(it, c) } } }
  val shift = remember(d) { CashShift.current(d) }
  var mode by remember { mutableStateOf<String?>(null) }
  var history by remember { mutableStateOf(false) }

  Panel {
    Text(StrSafe.title, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
    if (canMove && views.size > 1) {
      //  چند ارز: ماندهٔ هر ارز جدا، افغانی اول؛ شمارِ ردیف‌های کنارگذاشته (ردیف، نه پول) یک‌جا
      SafeBalanceList(views) { names[it] ?: it }
      val bad = views.values.sumOf { it.invalid.size }
      if (bad > 0) StatusPill(StrSafe.invalid(bad), PillTone.WARNING)
    } else if (canMove) {
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

  mode?.let { k -> SafeMoveDialog(k, views, store, me, toast) { mode = null } }
  if (history) SafeHistoryDialog(views) { history = false }
}

/**
 *  یک پنجره برای هر شش نوع؛ شناسهٔ ردیف (و ردیفِ جفتِ صندوق) هنگامِ باز شدن ساخته می‌شود ⇒ دو ضربه یک ثبت.
 *  ارز: کادرِ «ارز» کنارِ مبلغ (جز انتقال با دخل که افغانی است)؛ ماندهٔ شمارش همان ارز.
 */
@Composable
private fun SafeMoveDialog(kind: String, views: Map<String, SafeCore.View>, store: ShopStore, me: String, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val linked = kind == SafeCore.FROM_DRAWER || kind == SafeCore.TO_DRAWER
  var custom by remember { mutableStateOf(SafeCurrencyPref.custom(context)) }
  val choices = SafeCurrencies.all(custom, views.keys.toList())
  var code by remember { mutableStateOf(if (linked) SafeCurrency.AFN else SafeCurrencies.pick(SafeCurrencyPref.last(context), choices)) }
  val canAdd = remember { !ShopRole.isStaff(context) }
  val balanceMinor = views[code]?.balanceMinor ?: 0L
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
  val minor = SafeCurrencies.parse(amount, code)
  val choice = SafeCurrencyChoice(
    code = code, choices = choices, name = { SafeCurrencies.name(it, custom) }, onPick = { code = it; error = null },
    onAdd = if (!canAdd) null else { c, n ->
      when (val r = SafeCurrencies.add(custom, c, n)) {
        is SafeCurrencies.Added.Bad -> r.message
        is SafeCurrencies.Added.Ok -> { SafeCurrencyPref.saveCustom(context, r.list); custom = r.list; code = r.code; null }
      }
    },
  )

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
          SafeCore.COUNT -> SafeBook.count(cur, m, code, text, me, now) { k }
          SafeCore.IN -> SafeBook.deposit(cur, m, code, reason.orEmpty(), text, me, now) { k }
          SafeCore.OUT -> SafeBook.withdraw(cur, m, code, reason.orEmpty(), text, me, now) { k }
          SafeCore.TO_BANK -> SafeBook.toBank(cur, m, code, bag, text, me, now) { k }
          else -> SafeBook.transfer(cur, kind, m, me, today, now, k, auditKey)
        }
      }
      busy = false
      when (r) {
        is SafeBookPref.Saved.Ok -> {
          if (!linked) SafeCurrencyPref.setLast(context, code)
          toast(if (r.again) StrSafe.already else StrSafe.saved); onDismiss()
        }
        is SafeBookPref.Saved.Bad -> error = safeErrorText(r.failed)
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
        val afn = code == SafeCurrency.AFN
        Row(verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = amount, onValueChange = { amount = numericInput(it); error = null },
            label = {
              Text(
                when {
                  isCount && afn -> StrSafe.counted
                  isCount -> StrSafeCur.countedIn(code)
                  afn -> StrSafe.amount
                  else -> StrSafeCur.amountIn(code)
                },
              )
            },
            singleLine = true, suffix = { Text(SafeCurrencies.symbol(code)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
          )
          if (!linked) {
            Spacer(Modifier.width(6.dp))
            SafeCurrencyButton(choice, enabled = !busy)
          }
        }
        if (isCount) {
          Text(StrSafe.bookBalance(SafeCurrencies.format(balanceMinor, code)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          val v = minor?.takeIf { amount.isNotBlank() }?.let { it - balanceMinor }
          if (v != null && v != 0L) {
            Text(
              if (v > 0) StrSafe.over(SafeCurrencies.format(v, code)) else StrSafe.short(SafeCurrencies.format(-v, code)),
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
                  SafeCore.COUNT -> if (afn) StrSafe.varianceReason else StrSafeCur.varianceAnyReason
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

/** تاریخچهٔ همهٔ ارزها، تازه‌ترین اول — تا [HISTORY_MAX] ردیف؛ هر ردیف مبلغ و مانده را به ارزِ خودش */
@Composable
private fun SafeHistoryDialog(views: Map<String, SafeCore.View>, onDismiss: () -> Unit) {
  val lines = remember(views) {
    views.values.flatMap { it.history }.sortedWith(compareByDescending<SafeCore.Line> { it.entry.at }.thenByDescending { it.entry.id })
  }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrSafe.historyTitle, color = Shop.colors.text) },
    text = {
      Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
        if (lines.isEmpty()) Text(StrSafe.empty, color = Shop.colors.muted)
        lines.take(HISTORY_MAX).forEachIndexed { i, line ->
          if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp), color = Shop.colors.border)
          SafeHistoryLine(line)
        }
        if (lines.size > HISTORY_MAX) {
          Text(StrSafe.more(lines.size - HISTORY_MAX), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
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
  val code = SafeCore.currencyOf(e)
  fun fmt(m: Long) = SafeCurrencies.format(m, code)
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(kindLabel(e.kind), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Shop.colors.text, modifier = Modifier.weight(1f))
    val amount = if (e.kind == SafeCore.COUNT) fmt(e.amountMinor) else (if (line.deltaMinor > 0) "+" else "−") + fmt(e.amountMinor)
    Text(amount, style = MaterialTheme.typography.bodyMedium, color = if (line.deltaMinor < 0 && e.kind != SafeCore.COUNT) Shop.colors.danger else Shop.colors.text)
  }
  val details = listOfNotNull(
    formatMillis(e.at),
    e.by,
    e.reason?.let { if (e.kind == SafeCore.COUNT) it else reasonLabel(it) },
    e.note,
    e.bagNo?.let { StrSafe.bag(it) },
    if (e.kind == SafeCore.COUNT) line.varianceMinor?.let { v -> if (v > 0) StrSafe.over(fmt(v)) else if (v < 0) StrSafe.short(fmt(-v)) else null } ?: StrSafe.startCount else null,
  )
  Text(details.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
  Text(StrSafe.after(fmt(line.balanceMinor)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
  if (line.late) StatusPill(StrSafe.late, PillTone.WARNING)
}

/** بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) — یک خط در تنظیماتِ نسخهٔ ۳ (`Settings.kt`) */
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
