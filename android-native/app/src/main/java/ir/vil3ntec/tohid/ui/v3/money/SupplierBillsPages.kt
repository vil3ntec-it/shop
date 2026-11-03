package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSupplierBills
import ir.vil3ntec.tohid.data.PoBillDto
import ir.vil3ntec.tohid.data.PoConfigDto
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.platform.rememberPurchaseOrderHost
import ir.vil3ntec.tohid.ui.screens.PoBill
import ir.vil3ntec.tohid.ui.screens.PoBillVoidDialog
import ir.vil3ntec.tohid.ui.screens.PoBillsList
import ir.vil3ntec.tohid.ui.screens.PoMatchLines
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import kotlinx.coroutines.launch

/*
 *  PUR-14 روی گوشیِ نسخهٔ ۳ — «خرید ⇐ فاکتورهای تأمین‌کننده» (پیش‌تر فقط در پنجرهٔ «سفارش‌های خرید» ِ کامپیوتر):
 *    `purchasing/bills`             سفارش‌هایی که فاکتور می‌گیرند (`PurchaseOrderBook.billable`)
 *    `purchasing/bills/{id}`        فاکتورهای همان سفارش با «باطل» و تطبیقِ سه‌طرفه
 *    `purchasing/bills/{id}/new`    فرمِ «فاکتورِ تأمین‌کننده» (همان `PoBill` ِ کامپیوتر، کلیدِ یکتای هر فرم)
 *  منطق و پنجره‌ها مشترک‌اند (`data/PurchaseOrderBook`، `ui/screens/PurchaseBills.kt`)؛ این‌جا فقط سیم‌کشیِ صفحه است.
 *  ⛔ پرچمِ خاموشِ `shop.purchase_orders` ⇒ درِ زبانهٔ خرید پنهان و صفحه «روشن نیست». ⛔ فقط مدیر و صاحب
 *  (`permissions.manage` = `stock.adjust` ِ سرور، که خودش هم دوباره می‌سنجد) — شاگرد صفحه را بسته می‌بیند.
 */

/** درِ «فاکتورهای تأمین‌کننده» در زبانهٔ خرید: فقط با پرچمِ روشن و برای مدیر/صاحب (`GET /config`)؛ بی اینترنت پنهان */
@Composable
internal fun rememberSupplierBillsDoor(): Boolean {
  val host = rememberPurchaseOrderHost()
  var cfg by remember { mutableStateOf<PoConfigDto?>(null) }
  LaunchedEffect(host) { cfg = host.repo.config().valueOrNull() }
  return cfg?.let { it.enabled && it.permissions.manage } == true
}

/** حالتِ مشترکِ سه صفحه پیش از دیدنِ داده: خواندن، خطا، پرچمِ خاموش، نه‌مدیر */
@Composable
private fun GateState(cfg: PoConfigDto?, failed: Boolean, onRetry: () -> Unit): Boolean {
  val c = PhoneV3.colors
  when {
    cfg == null && failed -> V3EmptyState(Icons.Outlined.CloudOff, StrSupplierBills.failed, action = StrSupplierBills.retry, onAction = onRetry)
    cfg == null -> Text(StrSupplierBills.loading, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 16.dp))
    !cfg.enabled -> V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, StrSupplierBills.off)
    !cfg.permissions.manage -> V3EmptyState(Icons.Outlined.Lock, StrSupplierBills.managerOnly)
    else -> return true
  }
  return false
}

/** `purchasing/bills` */
@Composable
internal fun RouteScope.SupplierBillsRoute() {
  val repo = rememberPurchaseOrderHost().repo
  val scope = rememberCoroutineScope()
  var tick by remember { mutableStateOf(0) }
  var cfg by remember { mutableStateOf<PoConfigDto?>(null) }
  var failed by remember { mutableStateOf(false) }
  var orders by remember { mutableStateOf<List<PurchaseOrderDto>?>(null) }
  var next by remember { mutableStateOf<String?>(null) }
  var more by remember { mutableStateOf(false) }
  LaunchedEffect(tick) {
    failed = false
    val got = repo.config().valueOrNull()
    cfg = got
    if (got == null) { failed = true; return@LaunchedEffect }
    if (!got.enabled || !got.permissions.manage) return@LaunchedEffect
    val page = repo.list().valueOrNull()
    if (page == null) failed = true else { orders = page.orders; next = page.next }
  }
  val c = PhoneV3.colors
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    Text(StrSupplierBills.note, style = V3Type.footnote, color = c.inkMuted)
    val open = GateState(cfg, failed) { tick++ }
    val list = orders?.let(PurchaseOrderBook::billable)
    when {
      !open -> Unit
      list == null && failed -> V3EmptyState(Icons.Outlined.CloudOff, StrSupplierBills.failed, action = StrSupplierBills.retry, onAction = { tick++ })
      list == null -> Text(StrSupplierBills.loading, style = V3Type.callout, color = c.inkMuted, modifier = Modifier.padding(top = 16.dp))
      list.isEmpty() && next == null -> V3EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, StrSupplierBills.none)
      else -> V3Section(null) {
        list.forEachIndexed { i, o ->
          if (i > 0) V3Divider()
          V3ListRow(
            StrSupplierBills.orderTitle(o.no, o.supplierName),
            subtitle = StrSupplierBills.line(PurchaseOrderBook.label(PurchaseOrderBook.status(o)), StrSupplierBills.bills(o.billCount)),
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            onClick = { nav.open(MoneyPaths.supplierBill(o.id)) },
          )
        }
      }
    }
    val cursor = next
    if (open && cursor != null) {
      V3Button(
        StrSupplierBills.more,
        {
          more = true
          scope.launch {
            repo.list(cursor = cursor).valueOrNull()?.let { p -> orders = orders.orEmpty() + p.orders; next = p.next }
            more = false
          }
        },
        Modifier.fillMaxWidth().padding(top = 12.dp), kind = V3ButtonKind.SECONDARY, loading = more,
      )
    }
  }
}

/** `purchasing/bills/{id}` */
@Composable
internal fun RouteScope.SupplierBillRoute(id: String) {
  val repo = rememberPurchaseOrderHost().repo
  val scope = rememberCoroutineScope()
  var tick by remember { mutableStateOf(0) }
  var cfg by remember { mutableStateOf<PoConfigDto?>(null) }
  var failed by remember { mutableStateOf(false) }
  var order by remember(id) { mutableStateOf<PurchaseOrderDto?>(null) }
  var busy by remember { mutableStateOf(false) }
  var voidAsk by remember { mutableStateOf<PoBillDto?>(null) }
  LaunchedEffect(id, tick) {
    failed = false
    val got = repo.config().valueOrNull()
    cfg = got
    if (got == null) { failed = true; return@LaunchedEffect }
    if (!got.enabled || !got.permissions.manage) return@LaunchedEffect
    val r = repo.get(id).valueOrNull()
    if (r == null) failed = true else order = r
  }
  val c = PhoneV3.colors
  val manage = cfg?.permissions?.manage == true
  Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
    val open = GateState(cfg, failed) { tick++ }
    val o = order
    when {
      !open -> Unit
      o == null && failed -> V3EmptyState(Icons.Outlined.CloudOff, StrSupplierBills.failed, action = StrSupplierBills.retry, onAction = { tick++ })
      o == null -> Text(StrSupplierBills.loading, style = V3Type.callout, color = c.inkMuted)
      else -> {
        Text(StrSupplierBills.orderTitle(o.no, o.supplierName), style = V3Type.title, color = c.ink)
        Text(
          StrSupplierBills.line(PurchaseOrderBook.label(PurchaseOrderBook.status(o)), StrSupplierBills.bills(o.billCount)),
          style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp),
        )
        Text(
          StrSupplierBills.totals(money(PurchaseOrderBook.asAfn(o.totals.orderedMinor)), money(PurchaseOrderBook.asAfn(o.totals.acceptedMinor))),
          style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 4.dp),
        )
        if (PurchaseOrderBook.canBill(o, manage)) {
          V3Button(
            StrSupplierBills.newBill,
            { nav.openForResult(MoneyPaths.newSupplierBill(o.id)) { done -> if (done != null) tick++ } },
            Modifier.fillMaxWidth().padding(top = 12.dp), icon = Icons.Outlined.Add, enabled = !busy,
          )
        }
        PoBillsList(o, manage, busy) { voidAsk = it }
        if (o.receipts.isNotEmpty() || PurchaseOrderBook.hasSupplierBills(o)) PoMatchLines(data, o)
      }
    }
  }
  val asked = voidAsk
  val shown = order
  if (asked != null && shown != null) {
    val b = asked
    val o = shown
    PoBillVoidDialog(b, busy, onDismiss = { voidAsk = null }) { why ->
      voidAsk = null
      busy = true
      scope.launch {
        val r = repo.voidBill(o.id, b.id, why)
        busy = false
        r.valueOrNull()?.let { order = it.order; toasts.show(StrSupplierBills.voided, V3Tone.SUCCESS) }
          ?: toasts.show(r.errorMessage() ?: StrSupplierBills.failedShort, V3Tone.ERROR)
      }
    }
  }
}

/** `purchasing/bills/{id}/new` — نتیجه (`nav.finish`) شناسهٔ سفارش است تا جزئیات دوباره بخواند */
@Composable
internal fun RouteScope.SupplierBillNewRoute(id: String) {
  val repo = rememberPurchaseOrderHost().repo
  var tick by remember { mutableStateOf(0) }
  var cfg by remember { mutableStateOf<PoConfigDto?>(null) }
  var failed by remember { mutableStateOf(false) }
  var order by remember(id) { mutableStateOf<PurchaseOrderDto?>(null) }
  LaunchedEffect(id, tick) {
    failed = false
    val got = repo.config().valueOrNull()
    cfg = got
    if (got == null) { failed = true; return@LaunchedEffect }
    if (!got.enabled || !got.permissions.manage) return@LaunchedEffect
    val r = repo.get(id).valueOrNull()
    if (r == null) failed = true else order = r
  }
  val c = PhoneV3.colors
  Box(Modifier.fillMaxSize().padding(16.dp)) {
    Column {
      val open = GateState(cfg, failed) { tick++ }
      val o = order
      when {
        !open -> Unit
        o == null && failed -> V3EmptyState(Icons.Outlined.CloudOff, StrSupplierBills.failed, action = StrSupplierBills.retry, onAction = { tick++ })
        o == null -> Text(StrSupplierBills.loading, style = V3Type.callout, color = c.inkMuted)
        //  سفارشی که دیگر فاکتور نمی‌گیرد (پیش‌نویس) ⇒ همان پیامِ «فقط پس از فرستادن» ِ سرور، بی فرم
        !PurchaseOrderBook.canBill(o, cfg?.permissions?.manage == true) -> V3EmptyState(Icons.Outlined.Lock, StrSupplierBills.notSent)
        else -> PoBill(data, repo, o, toast = { toasts.show(it, V3Tone.SUCCESS) }, onDone = { nav.finish(o.id) })
      }
    }
  }
}
