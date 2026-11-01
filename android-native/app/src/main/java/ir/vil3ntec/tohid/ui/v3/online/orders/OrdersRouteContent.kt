package ir.vil3ntec.tohid.ui.v3.online.orders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3TwoPane
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.online.OnlinePaths
import kotlinx.coroutines.launch

/*
 *  پیوندِ مسیرهای سفارش به صفحه‌های بی‌حالت: بارِ داده (`OrdersHub`، `OrdersQueueController`)،
 *  ناوبری و پیام‌ها این‌جاست؛ صفحه‌ها فقط حالت می‌کشند (و عکس‌ها همان صفحه‌ها را با دادهٔ نمایشی).
 */

/** `orders?tab=` — گوشی: صف؛ کامپیوترِ پهن: صف | جزئیات (V3TwoPane) */
@Composable
internal fun RouteScope.OrdersRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val cfg = OrdersLive.config.value
  val ctl = remember { OrdersQueueController(context, scope, OrdersModel.initialTab(args.query("tab"))) }
  val rev = OrdersLive.rev.value
  val changed = OrdersHub.changed.value
  LaunchedEffect(rev, changed) { ctl.reload() }
  //  اعلانِ سفارش (`OrdersLive.openOrder`) ⇐ مستقیم سرِ همان سفارش (NAV-MAP بندِ ۶)
  val pending = OrdersLive.pendingOrderId.value
  LaunchedEffect(pending) {
    if (pending != null) {
      OrdersLive.pendingOrderId.value = null
      nav.open(OnlinePaths.order(pending))
    }
  }
  val wide = desk
  var selected by rememberSaveable { mutableStateOf<String?>(null) }
  val perms = cfg?.permissions ?: OrderPermsDto()
  val notes = OrderFulfillNotes.queue(
    rollout = OrdersLive.rolloutNotice(), review = OrdersLive.review.value, unposted = OrdersLive.unposted.value,
    waiting = OrdersLive.waiting.value, shortfalls = OrdersLive.shortfalls.value, canRefund = perms.refund,
  )
  val state = ctl.state.copy(perms = perms, counts = OrdersLive.counts.value, notes = notes)
  val actions = QueueActions(
    onTab = ctl::selectTab,
    onOpen = { o -> if (wide) selected = o.id else nav.open(OnlinePaths.order(o.id)) },
    onStep = { o, to -> nav.open(OnlinePaths.action(o.id, to)) },
    onNew = { nav.open(OnlinePaths.NEW_ORDER) },
    onRetry = ctl::reload,
    onMore = ctl::more,
    onNote = { n ->
      when (n.act) {
        OrderFulfillNotes.Act.RETRY_ALL -> scope.launch { OrdersLive.reconcile(context, store, force = true) }
        OrderFulfillNotes.Act.OPEN_REVIEW -> n.orderId?.let { if (wide) selected = it else nav.open(OnlinePaths.order(it)) }
        else -> Unit
      }
    },
  )
  //  کامپیوتر: Ctrl+N همان «سفارشِ دستی»
  val create by rememberUpdatedState(perms.create)
  DisposableEffect(Unit) {
    val action = { if (create) nav.open(OnlinePaths.NEW_ORDER) }
    AppNav.addAction.value = action
    onDispose { if (AppNav.addAction.value === action) AppNav.addAction.value = null }
  }
  if (!wide) {
    OrdersScreen(state, actions, backOrNull)
    return
  }
  V3TwoPane(
    list = { OrdersScreen(state, actions, backOrNull, selectedId = selected) },
    detail = {
      val id = selected
      if (id == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        V3EmptyState(Icons.Outlined.ReceiptLong, StrOnline.selectOrder)
      } else OrderDetailRoute(id, embedded = true)
    },
  )
}

/** `orders/{id}` (یا ستونِ دومِ کامپیوتر) */
@Composable
internal fun RouteScope.OrderDetailRoute(id: String, embedded: Boolean = false) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val uri = LocalUriHandler.current
  val clipboard = LocalClipboardManager.current
  val cfg = OrdersLive.config.value
  var error by remember(id) { mutableStateOf<String?>(null) }
  var busy by remember(id) { mutableStateOf<String?>(null) }
  var failure by remember(id) { mutableStateOf<String?>(null) }
  var reload by remember(id) { mutableStateOf(0) }
  val rev = OrdersLive.rev.value
  //  صفِ زنده: اگر کسِ دیگری وضعیت را عوض کرد، همین‌جا تازه می‌شود
  LaunchedEffect(id, rev, reload) {
    OrdersHub.fetch(context, id).onSuccess { error = null }.onFailure { if (OrdersHub.known[id] == null) error = it.userMessage }
  }
  val order = OrdersHub.known[id]
  val ledger by store.data.collectAsState()
  val perms = cfg?.permissions ?: OrderPermsDto()
  val notes = order?.let { o ->
    OrderFulfillNotes.detail(
      ledger, o, OrdersLive.review.value, OrdersLive.unposted.value, OrdersLive.handoverSince(),
      manage = perms.manage, canRefund = perms.refund, canRecord = OrdersLive.canRecord(cfg, o),
      canReverse = OrdersLive.canReverse(cfg, o), waiting = OrdersLive.isWaiting(o.id),
    )
  }.orEmpty()
  val state = DetailState(
    order = order, loading = order == null && error == null, error = error, stale = OrdersHub.stale[id] == true,
    perms = perms, now = System.currentTimeMillis(), busy = busy, failure = failure, notes = notes,
  )
  val actions = DetailActions(
    onRetry = { error = null; reload++ },
    onReload = { OrdersHub.stale.remove(id); reload++ },
    onStep = { to ->
      val o = order
      if (o != null) {
        if (OrdersModel.needsSheet(to)) nav.open(OnlinePaths.action(o.id, to))
        else if (busy == null) {
          val blocked = OrdersHub.preflight(context, store, o, to)
          if (blocked != null) { failure = blocked; return@DetailActions }
          busy = to
          failure = null
          scope.launch {
            OrdersHub.transition(context, store, o, to, say = { toasts.show(it) })
              .onSuccess { toasts.show(StrOnline.movedTo(it.orderNo, StrOnline.orderStatus(it.status))) }
              .onFailure { f -> if (f.code != "status_changed") failure = f.userMessage }
            busy = null
          }
        }
      }
    },
    onCall = { phone ->
      runCatching { uri.openUri("tel:$phone") }.onFailure {
        clipboard.setText(AnnotatedString(phone))
        toasts.show(StrOnline.numberCopied)
      }
    },
    onCopy = { phone ->
      clipboard.setText(AnnotatedString(phone))
      Haptics.confirm(context)
      toasts.show(StrOnline.numberCopied)
    },
    onMap = { link -> runCatching { uri.openUri(link) } },
    onNote = { n ->
      val o = order
      if (o != null && busy == null && (n.act == OrderFulfillNotes.Act.POST || n.act == OrderFulfillNotes.Act.ACK)) {
        busy = n.key
        scope.launch {
          val msg = if (n.act == OrderFulfillNotes.Act.POST) OrdersHub.postSale(context, store, o) else OrdersHub.ackReview(context, o)
          msg?.let { toasts.show(it) }
          busy = null
        }
      }
    },
  )
  OrderDetailScreen(state, actions, onBack = if (embedded) null else backOrNull)
}

/** `orders/{id}/action?to=` — برگه روی جزئیات (یا صف) */
@Composable
internal fun RouteScope.OrderActionRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val id = args["id"].orEmpty()
  val to = args.query("to").orEmpty()
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(id) { if (OrdersHub.known[id] == null) OrdersHub.fetch(context, id).onFailure { error = it.userMessage } }
  val order = OrdersHub.known[id]
  val perms = OrdersLive.config.value?.permissions ?: OrderPermsDto()
  //  گذارِ نامجاز (پیوندِ کهنه یا وضعیتِ تازه) ⇐ برگه بسته می‌شود؛ سرور هم نمی‌پذیرفت
  LaunchedEffect(order?.status, to) {
    if (order != null && to !in OrdersModel.bar(order, perms).let { listOfNotNull(it.primary) + it.danger + it.others }) back()
  }
  if (order == null) return
  OrderActionSheet(
    order = order,
    to = to,
    busy = busy,
    error = error,
    onDismiss = { back() },
    onConfirm = { f ->
      val blocked = OrdersHub.preflight(context, store, order, to)
      if (blocked != null) { error = blocked; return@OrderActionSheet }
      busy = true
      error = null
      scope.launch {
        OrdersHub.transition(context, store, order, to, f.reason, f.note, OrderActionModel.readyOf(to, f), say = { toasts.show(it) })
          .onSuccess {
            Haptics.success(context)
            toasts.show(StrOnline.movedTo(it.orderNo, StrOnline.orderStatus(it.status)))
            back()
          }
          .onFailure { e ->
            if (e.code == "status_changed") { toasts.show(StrOnline.staleStatus); back() } else error = e.userMessage
          }
        busy = false
      }
    },
  )
}

/** `orders/new` — یک کلیدِ یکتا برای همین فرم: «ثبت» ِ دوباره (قطعِ نت) همان سفارش است */
@Composable
internal fun RouteScope.NewOrderRoute() {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val key = rememberSaveable { OrdersRepository.newKey() }
  var busy by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<String?>(null) }
  NewOrderScreen(
    sources = NewOrderModel.sources(OrdersLive.config.value?.sources.orEmpty()),
    busy = busy,
    failure = failure,
    onBack = { back() },
    onSubmit = { form ->
      when (val r = ir.vil3ntec.tohid.core.orders.OrderDraft.build(form)) {
        is ir.vil3ntec.tohid.core.orders.OrderDraft.Result.Error -> failure = r.message
        is ir.vil3ntec.tohid.core.orders.OrderDraft.Result.Ok -> if (!busy) {
          busy = true
          failure = null
          scope.launch {
            OrdersHub.create(context, key, r.body)
              .onSuccess { o ->
                Haptics.success(context)
                toasts.show(StrOnline.created(o.orderNo))
                nav.replace(OnlinePaths.order(o.id))
              }
              .onFailure { f -> failure = if (f.retryable) StrOnline.retryOnce else f.userMessage }
            busy = false
          }
        }
      }
    },
  )
}
