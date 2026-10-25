package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.chat.ChatConversation
import ir.vil3ntec.tohid.core.chat.ChatCustomer
import ir.vil3ntec.tohid.core.chat.ChatLast
import ir.vil3ntec.tohid.core.chat.ChatMediaRef
import ir.vil3ntec.tohid.core.chat.ChatMessage
import ir.vil3ntec.tohid.core.chat.ChatMeta
import ir.vil3ntec.tohid.core.chat.ChatQueue
import ir.vil3ntec.tohid.core.chat.ChatReceipts
import ir.vil3ntec.tohid.core.chat.OutboxItem
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.DeskPerms
import ir.vil3ntec.tohid.core.delivery.ExternalCompany
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.delivery.SettleRow
import ir.vil3ntec.tohid.core.delivery.SettlementPreview
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderAddressDto
import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDetailDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrderLineDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.core.storefront.MarketCategoryDto
import ir.vil3ntec.tohid.core.storefront.MarketListingDto
import ir.vil3ntec.tohid.core.storefront.MarketLocationDto
import ir.vil3ntec.tohid.core.storefront.MarketStateDto
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery
import ir.vil3ntec.tohid.data.repo.PresenceStatusDto
import ir.vil3ntec.tohid.data.repo.StorefrontConfigDto
import ir.vil3ntec.tohid.data.repo.StorefrontFulfillmentDto
import ir.vil3ntec.tohid.data.repo.StorefrontPermsDto
import ir.vil3ntec.tohid.data.repo.StorefrontProductDto
import ir.vil3ntec.tohid.data.repo.StorefrontSettingsDto
import ir.vil3ntec.tohid.data.repo.StorefrontSpecDto
import ir.vil3ntec.tohid.ui.screens.DeliveryGate
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3FormBar
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.online.chat.ChatComposer
import ir.vil3ntec.tohid.ui.v3.online.chat.ChatInboxScreen
import ir.vil3ntec.tohid.ui.v3.online.chat.ChatThreadScreen
import ir.vil3ntec.tohid.ui.v3.online.chat.InboxState
import ir.vil3ntec.tohid.ui.v3.online.chat.ThreadActions
import ir.vil3ntec.tohid.ui.v3.online.chat.ThreadView
import ir.vil3ntec.tohid.ui.v3.online.delivery.AssignActions
import ir.vil3ntec.tohid.ui.v3.online.delivery.AssignSheet
import ir.vil3ntec.tohid.ui.v3.online.delivery.AssignTab
import ir.vil3ntec.tohid.ui.v3.online.delivery.CodeOnceSheet
import ir.vil3ntec.tohid.ui.v3.online.delivery.CompaniesTab
import ir.vil3ntec.tohid.ui.v3.online.delivery.CouriersActions
import ir.vil3ntec.tohid.ui.v3.online.delivery.CouriersTab
import ir.vil3ntec.tohid.ui.v3.online.delivery.DeliveryModel
import ir.vil3ntec.tohid.ui.v3.online.delivery.DeliveryScreen
import ir.vil3ntec.tohid.ui.v3.online.delivery.DeskData
import ir.vil3ntec.tohid.ui.v3.online.delivery.PickupActions
import ir.vil3ntec.tohid.ui.v3.online.delivery.PickupTab
import ir.vil3ntec.tohid.ui.v3.online.delivery.SettlementSheet
import ir.vil3ntec.tohid.ui.v3.online.orders.DetailActions
import ir.vil3ntec.tohid.ui.v3.online.orders.DetailState
import ir.vil3ntec.tohid.ui.v3.online.orders.NewOrderModel
import ir.vil3ntec.tohid.ui.v3.online.orders.NewOrderScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderActionModel
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderActionSheet
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderDetailScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.OrdersScreen
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueActions
import ir.vil3ntec.tohid.ui.v3.online.orders.QueueState
import ir.vil3ntec.tohid.ui.v3.online.storefront.MarketController
import ir.vil3ntec.tohid.ui.v3.online.storefront.MarketPane
import ir.vil3ntec.tohid.ui.v3.online.storefront.ProductSheet
import ir.vil3ntec.tohid.ui.v3.online.storefront.ProductsActions
import ir.vil3ntec.tohid.ui.v3.online.storefront.ProductsPane
import ir.vil3ntec.tohid.ui.v3.online.storefront.SettingsPane
import ir.vil3ntec.tohid.ui.v3.online.storefront.StatusPane
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontModel
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontScreen
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontSettingsModel
import ir.vil3ntec.tohid.ui.v3.online.storefront.ZonesForm
import ir.vil3ntec.tohid.ui.v3.online.storefront.ZonesPane
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts

/**
 *  عکس‌های گروهِ G8 آنلاین (`online.md`) — روی دادهٔ نمایشیِ همین فایل (نامِ دکان و مشتری و شمارهٔ
 *  ساختگی، نه دادهٔ دکانِ واقعی): صفِ سفارش در هر برگه و خالی و خطا، جزئیاتِ ارسال و حضوری، برگهٔ کار،
 *  سفارشِ دستی، پنج برگهٔ ویترین و دروازه‌هایش، صندوق و گفت‌وگو و بی‌اتصال، و چهار برگهٔ میزِ تحویل با
 *  برگه‌های سپردن و تسویه و کد. صفحه‌ها بی‌حالت‌اند و مستقیم کشیده می‌شوند؛ کارها بی‌اثرند.
 */
object OnlineShots : GroupShots {
  override val group = "online"
  override val shots = listOf(
    //  سفارش‌ها
    Shot("orders-new", content = { Queue("new") }),
    Shot("orders-preparing", content = { Queue("preparing") }),
    Shot("orders-on-the-way", content = { Queue("on_the_way") }),
    Shot("orders-empty", content = { Queue("done") }),
    Shot("orders-error", content = { Queue("new", error = true) }),
    Shot("orders-loading", content = { Queue("new", loading = true) }),
    Shot("order-delivery", content = { Detail(OnlineDemo.delivery) }),
    Shot("order-pickup", content = { Detail(OnlineDemo.pickupReady, stale = true) }),
    Shot("order-reject", content = { ActionShot("rejected", OrderActionModel.Form(reason = "out_of_stock", note = "روغنِ ۴ لیتری تمام شده")) }),
    Shot("order-accept", content = { ActionShot("accepted", null) }),
    Shot("order-new", content = { NewOrder(errors = false) }),
    Shot("order-new-errors", content = { NewOrder(errors = true) }),
    Shot("route-orders", route = "orders"),
    //  ویترین
    Shot("storefront-status", content = { Store(StorefrontModel.STATUS) }),
    Shot("storefront-settings", content = { Store(StorefrontModel.SETTINGS) }),
    Shot("storefront-products", content = { Store(StorefrontModel.PRODUCTS) }),
    Shot("storefront-product-sheet", content = { Store(StorefrontModel.PRODUCTS, sheet = true) }),
    Shot("storefront-market", content = { Store(StorefrontModel.MARKET) }),
    Shot("storefront-delivery", content = { Store(StorefrontModel.DELIVERY) }),
    Shot("storefront-not-entitled", content = { Store(StorefrontModel.STATUS, OnlineDemo.store.copy(entitled = false)) }),
    Shot("storefront-create-first", content = { Store(StorefrontModel.STATUS, OnlineDemo.store.copy(settings = null)) }),
    Shot("storefront-off", content = { Store(StorefrontModel.STATUS, OnlineDemo.store.copy(flag = false)) }),
    Shot("route-storefront", route = "storefront"),
    //  گفت‌وگو
    Shot("chat-inbox", content = { Inbox(offline = false) }),
    Shot("chat-inbox-offline", content = { Inbox(offline = true) }),
    Shot("chat-thread", content = { Thread(offline = false, canReply = true) }),
    Shot("chat-thread-offline", content = { Thread(offline = true, canReply = true) }),
    Shot("chat-thread-readonly", content = { Thread(offline = false, canReply = false) }),
    //  میزِ تحویل
    Shot("delivery-assign", content = { Desk(DeliveryModel.ASSIGN) }),
    Shot("delivery-couriers", content = { Desk(DeliveryModel.COURIERS) }),
    Shot("delivery-pickup", content = { Desk(DeliveryModel.PICKUP) }),
    Shot("delivery-external", content = { Desk(DeliveryModel.EXTERNAL) }),
    Shot("delivery-readonly", content = { Desk(DeliveryModel.ASSIGN, OnlineDemo.desk.copy(entitled = false)) }),
    Shot("delivery-assign-sheet", content = { Desk(DeliveryModel.ASSIGN) { AssignSheet(OnlineDemo.deskOrders[0], OnlineDemo.deskData, OnlineDemo.desk, repo(), {}, { _, _ -> }) } }),
    Shot("delivery-settle", content = { Desk(DeliveryModel.COURIERS) { SettlementSheet(OnlineDemo.navid, repo(), {}, {}, start = OnlineDemo.settle) } }),
    Shot("delivery-code", content = { Desk(DeliveryModel.PICKUP) { CodeOnceSheet(2037, "482913") {} } }),
  )
}

/* ------------------------------ سفارش‌ها ------------------------------ */

private val noQueue = QueueActions({}, {}, { _, _ -> }, {}, {}, {})
private val noDetail = DetailActions({}, {}, {}, {}, {}, {})

@Composable
private fun Queue(tab: String, error: Boolean = false, loading: Boolean = false) {
  val items = if (error || loading) emptyList() else OnlineDemo.queue.filter { it.tab == tab }
  OrdersScreen(
    QueueState(
      tab = tab, counts = OnlineDemo.counts, items = items, loading = loading,
      error = if (error) StrOnline.checkConnection else null, hasMore = tab == "new",
      perms = OnlineDemo.perms, now = OnlineDemo.now, fresh = if (tab == "new") setOf("o1046") else emptySet(),
    ),
    noQueue,
    onBack = {},
  )
}

@Composable
private fun Detail(o: OrderDto, stale: Boolean = false) {
  OrderDetailScreen(DetailState(order = o, loading = false, stale = stale, perms = OnlineDemo.perms, now = OnlineDemo.now), noDetail, onBack = {})
}

@Composable
private fun ActionShot(to: String, form: OrderActionModel.Form?) {
  val o = OnlineDemo.queue.first { it.status == "new" }
  Box(Modifier.fillMaxSize()) {
    Detail(o)
    OrderActionSheet(o, to, busy = false, error = null, onConfirm = {}, onDismiss = {}, start = form ?: OrderActionModel.start(o, to))
  }
}

@Composable
private fun NewOrder(errors: Boolean) {
  Toasted {
    NewOrderScreen(
      sources = NewOrderModel.DEFAULT_SOURCES, busy = false, failure = null, onSubmit = {}, onBack = {},
      start = if (errors) {
        OrderDraft.Form(source = "whatsapp", fulfillment = "delivery", name = "فرید", lines = listOf(OrderDraft.LineInput("چای سبز", "۲", "")))
      } else {
        OrderDraft.Form(
          source = "phone", fulfillment = "delivery", name = "فرید احمدی", phone = "0700000318",
          addressText = "ناحیهٔ چهارم، سرکِ دوم", landmark = "روبه‌روی نانوایی", deliveryFee = "50", note = "زنگِ دوم را بزنید",
          lines = listOf(OrderDraft.LineInput("برنجِ دانه‌دراز ۵ کیلویی", "2", "1450"), OrderDraft.LineInput("چای سبز ۵۰۰ گرمی", "1", "125")),
        )
      },
      showErrors = errors,
    )
  }
}

/* ------------------------------ ویترین ------------------------------ */

@Composable
private fun Store(tab: String, config: StorefrontConfigDto = OnlineDemo.store, sheet: Boolean = false) {
  Toasted {
    val form = remember { StorefrontSettingsModel.from(OnlineDemo.settings) }
    val market = remember { MarketController(null).also { it.show(OnlineDemo.market) } }
    Box(Modifier.fillMaxSize()) {
      StorefrontScreen(
        config = config, loading = false, error = null, tab = tab, onTab = {}, onRetry = {}, onBack = {},
        bottomBar = when (tab) {
          StorefrontModel.SETTINGS -> ({ V3FormBar(StrOnline.sfSave, {}) })
          StorefrontModel.MARKET -> ({ V3FormBar(StrOnline.mkSave, {}) })
          StorefrontModel.DELIVERY -> ({ V3FormBar(StrOnline.dzSave, {}) })
          else -> null
        },
      ) { t, pad ->
        when (t) {
          StorefrontModel.STATUS -> config.settings?.let { StatusPane(it, pending = false, sending = false, notice = null, confirmed = 1, onDeclare = { _, _ -> }, onResend = {}, pad = pad) }
          StorefrontModel.SETTINGS -> SettingsPane(form, emptyMap(), {}, pad)
          StorefrontModel.PRODUCTS -> ProductsPane(OnlineDemo.products, null, false, null, true, ProductsActions({}, { _, _ -> }, { _, _ -> }, {}), pad)
          StorefrontModel.MARKET -> MarketPane(market, config, hasLocator = true, onLocate = {}, onRetry = {}, pad = pad)
          StorefrontModel.DELIVERY -> ZonesPane(OnlineDemo.zones, emptyMap(), {}, pad)
        }
      }
      if (sheet) ProductSheet(OnlineDemo.products[0], busy = false, failure = null, onSave = {}, onDismiss = {})
    }
  }
}

/* ------------------------------ گفت‌وگو ------------------------------ */

@Composable
private fun Inbox(offline: Boolean) {
  ChatInboxScreen(
    InboxState(list = OnlineDemo.conversations, loading = false, error = if (offline) StrOnline.ctOffline else null, now = OnlineDemo.now),
    onQuery = {}, onStatus = {}, onOpen = {}, onBack = {},
  )
}

@Composable
private fun Thread(offline: Boolean, canReply: Boolean) {
  val conv = OnlineDemo.conversations[0]
  ChatThreadScreen(
    ThreadView(
      conversation = conv,
      server = OnlineDemo.messages,
      local = if (canReply) listOf(OutboxItem("q1", conv.id, body = "فردا صبح هم می‌آورید؟", state = if (offline) ChatQueue.WAITING else ChatQueue.SENDING, createdAt = OnlineDemo.now)) else emptyList(),
      banner = if (offline) StrOnline.ctOfflineThread to false else null,
      canReply = canReply,
      canModerate = canReply,
      imageOf = { null },
      localImageOf = { null },
    ),
    ThreadActions(
      onOrder = {}, onToggleStatus = {}, onToggleBlock = {}, onReport = {}, onImage = {}, onAudio = {},
      onRetry = {}, onCancel = {}, loadThumb = {}, loadLocal = {},
    ),
    onBack = {},
    composer = { ChatComposer(draft = if (canReply) "بله، تا یک ساعتِ دیگر" else "", onDraft = {}, enabled = canReply, onSend = {}, onPhoto = {}, mic = null, status = null) },
  )
}

/* ------------------------------ میزِ تحویل ------------------------------ */

@Composable
private fun repo() = DeliveryGate.repository(LocalContext.current)

@Composable
private fun Desk(tab: String, cfg: DeskConfig = OnlineDemo.desk, sheet: (@Composable () -> Unit)? = null) {
  Toasted {
    Box(Modifier.fillMaxSize()) {
      DeliveryScreen(cfg, OnlineDemo.deskData, error = null, loading = false, tab = tab, wide = false, onTab = {}, onRefresh = {}, onBack = {}) { t, d, pad, header ->
        val writable = cfg.entitled
        when (t) {
          DeliveryModel.ASSIGN -> AssignTab(d, cfg, writable, AssignActions({}, {}, { _, _ -> }), pad, header)
          DeliveryModel.COURIERS -> CouriersTab(d, cfg, writable, CouriersActions({}, {}, {}), pad, header)
          DeliveryModel.PICKUP -> PickupTab(d, cfg, writable, null, PickupActions({}, { _, _ -> }, {}), pad, header)
          DeliveryModel.EXTERNAL -> CompaniesTab(d, cfg, {}, pad, header)
        }
      }
      sheet?.invoke()
    }
  }
}

@Composable
private fun Toasted(content: @Composable () -> Unit) {
  val host = remember { SnackbarHostState() }
  val toasts = rememberV3Toasts(host)
  CompositionLocalProvider(LocalV3Toasts provides toasts) {
    Box(Modifier.fillMaxSize()) {
      content()
      V3SnackbarHost(host, Modifier.align(Alignment.TopCenter))
    }
  }
}

/* ------------------------------ دادهٔ نمایشی ------------------------------ */

/** همه ساختگی: نام‌ها، شماره‌ها (۰۷۰۰۰۰۰…)، نشانی‌ها و مبلغ‌ها */
private object OnlineDemo {
  val now = System.currentTimeMillis()
  private const val MIN = 60_000L
  val perms = OrderPermsDto(create = true, manage = true, contact = true, refund = true)

  private fun line(no: Int, name: String, unit: String, qty: Long, price: Long, note: String = "") =
    OrderLineDto(no, "p$no", name, unit, qty * 1000, price, qty * price, note)

  private val basket = listOf(
    line(1, "برنجِ دانه‌دراز ۵ کیلویی", "بسته", 2, 145_000),
    line(2, "روغنِ آفتاب‌گردان ۴ لیتری", "بوتل", 1, 98_000, note = "اگر نبود، ۵ لیتری"),
    line(3, "چای سبز ۵۰۰ گرمی", "پاکت", 3, 12_500),
  )

  private fun order(
    no: Long, status: String, fulfillment: String, name: String, ago: Long, source: String,
    paid: Boolean = false, scheduled: Long? = null, test: Boolean = false, lines: List<OrderLineDto> = basket.take(2),
  ): OrderDto {
    val fee = if (fulfillment == "delivery") 5_000L else 0L
    val sub = lines.sumOf { it.lineTotalMinor }
    return OrderDto(
      id = "o$no", orderNo = no, status = status, tab = OrderStates.tabOf(status, scheduled, now), next = OrderStates.nextOf(status),
      source = source, isTest = test, fulfillment = fulfillment, paymentMethod = if (paid) "online" else "cod",
      paymentStatus = if (paid) "paid" else "unpaid", customer = OrderCustomerDto(name, "07•• ••• ${no % 1000}", "0700000${no % 1000}"),
      itemsCount = lines.size, currency = "AFN", totalMinor = sub + fee, scheduledFor = scheduled,
      readyAt = if (status in setOf("accepted", "picking", "packed")) now + 12 * MIN else null,
      version = 3, createdAt = now - ago * MIN, updatedAt = now - ago * MIN / 2, canContact = true,
      address = if (fulfillment == "delivery") OrderAddressDto(34.53, 69.17, "روبه‌روی مسجدِ جامع، کوچهٔ دوم", "ناحیهٔ چهارم، سرکِ اصلی، خانهٔ ۱۲") else OrderAddressDto(),
      note = if (no % 2 == 0L) "لطفاً پیش از آمدن زنگ بزنید" else "",
      subtotalMinor = sub, deliveryFeeMinor = fee, lines = lines,
      events = listOfNotNull(
        OrderEventDto(1, "created", "", "new", "customer", at = now - ago * MIN),
        if (status != "new") OrderEventDto(2, "status", "new", "accepted", "user", detail = OrderEventDetailDto(readyAt = now + 12 * MIN), at = now - (ago - 2) * MIN) else null,
        if (status in setOf("picking", "packed", "ready_for_pickup", "out_for_delivery")) OrderEventDto(3, "status", "accepted", "picking", "user", at = now - (ago - 4) * MIN) else null,
        if (status in setOf("packed", "ready_for_pickup", "out_for_delivery")) OrderEventDto(4, "status", "picking", "packed", "user", at = now - (ago - 6) * MIN) else null,
        if (status == "ready_for_pickup") OrderEventDto(5, "status", "packed", "ready_for_pickup", "user", note = "در قفسهٔ کنارِ صندوق", at = now - 3 * MIN) else null,
      ),
    )
  }

  val queue: List<OrderDto> = listOf(
    order(1046, "new", "delivery", "مریم رحیمی", 1, "storefront", lines = basket),
    order(1045, "new", "pickup", "احمد کریمی", 6, "phone"),
    order(1044, "new", "delivery", "سارا نوری", 14, "telegram", scheduled = now + 20 * 60 * MIN),
    order(1043, "new", "delivery", "دکانِ آزمایشی", 22, "whatsapp", test = true),
    order(1041, "accepted", "delivery", "حمید صدیقی", 18, "storefront"),
    order(1040, "picking", "pickup", "نسرین امیری", 25, "phone", paid = true),
    order(1039, "packed", "delivery", "یوسف حیدری", 34, "storefront", lines = basket),
    order(1037, "out_for_delivery", "delivery", "لیلا اکبری", 52, "storefront"),
  )

  val counts = mapOf("new" to 4, "preparing" to 3, "ready" to 2, "on_the_way" to 1, "scheduled" to 1, "done" to 0)

  val delivery: OrderDto = order(1039, "packed", "delivery", "یوسف حیدری", 34, "storefront", lines = basket)
  val pickupReady: OrderDto = order(1038, "ready_for_pickup", "pickup", "نسرین امیری", 40, "phone", paid = true)

  /* ویترین */
  val settings = StorefrontSettingsDto(
    slug = "nemoone-market", title = "سوپرمارکتِ نمونه", tagline = "تازه، نزدیک، هر روز", contactPhone = "+93700000000",
    accent = "", notice = "جمعه‌ها از ساعتِ ۲ باز هستیم",
    status = PresenceStatusDto(
      badge = "open_online", mode = "auto", connection = "online", lastConfirmedAt = now - 2 * MIN, orderAcceptance = "accepting",
      localTime = "10:15", hoursSet = true, today = listOf(listOf(480, 1260)),
    ),
    fulfillment = StorefrontFulfillmentDto(delivery = true, pickup = true), deliveryFeeMinor = 5_000, minOrderMinor = 30_000, enabled = true,
    weeklyHours = (0..6).associate { d -> d.toString() to if (d == 5) listOf(listOf(840, 1260)) else listOf(listOf(480, 780), listOf(840, 1260)) },
    closedDates = listOf("2026-11-02"),
  )
  val store = StorefrontConfigDto(
    flag = true, ordersFlag = true, storefrontFlag = true, entitled = true, enabled = true,
    settings = settings, permissions = StorefrontPermsDto(manage = true), zones = true,
  )
  val products = listOf(
    StorefrontProductDto(
      "p1", "برنجِ دانه‌دراز ۵ کیلویی", "بسته", "خواربار", priceMinor = 150_000, maxQtyMilli = 5_000, published = true, onlinePriceMinor = 145_000,
      description = "دانه‌دراز، خوش‌پخت", specs = listOf(StorefrontSpecDto("وزن", "۵ کیلو"), StorefrontSpecDto("کشور", "پاکستان")),
    ),
    StorefrontProductDto("p2", "روغنِ آفتاب‌گردان ۴ لیتری", "بوتل", "خواربار", priceMinor = 98_000, published = true, photo = true),
    StorefrontProductDto("p3", "چای سبز ۵۰۰ گرمی", "پاکت", "نوشیدنی", priceMinor = 12_500, published = false),
    StorefrontProductDto("p4", "شیرِ تازه یک لیتری", "بوتل", "لبنیات", priceMinor = null, published = false),
    StorefrontProductDto("p5", "صابونِ دست با نامِ بسیار بلند برای آزمودنِ شکستنِ خط در ردیف", "دانه", "بهداشتی", priceMinor = 4_500, published = true),
    StorefrontProductDto("p6", "نوشابهٔ قدیمی", "قطی", "نوشیدنی", priceMinor = 3_000, published = false, active = false),
  )
  val market = MarketStateDto(
    directory = true,
    listing = MarketListingDto(
      listed = true, category = "grocery", area = "کارته‌پروان", location = MarketLocationDto(34.53, 69.17), updatedAt = now - 86_400_000L,
      categories = listOf(MarketCategoryDto("grocery", "سوپرمارکت", 14), MarketCategoryDto("bakery", "نانوایی", 6), MarketCategoryDto("pharmacy", "دواخانه", 3)),
    ),
  )
  val zones = ZonesForm(
    zones = listOf(
      StorefrontDelivery.ZoneDraft("نزدیکِ دکان", kind = "radius", fee = "50", min = "300", free = "1500", center = "34.53, 69.17", radiusKm = "2"),
      StorefrontDelivery.ZoneDraft("شهرِ نو", kind = "polygon", fee = "100", min = "500", polygon = "34.52, 69.16\n34.54, 69.16\n34.54, 69.19\n34.52, 69.19"),
    ),
    rules = listOf(
      StorefrontDelivery.RuleDraft(),
      StorefrontDelivery.RuleDraft(fulfillment = "pickup", weekdays = setOf(5), range = "16:00-19:00", capacity = "10", cutoff = "30"),
    ),
  )

  /* گفت‌وگو */
  val conversations = listOf(
    ChatConversation(
      "c1", seq = 9, unread = 2, orderNo = 1046, orderId = "o1046", lastMessageAt = now - 3 * MIN,
      lastMessage = ChatLast("text", "customer", now - 3 * MIN, "برنج را دانه‌دراز بفرستید لطفاً"),
      customer = ChatCustomer("مریم رحیمی", "07•• ••• 046"), receipts = ChatReceipts(peerDeliveredSeq = 8, peerReadSeq = 7),
    ),
    ChatConversation("c2", seq = 4, lastMessageAt = now - 18 * MIN, lastMessage = ChatLast("image", "customer", now - 18 * MIN), customer = ChatCustomer("احمد کریمی", "07•• ••• 045")),
    ChatConversation("c3", seq = 6, unread = 1, lastMessageAt = now - 70 * MIN, lastMessage = ChatLast("audio", "customer", now - 70 * MIN), customer = ChatCustomer("سارا نوری", "07•• ••• 044"), orderNo = 1044),
    ChatConversation("c4", seq = 3, lastMessageAt = now - 5 * 60 * MIN, lastMessage = ChatLast("text", "shop", now - 5 * 60 * MIN, "سفارش‌تان رسید، ممنون"), customer = ChatCustomer("حمید صدیقی", "07•• ••• 041")),
    ChatConversation("c5", seq = 2, lastMessageAt = now - 26 * 60 * MIN, lastMessage = ChatLast("text", "customer", now - 26 * 60 * MIN, "…", deleted = true), customer = ChatCustomer("", "07•• ••• 913"), blockedCustomer = true),
  )
  val messages = listOf(
    ChatMessage("m1", seq = 5, sender = "customer", createdAt = now - 30 * MIN, body = "سلام، برنجِ دانه‌دراز دارید؟"),
    ChatMessage("m2", seq = 6, sender = "shop", mine = true, createdAt = now - 28 * MIN, body = "سلام، بله — بستهٔ ۵ کیلویی ۱٬۴۵۰ افغانی."),
    ChatMessage("m3", seq = 7, sender = "customer", createdAt = now - 20 * MIN, kind = "audio", meta = ChatMeta(durationMs = 14_000), media = ChatMediaRef("a1", "audio/ogg", 22_000)),
    ChatMessage("m4", seq = 8, sender = "shop", mine = true, createdAt = now - 12 * MIN, body = "سفارشِ ۱۰۴۶ ثبت شد؛ تا یک ساعتِ دیگر می‌رسد."),
    ChatMessage("m5", seq = 9, sender = "customer", createdAt = now - 3 * MIN, body = "برنج را دانه‌دراز بفرستید لطفاً"),
  )

  /* میزِ تحویل */
  val desk = DeskConfig(
    readable = true, entitled = true, enabled = true, defaultCashCapMinor = 500_000,
    perms = DeskPerms(view = true, assign = true, handover = true, couriers = true, settle = true, photo = true),
  )
  val navid = CourierRow("cr1", name = "نوید", phone = "0700000101", cashCapMinor = 500_000, cashHeldMinor = 120_000, liveCount = 1, liveExpectedMinor = 103_000)
  private val couriers = listOf(
    navid,
    CourierRow("cr2", name = "جاوید", phone = "0700000102", cashCapMinor = 300_000, cashHeldMinor = 260_000, liveCount = 1, liveExpectedMinor = 40_000),
    CourierRow("cr3", name = "سهراب", phone = "0700000103", active = false, cashCapMinor = 300_000),
  )
  val deskOrders = listOf(
    order(2044, "accepted", "delivery", "کامران عزیزی", 4, "storefront"),
    order(2043, "packed", "delivery", "زهرا فرهمند", 11, "phone", paid = true),
    order(2041, "out_for_delivery", "delivery", "بهار سلطانی", 30, "storefront"),
    order(2040, "packed", "delivery", "عمر رسولی", 26, "telegram"),
    order(2037, "ready_for_pickup", "pickup", "نسرین امیری", 40, "phone"),
    order(2036, "picking", "pickup", "داوود هاشمی", 9, "storefront", paid = true),
  )
  private val live = listOf(
    Mission("m1", "o2041", 2041, mode = "shop_courier", courierId = "cr1", status = "moving", live = true, expectedCollectMinor = 103_000, fareMinor = 10_000, createdAt = now - 20 * MIN, orderStatus = "out_for_delivery", customerName = "بهار سلطانی"),
    Mission("m2", "o2040", 2040, mode = "external", externalName = "پیک‌رسانِ شهر", externalRef = "PR-5521", status = "picked_up", live = true, expectedCollectMinor = 103_000, fareMinor = 15_000, createdAt = now - 15 * MIN, orderStatus = "packed", customerName = "عمر رسولی"),
    Mission("m3", "o2037", 2037, mode = "pickup", status = "assigned", live = true, expectedCollectMinor = 103_000, hasCode = true, createdAt = now - 10 * MIN, orderStatus = "ready_for_pickup", customerName = "نسرین امیری"),
  )
  private val companies = listOf(
    ExternalCompany("پیک‌رسانِ شهر", assignments = 42, live = 1, delivered = 39, lastFareMinor = 15_000, lastUsedAt = now - 2 * 60 * MIN),
    ExternalCompany("بارِ سریع", assignments = 8, live = 0, delivered = 8, lastFareMinor = 20_000, lastUsedAt = now - 3 * 24 * 60 * MIN),
  )
  val deskData = DeskData(couriers, live, deskOrders, companies)
  val settle = SettlementPreview(
    navid,
    listOf(
      SettleRow("a1", "o2031", 2031, now - 5 * 60 * MIN, expectedCollectMinor = 145_000, cashCollectedMinor = 145_000, fareMinor = 10_000),
      SettleRow("a2", "o2033", 2033, now - 3 * 60 * MIN, expectedCollectMinor = 98_000, cashCollectedMinor = 90_000, collectionDiffMinor = -8_000, fareMinor = 10_000),
      SettleRow("a3", "o2035", 2035, now - 60 * MIN, expectedCollectMinor = 0, cashCollectedMinor = 0, fareMinor = 10_000),
    ),
    serverTime = now,
  )
}
