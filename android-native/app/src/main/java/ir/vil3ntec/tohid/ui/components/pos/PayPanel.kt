package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  پنلِ پرداختِ ترکیبی (PAY-01، 02، 04، 05، 06، 07؛ S01، S06) — فقط با پرچمِ
 *  `ShopPolicy.payV2`. همهٔ حساب از `PayFlow` (هستهٔ `core/pay`)؛ این‌جا فقط کادر و کلید.
 *  گوشی (`CheckoutDialog`) و کامپیوتر (`PayPanelDialog`): نقدِ دریافتی و «نقدِ دقیق»، ردیف‌های
 *  کیف پول/کارت/حواله با شمارهٔ مرجع، دریافتی، پولِ پس (فقط از نقد) و باقی‌مانده.
 *
 *  کلیدها: Enter روی نقدِ خالی = نقدِ دقیق، وگرنه ثبت؛ Enter در مرجع ⇒ مبلغ؛ Enter
 *  در مبلغ ⇒ ثبت؛ PgDn در نقد ⇒ ردیفِ تازه (HesabPay)، در ردیف ⇒ روشِ بعدی؛ PgUp ⇒ قبلی.
 */

/** متنِ کادرهای پنل؛ مشتری از صفحه می‌آید */
class PayPanelState {
  var cash by mutableStateOf("")
  val rows = mutableStateListOf<PayFlow.DraftRow>()

  fun draft(total: Double, customerId: String?) = PayFlow.Draft(total, cash, rows.toList(), customerId)

  fun addRow(): Int {
    rows += PayFlow.DraftRow(PayFlow.panelMethods().firstOrNull()?.id.orEmpty())
    return rows.lastIndex
  }

  fun cycle(i: Int, step: Int) {
    val ids = PayFlow.panelMethods().map { it.id }
    if (ids.isEmpty() || i !in rows.indices) return
    val at = ids.indexOf(rows[i].method)
    rows[i] = rows[i].copy(method = ids[(at + step + ids.size) % ids.size])
  }

  /** «نقدِ دقیق»: نقدی که با ردیف‌های غیرنقدِ فعلی فاکتور را می‌بندد */
  fun fillExact(total: Double, customerId: String?) {
    val r = PayFlow.exactCash(draft(total, customerId))
    if (r is PayFlow.Outcome.Ok) cash = if (r.value > 0) plainAmount(r.value) else ""
  }
}

private fun plainAmount(v: Double): String = if (v == Math.rint(v)) v.toLong().toString() else v.toString()

//  `Key` کلاسِ مقداری است و vararg نمی‌گیرد — دو کلید بس است (Enter و Enterِ عددی)
private fun KeyEvent.down(k: Key, also: Key? = null) = type == KeyEventType.KeyDown && (key == k || key == also)

@Composable
fun PaySplitSection(
  state: PayPanelState,
  total: Double,
  customerId: String?,
  customerName: String?,
  target: Dp,
  cashFocus: FocusRequester,
  onSubmit: () -> Unit,
) {
  //  فوکوسِ هر ردیف — فهرستِ ساده، نه state: فقط هم‌اندازهٔ ردیف‌ها می‌ماند
  val refFocus = remember { ArrayList<FocusRequester>() }
  val amountFocus = remember { ArrayList<FocusRequester>() }
  while (refFocus.size < state.rows.size) { refFocus += FocusRequester(); amountFocus += FocusRequester() }
  var focusRef by remember { mutableStateOf(-1) }
  LaunchedEffect(focusRef, state.rows.size) {
    if (focusRef in state.rows.indices) runCatching { refFocus[focusRef].requestFocus() }
    focusRef = -1
  }
  fun addRow() { focusRef = state.addRow() }
  val preview = PayFlow.preview(state.draft(total, customerId))

  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(
      value = state.cash,
      onValueChange = { state.cash = numericInput(it) },
      label = { Text("نقدِ دریافتی") },
      supportingText = { Text("Enter روی کادرِ خالی = نقدِ دقیق") },
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { if (state.cash.isBlank()) state.fillExact(total, customerId) else onSubmit() }),
      modifier = Modifier.weight(1f).focusRequester(cashFocus).onPreviewKeyEvent { e ->
        when {
          e.down(Key.Enter, Key.NumPadEnter) -> { if (state.cash.isBlank()) state.fillExact(total, customerId) else onSubmit(); true }
          e.down(Key.PageDown) -> { addRow(); true }
          else -> false
        }
      },
    )
    OutlinedButton(
      onClick = { state.fillExact(total, customerId); runCatching { cashFocus.requestFocus() } },
      modifier = Modifier.heightIn(min = maxOf(target, 44.dp)),
    ) { Text("نقدِ دقیق") }
  }

  state.rows.forEachIndexed { i, row ->
    val bad = preview is PayFlow.Outcome.Bad && preview.problem.index == i
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      MethodPicker(row.method, target, Modifier.weight(1.2f)) { m -> state.rows[i] = row.copy(method = m) }
      val keys = Modifier.onPreviewKeyEvent { e ->
        when {
          e.down(Key.PageDown) -> { state.cycle(i, 1); true }
          e.down(Key.PageUp) -> { state.cycle(i, -1); true }
          else -> false
        }
      }
      OutlinedTextField(
        value = row.ref,
        onValueChange = { state.rows[i] = row.copy(ref = it) },
        label = { Text("شمارهٔ مرجع") },
        singleLine = true,
        isError = bad,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { runCatching { amountFocus[i].requestFocus() } }),
        modifier = Modifier.weight(1f).focusRequester(refFocus[i]).then(keys).onPreviewKeyEvent { e ->
          if (e.down(Key.Enter, Key.NumPadEnter)) { runCatching { amountFocus[i].requestFocus() }; true } else false
        },
      )
      OutlinedTextField(
        value = row.amount,
        onValueChange = { state.rows[i] = row.copy(amount = numericInput(it)) },
        label = { Text("مبلغ") },
        singleLine = true,
        isError = bad,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = Modifier.weight(1f).focusRequester(amountFocus[i]).then(keys).onPreviewKeyEvent { e ->
          if (e.down(Key.Enter, Key.NumPadEnter)) { onSubmit(); true } else false
        },
      )
      IconButton(onClick = { state.rows.removeAt(i); refFocus.removeAt(i); amountFocus.removeAt(i); runCatching { cashFocus.requestFocus() } }) {
        Icon(Icons.Filled.Close, contentDescription = "حذفِ این روش", tint = Shop.colors.muted)
      }
    }
  }
  TextButton(onClick = { addRow() }) { Text("+ روشِ دیگر (PgDn)") }

  //  خلاصه — همان عددِ هسته
  val s: Settlement? = preview.valueOrNull()
  Column(
    Modifier.fillMaxWidth().background(Shop.colors.surface2, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
  ) {
    SumRow("مبلغِ قابلِ پرداخت", money(total), strong = true)
    SumRow("دریافتی", s?.let { money(PayFlow.major(it.tendered)) } ?: "—")
    SumRow("پولِ پس (فقط از نقد)", s?.let { money(PayFlow.major(it.change)) } ?: "—")
    SumRow(
      if ((s?.toAccount ?: 0) > 0) "به حسابِ ${customerName ?: "مشتری"}" else "باقی‌مانده",
      s?.let { money(PayFlow.major(it.remaining)) } ?: "—",
      warn = (s?.remaining ?: 0) > 0,
    )
    if (s != null && s.payments.any { it.status == "pending" }) {
      Spacer(Modifier.height(4.dp))
      StatusPill("غیرنقد: ثبتِ دستی، در انتظارِ تأیید", PillTone.WARNING)
    }
  }
  val error = when {
    preview is PayFlow.Outcome.Bad -> PayFlow.message(preview.problem)
    s != null && s.status == "short" && (s.tendered > 0 || state.rows.isNotEmpty()) -> PayFlow.message(PayFlow.Problem("SHORT", remaining = s.remaining))
    else -> null
  }
  if (error != null) {
    Spacer(Modifier.height(6.dp))
    Text(error, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
  }
}

@Composable
private fun SumRow(label: String, value: String, strong: Boolean = false, warn: Boolean = false) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    Text(
      "$value ؋",
      style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
      fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
      color = if (warn) Shop.colors.warning else Shop.colors.text,
    )
  }
}

@Composable
private fun MethodPicker(selected: String, target: Dp, modifier: Modifier, onPick: (String) -> Unit) {
  var open by remember { mutableStateOf(false) }
  Box(modifier) {
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().heightIn(min = maxOf(target, 44.dp))) {
      Text(PayFlow.methodName(selected), maxLines = 1)
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      PayFlow.panelMethods().forEach { m ->
        DropdownMenuItem(text = { Text(m.name) }, onClick = { onPick(m.id); open = false })
      }
    }
  }
}

/**
 *  پنلِ پرداختِ کامپیوتر (F9 با پرچم): همان `PaySplitSection` در پنجره. مشتری را صفحه
 *  پیش از F9 با F2 می‌گذارد؛ باقی‌مانده فقط با مشتری به حساب می‌رود.
 */
@Composable
fun PayPanelDialog(
  total: Double,
  customerId: String?,
  customerName: String?,
  busy: Boolean,
  target: Dp,
  onDismiss: () -> Unit,
  onConfirm: (Settlement) -> Unit,
) {
  val state = remember { PayPanelState() }
  val cashFocus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { cashFocus.requestFocus() } }
  var error by remember { mutableStateOf<String?>(null) }
  fun submit() {
    if (busy) return
    when (val r = PayFlow.checkout(state.draft(total, customerId))) {
      is PayFlow.Outcome.Ok -> onConfirm(r.value)
      is PayFlow.Outcome.Bad -> error = PayFlow.message(r.problem)
    }
  }
  Dialog(onDismissRequest = onDismiss) {
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 640.dp)) {
      Column(
        Modifier.padding(18.dp).verticalScroll(rememberScrollState()).onPreviewKeyEvent { e ->
          if (e.down(Key.Escape)) { onDismiss(); true } else false
        },
      ) {
        Text("پنلِ پرداخت", style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Text(
          customerName?.let { "مشتری: $it — باقی‌مانده به حسابش می‌رود" } ?: "بی مشتری — برای پرداختِ جزئی پیش از F9 با F2 مشتری بگذارید",
          style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
        )
        Spacer(Modifier.height(10.dp))
        PaySplitSection(state, total, customerId, customerName, target, cashFocus, ::submit)
        error?.let {
          Spacer(Modifier.height(4.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Shop.colors.border)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text("انصراف (Esc)") }
          Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.weight(1.6f).height(48.dp)) {
            Text(if (busy) "در حالِ ثبت…" else "ثبتِ فروش (Enter)")
          }
        }
      }
    }
  }
}
