package ir.vil3ntec.tohid.ui.components.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrLayaway
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.pos.Layaway
import ir.vil3ntec.tohid.core.pos.SpecialOrder
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.DebtEntry
import ir.vil3ntec.tohid.data.LayawayPref
import ir.vil3ntec.tohid.data.Layaways
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.print.HwPages
import ir.vil3ntec.tohid.print.PrintSpooler
import ir.vil3ntec.tohid.print.PrinterRegistry
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 *  POS-27 — فروشِ امانی با پیش‌پرداخت و جدولِ قسط در صندوقِ گوشی و کامپیوتر (یک خط کنارِ `GiftCardBar` در `Sale`،
 *  `SaleLegacy` و `DeskSale`). فقط با تنظیمِ همین دستگاه (`LayawayPref`، پیش‌فرض خاموش ⇒ هیچ). سبد و مشتری همان سبد و
 *  مشتریِ فاکتورِ صندوق ([cart]). هر ثبت فقط با `LayawayPref.record` روی دفترِ همین لحظهٔ دیسک و کلیدی که از شناسهٔ سند
 *  ساخته می‌شود (`Layaways.payKey`/`deliverKey`/`cancelKey`)؛ قاعده‌ها در `Layaways` و هستهٔ `Layaway`. تحویل فروشِ
 *  عادیِ صندوق است و چاپِ خودکارِ فاکتور همان راهِ صندوق؛ جدول روی چاپگرِ فاکتور از صفِ چاپ.
 */
@Composable
fun LayawayBar(d: ShopData, store: ShopStore, cart: CartStore, toast: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { LayawayPref.on(context) }
  if (!on) return
  val lines by cart.lines.collectAsState()
  val debtorId by cart.debtorId.collectAsState()
  val today = remember { ir.vil3ntec.tohid.todayIso() }
  val views = remember(d) { Layaways.all(d, today) }
  var making by remember { mutableStateOf(false) }
  var listing by remember { mutableStateOf(false) }

  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = {
      when {
        lines.isEmpty() -> toast(StrLayaway.emptyCart)
        debtorId == null -> toast(StrLayaway.pickCustomerFirst)
        else -> making = true
      }
    }) { Text(StrLayaway.newLayaway) }
    TextButton(onClick = { listing = true }) { Text(StrLayaway.list(views.count { it.active }, views.count { it.late })) }
  }

  val who = debtorId
  if (making && who != null) LayawayNewDialog(d, store, cart, who, lines, toast) { making = false }
  if (listing) LayawayListDialog(store, views, toast) { listing = false }
}

/** پروندهٔ مشتری (`DebtorsLegacy`): امانی‌های همین مشتری با نشانِ «دیر» و همان کارها — فقط با همان تنظیم */
@Composable
fun LayawayCustomerPanel(d: ShopData, store: ShopStore?, debtorId: String, toast: (String) -> Unit) {
  val context = LocalContext.current
  val on = remember { LayawayPref.on(context) }
  if (!on) return
  val today = remember { ir.vil3ntec.tohid.todayIso() }
  val views = remember(d, debtorId) { Layaways.ofCustomer(d, debtorId, today) }
  if (views.isEmpty()) return
  var listing by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth()) {
    Spacer(Modifier.height(12.dp))
    Text(StrLayaway.customerTitle(views.count { it.active }), style = MaterialTheme.typography.titleSmall)
    views.filter { it.active }.forEach { v -> LayawaySummary(v) }
    if (store != null) TextButton(onClick = { listing = true }) { Text(StrLayaway.list(views.count { it.active }, views.count { it.late })) }
  }
  if (listing && store != null) LayawayListDialog(store, views, toast) { listing = false }
}

/** کدِ ردِ `Layaways`/هسته ⇐ پیامِ صفحه */
internal fun layawayProblem(code: String, detail: String?): String = when (code) {
  "NO_CUSTOMER" -> StrLayaway.errNoCustomer
  "EMPTY" -> StrLayaway.errEmpty
  "TOO_MANY" -> StrLayaway.errTooMany
  "BAD_QTY" -> StrLayaway.errQty
  "BAD_TOTAL" -> StrLayaway.errTotal
  "BAD_DEPOSIT" -> StrLayaway.errDeposit
  "NOT_WHOLE" -> StrLayaway.errWhole
  "DEPOSIT_OVER_TOTAL" -> StrLayaway.errDepositOver
  "BAD_COUNT" -> StrLayaway.errCount
  "BAD_EVERY" -> StrLayaway.errEvery
  "BAD_DUE", "DUE_PAST", "DUE_TOO_FAR" -> StrLayaway.errDue
  "PART_TOO_SMALL" -> StrLayaway.errPartSmall
  "NO_PRODUCT" -> StrLayaway.errNoProduct
  "NOT_FOUND", "NOT_ALLOWED", "NOT_LAYAWAY" -> StrLayaway.errState
  "REVIEW" -> StrLayaway.errReview
  "STALE" -> StrLayaway.errStale
  "BAD_AMOUNT" -> StrLayaway.errAmount
  "OVER_REMAINING" -> StrLayaway.errOver
  "SETTLED" -> StrLayaway.errSettled
  "NOT_SETTLED" -> StrLayaway.errNotSettled
  "BAD_REASON" -> StrLayaway.errReason
  "SALE" -> detail ?: StrLayaway.errNotSaved
  "TOTAL_MISMATCH" -> StrLayaway.errMismatch
  "UNREAD" -> DebtEntry.NOT_REREAD
  else -> StrLayaway.errNotSaved
}

/** پاسخِ هر ثبت: رد ⇒ پیامش؛ «ثبت نشد» ِ قفل/دیسک ⇒ هیچ (پیام از `AppRoot`)؛ ثبت ⇒ [onOk] */
private fun LayawayPref.Saved.show(toast: (String) -> Unit, onOk: (LayawayPref.Saved.Ok) -> Unit) {
  when (this) {
    is LayawayPref.Saved.Bad -> toast(layawayProblem(code, detail))
    LayawayPref.Saved.NotSaved -> Unit
    is LayawayPref.Saved.Ok -> onOk(this)
  }
}

private fun wholeAfghani(text: String): Long? = text.toLongOrNull()?.takeIf { it <= Layaway.MAX_MINOR / Layaway.WHOLE }?.let { it * Layaway.WHOLE }

private fun digits(raw: String, max: Int = 11): String = ir.vil3ntec.tohid.numericInput(raw).filter(Char::isDigit).take(max)

private fun afn(m: Long): String = money(PayFlow.major(m))

private fun me(context: android.content.Context): String = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("")

@Composable
private fun LayawayNewDialog(d: ShopData, store: ShopStore, cart: CartStore, debtorId: String, lines: List<SalesEngine.CartLine>, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val by = remember { me(context) }
  //  شناسهٔ سند — دو ضربه یک امانی
  val key = remember { newId() }
  val today = remember { ir.vil3ntec.tohid.todayIso() }
  var deposit by remember { mutableStateOf("") }
  var count by remember { mutableStateOf("3") }
  var every by remember { mutableStateOf("30") }
  var firstIn by remember { mutableStateOf("30") }
  var busy by remember { mutableStateOf(false) }
  val order = Layaways.NewLayaway(
    debtorId = debtorId, cart = lines,
    depositMinor = if (deposit.isEmpty()) 0L else wholeAfghani(deposit),
    count = count.toIntOrNull(), everyDays = every.toIntOrNull(),
    firstDue = firstIn.toIntOrNull()?.takeIf { it in 0..Layaway.MAX_FIRST_DAYS }?.let { CivilDays.addDays(today, it) }.orEmpty(),
  )
  val (plan, problem) = remember(order, d) { Layaways.plan(d, order, today) }
  val total = remember(lines, d) { Layaways.linesOf(d, lines)?.let(Layaways::linesTotal) ?: 0L }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrLayaway.newLayaway) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(StrLayaway.customer(d.debtors.find { it.id == debtorId }?.name.orEmpty()), style = MaterialTheme.typography.bodyMedium)
        Text(StrLayaway.cartTotal(lines.size, afn(total)), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
          value = deposit, onValueChange = { deposit = digits(it) }, label = { Text(StrLayaway.deposit) },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = count, onValueChange = { count = digits(it, 2) }, label = { Text(StrLayaway.count) },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = every, onValueChange = { every = digits(it, 2) }, label = { Text(StrLayaway.every) },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = firstIn, onValueChange = { firstIn = digits(it, 3) }, label = { Text(StrLayaway.firstIn) },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        if (problem != null) Text(layawayProblem(problem, null), style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
        else {
          Text(StrLayaway.scheduleTitle, style = MaterialTheme.typography.titleSmall)
          plan.forEach { Text(StrLayaway.dueLine(it.no, displayDate(it.due), afn(it.amountMinor)), style = MaterialTheme.typography.bodySmall) }
        }
        Text(StrLayaway.cashNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      }
    },
    confirmButton = {
      TextButton(enabled = !busy && problem == null, onClick = {
        busy = true
        scope.launch {
          val now = System.currentTimeMillis()
          val expired = ir.vil3ntec.tohid.data.ShopPolicy.blockExpired(context)
          LayawayPref.record(store, key, Layaways::hasRow) { cur, k -> Layaways.create(cur, k, order, today, now, by, expired) }
            .show(toast) { ok ->
              //  کالا کنار رفت ⇒ سبدِ صندوق خالی برای مشتریِ بعدی
              cart.clear()
              onDismiss()
              if (!ok.again) toast(StrLayaway.saved(afn(order.depositMinor ?: 0L)))
            }
          busy = false
        }
      }) { Text(StrLayaway.save) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrLayaway.cancel) } },
  )
}

private fun stateLabel(v: Layaways.View): String = when (v.status) {
  Layaways.Status.OPEN -> if (v.paidUp) StrLayaway.statePaidUp else StrLayaway.stateOpen
  Layaways.Status.DELIVERED -> StrLayaway.stateDelivered
  Layaways.Status.CANCELLED -> StrLayaway.stateCancelled
}

/** سه خطِ هر امانی: مشتری، مبلغ‌ها، وضعیت با نشانِ «دیر» و «بازبینی» */
@Composable
private fun LayawaySummary(v: Layaways.View) {
  Text(StrLayaway.row(v.customer, v.lines.size), style = MaterialTheme.typography.bodyMedium)
  Text(StrLayaway.amounts(afn(v.totalMinor), afn(v.heldMinor), afn(v.progress.remainingMinor)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
  val next = v.progress.next
  Text(
    listOfNotNull(
      stateLabel(v),
      next?.takeIf { v.active }?.let { StrLayaway.nextDue(displayDate(it.due), afn(it.amountMinor - it.paidMinor)) },
      StrLayaway.late(afn(v.progress.lateMinor)).takeIf { v.late },
      StrLayaway.review.takeIf { v.review },
    ).joinToString(" · "),
    style = MaterialTheme.typography.labelSmall,
    color = if (v.late || v.review) Shop.colors.danger else Shop.colors.muted,
  )
}

/** سقفِ ردیف‌های بستهٔ فهرست (بازها همه می‌آیند) */
private const val CLOSED_SHOWN = 30

@Composable
private fun LayawayListDialog(store: ShopStore, views: List<Layaways.View>, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val by = remember { me(context) }
  val today = remember { ir.vil3ntec.tohid.todayIso() }
  var paying by remember { mutableStateOf<Layaways.View?>(null) }
  var delivering by remember { mutableStateOf<Layaways.View?>(null) }
  var cancelling by remember { mutableStateOf<Layaways.View?>(null) }
  val shown = remember(views) { views.filter { it.active } + views.filter { !it.active }.take(CLOSED_SHOWN) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(StrLayaway.list(views.count { it.active }, views.count { it.late })) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (shown.isEmpty()) Text(StrLayaway.empty, color = Shop.colors.muted)
        shown.forEach { v ->
          LayawaySummary(v)
          Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (v.active && !v.paidUp) TextButton(onClick = { paying = v }) { Text(StrLayaway.pay) }
            if (v.active && v.paidUp) TextButton(onClick = { delivering = v }) { Text(StrLayaway.deliver) }
            TextButton(onClick = { scope.launch { toast(printLayaway(context, v)) } }) { Text(StrLayaway.print) }
            if (v.active) TextButton(onClick = { cancelling = v }) { Text(StrLayaway.cancelLayaway, color = Shop.colors.danger) }
          }
          HorizontalDivider(color = Shop.colors.fieldBorder.copy(alpha = 0.35f))
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(StrLayaway.close) } },
  )

  paying?.let { v -> LayawayPayDialog(store, v, by, today, toast) { paying = null } }
  delivering?.let { v -> LayawayDeliverDialog(store, v, by, today, toast) { delivering = null } }
  cancelling?.let { v -> LayawayCancelDialog(store, v, by, today, toast) { cancelling = null } }
}

@Composable
private fun LayawayPayDialog(store: ShopStore, v: Layaways.View, by: String, today: String, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val scope = rememberCoroutineScope()
  //  شناسهٔ رسید از سند و شمارهٔ پرداخت — دو ضربه یا تکرار پس از قطع یک رسید
  val key = remember(v.id) { Layaways.payKey(v) }
  val next = v.progress.next
  var amount by remember { mutableStateOf(next?.let { ((it.amountMinor - it.paidMinor) / Layaway.WHOLE).toString() }.orEmpty()) }
  var busy by remember { mutableStateOf(false) }
  val minor = wholeAfghani(amount)
  val problem = minor?.let { Layaway.payProblem(v.progress, it) } ?: "BAD_AMOUNT"
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrLayaway.payTitle(v.payCount + 1)) },
    text = {
      Column {
        Text(StrLayaway.row(v.customer, v.lines.size), style = MaterialTheme.typography.bodyMedium)
        Text(StrLayaway.remaining(afn(v.progress.remainingMinor)), style = MaterialTheme.typography.titleSmall)
        if (next != null) Text(StrLayaway.nextDue(displayDate(next.due), afn(next.amountMinor - next.paidMinor)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        OutlinedTextField(
          value = amount, onValueChange = { amount = digits(it) }, label = { Text(StrLayaway.payAmount) },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        if (amount.isNotEmpty() && problem != null) Text(layawayProblem(problem, null), style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
      }
    },
    confirmButton = {
      TextButton(enabled = !busy && problem == null && minor != null, onClick = {
        val m = minor ?: return@TextButton
        busy = true
        scope.launch {
          val now = System.currentTimeMillis()
          LayawayPref.record(store, key, Layaways.hasPayment(v.id, m)) { cur, k -> Layaways.pay(cur, k, v.id, m, today, now, by) }
            .show(toast) { ok ->
              onDismiss()
              toast(if (ok.again) StrLayaway.payAgain else StrLayaway.paid(afn(m), afn(v.progress.remainingMinor - m)))
            }
          busy = false
        }
      }) { Text(StrLayaway.payConfirm) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrLayaway.cancel) } },
  )
}

@Composable
private fun LayawayDeliverDialog(store: ShopStore, v: Layaways.View, by: String, today: String, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  //  شناسهٔ فاکتورِ تحویل از سند — دو ضربه یا دو دستگاه یک فروش
  val key = remember(v.id) { Layaways.deliverKey(v) }
  var busy by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrLayaway.deliverTitle) },
    text = {
      Column {
        Text(StrLayaway.row(v.customer, v.lines.size), style = MaterialTheme.typography.bodyMedium)
        v.lines.forEach { l -> Text(l.name + " × " + qty(l.qtyMilli / 1000.0), style = MaterialTheme.typography.bodySmall) }
        Text(StrLayaway.slipTotal(afn(v.totalMinor)), style = MaterialTheme.typography.titleSmall)
        Text(StrLayaway.deliverBody, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
          val now = System.currentTimeMillis()
          val series = ir.vil3ntec.tohid.sync.DocSeries.current(context)
          val expired = ir.vil3ntec.tohid.data.ShopPolicy.blockExpired(context)
          LayawayPref.record(store, key, Layaways::hasSale) { cur, k ->
            Layaways.deliver(cur, k, v.id, today, now, by, series, expired, ::newId)
          }.show(toast) { ok ->
            onDismiss()
            val data = ok.data
            val sale = data?.sales?.firstOrNull { it.id == key }
            if (!ok.again) toast(StrLayaway.delivered(sale?.invoiceLabel() ?: ""))
            //  چاپِ خودکارِ همان فروشِ صندوق؛ پس از نوشتن و جدا از آن (چاپگرِ خاموش ثبت را نگه نمی‌دارد)
            if (data != null && sale != null) runCatching { PrintSpooler.afterSale(context.applicationContext, data, sale.id) }
          }
          busy = false
        }
      }) { Text(StrLayaway.deliverConfirm) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrLayaway.cancel) } },
  )
}

@Composable
private fun LayawayCancelDialog(store: ShopStore, v: Layaways.View, by: String, today: String, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val key = remember(v.id) { Layaways.cancelKey(v) }
  val allowed = remember { ir.vil3ntec.tohid.data.ShopRole.canCancelSale(context) }
  var reason by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrLayaway.cancelTitle) },
    text = {
      Column {
        Text(StrLayaway.row(v.customer, v.lines.size), style = MaterialTheme.typography.bodyMedium)
        if (v.heldMinor > 0) Text(StrLayaway.refund(afn(v.heldMinor)), style = MaterialTheme.typography.titleSmall)
        if (!allowed) Text(StrLayaway.noCancelRight, color = Shop.colors.danger, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
          value = reason, onValueChange = { reason = it.replace('\n', ' ').take(SpecialOrder.REASON_MAX) },
          label = { Text(StrLayaway.reason) }, singleLine = true, enabled = allowed, modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(enabled = allowed && !busy, onClick = {
        busy = true
        scope.launch {
          val now = System.currentTimeMillis()
          LayawayPref.record(store, key, Layaways::hasRow) { cur, k -> Layaways.cancel(cur, k, v.id, reason, today, now, by) }
            .show(toast) { ok ->
              onDismiss()
              if (!ok.again) toast(StrLayaway.cancelled(afn(v.heldMinor)))
            }
          busy = false
        }
      }) { Text(StrLayaway.cancelConfirm, color = Shop.colors.danger) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrLayaway.cancel) } },
  )
}

/** برگهٔ جدول و رسید (روی چاپگرِ فاکتور، از صفِ چاپ — همان راهِ برگهٔ پیش‌فاکتور): ردیف‌ها، جمع، پرداختی، مانده و هر قسط با وضعیتش */
private suspend fun printLayaway(context: android.content.Context, v: Layaways.View): String {
  val printer = PrinterRegistry.defaultSaved(context) ?: return StrLayaway.noPrinter
  val title = StrLayaway.slipTitle(displayDate(v.day))
  val bytes = withContext(Dispatchers.IO) {
    runCatching {
      val p = printer.profile()
      val rows = v.lines.map { l ->
        val q = l.qtyMilli / 1000.0
        HwPages.reportLine(StrLayaway.slipLine(l.name, qty(q), afn(l.unitMinor), money(SalesEngine.lineTotal(PayFlow.major(l.unitMinor), q))))
      }
      val dues = v.progress.lines.map { x ->
        val state = when (x.state) {
          Layaway.State.PAID -> StrLayaway.duePaid
          Layaway.State.PARTIAL -> StrLayaway.duePartial
          Layaway.State.DUE -> StrLayaway.dueDue
          Layaway.State.LATE -> StrLayaway.dueLate
        }
        HwPages.reportLine(StrLayaway.slipDue(x.no, displayDate(x.due), afn(x.amountMinor), state), strong = x.state == Layaway.State.LATE)
      }
      val lines = listOf(HwPages.Line(title, HwPages.Style.TITLE)) +
        HwPages.reportLine(StrLayaway.customer(v.customer)) + rows +
        HwPages.reportLine(StrLayaway.slipTotal(afn(v.totalMinor)), strong = true) +
        HwPages.reportLine(StrLayaway.slipPaid(afn(v.heldMinor))) +
        HwPages.reportLine(StrLayaway.remaining(afn(v.progress.remainingMinor)), strong = true) +
        HwPages.reportLine(StrLayaway.scheduleTitle, strong = true) + dues +
        HwPages.Line(StrLayaway.slipFooter, HwPages.Style.SMALL)
      EscPos.document(HwPages.text(context, p.paperDots, lines), p)
    }
  }.getOrElse { return StrLayaway.notPrinted(it.message ?: Str.unknownReason) }
  return PrintSpooler.submitAndWait(context, PrintQueue.Kind.REPORT, printer.link, title, bytes, done = StrLayaway.printed)
}

/** بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) — یک خط در `SettingsLegacy` کنارِ تنظیم‌های صندوق */
@Composable
fun LayawaySettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(LayawayPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.Schedule,
    title = StrLayaway.settingsTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrLayaway.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; LayawayPref.set(context, on) },
    )
  }
}
