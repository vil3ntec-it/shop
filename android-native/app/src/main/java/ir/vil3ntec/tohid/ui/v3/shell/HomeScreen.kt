package ir.vil3ntec.tohid.ui.v3.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.HomeAlerts
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.PhoneStatsPage
import ir.vil3ntec.tohid.ui.screens.PullCard
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.HERO_HEIGHT
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.nav.HomePager
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.v3Stagger
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** پهنای تبلتی که خانه را دوستونه می‌کند (shell.md) */
private val TABLET_SPLIT = 840.dp

/** فاصلهٔ میانِ بلوک‌ها (HOME-V2 §۳) */
private val BLOCK = 24.dp

/** فاصلهٔ زیرِ کادرِ پنهانِ باز تا کادرِ اصلی */
private val DRAWER_GAP = 12.dp

/** نشانِ «راهنمای کشیدن دیگر لازم نیست» — فقط روی همین دستگاه */
private const val PREFS = "tohid-shell"
private const val HINT_DONE = "pull_hint_done"

/**
 *  خانه (`home`، TAB) — HOME-V2: ساده و جذاب، از بالا به پایین همین و نه بیشتر:
 *  سرخط (نامِ دکان ˅ + زنگ)، دستگیرهٔ کادرِ پنهان، کادرِ اصلیِ «فروشِ امروز»، چهار میان‌بر،
 *  «امروز» (۵ ردیف) و هشدار فقط وقتی کاری فوری هست. کادرِ «صندوق / گاوصندوق» پنهان است و فقط با
 *  کشیدنِ به‌زورِ صفحه از بالا بیرون می‌آید (`PullCard`). ورقهٔ ۲: آمار (G6، `HomePager.stats`)؛
 *  زدن روی کادرِ اصلی همان‌جا می‌رود. تبلتِ ≥۸۴۰dp ⇐ خانه | آمار.
 *
 *  @param bottomReserve جای نوارِ شناورِ پایین؛ محتوا زیرِ آن هم دیده می‌شود
 */
@Composable
fun HomeScreen(d: ShopData, onOpen: (String) -> Unit, bottomReserve: Dp, shot: HomeShot = HomeShot.NONE) {
  val live = rememberHomeView(d)
  val view = (if (shot.big) live.big() else live).let { v -> shot.alerts?.let { v.copy(alerts = it, bellDot = it.any { a -> a.urgent }) } ?: v }
  val today = ir.vil3ntec.tohid.todayIso()
  val trend = remember(d, today) { HomeTrend.of(d.sales, today, System.currentTimeMillis()) }
  val pages = if (view.canSeeMoney) 2 else 1
  BoxWithConstraints(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
    if (maxWidth >= TABLET_SPLIT && !LocalDeskLayout.current) {
      HomeTablet(d, view, trend, onOpen, bottomReserve, pages, shot)
      return@BoxWithConstraints
    }
    val pager = rememberPagerState(initialPage = (HomePager.page.value ?: shot.page).coerceIn(0, pages - 1)) { pages }
    val scope = rememberCoroutineScope()
    //  پیوندِ `stats?period=` (G6) ورقِ آمار را می‌خواهد ⇐ با فنرِ کیت همان‌جا
    val wanted = HomePager.page.value
    LaunchedEffect(wanted, pages) {
      if (wanted != null) {
        pager.animateScrollToPage(wanted.coerceIn(0, pages - 1), animationSpec = MotionSpec.sheet())
        HomePager.page.value = null
      }
    }
    val onStats: (() -> Unit)? = if (pages > 1) ({ scope.launch { pager.animateScrollToPage(1, animationSpec = MotionSpec.sheet()) } }) else null
    val rtl = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
      //  ورق با انگشت ۱:۱ می‌آید و با سرعتِ رها کردن روی فنرِ کیت می‌نشیند (MOTION-APPLE)
      HorizontalPager(
        state = pager,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 0,
        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = MotionSpec.sheet()),
      ) { page ->
        CompositionLocalProvider(LocalLayoutDirection provides rtl) {
          if (page == 0) HomePage(d, view, trend, onOpen, onStats, bottomReserve, shot)
          else HomeStatsPage(d, bottomReserve, pages, pager.currentPage, shot.big)
        }
      }
    }
  }
}

/**
 *  ورقهٔ دوم (آمارِ r8) — محتوا از گروهِ گزارش (G6) با `HomePager.stats`؛ تا G6 ثبتش کند
 *  همان صفحهٔ آمارِ امروز.
 */
@Composable
private fun HomeStatsPage(d: ShopData, bottomReserve: Dp, pages: Int, current: Int, big: Boolean) {
  val stats = HomePager.stats
  if (stats != null) stats(d, bottomReserve, pages, current) else PhoneStatsPage(d, bottomReserve, pages, current, big = big)
}

@Composable
private fun HomePage(
  d: ShopData,
  view: HomeView,
  trend: HomeTrend.View,
  onOpen: (String) -> Unit,
  onStats: (() -> Unit)?,
  bottomReserve: Dp,
  shot: HomeShot,
  modifier: Modifier = Modifier,
) {
  val p = PhoneV3.colors
  val density = LocalDensity.current
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val scroll = rememberScrollState()
  val scope = rememberCoroutineScope()
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBottom = LocalV3SystemBottom.current ?: WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val drawerPager = rememberPagerState(initialPage = shot.drawerPage.coerceIn(0, DrawerPages.COUNT - 1)) { DrawerPages.COUNT }
  val stagger = rememberV3Stagger()

  /* ---------- کادرِ پنهان: کششِ لاستیکی از بالای صفحه (HOME-V2 §۱) ---------- */
  //  بلندیِ کادرِ باز = همان کادرِ بزرگ (با درشتیِ نوشته) + فاصلهٔ زیرش
  val cfg = remember(density) {
    val open = HERO_HEIGHT * density.fontScale.coerceIn(1f, 1.3f) + DRAWER_GAP
    with(density) { PullCard.Config(open = open.toPx(), threshold = 84.dp.toPx(), flingMin = 28.dp.toPx(), flingVelocity = 1100.dp.toPx()) }
  }
  //  حالت فقط در لَمبداهای اندازه/رسم خوانده می‌شود ⇐ کشیدن صفحه را دوباره نمی‌سازد (۶۰ فریم)
  var pc by remember {
    mutableStateOf(
      when {
        shot.reveal >= 1f -> PullCard.forceShown()
        shot.reveal > 0f -> PullCard.forcePulled(shot.reveal, cfg)
        else -> PullCard.State()
      },
    )
  }
  val settle = remember { Animatable(if (shot.reveal >= 1f) cfg.open else 0f) }
  val shown by remember { derivedStateOf { pc.shown } }
  //  کادر فقط وقتی ساخته می‌شود که دیده شود؛ بولی ⇐ کشیدن ترکیب را دوباره نمی‌سازد
  val alive by remember { derivedStateOf { pc.shown || pc.dragging || settle.isRunning || settle.value > 0f } }
  fun visible(): Float = if (pc.dragging) PullCard.visible(pc, cfg) else settle.value
  fun progress(): Float = (visible() / cfg.open).coerceIn(0f, 1f)

  val prefs = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }
  var hint by remember { mutableStateOf(!prefs.getBoolean(HINT_DONE, false)) }
  fun opened() {
    if (hint) {
      hint = false
      prefs.edit().putBoolean(HINT_DONE, true).apply()
    }
  }
  fun open() {
    pc = PullCard.forceShown()
    opened()
    scope.launch { settle.animateTo(cfg.open, MotionSpec.sheet()) }
  }
  fun close() {
    if (!pc.shown) return
    pc = PullCard.dismiss(pc)
    scope.launch { settle.animateTo(0f, MotionSpec.sheet()) }
  }
  BackHandler(enabled = shown) { close() }

  val connection = remember(cfg) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
        //  باز: کشیدن رو به بالا یا اسکرول ⇐ بسته (اسکرول همان‌طور ادامه می‌دهد)
        if (pc.shown) {
          close()
          return Offset.Zero
        }
        val (s, used) = PullCard.pullUp(pc, -available.y, cfg)
        pc = s
        return Offset(0f, -used)
      }

      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        //  فقط حرکتِ انگشت که اسکرول دیگر نخورد (بالای صفحه) — نه پرتاب
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        val before = pc
        pc = PullCard.pullDown(pc, available.y, cfg)
        if (!before.latched && pc.latched) haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        return if (pc != before) Offset(0f, available.y) else Offset.Zero
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        if (!pc.dragging) return Velocity.Zero
        val from = PullCard.visible(pc, cfg)
        val next = PullCard.release(pc, available.y, cfg)
        settle.snapTo(from)
        pc = next
        if (next.shown) opened()
        scope.launch {
          settle.animateTo(if (next.shown) cfg.open else 0f, MotionSpec.sheet(), initialVelocity = if (next.shown) available.y * PullCard.RUBBER * 0.5f else 0f)
        }
        return available
      }
    }
  }

  Box(
    modifier
      .fillMaxSize()
      .background(p.bg)
      .pointerInput(cfg) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          val atTop = scroll.value == 0
          //  فنرِ برگشت هنوز در راه است ⇐ کشش از همان جا ادامه می‌یابد، نه از صفر
          val carry = if (!pc.shown && !pc.dragging && settle.value > 0f) PullCard.fingerFor(settle.value, cfg) else 0f
          pc = PullCard.touchDown(pc, atTop).let { if (carry > 0f && atTop) it.copy(finger = carry) else it }
          if (carry > 0f) scope.launch { settle.stop() }
        }
      }
      .nestedScroll(connection),
  ) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
      Spacer(Modifier.height(top + 4.dp))
      HomeHeader(view, onOpen, Modifier.v3Stagger(stagger, "header", 0))
      PullHandle(shown = shown, hint = hint, progress = ::progress, onToggle = { if (pc.shown) close() else open() })
      //  کادرِ پنهان: بلندی = پیکسلِ دیدنی، در مرحلهٔ اندازه؛ کادر از زیرِ سرخط پایین می‌لغزد
      Box(
        Modifier
          .fillMaxWidth()
          .clipToBounds()
          .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
            val v = visible().roundToInt().coerceAtLeast(0)
            layout(placeable.width, v) {
              val full = cfg.open.roundToInt()
              placeable.placeRelativeWithLayer(0, v - full) {
                val f = (v / cfg.open).coerceIn(0f, 1f)
                alpha = 0.35f + 0.65f * f
                scaleX = 0.94f + 0.06f * f
                scaleY = scaleX
              }
            }
          },
      ) {
        //  تا کسی نکشیده، ساخته نمی‌شود
        if (alive) {
          Column {
            HomeDrawerPager(view, drawerPager, onOpen)
            Spacer(Modifier.height(DRAWER_GAP))
          }
        }
      }
      Spacer(Modifier.height(4.dp))
      HomeSalesCard(view.todaySales, trend, onStats, Modifier.v3Stagger(stagger, "sales", 1))
      Spacer(Modifier.height(BLOCK))
      HomeShortcuts(onOpen, Modifier.v3Stagger(stagger, "shortcuts", 2))
      Spacer(Modifier.height(BLOCK))
      HomeToday(d, purchases = view.canSeeMoney, onOpen = onOpen, modifier = Modifier.v3Stagger(stagger, "today", 3))
      val pressing = HomeAlerts.pressing(view.alerts)
      if (pressing.isNotEmpty()) {
        Spacer(Modifier.height(BLOCK))
        HomeUrgent(pressing, onOpen, Modifier.v3Stagger(stagger, "urgent", 4))
      }
      Spacer(Modifier.height(bottomReserve + navBottom + BLOCK))
    }
  }
}

/** تبلت: آغاز = همان خانهٔ ساده (با کادرِ پنهان)؛ پایان = آمار (برای کسی که آمار دارد) */
@Composable
private fun HomeTablet(d: ShopData, view: HomeView, trend: HomeTrend.View, onOpen: (String) -> Unit, bottomReserve: Dp, pages: Int, shot: HomeShot) {
  val p = PhoneV3.colors
  Row(Modifier.fillMaxSize().background(p.bg)) {
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
      HomePage(d, view, trend, onOpen, onStats = null, bottomReserve = bottomReserve, shot = shot, modifier = Modifier.widthIn(max = 640.dp))
    }
    if (pages > 1) {
      Spacer(Modifier.width(1.dp).fillMaxHeight().background(p.hairline))
      Box(Modifier.weight(1f).fillMaxHeight()) { HomeStatsPage(d, 0.dp, 1, 0, big = false) }
    }
  }
}
