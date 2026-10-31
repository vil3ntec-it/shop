package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.invoiceLabel
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.components.common.OrderStatusTone
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  بخشِ «سفارش‌ها» — صفِ کارمند برای سفارش‌های ویترین، تلفن، تلگرام و واتس‌اپ
 *  (فاز ۴: ORD-01، ۰۶، ۰۷، ۱۱، ۱۲، ۱۶، ۱۷، ۲۱).
 *
 *  ⛔ فقط وقتی `OrdersLive.config` تهی نیست (سرور گفته `enabled`)؛ وگرنه
 *  صفحه خودش را می‌بندد.
 *  ⛔ دکمه‌های گذار از `OrderStates` (آینهٔ سنجیده‌شدهٔ `order-states.js`) و فقط
 *  با `orders.manage`؛ سرور هر گذار را دوباره می‌سنجد.
 *  ⛔ شمارهٔ کامل و دکمهٔ تماس فقط وقتی سرور شماره را داده (`orders.contact`).
 *  نمای کارتی/جدولی: `ViewModePref` (گوشی همیشه کارتی).
 *  ⛔ موجودی (تصمیمِ صاحبِ مخزن): سایت و سرور به موجودی دست نمی‌زنند. «مشتری گرفت»، «ارسال
 *  شد» یا «تحویل شد» ⇒ همین برنامه یک فروشِ عادی با قیمت‌های سفارش ثبت می‌کند
 *  (`OrderFulfillment`) و همان لحظه همگام می‌کند؛ کمبود ⇒ گذار فرستاده نمی‌شود و پیامِ روشن.
 */
@Composable
fun OrdersScreen(store: ShopStore, snackbar: SnackbarHostState, onLeave: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val cfg = OrdersLive.config.value
  if (cfg == null) {
    LaunchedEffect(Unit) { onLeave() }
    return
  }
  val repo = remember(context) { OrdersLive.repository(context) }
  val colors = Shop.colors
  val view = rememberViewMode("orders", ScreenKind.MANAGEMENT)

  var tab by rememberSaveable { mutableStateOf("new") }
  var items by remember { mutableStateOf<List<OrderDto>>(emptyList()) }
  var cursor by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }
  var openId by remember { mutableStateOf<String?>(null) }
  var creating by remember { mutableStateOf(false) }; var pickFor by remember { mutableStateOf<OrderDto?>(null) }   // + برگهٔ جمع‌آوری (PCK)
  var reload by remember { mutableStateOf(0) }
  var now by remember { mutableStateOf(System.currentTimeMillis()) }
  val rev = OrdersLive.rev.value

  suspend fun load(reset: Boolean) {
    loading = true
    repo.list(tab, if (reset) null else cursor)
      .onSuccess { page ->
        items = if (reset) page.items else items + page.items
        cursor = page.nextCursor
        error = null
        now = System.currentTimeMillis()
      }
      .onFailure { error = it.userMessage }
    loading = false
  }
  LaunchedEffect(tab, rev, reload) { load(reset = true) }

  //  راهِ ورودِ اعلان: سفارشِ خواسته‌شده باز می‌شود (جزئیات تازه از سرور)
  val pending = OrdersLive.pendingOrderId.value
  LaunchedEffect(pending) {
    if (pending != null) { openId = pending; OrdersLive.pendingOrderId.value = null }
  }

  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("سفارش‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = colors.text)
        Text("ویترین، تلفن، تلگرام و واتس‌اپ — در یک صف", style = MaterialTheme.typography.bodySmall, color = colors.muted)
      }
      ViewModeToggle(view)
      if (cfg.permissions.create) {
        Spacer(Modifier.width(8.dp))
        TohidButton("+ سفارشِ دستی", onClick = { creating = true })
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      "اعلانِ فوری (وقتی برنامه بسته است) هنوز وصل نیست؛ تا برنامه باز است، صف هر چند ثانیه خودش تازه می‌شود.",
      style = MaterialTheme.typography.labelSmall, color = colors.muted,
    )
    //  دورهٔ گذار: دستگاهِ کهنه فروشِ سفارش را دستی می‌زند و این برنامه خودکار ⇒ دو فروش
    if (OrdersLive.rolloutNotice()) {
      Spacer(Modifier.height(8.dp))
      FulfillNotice(title = OrdersLive.ROLLOUT_NOTE, body = "", action = null, onAction = {})
    }
    //  بررسیِ صاحب: کاری که خودکار نشد و نباید بشود (تحویلِ پیش از راه‌اندازی، برگشت پس از مرجوعی)
    val review = OrdersLive.review.value
    if (review.isNotEmpty() && cfg.permissions.refund) {
      Spacer(Modifier.height(8.dp))
      FulfillNotice(
        title = "${review.size} سفارش بررسیِ صاحب می‌خواهد",
        body = review.take(3).joinToString("\n") { it.message },
        //  باز کردنِ نخستین؛ «بررسی شد» در جزئیاتِ همان سفارش
        action = "بررسی",
        onAction = { openId = review.first().orderId },
      )
    }
    //  تحویل‌هایی که فروششان ثبت نشد (قفل، دیسک…) — موجودی تا ثبتِ فروش کم نشده است
    val unposted = OrdersLive.unposted.value
    if (unposted.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      FulfillNotice(
        title = "${unposted.size} سفارشِ تحویل‌شده هنوز فروش ندارد — موجودی کم نشده",
        body = unposted.take(3).joinToString("\n") { it.message },
        action = "دوباره ثبت کن",
        onAction = { scope.launch { OrdersLive.reconcile(context, store, force = true) } },
      )
    }
    //  تحویل‌هایی که این حساب نمی‌تواند فروش یا لغوشان را بنویسد (پرچمِ سرورِ همان سفارش:
    //  اجازه یا سقفِ تخفیفِ همین کاربر) — منتظرِ دستگاهِ صاحب یا مدیر
    val waiting = OrdersLive.waiting.value
    if (waiting.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      FulfillNotice(
        title = "${waiting.size} سفارشِ تحویل‌شده منتظرِ دستگاهِ صاحب یا مدیر است",
        body = (listOf("این حساب فروش یا لغوِ این‌ها را نمی‌تواند ثبت کند (اجازه یا سقفِ تخفیف)؛ با باز شدنِ برنامه روی دستگاهِ صاحب یا مدیر خودکار ثبت می‌شود.") +
          waiting.take(3).map { it.message }).joinToString("\n"),
        action = null,
        onAction = {},
      )
    }
    //  فروش ثبت شد ولی کالا در دفتر کم بود (INV-19: ثبت‌شده رد نمی‌شود) — ورودِ انبار را درست کنید
    val shortfalls = OrdersLive.shortfalls.value
    if (shortfalls.isNotEmpty()) {
      Spacer(Modifier.height(8.dp))
      FulfillNotice(
        title = "موجودیِ ${shortfalls.size} کالا پس از تحویل منفی شد — ورودِ انبار را درست کنید",
        body = shortfalls.take(3).joinToString("\n") { it.message },
        action = null,
        onAction = {},
      )
    }
    Spacer(Modifier.height(10.dp))
    //  برگه‌های ORD-12 با شمارش
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      val counts = OrdersLive.counts.value
      OrderStates.TABS.forEach { t ->
        val n = counts[t] ?: 0
        TohidFilterChip(
          label = OrderText.tab(t) + if (n > 0) " ($n)" else "",
          selected = tab == t,
          onClick = { if (tab != t) { tab = t; items = emptyList(); cursor = null } },
        )
      }
    }
    Spacer(Modifier.height(10.dp))

    when {
      loading && items.isEmpty() -> TohidLoadingState(rows = 4)
      error != null && items.isEmpty() ->
        TohidErrorState(title = "فهرست نیامد", description = error ?: "", onRetry = { reload++ })
      items.isEmpty() -> TohidEmptyState(
        title = "سفارشی نیست",
        description = "در برگهٔ «${OrderText.tab(tab)}» سفارشی نیست.",
        icon = Icons.Filled.ShoppingBasket,
        actionText = if (tab == "new" && cfg.permissions.create) "+ سفارشِ دستی" else null,
        onAction = if (tab == "new" && cfg.permissions.create) ({ creating = true }) else null,
      )
      else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (view.mode == ViewMode.TABLE) {
          item(key = "head") { OrderTableHeader() }
          items(items, key = { it.id }) { o -> OrderTableRow(o, now) { openId = o.id } }
        } else {
          items(items, key = { it.id }) { o -> OrderCard(o, now) { openId = o.id } }
        }
        if (cursor != null) item(key = "more") {
          TohidSecondaryButton(
            "سفارش‌های بیشتر",
            onClick = { scope.launch { load(reset = false) } },
            modifier = Modifier.fillMaxWidth(),
            enabled = !loading,
          )
        }
      }
    }
  }
  pickFor?.let { o -> OrderPickHost(o, ir.vil3ntec.tohid.data.repo.Backend.api(context), { ok -> if (ok) Haptics.success(context) else Haptics.confirm(context) }, onClose = { pickFor = null; openId = o.id }) { reload++; scope.launch { OrdersLive.pollSummary(context) } } }
  openId?.let { id ->
    OrderDetailSheet(
      id = id, repo = repo, cfg = cfg, store = store, snackbar = snackbar,
      onClose = { openId = null }, onPick = { o -> openId = null; pickFor = o },
      onChanged = { reload++; scope.launch { OrdersLive.pollSummary(context) } },
    )
  }
  if (creating) {
    NewOrderSheet(repo, cfg, onClose = { creating = false }) { o ->
      creating = false
      tab = o.tab.ifEmpty { "new" }
      reload++
      scope.launch { OrdersLive.pollSummary(context) }
      scope.launch { snackbar.showSnackbar("سفارش #${o.orderNo} ثبت شد") }
    }
  }
}

/* ============================ نشان‌ها ============================ */

/**
 *  وضعیت: متن + رنگ + نقطه، هرگز رنگِ تنها (VISUAL-DIRECTION §۱). رنگ از هفت توکنِ
 *  وضعیتِ VILL3N و همان نگاشتِ پیک و میزِ تحویل (`OrderStatusTone`) — «در مسیر» همه‌جا آبی.
 */
@Composable
fun OrderStatusPill(status: String) {
  StatusPill(OrderText.status(status), OrderStatusTone.order(status))
}

@Composable
private fun Tag(text: String, warn: Boolean = false) {
  val c = Shop.colors
  Text(
    text,
    style = MaterialTheme.typography.labelSmall,
    color = c.text,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (warn) c.warningTint else c.surface2)
      .padding(horizontal = 8.dp, vertical = 2.dp),
  )
}

private fun fulfillmentLabel(o: OrderDto) = if (o.fulfillment == "delivery") "ارسال" else "حضوری"

private fun timeLabel(o: OrderDto, now: Long) =
  if (o.scheduledFor != null) "برای ${orderStamp(o.scheduledFor)}" else OrderText.ago(o.createdAt, now)

/** زمانِ محلی به تقویمِ خورشیدی: «7/25 14:05» */
private fun orderStamp(ms: Long): String {
  val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
  val j = Jalali.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
  val hh = cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
  val mm = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
  return "${j.month}/${j.day} $hh:$mm"
}

/* ============================ کارت و جدول ============================ */

/** کارتِ سفارش (ORD-11): ارسال/حضوری، اقلام، نقد/پرداخت‌شده، زمان */
@Composable
private fun OrderCard(o: OrderDto, now: Long, onOpen: () -> Unit) {
  val c = Shop.colors
  TohidCard(onClick = onOpen) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("#${o.orderNo}", fontWeight = FontWeight.Bold, color = c.text)
      Spacer(Modifier.width(8.dp))
      Text(o.customer.name, Modifier.weight(1f), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
      OrderStatusPill(o.status)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
      Tag(fulfillmentLabel(o))
      Tag("${o.itemsCount} قلم")
      Tag(OrderText.pay(o.paymentStatus, o.paymentMethod))
      Tag(OrderText.source(o.source))
      if (o.isTest) Tag("آزمایشی", warn = true)
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(timeLabel(o, now), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.muted)
      Text("${OrderText.money(o.totalMinor)} افغانی", fontWeight = FontWeight.Bold, color = c.text)
    }
  }
}

private val COLS = listOf("سفارش" to 0.8f, "مشتری" to 1.6f, "وضعیت" to 1.5f, "تحویل" to 0.8f, "اقلام" to 0.6f, "پرداخت" to 1.3f, "منبع" to 0.8f, "زمان" to 1.1f, "مبلغ" to 1.1f)

@Composable
private fun OrderTableHeader() {
  val c = Shop.colors
  Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
    COLS.forEach { (label, w) ->
      Text(label, Modifier.weight(w), style = MaterialTheme.typography.labelMedium, color = c.muted, fontWeight = FontWeight.SemiBold)
    }
  }
  HorizontalDivider(color = c.border)
}

/** ردیفِ جدول — همان داده و همان کارِ کلیکِ کارت */
@Composable
private fun OrderTableRow(o: OrderDto, now: Long, onOpen: () -> Unit) {
  val c = Shop.colors
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.surface)
      .clickable(role = Role.Button, onClick = onOpen).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val cell = @Composable { text: String, w: Float, bold: Boolean ->
      Text(text, Modifier.weight(w), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
    cell("#${o.orderNo}" + if (o.isTest) " ·آزمایشی" else "", COLS[0].second, true)
    Column(Modifier.weight(COLS[1].second)) {
      Text(o.customer.name, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
      if (o.customer.phoneMasked.isNotEmpty()) Text(o.customer.phoneMasked, color = c.muted, style = MaterialTheme.typography.labelSmall)
    }
    Box(Modifier.weight(COLS[2].second)) { OrderStatusPill(o.status) }
    cell(fulfillmentLabel(o), COLS[3].second, false)
    cell(o.itemsCount.toString(), COLS[4].second, false)
    cell(OrderText.pay(o.paymentStatus, o.paymentMethod), COLS[5].second, false)
    cell(OrderText.source(o.source), COLS[6].second, false)
    cell(timeLabel(o, now), COLS[7].second, false)
    cell(OrderText.money(o.totalMinor), COLS[8].second, true)
  }
}

/* ============================ جزئیات ============================ */

@Composable
private fun SheetColumn(content: @Composable ColumnScope.() -> Unit) {
  //  کامپیوتر: خودِ AdaptiveSheet می‌لغزد؛ گوشی: برگهٔ پایین نه، پس این‌جا
  val scroll = if (Platform.DESKTOP) Modifier else Modifier.verticalScroll(rememberScrollState())
  Column(Modifier.fillMaxWidth().then(scroll).padding(horizontal = 20.dp).padding(bottom = 24.dp), content = content)
}

@Composable
private fun OrderDetailSheet(
  id: String,
  repo: OrdersRepository,
  cfg: OrdersConfigDto,
  store: ShopStore,
  snackbar: SnackbarHostState,
  onClose: () -> Unit,
  onChanged: () -> Unit, onPick: (OrderDto) -> Unit = {},
) {
  val c = Shop.colors
  val context = LocalContext.current
  val ledger by store.data.collectAsState()
  val scope = rememberCoroutineScope()
  val uri = LocalUriHandler.current
  val clipboard = LocalClipboardManager.current
  var order by remember(id) { mutableStateOf<OrderDto?>(null) }
  var error by remember(id) { mutableStateOf<String?>(null) }
  var act by remember(id) { mutableStateOf<String?>(null) }
  var busy by remember(id) { mutableStateOf(false) }
  var actError by remember(id) { mutableStateOf<String?>(null) }
  val rev = OrdersLive.rev.value

  //  صفِ زنده: اگر کسِ دیگری وضعیت را عوض کرد، همین‌جا تازه می‌شود
  LaunchedEffect(id, rev) {
    repo.get(id)
      .onSuccess { order = it; error = null }
      .onFailure { if (order == null) { error = it.userMessage } }
  }

  /**
   *  فروشِ تحویل (یا لغوِ آن برای سفارشِ برگشته) در همین دفتر — همیشه پس از پرسیدن از سرور
   *  (`OrdersLive.postById`)؛ یکتا با شناسهٔ سفارش. نتیجه همیشه گفته می‌شود.
   */
  suspend fun postSale(o: OrderDto) {
    val back = o.status in OrderFulfillment.BACK_IN_SHOP
    when (val r = OrdersLive.postById(context, store, o.id)) {
      OrdersLive.Posted.Saved -> snackbar.showSnackbar(
        if (back) "فروشِ سفارش #${o.orderNo} لغو شد؛ کالا به موجودی برگشت و همگام می‌شود"
        else "فروشِ سفارش #${o.orderNo} ثبت شد؛ موجودی کم شد و همگام می‌شود"
      )
      OrdersLive.Posted.Already -> Unit
      is OrdersLive.Posted.Skipped -> snackbar.showSnackbar(r.message)
      is OrdersLive.Posted.Failed -> snackbar.showSnackbar(r.message)
      is OrdersLive.Posted.Review -> snackbar.showSnackbar(r.message)
    }
  }

  /** «بررسی شد» ِ صاحب برای ردیفِ بررسیِ همین سفارش — برای همهٔ دستگاه‌ها */
  suspend fun ackReview(o: OrderDto) {
    val err = OrdersLive.ackReview(context, o.id)
    snackbar.showSnackbar(err ?: "سفارش #${o.orderNo}: «بررسی شد» ثبت شد؛ از فهرستِ همهٔ دستگاه‌ها رفت")
  }

  fun send(to: String, reason: String = "", note: String = "", ready: Int? = null) {
    val o = order ?: return
    if (busy) return
    //  تحویل ⇒ فروش در همین دفتر: پیش از فرستادنِ گذار سنجیده می‌شود (سیاستِ موجودیِ صندوق)؛
    //  کمبود ⇒ گذار نمی‌رود و پیام روشن است، نه سفارشِ «تحویل‌شده» بی فروش
    if (to in OrderFulfillment.HANDED_OVER && OrdersLive.canRecord(cfg, o) && OrderFulfillment.saleOf(ledger, o.id) == null && !o.isTest) {
      val dry = OrderFulfillment.fulfill(
        ledger, o, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(),
        blockExpired = ir.vil3ntec.tohid.data.ShopPolicy.blockExpired(context),
      )
      if (dry is OrderFulfillment.Result.Failed) {
        val msg = "تحویل ثبت نشد — ${dry.message}"
        if (act != null) actError = msg else scope.launch { snackbar.showSnackbar(msg) }
        return
      }
    }
    busy = true
    actError = null
    scope.launch {
      repo.transition(o.id, to, o.status, reason, note, ready)
        .onSuccess {
          order = it
          act = null
          onChanged()
          scope.launch { snackbar.showSnackbar("سفارش #${it.orderNo}: ${OrderText.status(it.status)}") }
          if (it.status in OrderFulfillment.HANDED_OVER || it.status in OrderFulfillment.BACK_IN_SHOP) postSale(it)
        }
        .onFailure { f ->
          if (f.code == "status_changed") {
            act = null
            scope.launch { snackbar.showSnackbar("وضعیتِ این سفارش همین حالا جای دیگری عوض شد؛ وضعیتِ تازه را ببینید.") }
            repo.get(o.id).onSuccess {
              order = it
              //  دستگاهِ دیگر یا پیک تحویل داد ⇒ فقط اگر سرور هم بگوید لازم است (postSale می‌پرسد)
              if (OrderFulfillment.needsSale(ledger, it, OrdersLive.handoverSince()) || OrderFulfillment.needsReverse(ledger, it)) postSale(it)
            }
            onChanged()
          } else if (act != null) {
            actError = f.userMessage
          } else {
            scope.launch { snackbar.showSnackbar(f.userMessage) }
          }
        }
      busy = false
    }
  }

  AdaptiveSheet(onDismissRequest = onClose, containerColor = c.surfaceSolid) {
    SheetColumn {
      val o = order
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (o != null) "سفارش #${o.orderNo}" else "سفارش", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.text)
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "بستن", tint = c.muted) }
      }
      if (o == null) {
        if (error != null) TohidErrorState(title = "سفارش نیامد", description = error ?: "", onRetry = { error = null; scope.launch { repo.get(id).onSuccess { order = it }.onFailure { error = it.userMessage } } })
        else TohidLoadingState(rows = 3)
        return@SheetColumn
      }
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OrderStatusPill(o.status)
        if (o.statusReason.isNotEmpty()) Text("دلیل: ${OrderText.reason(o.statusReason)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
        if (o.readyAt != null) Text("آماده تا ${orderStamp(o.readyAt)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
      }
      Spacer(Modifier.height(8.dp))
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Tag(fulfillmentLabel(o)); Tag("${o.itemsCount} قلم"); Tag(OrderText.pay(o.paymentStatus, o.paymentMethod))
        Tag(OrderText.source(o.source)); Tag(timeLabel(o, System.currentTimeMillis()))
        if (o.isTest) Tag("آزمایشی", warn = true)
      }

      //  فروشِ تحویل در دفترِ دکان (موجودی فقط این‌جا کم می‌شود)
      val sold = OrderFulfillment.saleOf(ledger, o.id)
      if (sold != null) {
        Spacer(Modifier.height(6.dp))
        Text(
          "فروشِ تحویل: فاکتور #${sold.invoiceLabel() ?: "—"} — کالایی که پیک برگرداند ⇒ همین سفارش را «برگشت به دکان» بزنید (فروش خودش لغو می‌شود)؛ «مرجوعی» نزنید",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
      } else if (OrdersLive.review.value.any { it.orderId == o.id }) {
        Spacer(Modifier.height(8.dp))
        FulfillNotice(
          title = "بررسیِ صاحب — فروشِ این سفارش خودکار ثبت نمی‌شود",
          body = OrdersLive.review.value.first { it.orderId == o.id }.message,
          action = if (cfg.permissions.refund) "بررسی شد" else null,
          onAction = { if (!busy) scope.launch { busy = true; ackReview(o); busy = false } },
        )
      } else if (OrderFulfillment.needsSale(ledger, o, OrdersLive.handoverSince())) {
        Spacer(Modifier.height(8.dp))
        val able = cfg.permissions.manage && OrdersLive.canRecord(cfg, o) && !OrdersLive.isWaiting(o.id)
        FulfillNotice(
          title = "فروشِ این سفارش هنوز در دفترِ دکان نیست — موجودی کم نشده",
          body = OrdersLive.unposted.value.firstOrNull { it.orderId == o.id }?.message
            ?: if (able) "کالا از دکان بیرون رفته؛ فروش را با قیمت‌های همین سفارش ثبت کنید." else OrdersLive.SKIPPED_NOTE,
          action = if (able) "ثبتِ فروش" else null,
          onAction = { if (!busy) scope.launch { busy = true; postSale(o); busy = false } },
        )
      }
      if (sold != null && OrderFulfillment.needsReverse(ledger, o)) {
        Spacer(Modifier.height(8.dp))
        //  پیش‌تر «مرجوعیِ» دستی گرفته ⇒ لغوِ خودکار نه؛ صاحب بررسی کند
        val blocked = OrderFulfillment.reverseBlock(ledger, o)
        val able = blocked == null && cfg.permissions.manage && OrdersLive.canReverse(cfg, o) && !OrdersLive.isWaiting(o.id)
        //  پس از «مرجوعیِ» دستی: صاحب بررسی می‌کند و «بررسی شد» می‌زند (دیگر لغوِ خودکار نیست)
        val ackable = blocked != null && cfg.permissions.refund && OrdersLive.review.value.any { it.orderId == o.id }
        FulfillNotice(
          title = "کالای این سفارش به دکان برگشته ولی فروشش هنوز لغو نشده",
          body = blocked ?: if (able) "لغوِ همان فاکتور موجودی را برمی‌گرداند؛ پولی از دخل جابه‌جا نمی‌شود." else OrdersLive.SKIPPED_REVERSE_NOTE,
          action = if (able) "لغوِ فروشِ تحویل" else if (ackable) "بررسی شد" else null,
          onAction = {
            if (!busy) scope.launch { busy = true; if (able) postSale(o) else ackReview(o); busy = false }
          },
        )
      }

      val (actions, pickable) = orderPickActions(o, cfg)   // جمع‌آوری (PCK): «جمع‌آوری» و «بسته‌بندی» فقط از برگه
      if (actions.isNotEmpty() || pickable) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (pickable) OrderPickButton(o, busy) { onPick(o) }; actions.forEach { to ->
            val needsDialog = OrderStates.needsReason(to) || to == "accepted" || to in OrderStates.MONEY_TARGETS
            val click = { if (needsDialog) { actError = null; act = to } else send(to) }
            if (to in OrderText.DANGER) TohidDangerButton(OrderText.action(to), onClick = click, enabled = !busy)
            else TohidButton(OrderText.action(to), onClick = click, enabled = !busy)
          }
        }
      }

      Section("مشتری")
      Text(o.customer.name, color = c.text, fontWeight = FontWeight.SemiBold)
      val phone = o.customer.phone
      if (o.canContact && !phone.isNullOrEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(phone, Modifier.weight(1f), color = c.text, fontWeight = FontWeight.Bold)
          TohidSecondaryButton("تماس", onClick = {
            runCatching { uri.openUri("tel:$phone") }.onFailure {
              clipboard.setText(AnnotatedString(phone))
              scope.launch { snackbar.showSnackbar("شماره کپی شد: $phone") }
            }
          })
        }
      } else {
        Text(
          if (o.customer.phoneMasked.isNotEmpty()) "${o.customer.phoneMasked} — شمارهٔ کامل فقط برای صاحب و مدیرِ دکان" else "شماره ندارد",
          style = MaterialTheme.typography.bodySmall, color = c.muted,
        )
      }
      if (o.fulfillment == "delivery") {
        Section("نشانی")
        val bits = listOfNotNull(
          o.address.text.takeIf { it.isNotEmpty() },
          o.address.landmark.takeIf { it.isNotEmpty() }?.let { "نشانه: $it" },
          if (o.address.lat != null && o.address.lng != null) "پین: ${o.address.lat}, ${o.address.lng}" else null,
        )
        Text(bits.joinToString(" · ").ifEmpty { "—" }, color = c.text)
      }
      if (o.note.isNotEmpty()) { Section("یادداشت"); Text(o.note, color = c.text) }

      Section("اقلام")
      o.lines.forEach { l ->
        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
          Column(Modifier.weight(1f)) {
            Text(l.name, color = c.text)
            if (l.note.isNotEmpty()) Text(l.note, style = MaterialTheme.typography.labelSmall, color = c.muted)
          }
          Text("${OrderText.qty(l.qtyMilli)}${if (l.unit.isNotEmpty()) " ${l.unit}" else ""} × ${OrderText.money(l.unitPriceMinor)}", color = c.muted, style = MaterialTheme.typography.bodySmall)
          Spacer(Modifier.width(10.dp))
          Text(OrderText.money(l.lineTotalMinor), color = c.text, fontWeight = FontWeight.SemiBold)
        }
      }
      HorizontalDivider(Modifier.padding(vertical = 6.dp), color = c.border)
      SumRow("جمعِ اقلام", OrderText.money(o.subtotalMinor))
      if (o.discountMinor > 0) SumRow("تخفیف", "-" + OrderText.money(o.discountMinor))
      if (o.deliveryFeeMinor > 0) SumRow("هزینهٔ ارسال", OrderText.money(o.deliveryFeeMinor))
      SumRow("جمعِ کل", "${OrderText.money(o.totalMinor)} افغانی", bold = true)

      //  خطِ زمانی (ORD-16): هر تغییر با زمان، کننده و دلیل
      Section("خطِ زمانی")
      o.events.forEach { e ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
          Box(Modifier.padding(top = 6.dp).size(8.dp).clip(CircleShape).background(c.primary))
          Spacer(Modifier.width(10.dp))
          Column(Modifier.weight(1f)) {
            Text(orderStamp(e.at), style = MaterialTheme.typography.labelSmall, color = c.muted)
            Text(
              when (e.kind) {
                "created" -> "ثبت شد"
                "cod" -> "نقد: ${OrderText.COD[e.toStatus] ?: e.toStatus}"
                else -> "${OrderText.status(e.fromStatus)} ← ${OrderText.status(e.toStatus)}"
              },
              color = c.text, fontWeight = FontWeight.SemiBold,
            )
            val who = when (e.actorType) { "system" -> "سیستم"; "customer" -> "مشتری"; else -> "کارمند" }
            val extra = buildString {
              append(who)
              if (e.reason.isNotEmpty()) append(" · دلیل: ").append(OrderText.reason(e.reason))
              e.detail.readyAt?.let { append(" · آماده تا ").append(orderStamp(it)) }
            }
            Text(extra, style = MaterialTheme.typography.bodySmall, color = c.muted)
            if (e.note.isNotEmpty()) Text(e.note, style = MaterialTheme.typography.bodySmall, color = c.text)
          }
        }
      }
    }
  }

  val o = order
  val to = act
  if (o != null && to != null) {
    OrderActionDialog(
      order = o, to = to, busy = busy, error = actError,
      onDismiss = { act = null },
      onConfirm = { reason, note, ready -> send(to, reason, note, ready) },
    )
  }
}

/** هشدارِ «تحویل بی فروش» — رنگِ هشدار + متن، با کارِ بعدی (VISUAL-DIRECTION: هرگز رنگِ تنها) */
@Composable
private fun FulfillNotice(title: String, body: String, action: String?, onAction: () -> Unit) {
  val c = Shop.colors
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(c.warningTint).padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(c.warning))
      Spacer(Modifier.width(8.dp))
      Text(title, Modifier.weight(1f), color = c.text, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
    if (body.isNotBlank()) {
      Spacer(Modifier.height(4.dp))
      Text(body, color = c.text, style = MaterialTheme.typography.bodySmall)
    }
    if (action != null) {
      Spacer(Modifier.height(8.dp))
      TohidSecondaryButton(action, onClick = onAction)
    }
  }
}

@Composable
private fun Section(title: String) {
  Spacer(Modifier.height(14.dp))
  Text(title, style = MaterialTheme.typography.labelLarge, color = Shop.colors.muted)
  Spacer(Modifier.height(4.dp))
}

@Composable
private fun SumRow(label: String, value: String, bold: Boolean = false) {
  Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
    Text(label, Modifier.weight(1f), color = Shop.colors.text, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    Text(value, color = Shop.colors.text, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
  }
}

/** پنجرهٔ گذار: دلیل (ORD-06)، زمانِ آماده هنگامِ قبول (ORD-07)، تأییدِ بازپرداخت */
@Composable
private fun OrderActionDialog(
  order: OrderDto,
  to: String,
  busy: Boolean,
  error: String?,
  onDismiss: () -> Unit,
  onConfirm: (reason: String, note: String, ready: Int?) -> Unit,
) {
  val c = Shop.colors
  val choices = remember(order.status, to) { OrderStates.reasonChoices(order.status, to) }
  var reason by remember(to) { mutableStateOf(if (choices.size == 1) choices[0] else "") }
  var note by remember(to) { mutableStateOf("") }
  var ready by remember(to) { mutableStateOf<Int?>(15) }
  val needReason = OrderStates.needsReason(to)
  val valid = !needReason || (reason.isNotEmpty() && (reason != "other" || note.isNotBlank()))

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = c.surfaceSolid,
    title = { Text("${OrderText.action(to)} — سفارش #${order.orderNo}", color = c.text) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState())) {
        if (needReason) {
          Text("دلیل", style = MaterialTheme.typography.labelLarge, color = c.muted)
          choices.forEach { r ->
            Row(
              Modifier.fillMaxWidth().heightIn(min = 44.dp).selectable(selected = reason == r, role = Role.RadioButton) { reason = r },
              verticalAlignment = Alignment.CenterVertically,
            ) {
              RadioButton(selected = reason == r, onClick = null)
              Spacer(Modifier.width(8.dp))
              Text(OrderText.reason(r), color = c.text)
            }
          }
          Spacer(Modifier.height(8.dp))
          TohidTextField(
            value = note, onValueChange = { if (it.length <= 300) note = it },
            label = "توضیح", supporting = "برای «دلیلِ دیگر» لازم است", singleLine = false,
          )
        }
        if (to == "accepted") {
          Text("زمانِ آماده شدن", style = MaterialTheme.typography.labelLarge, color = c.muted)
          Spacer(Modifier.height(6.dp))
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TohidFilterChip(label = "بی زمان", selected = ready == null, onClick = { ready = null })
            OrderText.READY_MINUTES.forEach { m -> TohidFilterChip(label = "$m دقیقه", selected = ready == m, onClick = { ready = m }) }
          }
        }
        if (to in OrderStates.MONEY_TARGETS) {
          Text("پول به مشتری برمی‌گردد (${OrderText.money(order.totalMinor)} افغانی). مطمئنید؟", color = c.text)
        }
        if (error != null) {
          Spacer(Modifier.height(8.dp))
          Text(error, color = c.danger, style = MaterialTheme.typography.bodySmall)
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(reason, note, if (to == "accepted") ready else null) }, enabled = valid && !busy) { Text("تأیید") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
  )
}

/* ============================ سفارشِ دستی (ORD-21) ============================ */

@Composable
private fun NewOrderSheet(
  repo: OrdersRepository,
  cfg: OrdersConfigDto,
  onClose: () -> Unit,
  onCreated: (OrderDto) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  //  کلیدِ یکتایی یک بار برای همین فرم: «ثبت» دوباره (قطعِ نت، دو بار زدن) همان سفارش است
  val key = remember { OrdersRepository.newKey() }
  var form by remember { mutableStateOf(OrderDraft.Form()) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val sources = cfg.sources.ifEmpty { listOf("phone", "telegram", "whatsapp", "manual") }
  val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)

  fun submit() {
    if (busy) return
    when (val r = OrderDraft.build(form)) {
      is OrderDraft.Result.Error -> error = r.message
      is OrderDraft.Result.Ok -> {
        busy = true
        error = null
        scope.launch {
          repo.create(key, r.body)
            .onSuccess { onCreated(it) }
            .onFailure { f ->
              error = if (f.retryable) "اتصال قطع شد. دوباره «ثبتِ سفارش» را بزنید — سفارش دو بار ثبت نمی‌شود." else f.userMessage
            }
          busy = false
        }
      }
    }
  }

  AdaptiveSheet(onDismissRequest = onClose, containerColor = c.surfaceSolid) {
    SheetColumn {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("سفارشِ دستی", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.text)
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "بستن", tint = c.muted) }
      }
      Text("از کجا", style = MaterialTheme.typography.labelLarge, color = c.muted)
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sources.forEach { s -> TohidFilterChip(label = OrderText.source(s), selected = form.source == s, onClick = { form = form.copy(source = s) }) }
      }
      Spacer(Modifier.height(8.dp))
      Text("تحویل", style = MaterialTheme.typography.labelLarge, color = c.muted)
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TohidFilterChip(label = "حضوری (از دکان)", selected = form.fulfillment == "pickup", onClick = { form = form.copy(fulfillment = "pickup") })
        TohidFilterChip(label = "ارسال", selected = form.fulfillment == "delivery", onClick = { form = form.copy(fulfillment = "delivery") })
      }
      Spacer(Modifier.height(8.dp))
      TohidTextField(form.name, { form = form.copy(name = it.take(80)) }, label = "نامِ مشتری")
      TohidTextField(form.phone, { form = form.copy(phone = it.take(20)) }, label = "شماره",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
      if (form.fulfillment == "delivery") {
        TohidTextField(form.addressText, { form = form.copy(addressText = it.take(300)) }, label = "نشانی")
        TohidTextField(form.landmark, { form = form.copy(landmark = it.take(200)) }, label = "نشانه")
        TohidTextField(form.deliveryFee, { form = form.copy(deliveryFee = it.take(14)) }, label = "هزینهٔ ارسال (افغانی)", keyboardOptions = decimal)
      }
      Spacer(Modifier.height(10.dp))
      Text("اقلام (مقدار و فی به افغانی)", style = MaterialTheme.typography.labelLarge, color = c.muted)
      form.lines.forEachIndexed { i, l ->
        fun upd(n: OrderDraft.LineInput) { form = form.copy(lines = form.lines.toMutableList().also { it[i] = n }) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          TohidTextField(l.name, { upd(l.copy(name = it.take(200))) }, label = "قلمِ ${i + 1}", modifier = Modifier.weight(2f))
          TohidTextField(l.qty, { upd(l.copy(qty = it.take(14))) }, label = "مقدار", modifier = Modifier.weight(1f), keyboardOptions = decimal)
          TohidTextField(l.price, { upd(l.copy(price = it.take(16))) }, label = "فی", modifier = Modifier.weight(1.2f), keyboardOptions = decimal)
          if (form.lines.size > 1) IconButton(onClick = { form = form.copy(lines = form.lines.filterIndexed { j, _ -> j != i }) }) {
            Icon(Icons.Filled.Close, contentDescription = "حذفِ قلمِ ${i + 1}", tint = c.muted)
          }
        }
      }
      TextButton(onClick = { form = form.copy(lines = form.lines + OrderDraft.LineInput(qty = "1")) }) { Text("+ قلمِ دیگر") }
      TohidTextField(form.note, { form = form.copy(note = it.take(500)) }, label = "یادداشت", singleLine = false)
      Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = form.test, role = Role.Checkbox) { form = form.copy(test = !form.test) },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(checked = form.test, onCheckedChange = null)
        Spacer(Modifier.width(6.dp))
        Text("سفارشِ آزمایشی (برای آزمودنِ صف و چاپ)", color = c.text)
      }
      if (error != null) Text(error ?: "", color = c.danger, style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TohidSecondaryButton("انصراف", onClick = onClose, modifier = Modifier.weight(1f))
        TohidButton("ثبتِ سفارش", onClick = { submit() }, modifier = Modifier.weight(1.5f), busy = busy, enabled = !busy)
      }
    }
  }
}
