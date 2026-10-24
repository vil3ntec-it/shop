package ir.vil3ntec.tohid.ui.v3.modes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.AppModes
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.V3Tab
import ir.vil3ntec.tohid.ui.v3.V3TabBar
import ir.vil3ntec.tohid.ui.v3.nav.ModuleFlags
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3RouteHost
import ir.vil3ntec.tohid.ui.v3.nav.rememberV3Nav
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts

/**
 *  پوستهٔ حالتِ «خرید»: همان مسیریاب و همان کیت، با خانه، نوار و تب‌های خودِ
 *  این حالت (ورودی‌های `Place.NAV`ِ ماژول‌های حالتِ خرید). صفحه‌ها را گروهِ خرید
 *  می‌سازد؛ پیوندِ QRِ دکان (`ModeState.pendingLink`) همین‌جا باز می‌شود.
 */
@Composable
fun ShopperRoot(store: ShopStore, theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  val home = AppModes.home(AppMode.SHOPPER)
  val nav = rememberV3Nav(V3Modules.table, home)
  val data by store.data.collectAsState()
  val snackbar = remember { SnackbarHostState() }
  val toasts = rememberV3Toasts(snackbar)
  val cart = remember { CartStore(context) }
  LaunchedEffect(Unit) { ModuleFlags.load(context) }
  val access = Access(granted = emptySet(), flags = ModuleFlags.on.value, mode = AppMode.SHOPPER)
  val tabs = V3Modules.entries(Place.NAV, access).map { V3Tab(it.route, it.label(), it.icon) }
  nav.isTab = { p -> tabs.any { it.route == p } }

  val link = ModeState.pendingLink.value
  LaunchedEffect(link) {
    if (link != null && link.mode == AppMode.SHOPPER) {
      ModeState.pendingLink.value = null
      nav.openDeep(link.path)
    }
  }
  BackHandler(enabled = nav.canBack) { nav.back() }

  val current = RoutePaths.normalize(nav.current.substringBefore('?'))
  val tabRoot = tabs.any { it.route == current }
  val shell = ShellContext(
    store = store, data = data, cart = cart, snackbar = snackbar, toasts = toasts,
    theme = theme, onTheme = onTheme, bottomReserve = if (tabRoot) TAB_RESERVE else 0.dp,
    wide = false, access = access, navAllowed = emptySet(),
  )
  Box(Modifier.fillMaxSize().background(c.bg)) {
    V3RouteHost(nav, shell, Modifier.fillMaxSize())
    if (tabRoot) V3TabBar(tabs, current, onPick = { nav.open(it) }, modifier = Modifier.align(Alignment.BottomCenter))
    V3SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter))
  }
}

private val TAB_RESERVE = 84.dp
