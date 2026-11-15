package ir.vil3ntec.tohid.ui.v3.money

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.platform.rememberPurchaseOrderHost
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths

/*
 *  سیم‌کشیِ «خریدها» (e-rows-63b) — هیچ منطقِ دفتر این‌جا نیست؛ فقط مسیرها:
 *    `purchasing`                    زبانهٔ «خریدها» ([SuppliersScreen])
 *    `purchasing/purchases/{key}`    جزئیاتِ یک خرید ([PurchaseDetailScreen])؛ سفارشِ باز ⇐ `purchasing/orders/{id}`
 *    «پرداخت» ⇐ `purchasing/suppliers/{id}/pay?amount=&invoice=` (همان برگهٔ پرداختِ تأمین‌کننده با ماندهٔ همین خرید)
 *    «دریافتِ کالا» ⇐ `purchasing/orders/{id}/receive` (همان راهِ رسیدِ سفارش ⇐ دفتر، شناسه‌های قطعی)
 */

/** سفارش‌های بازِ سرور برای فهرستِ خریدها؛ پرچمِ خاموش یا بی اینترنت ⇒ خالی (فهرست همان دفتر است، بی پیامِ خطا) */
internal class OpenOrders(val orders: List<PurchaseOrderDto>, val perms: PoPermsDto?, val refresh: () -> Unit) {
  val canReceive: Boolean get() = perms?.receive == true
}

/** [door] = `rememberPurchaseOrdersDoor()` (پرچمِ روشن)؛ فقط آن‌گاه یک بار سفارش‌های باز خوانده می‌شوند */
@Composable
internal fun rememberOpenOrders(door: Boolean): OpenOrders {
  val repo = rememberPurchaseOrderHost().repo
  var orders by remember { mutableStateOf(emptyList<PurchaseOrderDto>()) }
  var perms by remember { mutableStateOf<PoPermsDto?>(null) }
  var tick by remember { mutableStateOf(0) }
  LaunchedEffect(door, tick) {
    if (!door) return@LaunchedEffect
    perms = repo.config().valueOrNull()?.permissions
    orders = repo.planningOrders()
  }
  return OpenOrders(orders, perms) { tick++ }
}

/** «پرداخت» ِ یک خرید: برگهٔ پرداختِ تأمین‌کننده با ماندهٔ همین خرید و یادداشتی که آن را نام می‌برد (`PurchaseBook.payNote`) */
internal fun RouteScope.payPurchase(v: PurchaseViews.View) {
  nav.open(MoneyPaths.payPurchase(v.supplierId, v.remaining, v.no.takeIf { it > 0 && !v.isOrder }))
}

/** کارهای فهرستِ «خریدها» در زبانهٔ خرید */
internal fun RouteScope.purchasesActions(open: OpenOrders): PurchasesActions = PurchasesActions(
  onOpen = { v ->
    val order = v.orderId
    if (order != null) nav.openForResult(MoneyPaths.purchaseOrder(order)) { open.refresh() }
    else nav.open(MoneyPaths.purchase(v.key))
  },
  onPay = { payPurchase(it) },
  onReceive = if (open.canReceive) ({ v -> v.orderId?.let { id -> nav.openForResult(MoneyPaths.receivePurchaseOrder(id)) { open.refresh() } } }) else null,
  onNew = { nav.open(MoneyPaths.newPurchase()) },
)

/** اسکنِ بارکد درونِ کادرِ جست‌وجوی خریدها (همان برگهٔ اسکنِ برنامه) */
internal fun RouteScope.purchaseScan(): ((String) -> Unit) -> Unit = { onCode ->
  nav.openForResult(SalesPaths.scan("purchases")) { code -> code?.let(onCode) }
}

/** `purchasing/purchases/{key}` */
@Composable
internal fun RouteScope.PurchaseDetailRoute(key: String) {
  if (key.startsWith("po:")) { LandOn(MoneyPaths.purchaseOrder(key.removePrefix("po:"))); return }
  val today = remember { todayIso() }
  val view = remember(data, key, today) { PurchaseViews.find(data, key, today) }
  val print = rememberPurchasePrint(data)
  PurchaseDetailScreen(
    view, today,
    PurchaseDetailActions(
      onBack = { back() },
      onSupplier = { nav.open(MoneyPaths.supplier(it)) },
      onProduct = { nav.open(ProductsPaths.item(it)) },
      onPay = { payPurchase(it) },
      onReceive = null,
      onOrder = null,
      onReturn = { nav.open(MoneyPaths.returnSupplier(it)) },
      onPrint = print,
    ),
  )
}

/** نقشِ کاربر در سفارشِ خرید (فرمِ «خریدِ تازه»: «الان رسیده؟ نه» فقط برای مدیر/صاحب با پرچمِ روشن) */
@Composable
internal fun rememberPoManage(door: Boolean): Boolean {
  val repo = rememberPurchaseOrderHost().repo
  var manage by remember { mutableStateOf(false) }
  LaunchedEffect(door) { manage = door && repo.config().valueOrNull()?.let { it.enabled && it.permissions.manage } == true }
  return manage
}
