package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.CashTenderField
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.OrderAmendCard
import ir.vil3ntec.tohid.ui.screens.OrderAmendEntry
import ir.vil3ntec.tohid.ui.screens.OrderDeliveryPrefsSection
import ir.vil3ntec.tohid.ui.screens.OrderFeedbackEntry
import ir.vil3ntec.tohid.ui.screens.OrderLineExtras
import ir.vil3ntec.tohid.ui.screens.OrderNoticesSection
import ir.vil3ntec.tohid.ui.screens.OrderPickButton
import ir.vil3ntec.tohid.ui.screens.OrderPickHost
import ir.vil3ntec.tohid.ui.screens.OrderSubstitutionNote
import ir.vil3ntec.tohid.ui.screens.OrderSubstitutionPicker
import ir.vil3ntec.tohid.ui.screens.PickDeskEntry
import ir.vil3ntec.tohid.ui.screens.RefundDetailNotices
import ir.vil3ntec.tohid.ui.screens.RefundListNotices
import ir.vil3ntec.tohid.ui.screens.RestockDialog
import ir.vil3ntec.tohid.ui.screens.orderPickActions
import ir.vil3ntec.tohid.ui.screens.postRefundNotice
import ir.vil3ntec.tohid.ui.screens.restockNotice
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import kotlinx.coroutines.launch

/**
 *  مرزِ پلتفرمِ کارهای سفارش که در صفحهٔ «سفارش‌ها» ِ رابطِ قدیم بودند و پس از پاک شدنش بی‌راه ماندند
 *  (`V3ReachabilityTest`) — همان اجزای `ui/screens` با همان پرچم‌های سرور (هر کدام بی پرچمِ خودش هیچ نمی‌کشد) و
 *  همان نوشتن‌ها (`OrdersLive`، `OrdersRepository`)؛ تازه شدن با `OrdersHub`.
 */

private fun buzz(context: android.content.Context): (Boolean) -> Unit = { ok -> if (ok) Haptics.success(context) else Haptics.confirm(context) }

/** سرِ صفِ سفارش‌ها: قفسه و دستهٔ جمع‌آوری (PCK-10/12)، گزارش‌ها و نظرها (NTF-07/09) و هشدارهای بازپرداخت (RET-11) */
@Composable
internal fun RouteScope.OrderQueueExtras(cfg: OrdersConfigDto?, onChanged: () -> Unit, onOpen: (String) -> Unit) {
  val c = cfg ?: return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val api = remember(context) { Backend.api(context) }
  val ledger by store.data.collectAsState()
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
    PickDeskEntry(c, api, buzz(context), onChanged)
    OrderFeedbackEntry(api, c.permissions.manage, OrdersLive.rev.value)
  }
  RefundListNotices(ledger, c, onOpen = onOpen, onRetry = { scope.launch { OrdersLive.reconcile(context, store, force = true) } })
}

/** زیرِ سرِ جزئیاتِ سفارش */
@Composable
internal fun RouteScope.OrderDetailExtras(o: OrderDto, cfg: OrdersConfigDto?) {
  val c = cfg ?: return
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val repo = remember(context) { OrdersHub.repo(context) }
  val ledger by store.data.collectAsState()
  var restocking by remember(o.id) { mutableStateOf(false) }
  var busy by remember(o.id) { mutableStateOf(false) }
  fun refreshed(message: String) {
    toasts.show(message)
    scope.launch { OrdersHub.fetch(context, o.id); OrdersHub.changed.value++ }
  }
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      //  «جمع‌آوری» و «بسته‌بندی» فقط از برگه (PCK)
      if (orderPickActions(o, c).second) OrderPickButton(o, busy) { nav.open(OnlinePaths.pick(o.id)) }
      OrderAmendEntry(o, c, repo, ::refreshed)
    }
    OrderAmendCard(o, c, repo, OrdersModel::stamp, ::refreshed)
    RefundDetailNotices(
      ledger, c, o,
      onRestock = { restocking = true },
      onRetry = {
        if (!busy) scope.launch { busy = true; postRefundNotice(snackbar, o) { OrdersLive.postById(context, store, o.id) }; busy = false }
      },
    )
    OrderDeliveryPrefsSection(o, c, repo) { fresh -> OrdersHub.put(fresh); OrdersHub.changed.value++ }
    OrderSubstitutionNote(o.substitution)
    o.lines.filter { it.note.isNotEmpty() || it.substitution.isNotEmpty() }.forEach { l ->
      Column(Modifier.padding(top = 4.dp)) {
        androidx.compose.material3.Text(l.name, style = ir.vil3ntec.tohid.ui.v3.V3Type.footnote, color = ir.vil3ntec.tohid.ui.theme.PhoneV3.colors.ink)
        OrderLineExtras(l)
      }
    }
    OrderNoticesSection(o, c, repo, OrdersModel::stamp) { toasts.show(it) }
    //  TEN-03: «باطل کردنِ لینکِ پیگیری» (فقط با کلیدِ سرور و فقط سفارشِ ویترین)
    OrderTrackLinkSection(o, c, repo) { toasts.show(it) }
  }
  if (restocking) {
    RestockDialog(o, OrderFulfillment.restockChoices(ledger, o.id), busy, onDismiss = { restocking = false }) { chosen ->
      if (!busy) scope.launch {
        busy = true
        restockNotice(snackbar, chosen) { OrdersLive.restock(context, store, o.id, o.orderNo, chosen) }
        busy = false
        restocking = false
      }
    }
  }
}

/** `orders/{id}/pick` — برگهٔ جمع‌آوری و بسته‌بندی (PCK) روی جزئیات */
@Composable
internal fun RouteScope.OrderPickRoute(id: String) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val o = OrdersHub.known[id]
  if (o == null) {
    LaunchedEffect(id) { if (!OrdersHub.fetch(context, id).isSuccess) back() }
    return
  }
  val api = remember(context) { Backend.api(context) }
  OrderPickHost(o, api, buzz(context), onClose = { back() }) {
    OrdersHub.changed.value++
    scope.launch { OrdersHub.fetch(context, id); OrdersLive.pollSummary(context) }
  }
}

/** فرمِ سفارشِ دستی: پولِ نقدِ مشتری (CHK-14) و «اگر کالایی نبود» (CHK-15) — فقط با پرچمِ همان در `/config` */
@Composable
internal fun NewOrderExtras(cfg: OrdersConfigDto?, form: OrderDraft.Form, onForm: (OrderDraft.Form) -> Unit) {
  val c = cfg ?: return
  CashTenderField(c.deliveryPrefs && form.fulfillment == "delivery", form.cashTender) { onForm(form.copy(cashTender = it)) }
  OrderSubstitutionPicker(c.substitution, form.substitution) { onForm(form.copy(substitution = it)) }
}
