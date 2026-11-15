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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.components.pos.PhoneCatalogItem
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleActions
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleScreen
import ir.vil3ntec.tohid.ui.v3.analytics.AnalyticsPaths
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  کارِ یک‌دستی (صاحب‌کار ۱۴۰۵/۰۸/۲۳) در پنجرهٔ واقعیِ Compose ِ **گوشیِ نسخهٔ ۳** — همان پوسته‌ای که برنامه دارد
 *  (`V3RoutePreview`: پشتهٔ پدرها، `ShellFrame`، نوارِ شناور):
 *   • نوارِ پایین روی کالاها و هر بخشِ اصلیِ دیگر (پیش‌تر فقط روی ریشهٔ تب‌ها؛ صندوق و بخش‌های «بیشتر» بی نوار)؛
 *   • جست‌وجو، اسکن و «کالای تازه» ِ کالاها (بالای نوار) و جستجو، اسکن، نما، «…» و دسته‌های صندوق در نیمهٔ پایینِ صفحه
 *     (مرزِ گره‌ها)؛
 *   • محتوا هرگز زیرِ نوار نمی‌ماند؛
 *   • دوربینِ اسکن و تسویه نوار ندارند.
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
    val store = RouteUiHarness.memoryStore(data)
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

  private fun barShown(): Boolean = rule.onAllNodes(barTab).fetchSemanticsNodes().isNotEmpty()

  private fun barTop(): Dp = rule.onAllNodes(barTab)[0].getBoundsInRoot().top

  private fun middle(): Dp = rule.onRoot().getBoundsInRoot().let { it.top + (it.bottom - it.top) / 2 }

  private fun top(m: SemanticsMatcher): Dp = rule.onAllNodes(m)[0].getBoundsInRoot().top

  private fun bottom(m: SemanticsMatcher): Dp = rule.onAllNodes(m)[0].getBoundsInRoot().bottom

  private fun await(m: SemanticsMatcher) = rule.waitUntil(15_000) { rule.onAllNodes(m).fetchSemanticsNodes().isNotEmpty() }

  @Test
  fun `نوارِ پایین روی کالاها و بخش‌های اصلیِ دیگر — نه روی جزئیات`() {
    show(ProductsPaths.LIST)
    assertTrue("کالاها", barShown())
    listOf(
      StockPaths.LIST, MoneyPaths.DEBTORS, SalesPaths.HISTORY, MoneyPaths.RECEIPTS, MoneyPaths.EXPENSES,
      MoneyPaths.PURCHASING, AnalyticsPaths.REPORTS, SettingsPaths.ROOT, "more", SalesPaths.SALE,
    ).forEach { p ->
      go(p)
      assertTrue("نوارِ پایین روی $p", barShown())
    }
    go(ProductsPaths.item("p-rice"))
    assertFalse("صفحهٔ درونی (جزئیاتِ کالا) نوار ندارد", barShown())
  }

  @Test
  fun `کالاها — جست‌وجو، اسکن و کالای تازه در نیمهٔ پایین و بالای نوار`() {
    show(ProductsPaths.LIST)
    val search = hasSetTextAction() and hasContentDescription(StrProducts.searchHint)
    await(search)
    val scan = hasContentDescription(StrKit.scanBarcode) and hasClickAction()
    val add = (hasText(StrProducts.newProduct) or hasContentDescription(StrProducts.newProduct)) and hasClickAction()
    val half = middle()
    assertTrue("جست‌وجو پایین", top(search) > half)
    assertTrue("اسکن پایین", top(scan) > half)
    assertTrue("کالای تازه پایین", top(add) > half)
    val bar = barTop()
    assertTrue("جست‌وجو زیرِ نوار نمی‌رود", bottom(search) <= bar)
    assertTrue("اسکن زیرِ نوار نمی‌رود", bottom(scan) <= bar)
    assertTrue("کالای تازه زیرِ نوار نمی‌رود", bottom(add) <= bar)
  }

  /**
   *  مسیرِ `sale` در آزمون پشتِ سدِ اشتراک (`VipGate`) است، پس خودِ صندوقِ گوشی (`SaleScreen`) با همان چیدمانِ برنامه
   *  کشیده می‌شود؛ نوارِ پایینِ صندوق را آزمونِ بخش‌ها و آزمونِ تسویه/اسکن (پوسته) می‌سنجند.
   */
  @Test
  fun `صندوق — جستجو، اسکن، نما، «…» و دسته‌ها در نیمهٔ پایین`() {
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
    val search = hasSetTextAction() and hasContentDescription(StrSale.searchItems)
    await(search)
    val half = middle()
    assertTrue("جستجو پایین", top(search) > half)
    assertTrue("اسکن پایین", top(hasContentDescription(StrKit.scanBarcode) and hasClickAction()) > half)
    assertTrue("کاشی/فهرست پایین", top(hasContentDescription(StrSale.listView) and hasClickAction()) > half)
    assertTrue("«…» پایین", top(hasContentDescription(StrKit.more) and hasClickAction()) > half)
    assertTrue("«همه» پایین", top(hasText(StrSale.allChip) and hasClickAction()) > half)
    assertTrue("«+» ِ دسته پایین", top(hasContentDescription(StrSale.newCategory) and hasClickAction()) > half)
    //  بالا فقط برگشت و نامِ صفحه
    assertTrue("برگشت بالا", top(hasContentDescription(StrKit.back) and hasClickAction()) < half)
  }

  @Test
  fun `محتوای پیمایشی تا لبهٔ نوار تمام می‌شود و زیرش نمی‌رود`() {
    show("more")
    val list = hasScrollAction() and androidx.compose.ui.test.hasAnyDescendant(hasClickAction())
    await(list)
    rule.onAllNodes(list)[0].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
    rule.waitForIdle()
    val bar = barTop()
    assertTrue("قابِ فهرست بالای نوار تمام می‌شود", bottom(list) <= bar)
    val rows = rule.onAllNodes(hasClickAction() and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes()
    assertTrue(rows.isNotEmpty())
  }

  @Test
  fun `دوربینِ اسکن و تسویه نوارِ پایین ندارند`() {
    val rice = data.products.first { it.id == "p-rice" }
    LineSheetHarness.seed(listOf(SalesEngine.CartLine(rice.id, 1.0)))
    show(SalesPaths.SALE)
    assertTrue(barShown())
    go(SalesPaths.CHECKOUT)
    assertFalse("تسویه", barShown())
    go(SalesPaths.scan("product"))
    assertFalse("دوربینِ اسکن", barShown())
  }
}
