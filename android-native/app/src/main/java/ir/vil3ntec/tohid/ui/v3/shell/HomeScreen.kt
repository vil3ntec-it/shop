package ir.vil3ntec.tohid.ui.v3.shell

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.PhoneStatsPage
import ir.vil3ntec.tohid.ui.screens.PagerDots
import ir.vil3ntec.tohid.ui.screens.PullCard
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.nav.HomePager
import kotlinx.coroutines.launch

/** پهنای تبلتی که خانه را دوستونه می‌کند (shell.md) */
private val TABLET_SPLIT = 840.dp

/**
 *  خانه (`home`، TAB). ورقهٔ ۱: کادرِ بزرگِ ورق‌خور با **یک قالب** برای «فروشِ امروز»، «صندوق» و
 *  «گاوصندوق» (THEME-V2، `DraftHeroCard`)، کارتِ میان‌برها با رنگِ همراهِ هر کدام، «بخش‌ها»،
 *  «تراکنش‌ها» (owner-feedback-3 B) و چرخِ هشدارها. ورقهٔ ۲: آمارِ r8 (G6). کشیدن به چپ ⇐ آمار؛
 *  ورقه‌ها چپ‌به‌راست و درونِ هر ورقه راست‌به‌چپ. کشیدنِ صفحه به پایین از بالا ⇐ همان کادر با
 *  انگشت پایین می‌آید و روی «صندوق» می‌نشیند (`PullCard`، آستانه‌ها بی‌تغییر). تبلتِ ≥۸۴۰dp ⇐ دو ستون.
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
    if (maxWidth >= TABLET_SPLIT && !LocalDeskLayout.current) {
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
          if (page == 0) HomePage(d, view, onOpen, bottomReserve, shot.heroPage, pages, pager.currentPage)
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

/** صفحهٔ «صندوق» در کادرِ بزرگ — مقصدِ کشیدن به پایین */
private const val DRAWER_FACE = 1

@Composable
private fun HomePage(d: ShopData, view: HomeView, onOpen: (String) -> Unit, bottomReserve: Dp, heroStart: Int, pages: Int, currentPage: Int) {
  val p = PhoneV3.colors
  val density = LocalDensity.current
  val scroll = rememberScrollState()
  val scope = rememberCoroutineScope()
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val faces = heroFaces(view)
  val hero = rememberPagerState(initialPage = heroStart.coerceIn(0, faces.lastIndex)) { faces.size }

  /* ---------- کشیدن به پایین از بالای صفحه: کادر با انگشت می‌آید و روی «صندوق» می‌نشیند ---------- */
  var pc by remember { mutableStateOf(PullCard.State()) }
  val cfg = remember(density) {
    with(density) { PullCard.Config(threshold = 64.dp.toPx(), flingMin = 22.dp.toPx(), flingVelocity = 1100.dp.toPx()) }
  }
  val connection = remember(cfg, hero) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
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
        if (PullCard.release(pc, available.y, cfg).shown) {
          scope.launch { hero.animateScrollToPage(DRAWER_FACE, animationSpec = MotionSpec.sheet()) }
        }
        pc = PullCard.State()
        return if (wasPulling) available else Velocity.Zero
      }
    }
  }
  val target = (pc.pull / cfg.threshold * 0.55f).coerceIn(0f, 0.8f)
  val anim = remember { Animatable(0f) }
  LaunchedEffect(target, pc.dragging) {
    if (pc.dragging || !Motion.enabled) anim.snapTo(target) else anim.animateTo(target, MotionSpec.sheet())
  }
  val pullPx = with(density) { 72.dp.toPx() }

  Box(
    Modifier
      .fillMaxSize()
      .background(p.bg)
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
          pc = PullCard.touchDown(pc, atTop = scroll.value == 0)
        }
      }
      .nestedScroll(connection),
  ) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
      Spacer(Modifier.height(top + 8.dp))
      //  ورقه‌های خانه ⇄ آمار
      Box(Modifier.fillMaxWidth().height(14.dp), contentAlignment = Alignment.Center) { PagerDots(pages, currentPage, p.ink, p.chevron) }
      Spacer(Modifier.height(6.dp))
      HomeHeroPager(view, faces, hero, onOpen, Modifier.graphicsLayer { translationY = anim.value * pullPx })
      Spacer(Modifier.height(14.dp))
      HomeShortcuts(onOpen)
      Spacer(Modifier.height(24.dp))
      HomeTitle(StrHome.sections, Modifier.padding(horizontal = 22.dp))
      Spacer(Modifier.height(14.dp))
      HomeTiles(view.tiles, onOpen)
      Spacer(Modifier.height(24.dp))
      HomeTransactions(d, purchases = view.canSeeMoney, onOpen = onOpen)
      Spacer(Modifier.height(24.dp))
      HomeAlertsRow(view.alerts, onOpen)
      Spacer(Modifier.height(bottomReserve + navBottom + 24.dp))
    }
  }
}

/** تبلت: آغاز = کادرِ بزرگ + میان‌برها + بخش‌ها + تراکنش‌ها؛ پایان = هشدارها (فهرستِ عمودی) + آمار */
@Composable
private fun HomeTablet(d: ShopData, view: HomeView, onOpen: (String) -> Unit, bottomReserve: Dp, pages: Int) {
  val p = PhoneV3.colors
  val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val faces = heroFaces(view)
  val hero = rememberPagerState { faces.size }
  Row(Modifier.fillMaxSize().background(p.bg)) {
    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
      Spacer(Modifier.height(top + 12.dp))
      HomeHeroPager(view, faces, hero, onOpen)
      Spacer(Modifier.height(14.dp))
      HomeShortcuts(onOpen)
      Spacer(Modifier.height(24.dp))
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
