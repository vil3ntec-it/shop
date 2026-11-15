package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.screens.DESK_RAIL_WIDTH
import ir.vil3ntec.tohid.ui.screens.DeskHeader
import ir.vil3ntec.tohid.ui.screens.DeskRail
import ir.vil3ntec.tohid.ui.screens.DeskStatusBar
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.example.EXAMPLE_FLAG
import ir.vil3ntec.tohid.ui.v3.nav.KIT_GALLERY_FLAG
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.nav.V3RouteHost
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ `claude/e-desk-d3a` برای صاحب‌کار: صندوق، کالاها و انبارِ **کامپیوتر** درست مثلِ پنجرهٔ برنامه — سربرگ
 *  (`DeskHeader`)، منوی کناری (`DeskRail`)، نوارِ وضعیت و مسیرِ واقعی (`V3RouteHost` با `LocalDeskLayout = true`) — در ۱۳۶۶×۷۶۸ و
 *  ۱۹۲۰×۱۰۸۰ با چگالیِ ۱، روشن و تیره، فارسی و راست‌به‌چپ. دادهٔ نمایشی همان `StockDemo` (۴۰ کالا، کم‌موجود، تاریخِ انقضا) با
 *  دو مشتری و سبدِ چهار ردیفه.
 *  فقط با `D3A_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست)؛ `D3A_TAG` (`before`/`after`) پسوندِ نام:
 *  `<پوشه>/<صفحه>-<tag>-<w>x<h>-<light|dark>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class D3aShotsTest {

  private val out: File? = System.getenv("D3A_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val tag: String = System.getenv("D3A_TAG")?.takeIf { it.isNotBlank() } ?: "after"
  private val only: Set<String> = System.getenv("D3A_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

  private fun shot(name: String, w: Int, h: Int, dark: Boolean, content: @Composable () -> Unit) = runDesktopComposeUiTest(w, h, testTimeout = 3.minutes) {
    I18n.source = { Lang.FA }
    mainClock.autoAdvance = false
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(1f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(w, h),
      ) {
        TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
        }
      }
    }
    repeat(16) {
      mainClock.advanceTimeBy(250)
      Thread.sleep(50)
    }
    val img = captureToImage().toAwtImage()
    val dir = out!!.apply { mkdirs() }
    ImageIO.write(img, "png", File(dir, "$name-$tag-${w}x$h-${if (dark) "dark" else "light"}.png"))
  }

  /** پنجرهٔ برنامهٔ کامپیوتر: سربرگ، منوی کناری، صفحهٔ مسیر و نوارِ وضعیت (همان چیدمانِ `AppRoot`) */
  @Composable
  private fun DeskWindow(path: String, title: String, rail: String, store: ShopStore, cart: CartStore, dark: Boolean) {
    val context = LocalContext.current
    val data by store.data.collectAsState()
    val nav = remember(path) { V3Nav(V3Modules.table, "home", NavStack.of("home")).also { n -> n.isTab = { it in setOf("home", "sale", "debtors", "products", "more", "stock") }; n.openDeep(path) } }
    val snackbar = remember { SnackbarHostState() }
    val toasts = rememberV3Toasts(snackbar)
    val shell = ShellContext(
      store = store, data = data, cart = cart, snackbar = snackbar, toasts = toasts,
      theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, onTheme = {},
      bottomReserve = 0.dp, wide = true,
      access = Access.all(AppMode.SHOP, setOf(EXAMPLE_FLAG, KIT_GALLERY_FLAG)),
      navAllowed = NavCatalog.allowed(canManage = true, ordersLive = true),
    )
    CompositionLocalProvider(LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides true) {
      Column(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
        DeskHeader(title = title, d = data, onBack = null, onMenu = {}, onSearch = {}, onOpen = {}, page = rail)
        Box(Modifier.weight(1f).fillMaxWidth()) {
          DeskRail(current = rail, onPick = {})
          Box(Modifier.fillMaxSize().padding(start = DESK_RAIL_WIDTH)) {
            //  صندوق بی سدِ اشتراکِ آزمون (`VipGate` بی حساب قفل است) — همان صفحه‌ای که مسیرِ `sale` برای حسابِ فعال می‌کشد
            if (path == "sale") ir.vil3ntec.tohid.ui.screens.DeskSaleScreen(store = store, cartStore = cart, d = data, snackbar = snackbar) {}
            else V3RouteHost(nav, shell, Modifier.fillMaxSize())
          }
        }
        DeskStatusBar(data)
      }
    }
    @Suppress("UNUSED_EXPRESSION") context
  }

  /** دفترِ حافظه‌ایِ «خوانده‌شده» — بی آن صفحه‌ها اسکلت می‌کشند */
  private fun loadedStore(d: ShopData): ShopStore = RouteUiHarness.memoryStore(d).also { s ->
    @Suppress("UNCHECKED_CAST")
    (ShopStore::class.java.getDeclaredField("_loaded").apply { isAccessible = true }.get(s) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>).value = true
  }

  private fun data(): ShopData {
    val d = StockDemo.full()
    val debtors = listOf(
      Debtor(id = "c1", name = "حاجی محمد کریمی", phone = "0700123456"),
      Debtor(id = "c2", name = "فروشگاهِ امید", phone = "0799000111"),
    )
    val tx = listOf(DebtTransaction(id = "t1", debtorId = "c1", type = "give", amount = 1_250.0, date = ir.vil3ntec.tohid.todayIso()))
    return d.copy(debtors = debtors, transactions = tx)
  }

  private fun screen(name: String, path: String, title: String, rail: String, withCart: Boolean = false, prepare: () -> Unit = {}) {
    if (only.isNotEmpty() && name !in only) return
    val d = data()
    for ((w, h) in listOf(1366 to 768, 1920 to 1080)) for (dark in listOf(false, true)) { prepare(); shot(name, w, h, dark) {
      val store = remember { loadedStore(d) }
      val cart = remember {
        CartStore(DesktopContext).also { c ->
          c.clear()
          if (withCart) {
            c.set(listOf(SalesEngine.CartLine("p1", 2.0), SalesEngine.CartLine("p14", 3.0), SalesEngine.CartLine("p4", 1.0), SalesEngine.CartLine("p29", 6.0)))
            c.setDebtor("c1")
          }
        }
      }
      DeskWindow(path, title, rail, store, cart, dark)
    } }
  }

  @Test
  fun shots() {
    Assume.assumeTrue("D3A_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    //  صندوق: نمای پیش‌فرضِ کارتی (همان پیشِ تغییر) و نمای جدولی — `PosShot` همان قلابِ عکسِ `DevShot`
    val pos = ir.vil3ntec.tohid.ui.components.pos.PosShot.view
    screen("sale", "sale", ir.vil3ntec.tohid.core.i18n.Str.sale, "sale", withCart = true) { pos.value = ir.vil3ntec.tohid.ui.components.common.ViewMode.CARD }
    screen("sale-table", "sale", ir.vil3ntec.tohid.core.i18n.Str.sale, "sale", withCart = true) { pos.value = ir.vil3ntec.tohid.ui.components.common.ViewMode.TABLE }
    screen("products", "products", ir.vil3ntec.tohid.core.i18n.StrProducts.listTitle, "products")
    screen("stock", "stock", ir.vil3ntec.tohid.core.i18n.Str.warehouse, "warehouse")
    screen("stock-item", "stock/" + StockDemo.RICE, ir.vil3ntec.tohid.core.i18n.Str.warehouse, "warehouse")
    screen("stock-moves", "stock/" + StockDemo.RICE + "/movements", ir.vil3ntec.tohid.core.i18n.StrStock.movementsRow, "warehouse")
    screen("stock-entry", "stock/entry", ir.vil3ntec.tohid.core.i18n.StrStock.entryTitle, "warehouse")
    CartStore(DesktopContext).clear()
  }
}
