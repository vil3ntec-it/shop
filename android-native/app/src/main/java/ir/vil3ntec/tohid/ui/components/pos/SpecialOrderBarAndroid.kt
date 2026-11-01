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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.StrSpecialOrder
import ir.vil3ntec.tohid.core.pos.SpecialOrder
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.data.DebtEntry
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.SpecialOrderPref
import ir.vil3ntec.tohid.data.SpecialOrders
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.screens.newId
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  POS-26 — سفارشِ ویژه با پیش‌پرداخت در صندوقِ گوشی و کامپیوتر (یک خط کنارِ `QuoteBar` در `Sale` و `DeskSale`). فقط با
 *  تنظیمِ همین دستگاه (`SpecialOrderPref`، پیش‌فرض خاموش ⇒ هیچ). مشتری همان مشتریِ فاکتورِ صندوق است ([debtorId]).
 *  هر ثبت فقط با `SpecialOrderPref.record` روی دفترِ همین لحظهٔ دیسک و کلیدی که پنجره هنگامِ باز شدن می‌سازد؛ قاعده‌ها
 *  در `SpecialOrders` و هستهٔ `SpecialOrder`. تحویل فروشِ عادیِ صندوق است و چاپِ خودکارِ فاکتور همان راهِ صندوق.
 */
@Composable
fun SpecialOrderBar(d: ShopData, store: ShopStore, debtorId: String?, toast: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { SpecialOrderPref.on(context) }
  if (!on) return
  val views = remember(d) { SpecialOrders.all(d) }
  var making by remember { mutableStateOf(false) }
  var listing by remember { mutableStateOf(false) }

  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    OutlinedButton(onClick = { if (debtorId == null) toast(StrSpecialOrder.pickCustomerFirst) else making = true }) { Text(StrSpecialOrder.newOrder) }
    TextButton(onClick = { listing = true }) { Text(StrSpecialOrder.list(views.count { it.active })) }
  }

  if (making && debtorId != null) SpecialOrderNewDialog(d, store, debtorId, toast) { making = false }
  if (listing) SpecialOrderListDialog(d, store, views, toast) { listing = false }
}

/** کدِ ردِ `SpecialOrders`/هسته ⇐ پیامِ صفحه */
internal fun specialOrderProblem(code: String, detail: String?): String = when (code) {
  "NO_CUSTOMER" -> StrSpecialOrder.errNoCustomer
  "NO_ITEM" -> StrSpecialOrder.errNoItem
  "BAD_QTY" -> StrSpecialOrder.errQty
  "BAD_TOTAL" -> StrSpecialOrder.errTotal
  "BAD_DEPOSIT" -> StrSpecialOrder.errDeposit
  "NOT_WHOLE" -> StrSpecialOrder.errWhole
  "DEPOSIT_OVER_TOTAL" -> StrSpecialOrder.errDepositOver
  "BAD_DUE", "DUE_PAST" -> StrSpecialOrder.errDue
  "NO_PRODUCT" -> StrSpecialOrder.errNoProduct
  "NOT_FOUND", "NOT_ALLOWED" -> StrSpecialOrder.errState
  "BAD_REASON" -> StrSpecialOrder.errReason
  "SALE" -> detail ?: StrSpecialOrder.errNotSaved
  "TOTAL_MISMATCH" -> StrSpecialOrder.errMismatch
  "UNREAD" -> DebtEntry.NOT_REREAD
  else -> StrSpecialOrder.errNotSaved
}

/** پاسخِ هر ثبت: رد ⇒ پیامش؛ «ثبت نشد» ِ قفل/دیسک ⇒ هیچ (پیام از `AppRoot`)؛ ثبت ⇒ [onOk] (ضربهٔ دوم بی پیامِ دوم) */
private fun SpecialOrderPref.Saved.show(toast: (String) -> Unit, onOk: (SpecialOrderPref.Saved.Ok) -> Unit) {
  when (this) {
    is SpecialOrderPref.Saved.Bad -> toast(specialOrderProblem(code, detail))
    SpecialOrderPref.Saved.NotSaved -> Unit
    is SpecialOrderPref.Saved.Ok -> onOk(this)
  }
}

private fun afn(text: String): Long? = text.toLongOrNull()?.takeIf { it <= SpecialOrder.MAX_MINOR / SpecialOrder.WHOLE }?.let { it * SpecialOrder.WHOLE }

private fun digits(raw: String, max: Int = 11): String = ir.vil3ntec.tohid.numericInput(raw).filter(Char::isDigit).take(max)

@Composable
private fun SpecialOrderNewDialog(d: ShopData, store: ShopStore, debtorId: String, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  val key = remember { newId() }
  var item by remember { mutableStateOf("") }
  var productId by remember { mutableStateOf<String?>(null) }
  var qty by remember { mutableStateOf("1") }
  var total by remember { mutableStateOf("") }
  var deposit by remember { mutableStateOf("") }
  var days by remember { mutableStateOf("7") }
  var busy by remember { mutableStateOf(false) }
  val today = remember { ir.vil3ntec.tohid.todayIso() }
  val due = days.toIntOrNull()?.takeIf { it in 0..365 }?.let { CivilDays.addDays(today, it) }.orEmpty()
  val linked = productId?.let { id -> d.products.find { it.id == id } }
  val matches = remember(item, productId, d.products) {
    val q = item.trim()
    if (productId != null || q.length < 2) emptyList()
    else d.products.filter { it.active && (q in it.barcodes || TextNormalizer.matches(it.name, q)) }.take(5)
  }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrSpecialOrder.newOrder) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(StrSpecialOrder.customer(d.debtors.find { it.id == debtorId }?.name.orEmpty()), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(6.dp))
        if (linked != null) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(StrSpecialOrder.linked(linked.name), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.primary)
            TextButton(onClick = { productId = null }) { Text(StrSpecialOrder.unlink) }
          }
        } else {
          OutlinedTextField(
            value = item, onValueChange = { item = it.replace('\n', ' ').take(SpecialOrder.ITEM_MAX) },
            label = { Text(StrSpecialOrder.itemField) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
          )
          matches.forEach { p ->
            TextButton(onClick = {
              productId = p.id
              item = p.name
              val q = ir.vil3ntec.tohid.numericInput(qty).toDoubleOrNull() ?: 1.0
              if (total.isEmpty() && p.salePrice > 0) total = Math.round(p.salePrice * q).toString()
            }) { Text(p.name + " — " + money(p.salePrice) + " ؋") }
          }
        }
        OutlinedTextField(
          value = qty, onValueChange = { qty = ir.vil3ntec.tohid.numericInput(it).take(12) }, label = { Text(StrSpecialOrder.qty) },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = total, onValueChange = { total = digits(it) }, label = { Text(StrSpecialOrder.total) },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = deposit, onValueChange = { deposit = digits(it) }, label = { Text(StrSpecialOrder.deposit) },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = days, onValueChange = { days = digits(it, 3) }, label = { Text(StrSpecialOrder.days) },
          singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        if (due.isNotEmpty()) Text(StrSpecialOrder.dueOn(displayDate(due)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        Text(StrSpecialOrder.depositIsCash, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
      }
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = {
        val order = SpecialOrders.NewOrder(
          debtorId = debtorId, productId = productId, item = linked?.name ?: item,
          qtyMilli = ir.vil3ntec.tohid.numericInput(qty).toDoubleOrNull()?.takeIf { it > 0 && it < 1e7 }?.let { Math.round(it * 1000) },
          totalMinor = afn(total), depositMinor = if (deposit.isEmpty()) 0L else afn(deposit), due = due,
        )
        busy = true
        scope.launch {
          val now = System.currentTimeMillis()
          SpecialOrderPref.record(store, key, SpecialOrders::hasRow) { cur, k -> SpecialOrders.create(cur, k, order, today, now, me, ::newId) }
            .show(toast) { ok ->
              onDismiss()
              if (!ok.again) toast(StrSpecialOrder.saved(money(PayFlow.major(order.depositMinor ?: 0L))))
            }
          busy = false
        }
      }) { Text(StrSpecialOrder.save) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrSpecialOrder.cancel) } },
  )
}

private fun stateLabel(v: SpecialOrders.View): String = when (v.status) {
  SpecialOrder.Status.OPEN -> StrSpecialOrder.stateOpen
  SpecialOrder.Status.READY -> StrSpecialOrder.stateReady
  SpecialOrder.Status.DELIVERED -> StrSpecialOrder.stateDelivered
  SpecialOrder.Status.CANCELLED -> StrSpecialOrder.stateCancelled
}

/** سقفِ ردیف‌های بستهٔ فهرست (باز و آماده همه می‌آیند) */
private const val CLOSED_SHOWN = 30

@Composable
private fun SpecialOrderListDialog(d: ShopData, store: ShopStore, views: List<SpecialOrders.View>, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val me = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  val today = remember { ir.vil3ntec.tohid.todayIso() }
  var delivering by remember { mutableStateOf<SpecialOrders.View?>(null) }
  var cancelling by remember { mutableStateOf<SpecialOrders.View?>(null) }
  val shown = remember(views) { views.filter { it.active } + views.filter { !it.active }.take(CLOSED_SHOWN) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(StrSpecialOrder.list(views.count { it.active })) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (shown.isEmpty()) Text(StrSpecialOrder.empty, color = Shop.colors.muted)
        shown.forEach { v ->
          val late = SpecialOrder.overdue(v.status, v.due, today)
          val item = if (v.qtyMilli == 1000L) v.item else v.item + " × " + ir.vil3ntec.tohid.qty(v.qtyMilli / 1000.0)
          Text(StrSpecialOrder.row(v.customer, item), style = MaterialTheme.typography.bodyMedium)
          Text(
            StrSpecialOrder.amounts(money(PayFlow.major(v.totalMinor)), money(PayFlow.major(v.heldMinor))),
            style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted,
          )
          Text(
            listOfNotNull(
              StrSpecialOrder.dueOn(displayDate(v.due)), stateLabel(v),
              StrSpecialOrder.overdue.takeIf { late }, StrSpecialOrder.review.takeIf { v.review },
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = if (late || v.review) Shop.colors.danger else Shop.colors.muted,
          )
          if (v.active) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (v.status == SpecialOrder.Status.OPEN) TextButton(onClick = {
              val key = newId()
              scope.launch {
                val now = System.currentTimeMillis()
                SpecialOrderPref.record(store, key, SpecialOrders::hasRow) { cur, k -> SpecialOrders.ready(cur, k, v.id, today, now, me) }
                  .show(toast) { if (!it.again) toast(StrSpecialOrder.readyDone) }
              }
            }) { Text(StrSpecialOrder.markReady) }
            TextButton(onClick = { delivering = v }) { Text(StrSpecialOrder.deliver) }
            TextButton(onClick = { cancelling = v }) { Text(StrSpecialOrder.cancelOrder, color = Shop.colors.danger) }
          }
          HorizontalDivider(color = Shop.colors.fieldBorder.copy(alpha = 0.35f))
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(StrSpecialOrder.close) } },
  )

  delivering?.let { v -> SpecialOrderDeliverDialog(store, v, me, today, toast) { delivering = null } }
  cancelling?.let { v -> SpecialOrderCancelDialog(store, v, me, today, toast) { cancelling = null } }
}

@Composable
private fun SpecialOrderDeliverDialog(store: ShopStore, v: SpecialOrders.View, me: String, today: String, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  //  شناسهٔ فاکتورِ تحویل — دو ضربه یک فروش
  val key = remember(v.id) { newId() }
  val s = remember(v) { SpecialOrders.settle(v) }
  var busy by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrSpecialOrder.deliverTitle) },
    text = {
      Column {
        Text(StrSpecialOrder.row(v.customer, v.item), style = MaterialTheme.typography.bodyMedium)
        Text(StrSpecialOrder.deliverBody, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        Spacer(Modifier.height(6.dp))
        Text(StrSpecialOrder.fromDeposit(money(PayFlow.major(s.applyMinor))))
        Text(StrSpecialOrder.payNow(money(PayFlow.major(s.payNowMinor))), style = MaterialTheme.typography.titleMedium)
        if (s.leftMinor > 0) Text(StrSpecialOrder.leftOnAccount(money(PayFlow.major(s.leftMinor))), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
      }
    },
    confirmButton = {
      TextButton(enabled = !busy, onClick = {
        busy = true
        scope.launch {
          val now = System.currentTimeMillis()
          val series = ir.vil3ntec.tohid.sync.DocSeries.current(context)
          val expired = ir.vil3ntec.tohid.data.ShopPolicy.blockExpired(context)
          SpecialOrderPref.record(store, key, SpecialOrders::hasSale) { cur, k ->
            SpecialOrders.deliver(cur, k, v.id, today, now, me, series, expired, ::newId)
          }.show(toast) { ok ->
            onDismiss()
            val data = ok.data
            val sale = data?.sales?.firstOrNull { it.id == key }
            if (!ok.again) toast(StrSpecialOrder.delivered(sale?.invoiceLabel() ?: ""))
            //  چاپِ خودکارِ همان فروشِ صندوق؛ پس از نوشتن و جدا از آن (چاپگرِ خاموش ثبت را نگه نمی‌دارد)
            if (data != null && sale != null) runCatching { ir.vil3ntec.tohid.print.PrintSpooler.afterSale(context.applicationContext, data, sale.id) }
          }
          busy = false
        }
      }) { Text(StrSpecialOrder.deliverConfirm) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrSpecialOrder.cancel) } },
  )
}

@Composable
private fun SpecialOrderCancelDialog(store: ShopStore, v: SpecialOrders.View, me: String, today: String, toast: (String) -> Unit, onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val key = remember(v.id) { newId() }
  val allowed = remember { ir.vil3ntec.tohid.data.ShopRole.canCancelSale(context) }
  var reason by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(StrSpecialOrder.cancelTitle) },
    text = {
      Column {
        Text(StrSpecialOrder.row(v.customer, v.item), style = MaterialTheme.typography.bodyMedium)
        if (v.heldMinor > 0) Text(StrSpecialOrder.refund(money(PayFlow.major(v.heldMinor))), style = MaterialTheme.typography.titleSmall)
        if (!allowed) Text(StrSpecialOrder.noCancelRight, color = Shop.colors.danger, style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(
          value = reason, onValueChange = { reason = it.replace('\n', ' ').take(SpecialOrder.REASON_MAX) },
          label = { Text(StrSpecialOrder.reason) }, singleLine = true, enabled = allowed, modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(enabled = allowed && !busy, onClick = {
        busy = true
        scope.launch {
          val now = System.currentTimeMillis()
          SpecialOrderPref.record(store, key, SpecialOrders::hasRow) { cur, k ->
            SpecialOrders.cancel(cur, k, v.id, reason, today, now, me, ::newId)
          }.show(toast) { ok ->
            onDismiss()
            if (!ok.again) toast(StrSpecialOrder.cancelled(money(PayFlow.major(v.heldMinor))))
          }
          busy = false
        }
      }) { Text(StrSpecialOrder.cancelConfirm, color = Shop.colors.danger) }
    },
    dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text(StrSpecialOrder.cancel) } },
  )
}

/** بخشِ تنظیمات (همین دستگاه، پیش‌فرض خاموش) — یک خط در `SettingsLegacy` کنارِ تنظیم‌های صندوق */
@Composable
fun SpecialOrderSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(SpecialOrderPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.Inventory2,
    title = StrSpecialOrder.settingsTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrSpecialOrder.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; SpecialOrderPref.set(context, on) },
    )
  }
}
