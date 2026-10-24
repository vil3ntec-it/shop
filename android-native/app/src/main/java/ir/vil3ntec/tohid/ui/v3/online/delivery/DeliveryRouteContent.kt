package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.DeliveryGate
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 *  پیوندِ مسیرهای میزِ تحویل به صفحه و برگه‌ها: بار و تازه‌سازی (`DeskStore`، هر نیم دقیقه و با هر
 *  تغییرِ صفِ سفارش)، ناوبری به برگهٔ هر کار، و پیام‌ها. کدِ یک‌باره فقط از `DeskStore.code` می‌آید.
 */

@Composable
internal fun RouteScope.DeliveryRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val cfg = DeliveryGate.config.value ?: return
  val repo = remember(context) { DeliveryGate.repository(context) }
  val reload = DeskStore.reload.intValue
  val rev = OrdersLive.rev.value
  LaunchedEffect(reload, rev) { if (!DeskStore.load(context)) back() }
  //  تا صفحه باز است، هر نیم دقیقه تازه می‌شود (پیک‌ها وضعیت را از گوشیِ خودشان می‌فرستند)
  LaunchedEffect(Unit) { while (isActive) { delay(REFRESH_MS); DeskStore.refresh() } }
  var tab by rememberSaveable { mutableStateOf(DeliveryModel.initialTab(args.query("tab"), cfg)) }
  LaunchedEffect(cfg.perms.assign) { if (tab !in DeliveryModel.tabs(cfg)) tab = DeliveryModel.ASSIGN }
  var busyId by remember { mutableStateOf<String?>(null) }
  var newCodeFor by remember { mutableStateOf<Mission?>(null) }
  val pickupKeys = remember { HashMap<String, DeliveryRepository.OnceKey>() }
  val writable = cfg.entitled

  DeliveryScreen(
    cfg = cfg,
    data = DeskStore.data.value,
    error = DeskStore.error.value,
    loading = DeskStore.loading.value,
    tab = tab,
    wide = desk,
    onTab = { tab = it },
    onRefresh = DeskStore::refresh,
    onBack = backOrNull,
  ) { t, d, pad, header ->
    when (t) {
      DeliveryModel.ASSIGN -> AssignTab(d, cfg, writable, AssignActions(
        onAssign = { o -> nav.open(OnlinePaths.deliveryAssign(o.id)) },
        onAuto = { list -> DeskStore.autoIds.value = list.map { it.id }; nav.open(OnlinePaths.DELIVERY_AUTO) },
        onAct = { m, a -> nav.open(OnlinePaths.missionAction(m.id, a.name)) },
      ), pad, header, table = desk)
      DeliveryModel.COURIERS -> CouriersTab(d, cfg, writable, CouriersActions(
        onSettle = { cr -> nav.open(OnlinePaths.courierSheet(cr.id, "settle")) },
        onLedger = { cr -> nav.open(OnlinePaths.courierSheet(cr.id, "ledger")) },
        onCap = { cr -> nav.open(OnlinePaths.courierSheet(cr.id, "cap")) },
      ), pad, header)
      DeliveryModel.PICKUP -> PickupTab(d, cfg, writable, busyId, PickupActions(
        onMakeCode = { o ->
          busyId = o.id
          scope.launch {
            val body = DeliveryWire.assignJson("pickup")
            repo.assign(o.id, body, pickupKeys.getOrPut(o.id) { DeliveryRepository.OnceKey() }.of(body))
              .onSuccess { a ->
                DeskStore.refresh()
                val code = a.code
                if (code != null) { DeskStore.showCode(o.orderNo, code); nav.open(OnlinePaths.DELIVERY_CODE) }
                //  کد پیش‌تر ساخته شده و پاسخش گم شده بود؛ سرور فقط HMACش را دارد
                else toasts.show(StrOnline.dlCodeExisted(o.orderNo))
              }
              .onFailure { toasts.show(it.userMessage) }
            busyId = null
          }
        },
        onHandover = { o, _ -> nav.open(OnlinePaths.deliveryPickup(o.id)) },
        onNewCode = { m -> newCodeFor = m },
      ), pad, header)
      DeliveryModel.EXTERNAL -> CompaniesTab(d, cfg, { co -> nav.open(OnlinePaths.deliveryInvoice(co.name)) }, pad, header)
    }
  }

  newCodeFor?.let { m ->
    V3ConfirmSheet(
      title = StrOnline.dlNewCodeTitle(m.orderNo),
      message = StrOnline.dlNewCodeBody,
      confirm = StrOnline.dlMake,
      danger = false,
      onDismiss = { newCodeFor = null },
      onConfirm = {
        newCodeFor = null
        scope.launch {
          repo.newCode(m.id)
            .onSuccess { DeskStore.showCode(m.orderNo, it); DeskStore.refresh(); nav.open(OnlinePaths.DELIVERY_CODE) }
            .onFailure { toasts.show(it.userMessage) }
        }
      },
    )
  }
}

/** پس از کارِ برگه: میز تازه، پیام، برگه بسته؛ اگر کدی آمد، برگهٔ «کد» جای همین برگه */
private fun RouteScope.finished(message: String?, code: Pair<Long, String>? = null) {
  DeskStore.refresh()
  message?.let(toasts::show)
  if (code != null) { DeskStore.showCode(code.first, code.second); nav.replace(OnlinePaths.DELIVERY_CODE) } else back()
}

@Composable
internal fun RouteScope.DeliveryAssignRoute() {
  val context = LocalContext.current
  val cfg = DeliveryGate.config.value ?: return
  val d = DeskStore.data.value ?: return
  val order = d.orders.firstOrNull { it.id == args["orderId"] }
  LaunchedEffect(order) { if (order == null) back() }
  if (order == null) return
  AssignSheet(order, d, cfg, DeliveryGate.repository(context), onDismiss = { back() }) { m, code ->
    Haptics.success(context)
    finished(StrOnline.dlAssigned(order.orderNo, DeliveryModel.who(m, d)), code?.let { m.orderNo to it })
  }
}

@Composable
internal fun RouteScope.DeliveryAutoRoute() {
  val context = LocalContext.current
  val d = DeskStore.data.value ?: return
  val ids = DeskStore.autoIds.value
  val orders = if (ids == null) d.toAssign else d.toAssign.filter { it.id in ids }
  AutoAssignSheet(orders, d, DeliveryGate.repository(context), onDismiss = { back() }, onFinished = { DeskStore.refresh() })
}

@Composable
internal fun RouteScope.MissionActionRoute() {
  val context = LocalContext.current
  val cfg = DeliveryGate.config.value ?: return
  val d = DeskStore.data.value ?: return
  val m = d.live.firstOrNull { it.id == args["id"] }
  val act = DeskActions.Act.entries.firstOrNull { it.name == args.query("act") }
  LaunchedEffect(m, act) { if (m == null || act == null) back() }
  if (m == null || act == null) return
  MissionActionSheet(
    m, act, cfg, DeliveryGate.repository(context),
    onDismiss = { back() },
    onCode = { code -> finished(null, m.orderNo to code) },
    onDone = { msg -> Haptics.success(context); finished(msg) },
  )
}

@Composable
internal fun RouteScope.DeliveryCodeRoute() {
  val shown = DeskStore.code.value
  LaunchedEffect(shown) { if (shown == null) back() }
  if (shown == null) return
  CodeOnceSheet(shown.first, shown.second) { DeskStore.code.value = null; back() }
}

@Composable
internal fun RouteScope.DeliveryPickupRoute() {
  val context = LocalContext.current
  val cfg = DeliveryGate.config.value ?: return
  val d = DeskStore.data.value ?: return
  val order = d.pickups.firstOrNull { it.id == args["orderId"] }
  val m = order?.let { d.missionOf(it.id)?.takeIf { x -> x.mode == "pickup" } }
  LaunchedEffect(order, m) { if (order == null || m == null) back() }
  if (order == null || m == null) return
  PickupHandoverSheet(order, m, cfg, DeliveryGate.repository(context), OrdersLive.repository(context), onDismiss = { back() }) { msg ->
    Haptics.success(context)
    finished(msg)
  }
}

@Composable
internal fun RouteScope.CourierSheetRoute(kind: String) {
  val context = LocalContext.current
  val d = DeskStore.data.value ?: return
  val cr = d.couriers.firstOrNull { it.id == args["id"] }
  LaunchedEffect(cr) { if (cr == null) back() }
  if (cr == null) return
  val repo = DeliveryGate.repository(context)
  when (kind) {
    "settle" -> SettlementSheet(cr, repo, onDismiss = { back() }, onDone = { s ->
      Haptics.success(context)
      val gap = when {
        s.shortageMinor > 0 -> " · " + StrOnline.dlShortage(StrOnline.afn(s.shortageMinor))
        s.excessMinor > 0 -> " · " + StrOnline.dlExcess(StrOnline.afn(s.excessMinor))
        else -> ""
      }
      finished(StrOnline.dlSettled(s.settleNo, gap))
    })
    "ledger" -> LedgerSheet(cr, repo, onDismiss = { back() })
    else -> CourierCapSheet(cr, repo, onDismiss = { back() }, onDone = { finished(StrOnline.dlSavedFor(cr.name)) })
  }
}

@Composable
internal fun RouteScope.DeliveryInvoiceRoute() {
  val context = LocalContext.current
  val d = DeskStore.data.value ?: return
  val co = d.companies.firstOrNull { it.name == args["id"] }
  LaunchedEffect(co) { if (co == null) back() }
  if (co == null) return
  InvoiceSheet(co, DeliveryGate.repository(context), onDismiss = { back() })
}

private const val REFRESH_MS = 30_000L
