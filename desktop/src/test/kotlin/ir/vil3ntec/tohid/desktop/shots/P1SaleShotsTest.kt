package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.QuotePref
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.SpecialOrderPref
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.OrdersLive
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.components.pos.DeliverLaterPrefs
import ir.vil3ntec.tohid.ui.components.pos.PhoneCartUi
import ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.v3.sales.cart.TillSpec
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteArgs
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.ShellFrame
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleRoute
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ صندوقِ گوشی (e-phone-p1، صاحب‌کار ۱۴۰۵/۰۸/۲۴: «بخش فروش خیلی داغونه … سبد صدها دکمه داره»):
 *  گوشیِ ۳۶۰×۷۶۰dp با چگالیِ ۲، فارسی، روشن و تیره — همان مسیرِ `sale` (`SaleRoute` با ابزارهای واقعیِ سبد) درونِ همان
 *  قراردادِ پوسته (`ShellFrame`: نوارِ شناور فقط جایی که پوسته می‌کشد). ابزارهایی که در عکسِ صاحب‌کار روشن بودند روشن‌اند
 *  (پیش‌فاکتور، سفارشِ ویژه، لیستِ قیمت، یادداشت، تحویلِ بعدی) و پس از عکس به پیش‌فرض برمی‌گردند.
 *  فقط با `P1_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست).
 */
@OptIn(ExperimentalTestApi::class)
class P1SaleShotsTest {

  private val out: File? = System.getenv("P1_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private val ledger: ShopData = run {
    val products = listOf(
      Product("p-choc", "شکلات", "شکلات‌ها", "عدد", salePrice = 120.0),
      Product("p-dark", "شکلاتِ تلخِ ۷۰٪", "شکلات‌ها", "عدد", salePrice = 260.0),
      Product("p-juice", "آب‌میوهٔ انار", "آبمیوه", "عدد", salePrice = 60.0),
      Product("p-orange", "آب‌میوهٔ پرتقال یک لیتری", "آبمیوه", "عدد", salePrice = 140.0),
      Product("p-biscuit", "بیسکویتِ ساقه‌طلایی", "خوراکی", "بسته", salePrice = 45.0),
      Product("p-rice", "برنجِ سلامِ هندی", "خوراکی", "کیلو", salePrice = 380.0),
      Product("p-oil", "روغنِ آفتاب‌گردانِ ۱٫۸ لیتری", "خوراکی", "عدد", salePrice = 450.0),
      Product("p-tea", "چای سبزِ احمد ۵۰۰ گرمی", "خوراکی", "عدد", salePrice = 320.0),
      Product("p-water", "آبِ معدنی ۱٫۵ لیتری", "آبمیوه", "عدد", salePrice = 25.0),
      Product("p-sugar", "شکر", "خوراکی", "کیلو", salePrice = 95.0),
      Product("p-gone", "کبریت", "خوراکی", "عدد", salePrice = 5.0),
    )
    val stock = mapOf(
      "p-choc" to 150.0, "p-dark" to 12.0, "p-juice" to 40.0, "p-orange" to 3.0, "p-biscuit" to 64.0, "p-rice" to 42.5,
      "p-oil" to 18.0, "p-tea" to 12.0, "p-water" to 96.0, "p-sugar" to 60.0,
    )
    val sold = listOf("p-choc", "p-choc", "p-juice", "p-water", "p-water", "p-biscuit")
    ShopData(
      products = products,
      productCategories = listOf("آبمیوه", "شکلات‌ها", "خوراکی"),
      warehouseEntries = stock.map { (id, q) -> WarehouseEntry(id = "w-$id", productId = id, units = q) },
      sales = sold.indices.map { Sale(id = "s$it", invoiceNumber = 100 + it, status = "completed") },
      saleItems = sold.mapIndexed { i, id -> SaleItem(id = "i$i", saleId = "s$i", productId = id, quantity = 1.0) },
    )
  }

  private val three = listOf(CartLine("p-choc", 3.0), CartLine("p-juice", 1.0), CartLine("p-biscuit", 2.0))

  /** همان پوستهٔ گوشی (`AppRoot`) برای یک صفحه: `ShellFrame` می‌گوید نوار هست یا نه */
  @Composable
  private fun TillShell(store: ShopStore, cart: CartStore, dark: Boolean) {
    val data by store.data.collectAsState()
    val navRoutes = NavLayouts.DEFAULT.ids.mapNotNull { NavCatalog.of(it)?.route?.substringBefore('?') }.toSet() + "home"
    val nav = remember {
      V3Nav(V3Modules.table, "home", NavStack.of("home")).also { n -> n.isTab = { it in navRoutes }; n.openDeep("sale") }
    }
    val snackbar = remember { SnackbarHostState() }
    val toasts = rememberV3Toasts(snackbar)
    val shell = ShellContext(
      store = store, data = data, cart = cart, snackbar = snackbar, toasts = toasts,
      theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, onTheme = {},
      bottomReserve = PhoneNav.RESERVE, wide = false,
      access = Access.all(AppMode.SHOP, emptySet()),
      navAllowed = NavCatalog.allowed(canManage = true, ordersLive = true),
    )
    val page = RoutePaths.normalize(nav.page.substringBefore('?'))
    val cartOpen by PhoneCartUi.sheetOpen
    val bar = ShellFrame.bar(
      phone = true, page = page, barTabs = navRoutes, sections = V3Modules.sections, gate = false,
      cartOpen = cartOpen, sheetOnTop = nav.current != nav.page, keyboard = false,
    )
    val navSearch = ShellFrame.navSearch(true, page, navRoutes, V3Modules.sections, false, NavCatalog.SEARCH in NavLayouts.DEFAULT.ids)
    CompositionLocalProvider(
      LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides false,
      ir.vil3ntec.tohid.ui.v3.LocalV3NavSearch provides navSearch,
    ) {
      Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
        Box(Modifier.fillMaxSize().padding(bottom = ShellFrame.pageBottom(true, bar, page, PhoneNav.RESERVE, 0.dp))) {
          val scope = remember(nav, shell) { RouteScope(RouteArgs("sale", emptyMap()), nav, shell) }
          scope.SaleRoute()
        }
        if (ShellFrame.navVisible(bar)) {
          PhoneNavBar(NavLayouts.DEFAULT, page, onPick = {}, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter))
        }
        V3SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter))
      }
    }
  }

  private fun ComposeUiTest.settle(steps: Int = 16) = repeat(steps) {
    mainClock.advanceTimeBy(250)
    Thread.sleep(40)
  }

  private fun ComposeUiTest.tap(m: SemanticsMatcher) {
    onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    settle()
  }

  /** گرهی که «باز کردنِ سبد» را دارد — پیش و پس هر دو همین برچسب */
  private val openCart = SemanticsMatcher("open cart") { it.config.getOrNull(SemanticsActions.OnClick)?.label == StrSale.openCart }

  /** نمای فهرستِ صندوق (همان انبارِ `CatalogMode` ِ `SaleRoute`) — عکسِ صاحب‌کار نمای فهرست بود */
  private fun listMode(on: Boolean) {
    val store = ir.vil3ntec.tohid.ui.components.common.ViewModePref.Store.of(DesktopContext.getSharedPreferences(ir.vil3ntec.tohid.ui.components.common.ViewModePref.PREFS, 0))
    val key = ir.vil3ntec.tohid.ui.components.common.ViewModePref.key(runCatching { ir.vil3ntec.tohid.sync.SyncStore(DesktopContext).accountId }.getOrDefault(""), "sale-phone")
    store.put(key, (if (on) ir.vil3ntec.tohid.ui.components.common.ViewMode.TABLE else ir.vil3ntec.tohid.ui.components.common.ViewMode.CARD).id)
  }

  private fun shot(name: String, dark: Boolean, lines: List<CartLine>, list: Boolean = false, step: ComposeUiTest.() -> Unit = {}) =
    runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
      listMode(list)
      I18n.source = { Lang.FA }
      mainClock.autoAdvance = false
      val store = RouteUiHarness.loadedStore(ledger)
      val cart = CartStore(DesktopContext)
      cart.clear()
      if (lines.isNotEmpty()) cart.set(lines)
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(2f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, 760),
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { TillShell(store, cart, dark) }
          }
        }
      }
      settle(24)
      step()
      settle(12)
      val img = captureToImage().toAwtImage()
      ImageIO.write(img, "png", File(out!!.apply { mkdirs() }, "$name-${if (dark) "dark" else "light"}-fa.png"))
      cart.clear()
    }

  @Test
  fun shots() {
    Assume.assumeTrue("P1_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val ctx = DesktopContext
    QuotePref.set(ctx, true)
    SpecialOrderPref.set(ctx, true)
    PriceListPrefs.set(ctx, true)
    DeliverLaterPrefs.set(ctx, true)
    OrdersLive.config.value = OrdersConfigDto(flag = true, entitled = true, enabled = true, permissions = OrderPermsDto(create = true))
    try {
      for (dark in listOf(false, true)) {
        shot("1-sale-empty", dark, emptyList())
        shot("2-sale-3items", dark, three)
        shot("2b-sale-3items-list", dark, three, list = true)
        shot("3-cart-open", dark, three) {
          val start = onNode(hasTestTag(TillSpec.BAR_TAG)).fetchSemanticsNode().boundsInRoot.center
          onRoot().performTouchInput { swipe(start, start - Offset(0f, height * 0.55f), durationMillis = 390) }
          settle()
        }
        //  پس: «…» ِ سبد (همهٔ ابزارهای سبد با توضیح)؛ پیش: تنها «…» ِ صندوق همان «…» ِ سربرگ بود
        shot("4-menu-open", dark, three) { tap(openCart); tap(hasContentDescription(StrSale.cartMore) and hasClickAction()) }
      }
    } finally {
      listMode(false)
      QuotePref.set(ctx, false)
      SpecialOrderPref.set(ctx, false)
      PriceListPrefs.set(ctx, false)
      DeliverLaterPrefs.set(ctx, false)
      OrdersLive.config.value = null
    }
  }
}
