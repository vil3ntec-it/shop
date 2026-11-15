package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrDesk
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.shots.DeskDemo
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.desk.DeskDashModel
import ir.vil3ntec.tohid.ui.desk.DeskDashboard
import ir.vil3ntec.tohid.ui.desk.DeskMoreModel
import ir.vil3ntec.tohid.ui.desk.DeskMoreScreen
import ir.vil3ntec.tohid.ui.screens.DeliveryGate
import ir.vil3ntec.tohid.ui.screens.DeskHeader
import ir.vil3ntec.tohid.ui.screens.DeskMenu
import ir.vil3ntec.tohid.ui.screens.DeskRail
import ir.vil3ntec.tohid.ui.screens.MoreLayout
import ir.vil3ntec.tohid.ui.screens.RailMode
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.StorefrontGate
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.nav.V3RouteHost
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import ir.vil3ntec.tohid.ui.v3.shell.ShellTargets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 *  پوستهٔ کامپیوتر (e-desk-d1) روی گره‌های واقعی، پنجرهٔ کامپیوتر با چگالیِ ۱ و راست‌به‌چپ:
 *   • منوی کناری هر بخشی را که «بیشتر» ِ گوشی و منوی پیشینِ کامپیوتر دارند نشان می‌دهد، ردیفِ باز «انتخاب‌شده» است،
 *     جمع/باز کار می‌کند، و با Tab/Enter ِ صفحه‌کلید هم می‌شود رفت؛
 *   • سربرگ وقتی منو پیداست ☰ ندارد؛
 *   • «همهٔ بخش‌ها» شبکهٔ کارت‌هاست و هیچ بخشِ «بیشتر» ِ گوشی گم نیست؛
 *   • تنظیماتِ دوستونه: هر شش دسته و «قابلیت‌ها» در ستونِ کنار و هر دسته در ستونِ پهن باز می‌شود؛
 *   • داشبورد: چهار کارتِ عدد با عددِ واقعیِ دفتر، ردیف‌های جدولِ امروز، و حالتِ خالی با کارِ «فروشِ تازه».
 */
@OptIn(ExperimentalTestApi::class)
class DeskShellUiTest {

  private val ctx = DesktopContext
  private var role = ShopRole.OWNER

  @Before fun setUp() {
    I18n.source = { Lang.FA }
    ir.vil3ntec.tohid.desktop.shots.GlobalsWarm.run()
    role = ShopRole.current(ctx)
    ShopRole.remember(ctx, ShopRole.OWNER)
    OrdersLive.config.value = null
    StorefrontGate.config.value = null
    DeliveryGate.config.value = null
  }

  @After fun tearDown() {
    I18n.source = { Lang.FA }
    ShopRole.remember(ctx, role)
  }

  private fun ComposeUiTest.show(w: Int, h: Int, content: @Composable () -> Unit) = setContent {
    CompositionLocalProvider(
      LocalDensity provides Density(1f, 1f),
      LocalContext provides ctx,
      LocalView provides remember { android.view.View(ctx) },
      LocalConfiguration provides Configuration(w, h),
      LocalLayoutDirection provides LayoutDirection.Rtl,
    ) { TohidTheme(ThemeChoice.LIGHT) { content() } }
  }

  private fun ComposeUiTest.count(text: String) = onAllNodes(hasText(text)).fetchSemanticsNodes().size

  private fun ComposeUiTest.click(text: String) {
    onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
  }

  private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

  private fun expectedItems(): List<DeskMenu.Item> {
    val access = MoreLayout.access(canManage = true, ordersOn = false, chatOn = false, storefrontOn = false, deliveryOn = false)
    val extras = V3Modules.entries(Place.MORE, access).mapNotNull { e -> e.group?.let { DeskMenu.Extra(e.id, e.label(), e.route, it) } }
    return DeskMenu.sidebar(true, extras = extras).flatMap { it.items } + DeskMenu.sidebarFooter(true)
  }

  @Test fun sidebarShowsEverySectionMarksTheOpenOneAndCollapses() = runDesktopComposeUiTest(320, 1700, testTimeout = 2.minutes) {
    val picks = ArrayList<String>()
    val modes = ArrayList<String>()
    show(320, 1700) { DeskRail(current = "debtors", onPick = { picks += it }, onMode = { modes += it }, path = "debtors") }
    waitUntil(timeoutMillis = 5_000) { count(StrDesk.allSections) > 0 }
    for (item in expectedItems()) assertTrue("«${item.label}» در منو نیست", count(item.label) > 0)
    //  فقط ردیفِ باز «انتخاب‌شده» است
    val chosen = onAllNodes(selected).fetchSemanticsNodes()
    assertEquals(1, chosen.size)
    assertTrue(onAllNodes(selected and hasText(DeskMenu.sidebar(true).flatMap { it.items }.single { it.id == "debtors" }.label)).fetchSemanticsNodes().size == 1)
    //  زدنِ ردیف ⇒ همان مسیر
    click(DeskMenu.sidebar(true).flatMap { it.items }.single { it.id == "warehouse" }.label)
    assertEquals(listOf("warehouse"), picks)
    //  جمع کردن ⇒ حالتِ فقط‌آیکن (انتخاب در `desk_rail` ِ پوسته)، و پنهان کردن
    click(StrShell.collapseMenu)
    assertEquals(listOf(RailMode.COMPACT), modes)
    onAllNodes(hasContentDescription(StrDesk.hideMenu) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    assertEquals(listOf(RailMode.COMPACT, RailMode.HIDDEN), modes)
  }

  @Test fun compactRailKeepsEveryLiveSectionAsANamedIcon() = runDesktopComposeUiTest(120, 1700, testTimeout = 2.minutes) {
    val modes = ArrayList<String>()
    show(120, 1700) { DeskRail(current = "dashboard", onPick = {}, compact = true, mode = RailMode.COMPACT, onMode = { modes += it }, path = "home") }
    waitForIdle()
    for (item in expectedItems().filter { it.state == DeskMenu.State.LIVE }) {
      assertTrue("«${item.label}» آیکنِ نام‌دار ندارد", onAllNodes(hasContentDescription(item.label)).fetchSemanticsNodes().isNotEmpty())
    }
    //  نامِ ردیف نوشته نمی‌شود (فقط راهنمای هاور و صفحه‌خوان)
    assertEquals(0, count(expectedItems().single { it.id == "debtors" }.label))
    onAllNodes(hasContentDescription(StrShell.openMenu) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    assertEquals(listOf(RailMode.FULL), modes)
  }

  @Test fun keyboardTabAndEnterOpenAMenuRow() = runDesktopComposeUiTest(320, 900, testTimeout = 2.minutes) {
    val picks = ArrayList<String>()
    show(320, 900) { DeskRail(current = "debtors", onPick = { picks += it }, path = "debtors") }
    waitForIdle()
    //  منو ریشهٔ معناییِ خودش را ندارد ولی گرهٔ «منوی بخش‌ها» یکتاست؛ کلید به گرهِ متمرکزِ همان پنجره می‌رود
    val nav = onNode(hasContentDescription(StrDesk.mainNav))
    nav.performKeyInput { pressKey(Key.Tab) }
    waitForIdle()
    nav.performKeyInput { pressKey(Key.Enter) }
    waitForIdle()
    assertEquals("Tab ⇒ نخستین ردیف (خانه)، Enter ⇒ همان", listOf("dashboard"), picks)
  }

  @Test fun headerHasNoHamburgerWhileTheMenuIsVisible() = runDesktopComposeUiTest(1366, 120, testTimeout = 2.minutes) {
    var hidden by androidx.compose.runtime.mutableStateOf(false)
    var menus = 0
    show(1366, 120) {
      DeskHeader(title = "خانه", d = ShopData(), onBack = null, onMenu = { menus++ }, onSearch = {}, onOpen = {}, page = "dashboard", menuVisible = !hidden)
    }
    waitForIdle()
    assertEquals(0, onAllNodes(hasContentDescription(StrDesk.showMenu)).fetchSemanticsNodes().size)
    hidden = true
    waitForIdle()
    onAllNodes(hasContentDescription(StrDesk.showMenu) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    assertEquals(1, menus)
  }

  @Test fun moreIsAGridWithEveryPhoneMoreSection() = runDesktopComposeUiTest(1366, 2600, testTimeout = 2.minutes) {
    val opened = ArrayList<String>()
    show(1366, 2600) { DeskMoreScreen(ShopData(), onOpen = { opened += it }) }
    waitUntil(timeoutMillis = 5_000) { count(StrDesk.allSectionsLead) > 0 }
    val sections = DeskMoreModel.sections(true, false, 0, false, false, false, emptySet())
    for (s in sections) {
      assertTrue("سرخطِ «${s.title}»", count(s.title) > 0)
      for (card in s.cards) assertTrue("کارتِ «${card.title}»", count(card.title) > 0)
    }
    //  هر بخشِ «بیشتر» ِ گوشی کارتی دارد (سنجهٔ کامل در `DeskSidebarModelTest`)
    val phone = MoreLayout.groups(canManage = true, ordersOn = false, newOrders = 0, chatOn = false, version = "1")
    for (g in phone) for (item in g.items) assertTrue("«${item.label}»", count(item.label) > 0 || item.id == "history")
    click(sections.flatMap { it.cards }.single { it.id == "debtors" }.title)
    assertEquals(listOf("debtors"), opened)
  }

  @Test fun settingsIsTwoPaneAndEveryCategoryOpens() = runDesktopComposeUiTest(1366, 1400, testTimeout = 3.minutes) {
    val store = RouteUiHarness.loadedStore(ShopData())
    show(1366, 1400) { DeskRoute("settings", store) }
    val groups = SettingsLayout.GROUPS
    waitUntil(timeoutMillis = 5_000) { count(SettingsLayout.groupLabel(groups.first().title)) > 0 }
    assertTrue(count(ir.vil3ntec.tohid.core.i18n.StrSettingsHome.features) > 0)
    for (g in groups) {
      val label = SettingsLayout.groupLabel(g.title)
      click(label)
      //  ستونِ کنار + سرِ ستونِ پهن («دستگاه‌ها» همان صفحهٔ همهٔ دستگاه‌ها با نامِ خودش)
      if (g.title in SettingsLayout.GROUP_LINKS) waitUntil(label, 5_000) { count(ir.vil3ntec.tohid.core.i18n.Str.devices) > 0 }
      else waitUntil(label, 5_000) { count(label) >= 2 }
    }
  }

  @Test fun dashboardShowsRealNumbersTableRowsAndAnEmptyState() = runDesktopComposeUiTest(1366, 1600, testTimeout = 3.minutes) {
    val d = DeskDemo.full()
    val today = ir.vil3ntec.tohid.todayIso()
    val opened = ArrayList<String>()
    var data by androidx.compose.runtime.mutableStateOf(d)
    show(1366, 1600) { DeskDashboard(data, onOpen = { opened += it }) }
    waitUntil(timeoutMillis = 5_000) { count(StrHome.todaySales) > 0 }
    for (label in listOf(StrHome.todaySales, StrHome.todayProfit, StrDesk.cashNow, StrHome.receivables)) assertTrue(label, count(label) > 0)
    val trend = ir.vil3ntec.tohid.ui.v3.shell.HomeTrend.of(d.sales, today, System.currentTimeMillis())
    assertTrue("فروشِ امروز", count(ir.vil3ntec.tohid.money(trend.today)) > 0)
    val owed = ir.vil3ntec.tohid.ui.v3.shell.DashboardNumbers.of(d, today).totalDebt
    assertTrue("طلب", count(ir.vil3ntec.tohid.money(owed)) > 0)
    val rows = DeskDashModel.rows(d, today, purchases = true, n = DeskDashModel.TABLE_ROWS)
    assertTrue(rows.isNotEmpty())
    for (r in rows) assertTrue("ردیفِ ${r.tx.invoice}", count("#" + r.tx.invoice) > 0)
    for (col in listOf(StrDesk.colTime, StrDesk.colInvoice, StrDesk.colCustomer, StrDesk.colAmount, StrDesk.colPayment, StrDesk.colStatus)) assertTrue(col, count(col) > 0)
    //  دفترِ خالی ⇒ حالتِ خالیِ جدول با کارِ «فروشِ تازه»
    data = ShopData()
    waitUntil(timeoutMillis = 5_000) { count(StrDesk.noTxToday) > 0 }
    onAllNodes(hasText(StrHome.newSale) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    assertEquals(ShellTargets.NEW_SALE, opened.last())
  }
}

/** یک مسیر با چیدمانِ کامپیوتری (`LocalDeskLayout` روشن، پوستهٔ پهن) — همان `V3RoutePreview` بی نوارِ گوشی */
@Composable
private fun DeskRoute(path: String, store: ShopStore) {
  val context = LocalContext.current
  val data by store.data.collectAsState()
  val nav = remember(path) { V3Nav(V3Modules.table, "home", NavStack.of("home")).also { it.wide = true; it.openDeep(path) } }
  val snackbar = remember { SnackbarHostState() }
  val toasts = rememberV3Toasts(snackbar)
  val cart = remember { CartStore(context) }
  val shell = ShellContext(
    store = store, data = data, cart = cart, snackbar = snackbar, toasts = toasts,
    theme = ThemeChoice.LIGHT, onTheme = {}, bottomReserve = 0.dp, wide = true,
    access = Access.all(AppMode.SHOP), navAllowed = emptySet(),
  )
  CompositionLocalProvider(LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides true) {
    V3RouteHost(nav, shell, Modifier.fillMaxSize())
  }
}
