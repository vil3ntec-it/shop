package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.core.pay.FxRate
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.TillMethods
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.numericInput
import ir.vil3ntec.tohid.ui.screens.matches
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlin.time.TimeSource

private fun KeyEvent.keyDown(k: Key, also: Key? = null) = type == KeyEventType.KeyDown && (key == k || key == also)

/**
 *  e-paystep — «گامِ پرداخت» ِ صندوقِ کامپیوتر (F9 یا Enter ِ کادرِ خالی؛ با پرچمِ `payV2` و بی آن): مبلغِ قابلِ پرداختِ درشت،
 *  پرداخت‌های تأییدشده، کاشی‌های روش‌ها (`TillMethods`) و کادرِ همان روش؛ هر پرداخت جدا تأیید می‌شود (`PayStep.confirm`) و
 *  [onConfirm] فقط با تسویهٔ کامل (همهٔ فاکتور پرداخت یا به حسابِ مشتری). کلیدها همان پنلِ پیشین: کادرِ نقدِ خالی + Enter ⇒
 *  «نقدِ دقیق»، Enter ِ دوباره ⇒ «دریافت شد»؛ Shift+1..6 هر بار یک اسکناس؛ F12 نقدِ دقیق و تأیید؛ F2 مشتری؛ Alt+P چاپ؛ Esc برگشت
 *  به صندوق با سبدِ دست‌نخورده. حساب‌پی همان برگهٔ درگاه (`CheckoutGateways`) برای باقی.
 */
@Composable
fun PayStepDialog(
  total: Double,
  customerId: String?,
  customerName: String?,
  busy: Boolean,
  target: Dp,
  tiles: List<TillMethods.Tile>,
  onDismiss: () -> Unit,
  onConfirm: (Settlement) -> Unit,
  initial: TillMethods.Tile? = null,
  gateways: GatewayHook? = null,
  ledger: ShopData? = null,
  stored: Boolean = false,
  cashRounding: Long = 0L,
  onCustomer: (() -> Unit)? = null,
  onPickCustomer: (() -> Unit)? = null,
  print: Boolean? = null,
  onTogglePrint: (() -> Unit)? = null,
  offMethods: Set<String> = emptySet(),
  fxRates: List<FxRate> = emptyList(),
) {
  val printToggle = onTogglePrint.takeIf { print != null }
  val state = remember { PayPanelState().also { it.stored = stored; it.cashRounding = cashRounding; it.off = offMethods; it.fxRates = fxRates; it.clock = ir.vil3ntec.tohid.data.ReportBridge::nowMs } }
  val entry = remember { PayEntry() }
  val cashFocus = remember { FocusRequester() }
  var error by remember { mutableStateOf<String?>(null) }
  var hesabAsk by remember { mutableStateOf(0) }
  //  PAY-04: Enter ِ پنل تا ۴۰۰ میلی‌ثانیه پس از باز شدن خورده می‌شود (ساعتِ یکنواخت)
  val opened = remember { TimeSource.Monotonic.markNow() }
  fun armed() = CashKeys.enterArmed(opened.elapsedNow().inWholeMilliseconds)
  val pickCustomer = onPickCustomer ?: onCustomer
  val cashTile = tiles.firstOrNull { it.kind == TillMethods.Kind.CASH }

  fun pick(t: TillMethods.Tile) {
    if (!t.enabled || busy) return
    error = null
    when (t.kind) {
      TillMethods.Kind.HESABPAY -> if (gateways != null && PayStep.dueMinor(state, total) > 0) { entry.tile = t; hesabAsk++ }
      TillMethods.Kind.CREDIT -> { PayStep.open(entry, t, state, total); if (customerId == null) pickCustomer?.invoke() }
      else -> { PayStep.open(entry, t, state, total); if (t.kind == TillMethods.Kind.CASH) runCatching { cashFocus.requestFocus() } }
    }
  }

  fun confirm() {
    if (busy) return
    when (val r = PayStep.confirm(entry, state, total, customerId, ledger)) {
      is PayStep.Result.Done -> { error = null; onConfirm(r.settlement) }
      PayStep.Result.More -> { error = null; cashTile?.let(::pick) }
      is PayStep.Result.Bad -> { error = r.message; if (r.needCustomer && customerId == null) pickCustomer?.invoke() }
    }
  }

  /** F12: نقدِ دقیق برای باقی و تأیید */
  fun exactAndConfirm() {
    val t = cashTile ?: return
    PayStep.open(entry, t, state, total)
    confirm()
  }

  LaunchedEffect(Unit) {
    (initial?.takeIf { it.enabled } ?: cashTile)?.let(::pick)
    if (entry.tile?.kind == TillMethods.Kind.CASH || entry.tile == null) runCatching { cashFocus.requestFocus() }
  }
  val due = PayStep.dueMinor(state, total)
  val paid = PayStep.paid(state)

  Dialog(onDismissRequest = onDismiss) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    Surface(color = Shop.colors.surfaceSolid, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 680.dp)) {
      Column(
        Modifier.padding(18.dp).verticalScroll(rememberScrollState()).onPreviewKeyEvent { e ->
          if (e.matches(KeyMap.SALE_FINISH_EXACT) && !busy) { exactAndConfirm(); return@onPreviewKeyEvent true }
          when (payPanelKey({ e.matches(it) }, canCustomer = pickCustomer != null && !busy, canPrint = printToggle != null && !busy)) {
            PayPanelKey.BACK -> { onDismiss(); true }
            PayPanelKey.CUSTOMER -> { pickCustomer?.invoke(); true }
            PayPanelKey.TOGGLE_PRINT -> { printToggle?.invoke(); true }
            PayPanelKey.NEXT_FIELD -> focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next)
            PayPanelKey.PREV_FIELD -> focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Previous)
            null -> false
          }
        },
      ) {
        Text(StrTillPay.payTitle, style = MaterialTheme.typography.titleMedium, color = Shop.colors.text)
        Text(customerName?.let { StrPos.customerRestToAccount(it) } ?: StrPos.noCustomerHint, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        Text(if (paid.isEmpty()) StrTillPay.amountDue else StrTillPay.remainingDue, style = MaterialTheme.typography.labelLarge, color = Shop.colors.muted)
        Text("${money(PayFlow.major(due))} ؋", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Shop.colors.text)
        if (paid.isNotEmpty()) {
          Text(StrTillPay.paidSummary("${money(total)} ؋", "${money(PayFlow.major(paid.sumOf { it.amountMinor }))} ؋"), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          Column(Modifier.fillMaxWidth().padding(top = 6.dp).background(Shop.colors.surface2, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            paid.forEach { p ->
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name + if (p.ref.isNotEmpty()) " · ${p.ref}" else "", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
                Text("${money(PayFlow.major(p.amountMinor))} ؋", style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
                IconButton(onClick = { PayStep.remove(state, p.row); error = null }, enabled = !busy) {
                  Icon(Icons.Filled.Close, contentDescription = StrTillPay.removePaid(p.name), tint = Shop.colors.muted)
                }
              }
            }
          }
        }
        if (due > 0) {
          Spacer(Modifier.height(10.dp))
          PanelTiles(tiles, entry.tile?.kind, busy, target, if (paid.isEmpty()) StrTillPay.methodsTitle else StrTillPay.otherForRest, ::pick)
          DeskEntry(total, state, entry, customerName, target, cashFocus, ::armed, ::confirm)
          //  حساب‌پی: همان دکمه و برگهٔ درگاه برای باقیِ همین گام (کاشی خودش برگه را باز می‌کند)
          if (gateways != null && entry.tile?.kind == TillMethods.Kind.HESABPAY) CheckoutGateways(
            hook = gateways, total = total, panel = state, customerId = customerId, enabled = true, busy = busy,
            onPaid = onConfirm, modifier = Modifier.padding(top = 8.dp), openHesab = hesabAsk, onHesabOpened = { hesabAsk = 0 },
          )
        }
        error?.let {
          Spacer(Modifier.height(6.dp))
          Text(it, style = MaterialTheme.typography.labelMedium, color = Shop.colors.danger)
          if (customerId == null && pickCustomer != null && entry.tile?.kind == TillMethods.Kind.CREDIT) {
            TextButton(onClick = pickCustomer) { Text(ir.vil3ntec.tohid.core.i18n.StrTillPay.pickCustomerKey) }
          }
        }
        if (printToggle != null && print != null) {
          TextButton(onClick = printToggle, enabled = !busy, modifier = Modifier.heightIn(min = target)) {
            Text(StrPos.printThisInvoice(print, KeyMap.SALE_PAYMENT_TOGGLE_PRINT.hint()), color = if (print) Shop.colors.text else Shop.colors.muted)
          }
        }
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = Shop.colors.border)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(48.dp)) { Text(StrPos.cancelEsc) }
          val t = entry.tile
          val label = when {
            due <= 0 -> StrTillPay.recordPaid
            t?.kind == TillMethods.Kind.CARD -> StrTillPay.cardApproved
            t?.kind == TillMethods.Kind.CREDIT -> StrTillPay.toAccountButton
            t?.kind == TillMethods.Kind.CASH -> StrTillPay.received
            else -> StrTillPay.confirmPaid
          }
          Button(
            onClick = { confirm() },
            enabled = !busy && (due <= 0 || (t != null && t.kind != TillMethods.Kind.HESABPAY)),
            modifier = Modifier.weight(1.6f).height(48.dp),
          ) { Text(if (busy) StrSale.posting else "$label (Enter)") }
        }
      }
    }
  }
}

/** کاشی‌های روش‌ها در پنجرهٔ پرداخت (ریختِ همان پنجره)؛ کاشیِ باز «انتخاب‌شده» */
@Composable
private fun PanelTiles(tiles: List<TillMethods.Tile>, open: TillMethods.Kind?, busy: Boolean, target: Dp, title: String, onPick: (TillMethods.Tile) -> Unit) {
  Text(title, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
  tiles.chunked(3).forEach { line ->
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      line.forEach { t ->
        androidx.compose.material3.FilterChip(
          selected = t.kind == open,
          onClick = { onPick(t) },
          enabled = t.enabled && !busy,
          label = { Text(ir.vil3ntec.tohid.ui.v3.sales.checkout.tileLabel(t.kind).first, maxLines = 1) },
          leadingIcon = { Icon(ir.vil3ntec.tohid.ui.v3.sales.checkout.tileIcon(t.kind), contentDescription = null, modifier = Modifier.heightIn(max = 18.dp)) },
          modifier = Modifier.weight(1f).heightIn(min = maxOf(target, 40.dp)),
        )
      }
      repeat(3 - line.size) { Spacer(Modifier.weight(1f)) }
    }
  }
  tiles.firstOrNull { it.until != null }?.until?.let { until ->
    Text(StrTillPay.hesabPending(ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime.stamp(until)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
  }
  Spacer(Modifier.height(8.dp))
}

/** کادرِ روشِ باز در پنجرهٔ پرداخت: نقد (دریافتی، اسکناس‌ها، پولِ پس)، کارت (کارت‌خوان)، کیف پول/حواله، نسیه */
@Composable
private fun DeskEntry(
  total: Double,
  state: PayPanelState,
  entry: PayEntry,
  customerName: String?,
  target: Dp,
  cashFocus: FocusRequester,
  armed: () -> Boolean,
  confirm: () -> Unit,
) {
  val t = entry.tile ?: return
  fun enter() { if (armed()) confirm() }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    when (t.kind) {
      TillMethods.Kind.CASH -> {
        val exact = PayStep.exactCashMinor(state, total)
        //  PAY-03: Shift+رقم پیش از کادر ⇒ یک اسکناس
        val noteKeys = Modifier.onPreviewKeyEvent { e ->
          val hit = NOTE_KEYS.firstOrNull { e.matches(it.first) } ?: return@onPreviewKeyEvent false
          CashKeys.addNote(entry.amount, hit.second)?.let { entry.amount = it }
          true
        }
        fun cashEnter() { if (!armed()) return; if (entry.amount.isBlank()) entry.amount = PayStep.plain(exact) else confirm() }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = entry.amount,
            onValueChange = { entry.amount = numericInput(it) },
            label = { Text(StrPos.cashReceived) },
            supportingText = { Text(StrTillPay.cashBlankExact("${money(PayFlow.major(exact))} ؋")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { cashEnter() }),
            modifier = Modifier.weight(1f).focusRequester(cashFocus).then(noteKeys).onPreviewKeyEvent { e ->
              if (e.keyDown(Key.Enter, Key.NumPadEnter)) { cashEnter(); true } else false
            },
          )
          OutlinedButton(onClick = { entry.amount = PayStep.plain(exact) }, modifier = Modifier.heightIn(min = maxOf(target, 44.dp))) { Text(StrPos.exactCash) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          PayStep.cashChips(exact).forEach { m ->
            OutlinedButton(onClick = { entry.amount = PayStep.plain(m) }, modifier = Modifier.heightIn(min = maxOf(target, 40.dp))) { Text(money(PayFlow.major(m))) }
          }
          CashKeys.NOTES.forEach { note ->
            TextButton(onClick = { CashKeys.addNote(entry.amount, note)?.let { entry.amount = it } }, modifier = Modifier.heightIn(min = maxOf(target, 40.dp))) { Text("+" + money(PayFlow.major(note))) }
          }
        }
        val change = PayStep.changeMinor(entry, state, total)
        if (change > 0) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          Text(StrPos.changeCashOnly, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Shop.colors.muted)
          Text("${money(PayFlow.major(change))} ؋", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.success)
        }
      }
      TillMethods.Kind.CARD -> {
        val amount = PayFlow.amountOf(entry.amount)?.takeIf { !it.isNaN() } ?: PayFlow.major(PayStep.dueMinor(state, total))
        Text(StrTillPay.cardCharge("${money(amount)} ؋"), style = MaterialTheme.typography.bodyLarge, color = Shop.colors.text)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = entry.amount, onValueChange = { entry.amount = numericInput(it) }, label = { Text(StrPos.amount) }, singleLine = true,
            modifier = Modifier.weight(1f).onPreviewKeyEvent { e -> if (e.keyDown(Key.Enter, Key.NumPadEnter)) { enter(); true } else false },
          )
          OutlinedTextField(
            value = entry.ref, onValueChange = { entry.ref = it.take(40) }, label = { Text(StrTillPay.approvalOptional) }, singleLine = true,
            modifier = Modifier.weight(1f).onPreviewKeyEvent { e -> if (e.keyDown(Key.Enter, Key.NumPadEnter)) { enter(); true } else false },
          )
        }
      }
      TillMethods.Kind.CREDIT -> {
        val rest = "${money(PayFlow.major(PayStep.dueMinor(state, total)))} ؋"
        Text(if (customerName != null) StrTillPay.toAccountLine(rest, customerName) else StrTillPay.creditPickFirst, style = MaterialTheme.typography.bodyLarge, color = Shop.colors.text)
      }
      TillMethods.Kind.HESABPAY -> Unit
      else -> {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          MethodPicker(entry.method, state.methods().filter { TillMethods.kindOf(it) != TillMethods.Kind.CARD }, target, Modifier.weight(1.2f)) { entry.method = it }
          OutlinedTextField(
            value = entry.ref, onValueChange = { entry.ref = it }, label = { Text(StrPos.reference) }, singleLine = true,
            modifier = Modifier.weight(1f).onPreviewKeyEvent { e -> if (e.keyDown(Key.Enter, Key.NumPadEnter)) { enter(); true } else false },
          )
          OutlinedTextField(
            value = entry.amount, onValueChange = { entry.amount = numericInput(it) }, label = { Text(StrPos.amount) }, singleLine = true,
            modifier = Modifier.weight(1f).onPreviewKeyEvent { e -> if (e.keyDown(Key.Enter, Key.NumPadEnter)) { enter(); true } else false },
          )
        }
      }
    }
  }
}
