package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeliveryDining
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.CourierCash
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeliveryText
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.ExternalCompany
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.ScreenKind
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatCard
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.components.common.ViewMode
import ir.vil3ntec.tohid.ui.components.common.ViewModeToggle
import ir.vil3ntec.tohid.ui.components.common.rememberViewMode
import ir.vil3ntec.tohid.ui.theme.Shape
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 *  آیا «میزِ تحویل» دیده شود؟ — فقط وقتی سرور (`/shop/delivery/config`)
 *  می‌گوید پرچمِ `shop.delivery` (و `shop.orders`) روشن است و کاربر دسترسیِ
 *  دیدن دارد. ⛔ پیش‌فرض خاموش: بی اینترنت، بی حساب یا پرچمِ خاموش ⇒ نه
 *  ردیفی در منو، نه صفحه‌ای. قطعِ گذرا بخشِ روشن را خاموش نمی‌کند.
 */
object DeliveryGate {
  val config = mutableStateOf<DeskConfig?>(null)
  val visible: Boolean get() = config.value?.let { it.readable && it.perms.view } == true

  @Volatile private var repo: DeliveryRepository? = null

  fun repository(context: Context): DeliveryRepository =
    repo ?: synchronized(this) { repo ?: DeliveryRepository(Backend.api(context)).also { repo = it } }

  suspend fun refresh(context: Context) {
    if (!Backend.isReady(context)) { config.value = null; return }
    when (val r = repository(context).config()) {
      is ApiResult.Success -> config.value = r.value.takeIf { it.readable && it.perms.view }
      is ApiResult.Failure -> if (!r.error.retryable) config.value = null
    }
  }
}

/** «۱٬۲۵۰ افغانی» */
internal fun afn(minor: Long) = "${OrderText.money(minor)} افغانی"

/** زمانِ محلی به خورشیدی: «7/25 14:05» */
internal fun deskStamp(ms: Long?): String {
  if (ms == null || ms <= 0) return "—"
  val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
  val j = ir.vil3ntec.tohid.Jalali.of(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
  val hh = cal.get(java.util.Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
  val mm = cal.get(java.util.Calendar.MINUTE).toString().padStart(2, '0')
  return "${j.month}/${j.day} $hh:$mm"
}

/**
 *  وضعیتِ مأموریت: متن + رنگ + نقطه. رنگ همان نگاشتِ پیک و سفارش (`OrderStatusTone`،
 *  هفت توکنِ وضعیتِ VILL3N) — «در راه» این‌جا هم همان آبیِ صفحهٔ پیک و سفارش‌هاست.
 */
@Composable
internal fun MissionPill(status: String) {
  StatusPill(DeliveryText.status(status), ir.vil3ntec.tohid.ui.components.common.OrderStatusTone.courier(status))
}

/** همهٔ داده‌ای که میز از سرور دارد — یک جا تا شمارش‌ها و برگه‌ها یکی باشند */
internal data class DeskData(
  val couriers: List<CourierRow> = emptyList(),
  val live: List<Mission> = emptyList(),
  val orders: List<OrderDto> = emptyList(),
  val companies: List<ExternalCompany> = emptyList(),
) {
  private val liveByOrder: Map<String, Mission> = live.associateBy { it.orderId }

  fun missionOf(orderId: String): Mission? = liveByOrder[orderId]

  /** سفارشِ ارسالیِ پذیرفته/در حالِ آماده‌سازی که هنوز مأموریتِ زنده ندارد */
  val toAssign: List<OrderDto> = orders.filter {
    it.fulfillment == "delivery" && it.status in ASSIGNABLE && it.id !in liveByOrder
  }.sortedWith(compareBy({ it.createdAt }, { it.orderNo }))

  /** سفارش‌های «دریافتِ حضوری» که هنوز تحویل نشده‌اند */
  val pickups: List<OrderDto> = orders.filter {
    it.fulfillment == "pickup" && it.status in PICKUP_OPEN
  }.sortedWith(compareBy({ it.createdAt }, { it.orderNo }))

  /** مأموریت‌های ارسال (نه دریافتِ حضوری) */
  val missions: List<Mission> = live.filter { it.mode != "pickup" }

  fun courierName(id: String): String = couriers.firstOrNull { it.id == id }?.name ?: "پیک"

  companion object {
    val ASSIGNABLE = setOf("accepted", "picking", "packed")
    val PICKUP_OPEN = setOf("accepted", "picking", "packed", "ready_for_pickup")
  }
}

/** چند صفحه از یک برگهٔ سفارش‌ها — میز سفارش‌های باز را یک‌جا می‌خواهد */
private suspend fun ordersOf(repo: OrdersRepository, tab: String, pages: Int = 4): ApiResult<List<OrderDto>> {
  val out = ArrayList<OrderDto>()
  var cursor: String? = null
  repeat(pages) {
    when (val r = repo.list(tab, cursor)) {
      is ApiResult.Failure -> return ApiResult.Failure(r.error)
      is ApiResult.Success -> {
        out += r.value.items
        cursor = r.value.nextCursor
      }
    }
    if (cursor == null) return ApiResult.Success(out)
  }
  return ApiResult.Success(out)
}

private suspend fun loadDesk(repo: DeliveryRepository, orders: OrdersRepository, withCompanies: Boolean): ApiResult<DeskData> {
  val couriers = when (val r = repo.couriers()) { is ApiResult.Failure -> return ApiResult.Failure(r.error); is ApiResult.Success -> r.value }
  val live = when (val r = repo.board("live")) { is ApiResult.Failure -> return ApiResult.Failure(r.error); is ApiResult.Success -> r.value }
  val preparing = when (val r = ordersOf(orders, "preparing")) { is ApiResult.Failure -> return ApiResult.Failure(r.error); is ApiResult.Success -> r.value }
  val ready = when (val r = ordersOf(orders, "ready")) { is ApiResult.Failure -> return ApiResult.Failure(r.error); is ApiResult.Success -> r.value }
  //  فهرستِ شرکت‌ها کرایه دارد ⇒ فقط با `delivery.assign` (سرور هم همین را می‌خواهد)؛
  //  پیش‌فرضِ فرم است و نیامدنش میز را نمی‌بندد
  val companies = if (withCompanies) repo.companies().valueOrNull().orEmpty() else emptyList()
  return ApiResult.Success(DeskData(couriers, live, (preparing + ready).distinctBy { it.id }, companies))
}

/**
 *  میزِ تحویلِ دکان (C01) — برنامهٔ اندروید و کامپیوتر.
 *
 *  چهار برگه: «سپردن» (سفارش‌های آماده ⇐ پیکِ دکان یا شرکتِ بیرونی، دستی
 *  یا خودکار با رعایتِ سقفِ نقد؛ مأموریت‌های زنده با کرایه)، «پیک‌ها و
 *  نقد» (دفترِ نقد، سقف، تسویهٔ پایانِ روز با تأییدِ مدیر)، «دریافتِ
 *  حضوری» (کد ⇐ تحویل ⇐ ثبتِ فروش) و «شرکت‌های بیرونی» (تطبیقِ صورت‌حساب).
 *
 *  ⛔ فقط وقتی `DeliveryGate.visible`؛ وگرنه صفحه خودش را می‌بندد.
 *  ⛔ هر دکمه فقط با دسترسیِ همان کار (`DeskPerms` از سرور) دیده می‌شود.
 */
@Composable
fun DeliveryScreen(snackbar: SnackbarHostState, onLeave: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val cfg = DeliveryGate.config.value
  if (cfg == null || !DeliveryGate.visible) {
    LaunchedEffect(Unit) { onLeave() }
    return
  }
  val repo = remember(context) { DeliveryGate.repository(context) }
  val ordersRepo = remember(context) { OrdersLive.repository(context) }
  val c = Shop.colors
  val view = rememberViewMode("delivery", ScreenKind.MANAGEMENT)

  var tab by rememberSaveable { mutableStateOf("assign") }
  var data by remember { mutableStateOf<DeskData?>(null) }
  var error by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }
  var reload by remember { mutableStateOf(0) }
  val rev = OrdersLive.rev.value

  LaunchedEffect(reload, rev) {
    loading = true
    loadDesk(repo, ordersRepo, withCompanies = cfg.perms.assign)
      .onSuccess { data = it; error = null }
      .onFailure { f ->
        if (f.code == "delivery_disabled") { DeliveryGate.refresh(context); onLeave() }
        error = f.userMessage
      }
    loading = false
  }
  //  تا صفحه باز است، هر نیم دقیقه تازه می‌شود (پیک‌ها وضعیت را از گوشیِ خودشان می‌فرستند)
  LaunchedEffect(Unit) {
    while (isActive) {
      delay(30_000)
      reload++
    }
  }
  val refresh: () -> Unit = { reload++ }

  Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("میزِ تحویل", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = c.text)
        Text("سپردن به پیک، نقدِ پیک‌ها، دریافتِ حضوری و شرکت‌های بیرونی", style = MaterialTheme.typography.bodySmall, color = c.muted)
      }
      if (tab == "assign") ViewModeToggle(view)
      Spacer(Modifier.width(8.dp))
      IconButton(onClick = refresh, enabled = !loading) {
        Icon(Icons.Filled.Refresh, contentDescription = "تازه‌سازی", tint = c.muted)
      }
    }
    if (!cfg.entitled) {
      Spacer(Modifier.height(6.dp))
      Text(
        "اشتراکِ تحویل برای این دکان فعال نیست؛ فقط دیدن ممکن است.",
        style = MaterialTheme.typography.bodySmall, color = c.warning,
      )
    }
    Spacer(Modifier.height(10.dp))

    val d = data
    if (d == null) {
      if (error != null) TohidErrorState(title = "میزِ تحویل نیامد", description = error ?: "", onRetry = refresh)
      else TohidLoadingState(rows = 4)
      return@Column
    }

    DeskStats(d, onTab = { tab = it })
    Spacer(Modifier.height(10.dp))
    SegmentedTabs(
      tabs = buildList {
        add(SegmentTab("assign", "سپردن", d.toAssign.size))
        add(SegmentTab("couriers", "پیک‌ها و نقد", d.couriers.size))
        add(SegmentTab("pickup", "دریافتِ حضوری", d.pickups.size))
        if (cfg.perms.assign) add(SegmentTab("external", "شرکت‌های بیرونی", d.companies.size))
      },
      selected = tab,
      onSelect = { tab = it },
      modifier = Modifier.horizontalScroll(rememberScrollState()),
    )
    if (error != null) {
      Spacer(Modifier.height(6.dp))
      Text("آخرین تازه‌سازی نشد: $error", style = MaterialTheme.typography.labelSmall, color = c.warning)
    }
    Spacer(Modifier.height(10.dp))
    val writable = cfg.entitled
    when (tab) {
      "assign" -> AssignTab(d, cfg, writable, repo, snackbar, table = view.mode == ViewMode.TABLE, onChanged = refresh)
      "couriers" -> CouriersTab(d, cfg, writable, repo, snackbar, onChanged = refresh)
      "pickup" -> PickupTab(d, cfg, writable, repo, ordersRepo, snackbar, onChanged = refresh)
      "external" -> if (cfg.perms.assign) CompaniesTab(d, cfg, repo) else LaunchedEffect(Unit) { tab = "assign" }
    }
  }
}

/* ============================ کارت‌های آماری ============================ */

@Composable
private fun DeskStats(d: DeskData, onTab: (String) -> Unit) {
  val held = d.couriers.sumOf { it.cashHeldMinor }
  val atCap = d.couriers.count { it.active && CourierCash.atCap(it) }
  val wide = Platform.DESKTOP || screenSize() != Screen.PHONE
  val cards = listOf<@Composable (Modifier) -> Unit>(
    { m -> StatCard("آمادهٔ سپردن", d.toAssign.size.toString(), "اکنون", m, icon = Icons.Filled.Inventory, onClick = { onTab("assign") }) },
    { m -> StatCard("مأموریتِ زنده", d.missions.size.toString(), "اکنون", m, icon = Icons.Filled.DeliveryDining, onClick = { onTab("assign") }) },
    { m -> StatCard("نقدِ دستِ پیک‌ها", afn(held), "تسویه‌نشده", m, icon = Icons.Filled.Payments, onClick = { onTab("couriers") }) },
    { m -> StatCard("پیکِ به سقف‌رسیده", atCap.toString(), "سفارشِ خودکار نمی‌گیرد", m, icon = Icons.Filled.Warning, onClick = { onTab("couriers") }) },
  )
  if (wide) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { cards.forEach { it(Modifier.weight(1f)) } }
  } else {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      cards.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { it(Modifier.weight(1f)) } }
      }
    }
  }
}

/* ============================ برگهٔ سپردن ============================ */

@Composable
private fun ColumnScope.AssignTab(
  d: DeskData,
  cfg: DeskConfig,
  writable: Boolean,
  repo: DeliveryRepository,
  snackbar: SnackbarHostState,
  table: Boolean,
  onChanged: () -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val canAssign = cfg.perms.assign && writable
  var assignFor by remember { mutableStateOf<OrderDto?>(null) }
  var autoFor by remember { mutableStateOf<List<OrderDto>?>(null) }
  var actOn by remember { mutableStateOf<Pair<Mission, DeskActions.Act>?>(null) }
  var shownCode by remember { mutableStateOf<Pair<Long, String>?>(null) }

  LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    item(key = "ready-head") {
      Row(verticalAlignment = Alignment.CenterVertically) {
        DeskSection("سفارش‌های آماده برای سپردن", Modifier.weight(1f))
        if (canAssign && d.toAssign.isNotEmpty() && cfg.shopCourier) {
          TohidButton("سپردنِ خودکار (${d.toAssign.size})", onClick = { autoFor = d.toAssign })
        }
      }
    }
    if (d.toAssign.isEmpty()) {
      item(key = "ready-empty") {
        Text("سفارشِ ارسالیِ آماده‌ای که پیک نداشته باشد نیست.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      }
    } else {
      if (table) item(key = "ready-th") { TableHead(READY_COLS) }
      items(d.toAssign, key = { "o-" + it.id }) { o ->
        ReadyOrderRow(o, table, canAssign, onAssign = { assignFor = o }, onAuto = { autoFor = listOf(o) })
      }
    }

    item(key = "live-head") { DeskSection("مأموریت‌های زنده") }
    if (d.missions.isEmpty()) {
      item(key = "live-empty") {
        Text("مأموریتِ زنده‌ای نیست.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      }
    } else {
      if (table) item(key = "live-th") { TableHead(MISSION_COLS) }
      items(d.missions, key = { "m-" + it.id }) { m ->
        val acts = if (writable) DeskActions.of(m, cfg.perms) else emptyList()
        MissionRow(m, d, table, acts, onAct = { a -> actOn = m to a })
      }
    }
  }

  assignFor?.let { o ->
    AssignDialog(
      order = o, d = d, cfg = cfg, repo = repo,
      onDismiss = { assignFor = null },
      onDone = { m, code ->
        assignFor = null
        onChanged()
        if (code != null) shownCode = m.orderNo to code
        scope.launch { snackbar.showSnackbar("سفارش #${o.orderNo} سپرده شد: ${missionWho(m, d)}") }
      },
    )
  }
  autoFor?.let { list ->
    AutoAssignDialog(
      orders = list, d = d, repo = repo,
      onDismiss = { autoFor = null },
      onFinished = { onChanged() },
    )
  }
  actOn?.let { (m, act) ->
    //  DLV-09: «اصلاحِ نشانی» فرمِ خودش را دارد (نه گذارِ وضعیت)
    if (act == DeskActions.Act.FIX_ADDRESS) {
      AddressFixDialog(
        m = m, repo = repo,
        onDismiss = { actOn = null },
        onDone = { msg ->
          actOn = null
          onChanged()
          scope.launch { snackbar.showSnackbar(msg) }
        },
      )
    } else {
      MissionActionDialog(
        m = m, act = act, cfg = cfg, repo = repo,
        onDismiss = { actOn = null },
        onCode = { code -> actOn = null; shownCode = m.orderNo to code },
        onDone = { msg ->
          actOn = null
          onChanged()
          scope.launch { snackbar.showSnackbar(msg) }
        },
      )
    }
  }
  shownCode?.let { (no, code) -> CodeOnceDialog(no, code) { shownCode = null } }
}

internal fun missionWho(m: Mission, d: DeskData): String = when (m.mode) {
  "shop_courier" -> d.courierName(m.courierId)
  "external" -> m.externalName.ifEmpty { "شرکتِ بیرونی" }
  else -> DeliveryText.mode(m.mode)
}

@Composable
internal fun DeskSection(title: String, modifier: Modifier = Modifier) {
  Text(
    title, modifier.padding(top = 6.dp, bottom = 2.dp),
    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Shop.colors.text,
  )
}

private val READY_COLS = listOf("سفارش" to 0.7f, "مشتری" to 1.5f, "وضعیت" to 1.1f, "نشانه" to 1.6f, "نقدِ گرفتنی" to 1.1f, "" to 1.6f)
private val MISSION_COLS = listOf("سفارش" to 0.7f, "مشتری" to 1.3f, "روش" to 1.4f, "وضعیت" to 1.2f, "نقدِ گرفتنی" to 1.1f, "کرایه" to 0.9f, "" to 2.2f)

@Composable
internal fun TableHead(cols: List<Pair<String, Float>>) {
  val c = Shop.colors
  Column {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
      cols.forEach { (label, w) ->
        Text(label, Modifier.weight(w), style = MaterialTheme.typography.labelMedium, color = c.muted, fontWeight = FontWeight.SemiBold)
      }
    }
    HorizontalDivider(color = c.border.copy(alpha = 0.5f))
  }
}

@Composable
private fun RowScope.RowCell(text: String, w: Float, bold: Boolean = false, muted: Boolean = false) {
  val c = Shop.colors
  Text(
    text, Modifier.weight(w).padding(end = 6.dp), color = if (muted) c.muted else c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
    style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
  )
}

/** ردیفِ جدول — همان سطحِ سفیدِ گرد، بی خطِ تیره */
@Composable
internal fun TableRowBox(content: @Composable RowScope.() -> Unit) {
  Row(
    Modifier.fillMaxWidth().clip(Shape.card).background(Shop.colors.surfaceSolid).padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

@Composable
private fun ReadyOrderRow(o: OrderDto, table: Boolean, canAssign: Boolean, onAssign: () -> Unit, onAuto: () -> Unit) {
  val c = Shop.colors
  val expected = ir.vil3ntec.tohid.core.delivery.AutoAssign.expectedOf(o.paymentMethod, o.paymentStatus, o.totalMinor)
  val landmark = o.address.landmark.ifEmpty { o.address.text }
  val buttons = @Composable {
    if (canAssign) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TohidSecondaryButton("خودکار", onClick = onAuto)
        TohidButton("سپردن…", onClick = onAssign)
      }
    }
  }
  if (table) {
    TableRowBox {
      RowCell("#${o.orderNo}", READY_COLS[0].second, bold = true)
      RowCell(o.customer.name, READY_COLS[1].second)
      Box(Modifier.weight(READY_COLS[2].second)) { OrderStatusPill(o.status) }
      RowCell(landmark.ifEmpty { "—" }, READY_COLS[3].second, muted = landmark.isEmpty())
      RowCell(if (expected > 0) afn(expected) else "پرداخت‌شده", READY_COLS[4].second, bold = expected > 0)
      Box(Modifier.weight(READY_COLS[5].second), contentAlignment = Alignment.CenterEnd) { buttons() }
    }
  } else {
    TohidCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("#${o.orderNo}", fontWeight = FontWeight.Bold, color = c.text)
        Spacer(Modifier.width(8.dp))
        Text(o.customer.name, Modifier.weight(1f), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        OrderStatusPill(o.status)
      }
      if (landmark.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(landmark, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          if (expected > 0) "نقدِ گرفتنی: ${afn(expected)}" else "پرداخت‌شده — نقدی گرفته نمی‌شود",
          Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.text,
        )
        buttons()
      }
    }
  }
}

@Composable
private fun MissionRow(m: Mission, d: DeskData, table: Boolean, acts: List<DeskActions.Act>, onAct: (DeskActions.Act) -> Unit) {
  val c = Shop.colors
  val how = "${DeliveryText.mode(m.mode)} · ${missionWho(m, d)}" + if (m.externalRef.isNotEmpty()) " · ${m.externalRef}" else ""
  val buttons = @Composable {
    if (acts.isNotEmpty()) {
      Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        acts.forEach { a ->
          if (a == DeskActions.Act.FAIL || a == DeskActions.Act.WITHDRAW || a == DeskActions.Act.RECALL) {
            TohidSecondaryButton(DeskActions.label(a), onClick = { onAct(a) })
          } else {
            TohidButton(DeskActions.label(a), onClick = { onAct(a) })
          }
        }
      }
    }
  }
  val notes = buildList {
    if (m.recallRequested && m.recallOutcome == "pending") add("فراخوانی فرستاده شد")
    if (m.cashReview) add("اختلافِ نقد — بررسی")
    if (m.attemptNo > 1) add("تلاشِ ${m.attemptNo}")
  }
  if (table) {
    TableRowBox {
      RowCell("#${m.orderNo}", MISSION_COLS[0].second, bold = true)
      RowCell(m.customerName.ifEmpty { "—" }, MISSION_COLS[1].second)
      RowCell(how, MISSION_COLS[2].second)
      Column(Modifier.weight(MISSION_COLS[3].second)) {
        MissionPill(m.status)
        if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.warning, maxLines = 1)
      }
      RowCell(if (m.expectedCollectMinor > 0) afn(m.expectedCollectMinor) else "—", MISSION_COLS[4].second)
      RowCell(if (m.fareMinor > 0) afn(m.fareMinor) else "—", MISSION_COLS[5].second, muted = m.fareMinor == 0L)
      Box(Modifier.weight(MISSION_COLS[6].second), contentAlignment = Alignment.CenterEnd) { buttons() }
    }
  } else {
    TohidCard {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("#${m.orderNo}", fontWeight = FontWeight.Bold, color = c.text)
        Spacer(Modifier.width(8.dp))
        Text(m.customerName, Modifier.weight(1f), color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        MissionPill(m.status)
      }
      Spacer(Modifier.height(4.dp))
      Text(how, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Spacer(Modifier.height(4.dp))
      Row {
        Text(
          if (m.expectedCollectMinor > 0) "نقد: ${afn(m.expectedCollectMinor)}" else "بی نقد",
          Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = c.text,
        )
        Text(if (m.fareMinor > 0) "کرایه: ${afn(m.fareMinor)}" else "بی کرایه", style = MaterialTheme.typography.bodySmall, color = c.muted)
      }
      if (notes.isNotEmpty()) {
        Text(notes.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = c.warning)
      }
      if (acts.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        buttons()
      }
    }
  }
}

/** ردیفِ ساده و قابلِ لمس برای انتخاب در فهرست‌های پنجره‌ها */
@Composable
internal fun PickRow(selected: Boolean, enabled: Boolean, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
  val c = Shop.colors
  Column(
    Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(12.dp))
      .background(if (selected) c.primaryTint else c.surface2)
      .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 10.dp),
    content = content,
  )
}
