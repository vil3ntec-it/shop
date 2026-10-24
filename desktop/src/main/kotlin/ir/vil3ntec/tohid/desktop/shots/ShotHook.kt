package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.nav.ShellFrame
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.example.EXAMPLE_FLAG
import ir.vil3ntec.tohid.ui.v3.nav.KIT_GALLERY_FLAG
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.nav.V3RouteHost
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts

/**
 *  یک حالتِ عکس: یا یک مسیر (`route`، همان‌طور که برنامه بازش می‌کند — با پوسته، نوار
 *  و پشتهٔ پدرها) یا یک محتوای دلخواه (`content`). `prepare` پیش از کشیدن (دادهٔ
 *  نمایشی، حالتِ «Shot»ِ صفحه) و `cleanup` پس از آن.
 */
class Shot(
  val name: String,
  val route: String? = null,
  val prepare: () -> Unit = {},
  val cleanup: () -> Unit = {},
  val content: (@Composable (ShopStore) -> Unit)? = null,
)

/** عکس‌های یک گروه — هر گروه فقط فایلِ `<Group>Shots.kt` ِ خودش را ویرایش می‌کند */
interface GroupShots {
  val group: String
  val shots: List<Shot>
}

/**
 *  فهرستِ همهٔ گروه‌ها — فرمانِ `groupshots:<گروه|all>`ِ `DevShot` هر حالت را روشن و
 *  تیره × فارسی و انگلیسی می‌گیرد: `<پوشه>/<گروه>/<حالت>-<light|dark>-<fa|en>.png`.
 */
object ShotRegistry {
  val ALL: List<GroupShots> = listOf(
    FoundationShots, ShellShots, OnboardingShots, ProductsShots, StockShots, MoneyShots,
    SalesShots, AnalyticsShots, AccountShots, OnlineShots, SettingsShots, ShopperShots,
  )

  fun of(group: String): List<GroupShots> = if (group == "all") ALL else ALL.filter { it.group == group }

  fun find(group: String, name: String): Shot? = ALL.firstOrNull { it.group == group }?.shots?.firstOrNull { it.name == name }
}

/**
 *  یک مسیر درست مثلِ برنامه: پشته از زنجیرهٔ پدرها (پیوندِ سرد)، پوسته (`V3RouteHost`)،
 *  و نوارِ شناورِ پایین وقتی مسیر تبِ نوار است. همهٔ نیازها و پرچمِ نمونه‌ها روشن‌اند.
 */
@Composable
fun V3RoutePreview(path: String, store: ShopStore, dark: Boolean) {
  val context = LocalContext.current
  val data by store.data.collectAsState()
  val mode = if (RoutePaths.base(path) == "shopper") AppMode.SHOPPER else AppMode.SHOP
  val navRoutes = NavLayouts.DEFAULT.ids.mapNotNull { NavCatalog.of(it)?.route?.substringBefore('?') }.toSet() + "home"
  val nav = remember(path) {
    V3Nav(V3Modules.table, "home", NavStack.of("home")).also { n ->
      n.isTab = { it in navRoutes }
      n.openDeep(path)
    }
  }
  val snackbar = remember { SnackbarHostState() }
  val toasts = rememberV3Toasts(snackbar)
  val cart = remember { CartStore(context) }
  val shell = ShellContext(
    store = store, data = data, cart = cart, snackbar = snackbar, toasts = toasts,
    theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, onTheme = {},
    bottomReserve = PhoneNav.RESERVE, wide = false,
    access = Access.all(mode, setOf(EXAMPLE_FLAG, KIT_GALLERY_FLAG)),
    navAllowed = NavCatalog.allowed(canManage = true, ordersLive = true),
  )
  //  همان قراردادِ پوسته (`ShellFrame`): نوار فقط روی ریشهٔ تب‌ها و پایینِ هر صفحه نگه‌داشته —
  //  عکس همان چیزی را نشان می‌دهد که گوشی (AUDIT-NAV S1/N11)
  val page = RoutePaths.normalize(nav.page.substringBefore('?'))
  val navVisible = mode == AppMode.SHOP && ShellFrame.navVisible(phone = true, page = page, barTabs = navRoutes, gate = nav.gate != null, cartOpen = false)
  val pageBottom = if (mode == AppMode.SHOP) ShellFrame.pageBottom(true, navVisible, page, PhoneNav.RESERVE, 0.dp) else 0.dp
  CompositionLocalProvider(LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp) {
    Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
      V3RouteHost(nav, shell, Modifier.fillMaxSize().padding(bottom = pageBottom))
      if (navVisible) {
        PhoneNavBar(NavLayouts.DEFAULT, page, onPick = { nav.open(it.route) }, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter))
      }
      V3SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter))
    }
  }
}
