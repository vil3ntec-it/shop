package ir.vil3ntec.tohid.ui.v3.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.PhoneStatsPage
import ir.vil3ntec.tohid.ui.screens.PullCard
import ir.vil3ntec.tohid.ui.screens.hostWindow
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.nav.HomePager

/** پهنای تبلتی که خانه را دوستونه می‌کند (shell.md) */
private val TABLET_SPLIT = 840.dp

/**
 *  خانه (`home`، TAB) — phone v3 §D روی کیتِ نسخهٔ ۳: ورقهٔ ۱ = r7 (سربرگِ برند، کارتِ چهار
 *  کارِ سریع، «بخش‌ها»)، «تراکنش‌ها» به شکلِ عکسِ ۳ ِ owner-feedback-3 و چرخِ هشدارها؛ ورقهٔ ۲ = آمارِ r8 (محتوا از گروهِ گزارش). کشیدن به
 *  چپ ⇐ آمار؛ ورقه‌ها چپ‌به‌راست و درونِ هر ورقه راست‌به‌چپ. کشیدنِ صفحه به پایین از بالا ⇐
 *  کارتِ سیاهِ r6 (`PullCard`، آستانه‌ها بی‌تغییر). تبلتِ ≥۸۴۰dp ⇐ دو ستون.
 *
 *  @param bottomReserve جای نوارِ شناورِ پایین؛ محتوا زیرِ آن هم دیده می‌شود
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(d: ShopData, onOpen: (String) -> Unit, bottomReserve: Dp, shot: HomeShot = HomeShot.NONE) {
  val live = rememberHomeView(d)
  val view = (if (shot.big) live.big() else live).let { v -> shot.alerts?.let { v.copy(alerts = it, bellDot = it.any { a -> a.urgent }) } ?: v }
  val pages = if (view.canSeeMoney) 2 else 1
  BoxWithConstraints(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
    if (maxWidth >= TABLET_SPLIT && !Platform.DESKTOP) {
      HomeTablet(d, view, onOpen, bottomReserve, pages)
      return@BoxWithConstraints
    }
    val pager = rememberPagerState(initialPage = (HomePager.page.value ?: shot.page).coerceIn(0, pages - 1)) { pages }
    //  پیوندِ `stats?period=` (G6) ورقِ آمار را می‌خواهد ⇐ با فنرِ کیت همان‌جا
    val wanted = HomePager.page.value
    LaunchedEffect(wanted, pages) {
      if (wanted != null) {
        pager.animateScrollToPage(wanted.coerceIn(0, pages - 1), animationSpec = MotionSpec.sheet())
        HomePager.page.value = null
      }
    }
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
          if (page == 0) HomePage(d, view, onOpen, bottomReserve, shot.pull, shot.pullPage, pages, pager.currentPage)
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
private fun HomePage(d: ShopData, view: HomeView, onOpen: (String) -> Unit, bottomReserve: Dp, forcePull: Boolean, pullPage: Int, pages: Int, currentPage: Int) {
  val p = PhoneV3.colors
  val density = LocalDensity.current
  val scroll = rememberScrollState()
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  /* -------------------- کارتِ کششی: ماشینِ حالت + اسکرولِ تودرتو -------------------- */
  var pc by remember(forcePull) { mutableStateOf(if (forcePull) PullCard.forceShown() else PullCard.State()) }
  val cfg = remember(density) {
    with(density) { PullCard.Config(threshold = 64.dp.toPx(), flingMin = 22.dp.toPx(), flingVelocity = 1100.dp.toPx()) }
  }
  val connection = remember(cfg, forcePull) {
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
  val reveal = cardH + with(density) { 10.dp.toPx() }
  val target = if (pc.shown) 1f else (pc.pull / cfg.threshold * 0.55f).coerceIn(0f, 0.8f)
  val anim = remember { Animatable(0f) }
  LaunchedEffect(target, pc.dragging) {
    if (pc.dragging || !Motion.enabled) anim.snapTo(target) else anim.animateTo(target, MotionSpec.sheet())
  }
  //  دکمهٔ برگشتِ گوشی هم کارت را می‌بندد
  BackHandler(enabled = pc.shown) { pc = PullCard.dismiss(pc) }
  LightStatusIcons()

  Box(
    Modifier
      .fillMaxSize()
      .background(p.card)
      .pointerInput(forcePull) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          pc = PullCard.touchDown(pc, atTop = scroll.value == 0)
        }
      }
      .nestedScroll(connection),
  ) {
    Column(Modifier.fillMaxSize().graphicsLayer { translationY = anim.value * reveal }.verticalScroll(scroll)) {
      HomeHero(view, top, pages, currentPage, onOpen)
      Spacer(Modifier.height(26.dp))
      HomeTitle(StrHome.sections, Modifier.padding(horizontal = 22.dp))
      Spacer(Modifier.height(14.dp))
      HomeTiles(view.tiles, onOpen)
      Spacer(Modifier.height(24.dp))
      HomeTransactions(d, purchases = view.canSeeMoney, onOpen = onOpen)
      Spacer(Modifier.height(24.dp))
      HomeAlertsRow(view.alerts, onOpen)
      Spacer(Modifier.height(bottomReserve + navBottom + 24.dp))
    }

    //  لمسِ بیرونِ کارت یا هل دادن به بالا ⇐ پنهان
    if (pc.shown) {
      Box(
        Modifier.fillMaxSize().pointerInput(forcePull) {
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
      HomePullCard(
        view = view,
        progress = anim.value,
        initialFace = pullPage,
        onHeight = { cardH = it },
        onDismiss = { pc = PullCard.dismiss(pc) },
        onOpen = { r -> pc = PullCard.dismiss(pc); onOpen(r) },
      )
    }
  }
}

/** تبلت: آغاز = سربرگ + کارهای سریع + بخش‌ها؛ پایان = هشدارها (فهرستِ عمودی) + آمار */
@Composable
private fun HomeTablet(d: ShopData, view: HomeView, onOpen: (String) -> Unit, bottomReserve: Dp, pages: Int) {
  val p = PhoneV3.colors
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  LightStatusIcons()
  Row(Modifier.fillMaxSize().background(p.bg)) {
    Column(Modifier.weight(1f).fillMaxHeight().background(p.card).verticalScroll(rememberScrollState())) {
      HomeHero(view, top, pages = 1, currentPage = 0, onOpen = onOpen)
      Spacer(Modifier.height(26.dp))
      HomeTitle(StrHome.sections, Modifier.padding(horizontal = 22.dp))
      Spacer(Modifier.height(14.dp))
      HomeTiles(view.tiles, onOpen)
      Spacer(Modifier.height(24.dp))
      HomeTransactions(d, purchases = view.canSeeMoney, onOpen = onOpen)
      Spacer(Modifier.height(bottomReserve + 24.dp))
    }
    Spacer(Modifier.width(1.dp).fillMaxHeight().background(p.hairline))
    Column(Modifier.weight(1f).fillMaxHeight()) {
      Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { HomeAlertsList(view, onOpen, Modifier.padding(top = top)) }
      if (pages > 1) Box(Modifier.weight(1f)) { HomeStatsPage(d, 0.dp, 1, 0, big = false) }
    }
  }
}

/** ساعت و باتری روی سربرگِ برند (و کارتِ سیاه) سفید؛ با رفتن از خانه همان که بود */
@Composable
private fun LightStatusIcons() {
  val host = LocalView.current
  DisposableEffect(host) {
    val bars = hostWindow(host.context)?.let { androidx.core.view.WindowCompat.getInsetsController(it, host) }
    val before = bars?.isAppearanceLightStatusBars
    bars?.isAppearanceLightStatusBars = false
    onDispose { if (bars != null && before != null) bars.isAppearanceLightStatusBars = before }
  }
}
