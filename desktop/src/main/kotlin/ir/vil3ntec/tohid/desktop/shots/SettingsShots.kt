package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.screens.isDark
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.nav.V3RouteHost
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import ir.vil3ntec.tohid.ui.v3.settings.sections.SettingsShotState

/**
 *  عکس‌های گروهِ G9 تنظیمات: صفحهٔ اول (صاحبِ دکان و کارمندِ بی‌دسترسی)، صفحهٔ هر دسته، «قابلیت‌ها»، جست‌وجو،
 *  هر صفحهٔ بخش، برگهٔ رمز، همگام‌سازیِ خطادار، پشتیبان و تأییدِ پاک‌سازی، و
 *  دستگاه‌های جانبی روی چاپگرها و گوشی ⇄ کامپیوتر. هر حالت روشن/تیره × فارسی/انگلیسی.
 */
object SettingsShots : GroupShots {
  override val group = "settings"
  override val shots = listOf(
    Shot("root", route = "settings"),
    Shot("root-staff", content = { StaffPreview("settings", it) }),
    //  تنظیماتِ نسخهٔ دوم (۱۴۰۵/۰۸/۱۱): صفحهٔ هر دسته، «قابلیت‌ها» و نتیجه‌های جست‌وجو (یکی با آزمایشی‌ها)
    Shot("shop", route = "settings/shop"),
    Shot("sales", route = "settings/sales"),
    Shot("goods", route = "settings/goods"),
    Shot("data", route = "settings/data"),
    Shot("look", route = "settings/look"),
    Shot("features", route = "settings/features"),
    Shot("search", route = "settings", prepare = { SettingsShotState.query = "رسید" }, cleanup = { SettingsShotState.query = "" }),
    Shot("search-experimental", route = "settings", prepare = { SettingsShotState.query = "آزمایشی" }, cleanup = { SettingsShotState.query = "" }),
    Shot("about", route = "settings/about"),
    Shot("alerts", route = "settings/shop/alerts"),
    Shot("appearance", route = "settings/look/appearance"),
    Shot("navbar", route = "settings/look/navbar"),
    Shot("language", route = "settings/look/language"),
    Shot("digits", route = "settings/look/digits"),
    Shot("app-lock", route = "settings/shop/app-lock", prepare = { SettingsShotState.lockOn = true }, cleanup = { SettingsShotState.lockOn = null }),
    Shot("app-lock-pin", route = "settings/shop/app-lock/pin"),
    Shot("staff-code", route = "settings/shop/staff-code"),
    Shot("sessions", route = "settings/shop/sessions"),
    Shot("password", route = "settings/shop/password"),
    Shot("expiry", route = "settings/sales/expiry"),
    //  بخش‌های آمده از تنظیماتِ رابطِ امروز (SettingsPortTest)
    Shot("gift-cards", route = "settings/sales/gift-cards"),
    Shot("checkout-rules", route = "settings/sales/checkout-rules"),
    Shot("held-invoices", route = "settings/sales/held-invoices"),
    Shot("returns", route = "settings/sales/returns"),
    Shot("payments", route = "settings/sales/payments"),
    Shot("credit-limit", route = "settings/sales/credit-limit"),
    Shot("customer-reports", route = "settings/data/customer-reports"),
    Shot("till-receive", route = "settings/sales/till-receive"),
    Shot("stock-insight", route = "settings/goods/stock-insight"),
    Shot("product-tools", route = "settings/goods/product-tools"),
    Shot("fraud-trail", route = "settings/shop/fraud-trail"),
    Shot("encryption", route = "settings/shop/encryption"),
    Shot("camera", route = "settings/devices/camera"),
    Shot(
      "sync-error", route = "settings/data/sync",
      prepare = { SettingsShotState.signedIn = true; SettingsShotState.syncError = "Unable to resolve host \"api.vill3n.top\"" },
      cleanup = { SettingsShotState.signedIn = null; SettingsShotState.syncError = null },
    ),
    Shot("sync-staff", content = { StaffPreview("settings/data/sync", it) }),
    Shot("backup", route = "settings/data/backup"),
    Shot("backup-wipe", route = "settings/data/backup?shot=wipe", prepare = { SettingsShotState.wipeSheet = true }, cleanup = { SettingsShotState.wipeSheet = false }),
    Shot("devices-printers", route = "devices?tab=printers"),
    Shot("devices-phone", route = "devices?tab=phone"),
  )
}

/** همان مسیر برای کارمندِ بی «مدیریت» — ریشهٔ تنظیمات بسته، وضعیتِ همگام‌سازی باز */
@Composable
private fun StaffPreview(path: String, store: ShopStore) {
  val context = LocalContext.current
  val data by store.data.collectAsState()
  val nav = remember(path) { V3Nav(V3Modules.table, "home", NavStack.of("home")).also { it.openDeep(path) } }
  val snackbar = remember { SnackbarHostState() }
  val toasts = rememberV3Toasts(snackbar)
  val cart = remember { CartStore(context) }
  val shell = ShellContext(
    store = store, data = data, cart = cart, snackbar = snackbar, toasts = toasts,
    theme = if (PhoneV3.colors.isDark) ThemeChoice.DARK else ThemeChoice.LIGHT, onTheme = {},
    bottomReserve = PhoneNav.RESERVE, wide = false,
    access = Access(granted = emptySet()),
    navAllowed = NavCatalog.allowed(canManage = false, ordersLive = false),
  )
  CompositionLocalProvider(LocalV3Toasts provides toasts) { V3RouteHost(nav, shell, Modifier.fillMaxSize()) }
}
