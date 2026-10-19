package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.sync.v1.SyncDot
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Glyph
import kotlinx.coroutines.launch

/**
 *  حالت‌های ثابت برای پیش‌نمایش و عکسِ خودکار (`PhonePreview` روی کامپیوتر) —
 *  برنامهٔ دستِ کاربر همیشه `NONE` می‌فرستد.
 */
data class PhoneHomeShot(
  val pull: Boolean = false,
  val page: Int = 0,
  /** نامِ بلندِ دکان و عددهای درشت — آزمونِ جا شدن */
  val big: Boolean = false,
) {
  companion object { val NONE = PhoneHomeShot() }
}

/** هر چیزی که خانه و کارتِ کششی نشان می‌دهند — یک بار به ازای هر دفتر */
data class PhoneHomeView(
  val shopName: String,
  val todaySales: Double,
  val todayCount: Int,
  val cash: Double,
  val shiftOpen: Boolean,
  /** `null` = این نقش عددِ سود را نمی‌بیند */
  val profit: Double?,
  val tiles: List<HomeSections.Tile>,
  val alerts: List<HomeAlerts.Alert>,
  val sync: HomeAlerts.Sync,
  val queued: Int,
  val canSeeMoney: Boolean,
) {
  fun big(): PhoneHomeView = copy(
    shopName = "سوپرمارکت و عمده‌فروشیِ برادرانِ محمدی و شرکا",
    todaySales = 12_345_678.0,
    cash = 98_765_432.0,
    profit = profit?.let { 4_321_987.0 },
  )
}

@Composable
fun rememberPhoneHomeView(d: ShopData): PhoneHomeView {
  val context = LocalContext.current
  val today = ir.vil3ntec.tohid.todayIso()
  val shopName = remember(d) {
    context.getSharedPreferences("tohid", android.content.Context.MODE_PRIVATE).getString("store_name", "").orEmpty()
  }
  val canSeeMoney = remember { ir.vil3ntec.tohid.data.ShopRole.canSeeMoney(context) }
  val canManage = remember { ir.vil3ntec.tohid.data.ShopRole.canOpenSettings(context) }
  val nums = remember(d, today) { DashboardNumbers.of(d, today) }
  //  نقدِ دخل: صندوقِ باز ⇐ «باید باشد»ِ همان صندوق؛ بسته ⇐ گردشِ نقدِ امروز
  val now = System.currentTimeMillis()
  val drawer = remember(d, today) {
    val shift = CashShift.current(d)
    if (shift != null) CashShift.totalsOf(d, shift, now).expected to true
    else {
      val start = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
      }.timeInMillis
      CashShift.totals(d, start, now, 0.0).expected to false
    }
  }
  val signedIn = remember { ir.vil3ntec.tohid.data.repo.Backend.isReady(context) }
  val status by ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).status.collectAsState()
  val sync = when {
    !signedIn -> HomeAlerts.Sync.LOCAL_ONLY
    status.dot == SyncDot.RED -> HomeAlerts.Sync.ERROR
    status.dot == SyncDot.YELLOW -> HomeAlerts.Sync.QUEUED
    status.dot == SyncDot.GREY -> HomeAlerts.Sync.OFFLINE
    else -> HomeAlerts.Sync.OK
  }
  val locked by ir.vil3ntec.tohid.sync.v1.SoftLock.locked.collectAsState()
  LaunchedEffect(Unit) { runCatching { ir.vil3ntec.tohid.sync.v1.SoftLock.refresh(context) } }
  val warnDays = remember(locked) { runCatching { ir.vil3ntec.tohid.sync.v1.SoftLock.warnDays(context) }.getOrNull() }
  val ordersLive = OrdersLive.enabled
  val newOrders = OrdersLive.newCount
  val alerts = remember(nums, sync, status.queued, locked, warnDays, ordersLive, newOrders) {
    HomeAlerts.build(
      HomeAlerts.Input(
        ordersLive = ordersLive, newOrders = newOrders,
        subscriptionLocked = locked, subscriptionDaysLeft = warnDays,
        sync = sync, queued = status.queued,
        outOfStock = nums.outOfStock.size, lowStock = nums.lowStock.size,
        overdueDebtors = nums.overdue.size, overdueTotal = "${money(nums.overdueTotal)} ؋",
        expiring = nums.expiring.size,
      ),
    )
  }
  return PhoneHomeView(
    shopName = shopName.ifBlank { "دکانِ من" },
    todaySales = nums.todayTotal,
    todayCount = nums.todaySales.size,
    cash = drawer.first,
    shiftOpen = drawer.second,
    profit = if (canSeeMoney) nums.todayProfit else null,
    tiles = remember(canManage, ordersLive) { HomeSections.tiles(canManage, ordersLive) },
    alerts = alerts,
    sync = sync,
    queued = status.queued,
    canSeeMoney = canSeeMoney,
  )
}

/**
 *  خانهٔ گوشی، phone v3 §D: صفحهٔ ۱ = r7 (سربرگِ بنفش، کارتِ چهار کارِ سریع، شبکهٔ
 *  بخش‌ها، هشدارها)، صفحهٔ ۲ = آمارِ r8. انگشت را به چپ بکشید ⇐ آمار (صاحب محصول:
 *  «چپ که می‌کشم»)؛ پس ورقه‌ها چپ‌به‌راست چیده می‌شوند و درونِ هر ورقه راست‌به‌چپ.
 *  کشیدنِ صفحه به پایین از بالا ⇐ کارتِ سیاهِ r6 (`PullCard`).
 *
 *  @param bottomReserve جای نوارِ شناورِ پایین؛ محتوا زیرِ آن هم دیده می‌شود
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneHomeScreen(
  d: ShopData,
  onOpen: (String) -> Unit,
  bottomReserve: Dp = PhoneNav.RESERVE,
  shot: PhoneHomeShot = PhoneHomeShot.NONE,
) {
  val live = rememberPhoneHomeView(d)
  val view = if (shot.big) live.big() else live
  val pages = if (view.canSeeMoney) 2 else 1
  val pager = rememberPagerState(initialPage = shot.page.coerceIn(0, pages - 1)) { pages }
  val rtl = LocalLayoutDirection.current
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0) { page ->
      CompositionLocalProvider(LocalLayoutDirection provides rtl) {
        if (page == 0) HomePage(view, onOpen, bottomReserve, shot, pages, pager.currentPage)
        else PhoneStatsPage(d, bottomReserve, pages, pager.currentPage, big = shot.big)
      }
    }
  }
}

/** دو نقطهٔ ورقه‌ها — نقطهٔ فعال کشیده */
@Composable
fun PagerDots(count: Int, current: Int, on: Color, off: Color) {
  if (count < 2) return
  //  ورقه‌ها چپ‌به‌راست‌اند؛ نقطه‌ها هم
  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
      repeat(count) { i ->
        val w by animateFloatAsState(if (i == current) 18f else 6f, if (Motion.enabled) spring() else snap(), label = "dot")
        Box(Modifier.width(w.dp).height(6.dp).clip(CircleShape).background(if (i == current) on else off))
      }
    }
  }
}

@Composable
private fun HomePage(
  view: PhoneHomeView,
  onOpen: (String) -> Unit,
  bottomReserve: Dp,
  shot: PhoneHomeShot,
  pages: Int,
  currentPage: Int,
) {
  val p = PhoneV3.colors
  val density = LocalDensity.current
  val scroll = rememberScrollState()
  val scope = rememberCoroutineScope()
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  /* -------------------- کارتِ کششی: ماشینِ حالت + اسکرولِ تودرتو -------------------- */
  var pc by remember(shot.pull) { mutableStateOf(if (shot.pull) PullCard.forceShown() else PullCard.State()) }
  val cfg = remember(density) {
    with(density) { PullCard.Config(threshold = 64.dp.toPx(), flingMin = 22.dp.toPx(), flingVelocity = 1100.dp.toPx()) }
  }
  val connection = remember(cfg, shot.pull) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        //  انگشت بالا می‌رود و کارت نیمه‌کشیده است ⇐ اول کارت جمع شود
        if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
        val (s, used) = PullCard.pullUp(pc, -available.y)
        pc = s
        return Offset(0f, -used)
      }

      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        //  فقط حرکتِ انگشت که اسکرول دیگر نخورد (بالای صفحه) — نه پرتاب
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        val before = pc
        pc = PullCard.pullDown(pc, available.y)
        return if (pc != before) Offset(0f, available.y) else Offset.Zero
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        val wasPulling = pc.dragging
        pc = PullCard.release(pc, available.y, cfg)
        return if (wasPulling) available else Velocity.Zero
      }
    }
  }
  var cardH by remember { mutableFloatStateOf(0f) }
  val gap = with(density) { 10.dp.toPx() }
  val reveal = cardH + gap
  val target = if (pc.shown) 1f else (pc.pull / cfg.threshold * 0.55f).coerceIn(0f, 0.8f)
  val anim = remember { Animatable(0f) }
  LaunchedEffect(target, pc.dragging) {
    if (pc.dragging || !Motion.enabled) anim.snapTo(target)
    else anim.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 360f))
  }
  //  دکمهٔ برگشتِ گوشی هم کارت را می‌بندد
  androidx.activity.compose.BackHandler(enabled = pc.shown) { pc = PullCard.dismiss(pc) }
  //  ساعت و باتری روی بنفش (و کارتِ سیاه) سفید — مثلِ سربرگِ برنامه (`TohidTopBar`)
  val hostView = androidx.compose.ui.platform.LocalView.current
  androidx.compose.runtime.DisposableEffect(hostView) {
    val bars = hostWindow(hostView.context)?.let { androidx.core.view.WindowCompat.getInsetsController(it, hostView) }
    val before = bars?.isAppearanceLightStatusBars
    bars?.isAppearanceLightStatusBars = false
    onDispose { if (bars != null && before != null) bars.isAppearanceLightStatusBars = before }
  }

  Box(
    Modifier
      .fillMaxSize()
      .background(p.card)
      .pointerInput(shot.pull) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          pc = PullCard.touchDown(pc, atTop = scroll.value == 0)
        }
      }
      .nestedScroll(connection),
  ) {
    var alertsY by remember { mutableFloatStateOf(0f) }
    Column(
      Modifier
        .fillMaxSize()
        .graphicsLayer { translationY = anim.value * reveal }
        .verticalScroll(scroll),
    ) {
      HomeHeader(view, top, pages, currentPage, onOpen) {
        scope.launch { scroll.animateScrollTo(alertsY.toInt()) }
      }
      Spacer(Modifier.height(26.dp))
      HomeTitle("بخش‌ها", Modifier.padding(horizontal = 22.dp))
      Spacer(Modifier.height(14.dp))
      SectionsGrid(view.tiles, onOpen)
      Spacer(Modifier.height(18.dp))
      Box(Modifier.onPlaced { alertsY = it.positionInParent().y }) {
        AlertsSection(view.alerts, onOpen)
      }
      Spacer(Modifier.height(bottomReserve + navBottom + 24.dp))
    }

    //  لمسِ بیرونِ کارت یا هل دادن به بالا ⇐ پنهان
    if (pc.shown) {
      Box(
        Modifier
          .fillMaxSize()
          .pointerInput(shot.pull) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              down.consume()
              var dy = 0f
              while (true) {
                val e = awaitPointerEvent()
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                dy += c.position.y - c.previousPosition.y
                c.consume()
                if (!c.pressed) break
                if (dy < -24f) break
              }
              if (dy <= 24f) pc = PullCard.dismiss(pc)
            }
          },
      )
    }
    if (anim.value > 0.001f || pc.shown) {
      PullCardView(
        view = view,
        progress = anim.value,
        onHeight = { cardH = it },
        onDismiss = { pc = PullCard.dismiss(pc) },
        onOpen = { r -> pc = PullCard.dismiss(pc); onOpen(r) },
      )
    }
  }
}

@Composable
private fun HomeTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text,
    modifier = modifier,
    color = PhoneV3.colors.ink,
    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold),
  )
}

/** سربرگِ بنفشِ r7 و کارتِ چهار کارِ سریع که لبه‌اش روی سفید می‌نشیند */
@Composable
private fun HomeHeader(
  view: PhoneHomeView,
  top: Dp,
  pages: Int,
  currentPage: Int,
  onOpen: (String) -> Unit,
  onBell: () -> Unit,
) {
  val p = PhoneV3.colors
  val headerContent = 214.dp            // ردیفِ آواتار تا نقطه‌ها
  val card = 82.dp
  val overlapOnWhite = 38.dp
  val sheetRadius = 26.dp
  val zone = top + headerContent + card
  val purple = Brush.linearGradient(
    listOf(lerp(p.accentDeep, p.accent, 0.55f), p.accentDeep),
    start = Offset(Float.POSITIVE_INFINITY, 0f),
    end = Offset(0f, Float.POSITIVE_INFINITY),
  )
  Box(Modifier.fillMaxWidth().height(zone)) {
    Box(Modifier.fillMaxWidth().height(zone - overlapOnWhite + sheetRadius).background(purple))
    Box(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(overlapOnWhite)
        .background(p.card, RoundedCornerShape(topStart = sheetRadius, topEnd = sheetRadius)),
    )
    Column(Modifier.fillMaxWidth().padding(top = top + 14.dp, start = 20.dp, end = 20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ShopAvatar(view.shopName, 46.dp, ring = p.onAccent.copy(alpha = 0.85f), modifier = Modifier.clickable(role = Role.Button) { onOpen("profile") })
        Spacer(Modifier.weight(1f))
        GlassBell(dot = view.alerts.isNotEmpty(), onClick = onBell)
      }
      Spacer(Modifier.height(12.dp))
      Text(
        "فروشِ امروز",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = p.onAccent.copy(alpha = 0.72f),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
      )
      Spacer(Modifier.height(2.dp))
      Text(
        "${money(view.todaySales)} ؋",
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = p.onAccent,
        maxLines = 1,
        style = MaterialTheme.typography.displaySmall.copy(
          fontSize = if (money(view.todaySales).length > 9) 34.sp else 42.sp,
          fontWeight = FontWeight.Bold,
          lineHeight = 52.sp,
        ),
      )
      Spacer(Modifier.height(14.dp))
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PagerDots(pages, currentPage, p.onAccent, p.onAccent.copy(alpha = 0.35f))
      }
    }
    QuickActions(
      Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp).fillMaxWidth().height(card),
      onOpen,
    )
  }
}

/** آواتارِ دکان: حرفِ اولِ نام روی دایرهٔ بنفش با حلقهٔ روشن */
@Composable
fun ShopAvatar(name: String, size: Dp, ring: Color, modifier: Modifier = Modifier) {
  val p = PhoneV3.colors
  val letter = name.trim().firstOrNull()?.toString() ?: "؟"
  Box(
    modifier
      .size(size)
      .clip(CircleShape)
      .background(ring)
      .padding(2.dp)
      .clip(CircleShape)
      .background(Brush.linearGradient(listOf(p.accent, p.accentDeep))),
    contentAlignment = Alignment.Center,
  ) {
    Text(letter, color = p.onAccent, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
  }
}

/** زنگِ نیمه‌شفاف روی بنفش، با نقطهٔ «تازه» */
@Composable
private fun GlassBell(dot: Boolean, onClick: () -> Unit) {
  val p = PhoneV3.colors
  val shape = RoundedCornerShape(16.dp)
  Box(
    Modifier
      .size(46.dp)
      .clip(shape)
      .background(p.onAccent.copy(alpha = 0.12f))
      .border(1.dp, p.onAccent.copy(alpha = 0.28f), shape)
      .clickable(role = Role.Button, onClickLabel = "هشدارها", onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(Icons.Outlined.Notifications, contentDescription = "هشدارها", tint = p.onAccent, modifier = Modifier.size(24.dp))
    if (dot) Box(
      Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 12.dp).size(9.dp)
        .clip(CircleShape).background(p.negative).border(1.5.dp, p.accentDeep, CircleShape),
    )
  }
}

private data class Quick(val label: String, val icon: ImageVector, val route: String)

/** کارتِ سفیدِ چهار کارِ سریع (r7: Top Up · Send · Request · History) */
@Composable
private fun QuickActions(modifier: Modifier, onOpen: (String) -> Unit) {
  val p = PhoneV3.colors
  val items = listOf(
    Quick("فروشِ تازه", Icons.Outlined.AddShoppingCart, "sale"),
    Quick("دریافت", Icons.Outlined.CallReceived, "debtors"),
    Quick("ورودِ کالا", Icons.Outlined.MoveToInbox, "warehouse"),
    Quick("تاریخچه", Icons.Outlined.History, "sales"),
  )
  //  بارِ اول ۸dp بالا می‌آید و پیدا می‌شود
  val rise = remember { Animatable(if (Motion.enabled) 1f else 0f) }
  LaunchedEffect(Unit) { if (Motion.enabled) rise.animateTo(0f, tween(380)) }
  val shape = RoundedCornerShape(20.dp)
  Row(
    modifier
      .graphicsLayer { translationY = rise.value * 8.dp.toPx(); alpha = 1f - rise.value * 0.6f }
      .shadow(18.dp, shape, clip = false, ambientColor = p.accentDeep.copy(alpha = 0.18f), spotColor = p.accentDeep.copy(alpha = 0.18f))
      .clip(shape)
      .background(p.card)
      .padding(horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items.forEach { q ->
      PressBox(Modifier.weight(1f).fillMaxSize(), onClick = { onOpen(q.route) }) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
          Icon(q.icon, contentDescription = null, tint = p.accent, modifier = Modifier.size(26.dp))
          Spacer(Modifier.height(6.dp))
          Text(q.label, color = p.ink, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp), maxLines = 1)
        }
      }
    }
  }
}

/** دکمهٔ بی‌موجی که با فشار کمی کوچک می‌شود (§G: «press-scale») */
@Composable
fun PressBox(
  modifier: Modifier = Modifier,
  scaleTo: Float = 0.94f,
  label: String? = null,
  onClick: () -> Unit,
  content: @Composable () -> Unit,
) {
  val source = remember { MutableInteractionSource() }
  val pressed by source.collectIsPressedAsState()
  val s by animateFloatAsState(if (pressed) scaleTo else 1f, if (Motion.enabled) spring(stiffness = 900f) else snap(), label = "press")
  Box(
    modifier
      .graphicsLayer { scaleX = s; scaleY = s }
      .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) { content() }
}

private fun tileIcon(id: String): ImageVector = when (id) {
  "products" -> Icons.Outlined.LocalOffer
  "warehouse" -> Icons.Outlined.Warehouse
  "debtors" -> Icons.Outlined.Group
  "expenses" -> Icons.Outlined.Payments
  "reports" -> Icons.Outlined.Assessment
  "team" -> Icons.Outlined.Badge
  "orders" -> Icons.Outlined.ShoppingBag
  "purchasing" -> Icons.Outlined.LocalShipping
  "sales" -> Icons.Outlined.History
  "receipts" -> Icons.Outlined.ReceiptLong
  else -> Icons.Outlined.GridView
}

/** شبکهٔ ۴×۲ِ «بخش‌ها» — کاشیِ روشنِ گرد با نشانهٔ رنگی و نام زیرش (r7 Payment List) */
@Composable
private fun SectionsGrid(tiles: List<HomeSections.Tile>, onOpen: (String) -> Unit) {
  val p = PhoneV3.colors
  Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    tiles.chunked(4).forEachIndexed { r, row ->
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        row.forEachIndexed { c, t ->
          val i = r * 4 + c
          Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            PressBox(Modifier.size(58.dp), scaleTo = 0.9f, label = t.label, onClick = { onOpen(t.route) }) {
              Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(PhoneV3.tileRadius)).background(p.tile),
                contentAlignment = Alignment.Center,
              ) {
                Icon(tileIcon(t.id), contentDescription = null, tint = PhoneV3Glyph.of(p, i), modifier = Modifier.size(26.dp))
                if (t.id == "orders" && OrdersLive.newCount > 0) Box(
                  Modifier.align(Alignment.TopEnd).padding(8.dp).size(8.dp).clip(CircleShape).background(p.negative),
                )
              }
            }
            Spacer(Modifier.height(8.dp))
            Text(
              t.label,
              color = p.ink.copy(alpha = 0.78f),
              style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp),
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

private fun alertIcon(k: HomeAlerts.Kind): ImageVector = when (k) {
  HomeAlerts.Kind.ORDERS -> Icons.Outlined.ShoppingBag
  HomeAlerts.Kind.SUBSCRIPTION -> Icons.Outlined.WorkspacePremium
  HomeAlerts.Kind.SYNC -> Icons.Outlined.CloudSync
  HomeAlerts.Kind.STOCK -> Icons.Outlined.Inventory2
  HomeAlerts.Kind.DEBTS -> Icons.Outlined.Group
  HomeAlerts.Kind.EXPIRY -> Icons.Outlined.EventBusy
}

/** «هشدارها و پیام‌ها» + «همه»، و چرخِ کارت‌های بزرگ (r7 Promo & Discount) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlertsSection(alerts: List<HomeAlerts.Alert>, onOpen: (String) -> Unit) {
  val p = PhoneV3.colors
  var all by remember { mutableStateOf(false) }
  Column {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
      HomeTitle("هشدارها و پیام‌ها", Modifier.weight(1f))
      if (alerts.isNotEmpty()) Text(
        "همه",
        color = p.accent,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button) { all = true }.padding(horizontal = 6.dp, vertical = 4.dp),
      )
    }
    Spacer(Modifier.height(14.dp))
    if (alerts.isEmpty()) {
      CalmCard(Modifier.padding(horizontal = 22.dp))
    } else {
      val state = rememberLazyListState()
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = (maxWidth - 44.dp - 28.dp).coerceAtLeast(240.dp)
        LazyRow(
          state = state,
          flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
          contentPadding = PaddingValues(horizontal = 22.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          itemsIndexed(alerts, key = { _, a -> a.kind.name + a.title }) { i, a ->
            AlertCard(a, if (i % 2 == 0) p.accentDeep else p.accent, Modifier.width(w).height(164.dp)) { onOpen(a.route) }
          }
        }
      }
    }
  }
  if (all) AllAlertsDialog(alerts, onDismiss = { all = false }) { r -> all = false; onOpen(r) }
}

@Composable
private fun AlertCard(a: HomeAlerts.Alert, bg: Color, modifier: Modifier, onClick: () -> Unit) {
  val p = PhoneV3.colors
  PressBox(modifier, scaleTo = 0.97f, label = a.action, onClick = onClick) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(bg)) {
      //  نقشِ پس‌زمینه مثلِ r7: دایرهٔ روشنِ بزرگ و نشانهٔ کم‌رنگ در گوشهٔ پایان
      Box(
        Modifier.align(Alignment.BottomEnd).offset(x = (-30).dp, y = 46.dp).size(150.dp)
          .clip(CircleShape).background(p.onAccent.copy(alpha = 0.08f)),
      )
      Icon(
        alertIcon(a.kind), contentDescription = null, tint = p.onAccent.copy(alpha = 0.22f),
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp).size(72.dp),
      )
      Column(Modifier.fillMaxSize().padding(start = 22.dp, top = 20.dp, end = 96.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (a.urgent) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(p.negative))
            Spacer(Modifier.width(8.dp))
          }
          Text(
            a.title, color = p.onAccent, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp),
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(
          a.body, color = p.onAccent.copy(alpha = 0.66f), maxLines = 2, overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
        )
        Spacer(Modifier.weight(1f))
        Text(
          a.action,
          color = bg,
          style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
          modifier = Modifier.clip(RoundedCornerShape(percent = 50)).background(p.onAccent).padding(horizontal = 16.dp, vertical = 6.dp),
        )
      }
    }
  }
}

/** هیچ هشداری نیست — یک کارتِ آرام، نه فهرستِ خالی */
@Composable
private fun CalmCard(modifier: Modifier) {
  val p = PhoneV3.colors
  Row(
    modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(22.dp)).background(p.tile).padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = p.positive, modifier = Modifier.size(34.dp))
    Spacer(Modifier.width(14.dp))
    Column {
      Text("همه چیز مرتب است", color = p.ink, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold))
      Spacer(Modifier.height(4.dp))
      Text("هشدار یا پیامِ تازه‌ای نیست", color = p.inkMuted, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp))
    }
  }
}

/** «همه» — فهرستِ کاملِ هشدارها در یک کارتِ گرد */
@Composable
private fun AllAlertsDialog(alerts: List<HomeAlerts.Alert>, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
  val p = PhoneV3.colors
  Dialog(onDismissRequest = onDismiss) {
    Column(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(PhoneV3.cardRadius)).background(p.card).padding(vertical = 14.dp),
    ) {
      Text(
        "هشدارها و پیام‌ها", color = p.ink,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
      )
      alerts.forEachIndexed { i, a ->
        if (i > 0) Box(Modifier.padding(start = 58.dp).fillMaxWidth().height(1.dp).background(p.hairline))
        Row(
          Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(a.route) }.padding(horizontal = 20.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(alertIcon(a.kind), contentDescription = null, tint = if (a.urgent) p.negative else p.icon, modifier = Modifier.size(24.dp))
          Spacer(Modifier.width(14.dp))
          Column(Modifier.weight(1f)) {
            Text(a.title, color = p.ink, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Text(a.body, color = p.inkMuted, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), maxLines = 2)
          }
          Icon(Icons.Outlined.ChevronLeft, contentDescription = null, tint = p.chevron)
        }
      }
    }
  }
}

