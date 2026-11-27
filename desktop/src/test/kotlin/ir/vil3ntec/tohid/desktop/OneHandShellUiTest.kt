package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalogItem
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.money.ExpensesModel
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.money.MoneyText
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleActions
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleScreen
import ir.vil3ntec.tohid.ui.v3.analytics.AnalyticsPaths
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  پوستهٔ گوشیِ نسخهٔ ۳ پس از ۶۳w (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «چرا جستجو و کادرهای دیگه رو آوردی بینِ این نوارها؛ خیلی مسخره است»)
 *  در پنجرهٔ واقعیِ Compose — همان پوسته‌ای که برنامه دارد (`V3RoutePreview`: پشتهٔ پدرها، `ShellFrame`، نوارِ شناور):
 *   • نوارِ پایین روی کالاها و هر بخشِ اصلیِ دیگر، نه روی جزئیات؛
 *   • میانِ محتوا و نوار هیچ چیزی نیست جز یک دکمهٔ شناور (نه کادرِ جست‌وجو، نه تراشه، نه آیکن)؛
 *   • دکمهٔ جست‌وجوی نوار جست‌وجوی **همان بخش** را باز می‌کند و کالای همان بخش را می‌یابد؛ صندوق ذره‌بین و اسکنِ شناور دارد؛
 *   • کادرِ کشوییِ ماه/دوره هم ماه را عوض می‌کند و هم عددها را؛
 *   • محتوا هرگز زیرِ نوار نمی‌ماند؛ دوربینِ اسکن و تسویه نوار ندارند.
 */
@OptIn(ExperimentalTestApi::class)
class OneHandShellUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val data = MoneyDemo.full()
  private val path = mutableStateOf(ProductsPaths.LIST)
  private lateinit var h: RouteUiHarness

  @Before fun up() {
    File(ctx.filesDir, CartStore.FILE).delete()
    ShopRole.remember(ctx, ShopRole.OWNER)
    h = RouteUiHarness(rule)
  }

  @After fun down() {
    h.close()
    File(ctx.filesDir, CartStore.FILE).delete()
  }

  /** یک بار صفحه؛ بعد با [go] مسیرِ دیگری در همان پنجره (پشته از نو، مثلِ پیوندِ سرد) */
  private fun show(first: String) {
    path.value = first
    I18n.source = { Lang.FA }
    val store = RouteUiHarness.loadedStore(data)
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) { V3RoutePreview(path.value, store, dark = false) }
      }
    }
    rule.waitForIdle()
  }

  private fun go(p: String) {
    path.value = p
    rule.waitForIdle()
  }

  /** «خانه» ِ نوارِ شناور (تبِ نوار، `Role.Tab`) */
  private val barTab: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasContentDescription(Str.home)

  /** دکمهٔ جست‌وجوی نوارِ شناور */
  private val navSearch: SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasContentDescription(Str.search)

  private fun barShown(): Boolean = rule.onAllNodes(barTab).fetchSemanticsNodes().isNotEmpty()

  private fun barTopPx(): Float = rule.onAllNodes(barTab).fetchSemanticsNodes()[0].boundsInRoot.top

  private fun middlePx(): Float = rule.onRoot().fetchSemanticsNode().boundsInRoot.let { it.top + it.height / 2 }

  private fun nodes(m: SemanticsMatcher): List<SemanticsNode> = rule.onAllNodes(m).fetchSemanticsNodes()

  private fun topPx(m: SemanticsMatcher): Float = nodes(m)[0].boundsInRoot.top

  private fun await(m: SemanticsMatcher) = rule.waitUntil(15_000) { nodes(m).isNotEmpty() }

  private fun click(m: SemanticsMatcher) {
    rule.onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private val inList: SemanticsMatcher = hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))

  /**
   *  «میانِ محتوا و نوار هیچ چیز جز دکمهٔ شناور»: در نیمهٔ پایین و بالای نوار، بیرون از خودِ فهرست، دست‌بالا یک چیزِ
   *  زدنی (دکمهٔ شناور)؛ هیچ کادرِ نوشتن (جست‌وجو تا زدنِ دکمهٔ نوار پنهان است) و هیچ ردیفِ افقیِ تراشه پایین‌تر از نیمه.
   */
  private fun assertNothingAboveBar(where: String) {
    val mid = middlePx()
    val bar = barTopPx()
    val loose = nodes(hasClickAction() and !inList).filter { it.boundsInRoot.top > mid && it.boundsInRoot.bottom <= bar + 1f }
    assertTrue("$where: بالای نوار جز دکمهٔ شناور چیزی نیست — ${loose.map { it.config }}", loose.size <= 1)
    assertTrue("$where: کادرِ جست‌وجو تا زدنِ دکمهٔ نوار پیدا نیست", nodes(hasSetTextAction()).isEmpty())
    val rows = nodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
    assertTrue("$where: ردیفِ تراشه‌ها بالای صفحه است", rows.all { it.boundsInRoot.top < mid })
  }

  @Test
  fun `نوارِ پایین روی کالاها و بخش‌های اصلیِ دیگر — نه روی جزئیات`() {
    show(ProductsPaths.LIST)
    assertTrue("کالاها", barShown())
    listOf(
      StockPaths.LIST, MoneyPaths.DEBTORS, SalesPaths.HISTORY, MoneyPaths.RECEIPTS, MoneyPaths.EXPENSES,
      MoneyPaths.PURCHASING, AnalyticsPaths.REPORTS, SettingsPaths.ROOT, "more",
    ).forEach { p ->
      go(p)
      assertTrue("نوارِ پایین روی $p", barShown())
    }
    //  تغییرِ عمدی (صاحب‌کار ۱۴۰۵/۰۸/۲۴، حالتِ تمرکزِ فروش): صندوق نوارِ شناور ندارد؛ نوارِ سبدِ خودش تنها چیزِ پایین است (`SaleFocusUiTest`)
    go(SalesPaths.SALE)
    assertFalse("صندوق (حالتِ تمرکز) نوار ندارد", barShown())
    go(ProductsPaths.item("p-rice"))
    assertFalse("صفحهٔ درونی (جزئیاتِ کالا) نوار ندارد", barShown())
  }

  @Test
  fun `میانِ محتوا و نوارِ پایین هیچ ردیفی نیست جز دکمهٔ شناور`() {
    show(ProductsPaths.LIST)
    await(hasText(data.products.first().name))
    assertNothingAboveBar("کالاها")
    //  نما (کاشی/فهرست) آیکنِ سربرگ است، بالای صفحه
    assertTrue("نما بالا", topPx(hasContentDescription(StrProducts.viewGrid) or hasContentDescription(StrProducts.viewList)) < middlePx())
    listOf(StockPaths.LIST, SalesPaths.HISTORY, MoneyPaths.EXPENSES, MoneyPaths.RECEIPTS, SettingsPaths.ROOT).forEach { p ->
      go(p)
      rule.mainClock.advanceTimeBy(1_000)
      rule.waitForIdle()
      assertNothingAboveBar(p)
    }
  }

  @Test
  fun `جست‌وجوی نوار جست‌وجوی همان بخش را باز می‌کند و کالای همان بخش را می‌یابد`() {
    show(ProductsPaths.LIST)
    val rice = data.products.first { it.id == "p-rice" }.name
    val oil = data.products.first { it.id == "p-oil" }.name
    await(hasText(oil))
    click(navSearch)
    val field = hasSetTextAction() and hasContentDescription(StrProducts.searchHint)
    await(field)
    assertTrue("کادرِ جست‌وجو بالای صفحه (جای سربرگ)", topPx(field) < middlePx())
    assertTrue("اسکن درونِ همان کادر", nodes(hasContentDescription(StrKit.scanBarcode) and hasClickAction()).any { it.boundsInRoot.top < middlePx() })
    rule.onAllNodes(field)[0].performTextInput("برنج")
    rule.waitForIdle()
    rule.waitUntil(5_000) { nodes(hasText(oil)).isEmpty() }
    assertTrue("کالای همان بخش پیدا شد", nodes(hasText(rice)).isNotEmpty())
    //  «بستن» نوشته را پاک می‌کند و فهرستِ کامل برمی‌گردد
    click(hasText(StrKit.close) and hasClickAction())
    await(hasText(oil))
    assertTrue(nodes(field).isEmpty())
    //  تاریخچهٔ فروش: همان دکمه ⇒ جست‌وجوی فاکتورها
    go(SalesPaths.HISTORY)
    click(navSearch)
    await(hasSetTextAction() and hasContentDescription(StrSale.historySearch))
  }

  /** صندوقِ گوشی (`SaleScreen` — مسیرِ `sale` در آزمون پشتِ سدِ اشتراک است) بی دکمهٔ جست‌وجوی نوار ⇒ ذره‌بینِ سربرگ */
  @Test
  fun `صندوق — دسته‌ها و آیکن‌ها بالا، سبدِ خالی پنهان، جستجو با ذره‌بین`() {
    I18n.source = { Lang.FA }
    val noop: (PhoneCatalogItem) -> Unit = {}
    val actions = SaleActions(
      tap = noop, hold = noop, minus = noop, step = { _, _ -> }, line = {}, customer = {}, clearCustomer = {}, pay = {}, scan = {},
      categories = {}, freeLine = {}, quickSale = {}, history = {}, back = {}, toggleMode = {},
    )
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
        LocalDeskLayout provides false,
      ) {
        TohidTheme(ThemeChoice.LIGHT) { SaleScreen(data, emptyList(), 0.0, null, listMode = false, actions = actions) }
      }
    }
    val find = hasContentDescription(StrKit.search) and hasClickAction()
    await(find)
    val half = middlePx()
    assertTrue("ذره‌بین بالا", topPx(find) < half)
    assertTrue("کاشی/فهرست بالا", topPx(hasContentDescription(StrSale.listView) and hasClickAction()) < half)
    assertTrue("«…» بالا", topPx(hasContentDescription(StrKit.more) and hasClickAction()) < half)
    assertTrue("«همه» بالا", topPx(hasText(StrSale.allChip) and hasClickAction()) < half)
    assertTrue("«+» ِ دسته بالا", topPx(hasContentDescription(StrSale.newCategory) and hasClickAction()) < half)
    assertTrue("بی کادرِ جستجو تا زدنِ ذره‌بین", nodes(hasSetTextAction()).isEmpty())
    val scan = nodes(hasContentDescription(StrKit.scanBarcode) and hasClickAction())
    assertEquals(1, scan.size)
    assertTrue("اسکن در سربرگِ سبدِ خالی", scan[0].boundsInRoot.top < half)
    val low = nodes(hasClickAction() and !inList).filter { it.boundsInRoot.top > half }
    assertTrue("پایینِ فروشِ بی کالا دکمه‌ای ندارد", low.isEmpty())
    //  ذره‌بین ⇒ نوارِ جستجو جای سربرگ، اسکن درونِ کادر؛ نوشتن فهرست را کوتاه می‌کند
    click(find)
    val field = hasSetTextAction() and hasContentDescription(StrSale.catalogHint)
    await(field)
    assertTrue(topPx(field) < half)
    rule.onAllNodes(field)[0].performTextInput("چای")
    rule.waitForIdle()
    val oil = data.products.first { it.id == "p-oil" }.name
    rule.waitUntil(5_000) { nodes(hasText(oil)).isEmpty() }
    assertTrue(nodes(hasText(data.products.first { it.id == "p-tea" }.name)).isNotEmpty())
  }

  @Test
  fun `کادرِ کشوییِ ماه ماه و عددهای مصارف را عوض می‌کند`() {
    show(MoneyPaths.EXPENSES)
    val month = todayIso().take(7)
    val old = data.expenses.first { it.id == "e5" }.date.take(7)
    val thisTotal = MoneyText.afn(ExpensesModel.total(data, month))
    val oldTotal = MoneyText.afn(ExpensesModel.total(data, old))
    rule.mainClock.advanceTimeBy(3_000)
    await(hasText(thisTotal))
    val field = hasText(MoneyText.month(month)) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
    assertTrue("کادرِ کشویی بالای صفحه", topPx(field) < middlePx())
    click(field)
    click(hasText(MoneyText.month(old)) and hasClickAction())
    rule.mainClock.advanceTimeBy(3_000)
    await(hasText(oldTotal))
    assertTrue("برچسبِ خلاصه همان ماه", nodes(hasText(StrMoney.monthTotal(MoneyText.month(old)))).isNotEmpty())
    assertTrue("عددِ ماهِ پیشین رفت", nodes(hasText(thisTotal)).isEmpty())
  }

  @Test
  fun `کادرِ کشوییِ دورهٔ تاریخچه دوره را عوض می‌کند`() {
    show(SalesPaths.HISTORY)
    val period = hasText(Str.today) and hasClickAction() and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)
    await(period)
    click(period)
    click(hasText(StrHistory.thisMonth) and hasClickAction())
    await(hasText(StrHistory.thisMonth) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
    //  خلاصهٔ بالا حالا جمعِ این ماه است («جمع فروش · این ماه · …»)
    assertTrue(nodes(hasText(StrSale.salesTotal + " · " + StrHistory.thisMonth, substring = true)).isNotEmpty())
  }

  @Test
  fun `محتوای پیمایشی تا لبهٔ نوار تمام می‌شود و زیرش نمی‌رود`() {
    show("more")
    val list = hasScrollAction() and androidx.compose.ui.test.hasAnyDescendant(hasClickAction())
    await(list)
    rule.onAllNodes(list)[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
    rule.waitForIdle()
    assertTrue("قابِ فهرست بالای نوار تمام می‌شود", nodes(list)[0].boundsInRoot.bottom <= barTopPx() + 1f)
    val rows = rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes()
    assertTrue(rows.isNotEmpty())
  }

  @Test
  fun `دوربینِ اسکن و تسویه نوارِ پایین ندارند`() {
    val rice = data.products.first { it.id == "p-rice" }
    LineSheetHarness.seed(listOf(SalesEngine.CartLine(rice.id, 1.0)))
    //  کالاها نوار دارند؛ صندوق (حالتِ تمرکز)، تسویه و دوربین نه
    show(ProductsPaths.LIST)
    assertTrue(barShown())
    go(SalesPaths.SALE)
    assertFalse("صندوق", barShown())
    go(SalesPaths.CHECKOUT)
    assertFalse("تسویه", barShown())
    go(SalesPaths.scan("product"))
    assertFalse("دوربینِ اسکن", barShown())
  }
}
