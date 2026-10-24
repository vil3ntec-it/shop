package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.DeskSettings
import ir.vil3ntec.tohid.ui.screens.DevicesScreen
import ir.vil3ntec.tohid.ui.screens.LocalSettingsTarget
import ir.vil3ntec.tohid.ui.screens.NavEditorScreen
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.SettingsScreen
import ir.vil3ntec.tohid.ui.screens.SettingsTarget
import ir.vil3ntec.tohid.ui.screens.deskSettingsSections
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/**
 *  مسیرهای تنظیمات (G9): هر گروه و هر بخش مسیرِ خودش را دارد تا «بیشتر ⇐ تنظیمات
 *  ⇐ امنیت» مستقیم سرِ همان صفحه برود و «برگشت» به همان جایی که از آن آمده.
 *  نامِ نشانی‌ها (`security`، `app-lock`، …) ثابت‌اند؛ کلیدِ هر بخش همان عنوانِ
 *  `SettingsSection`ِ امروز است (`SettingsLayout`).
 */
object SettingsPaths {
  const val ROOT = "settings"

  /** گروه ⇐ نامِ نشانی */
  val GROUP_SLUGS: Map<String, String> = mapOf(
    SettingsLayout.GENERAL to "general",
    SettingsLayout.SECURITY to "security",
    SettingsLayout.SALES to "sales",
    SettingsLayout.DEVICES to "devices",
    SettingsLayout.DATA to "data",
    SettingsLayout.ABOUT to "about",
  )

  /** بخش ⇐ نامِ نشانی (زیرِ گروهِ خودش) */
  val SECTION_SLUGS: Map<String, String> = mapOf(
    "هشدارها" to "alerts",
    "ظاهر" to "appearance",
    SettingsLayout.NAV_BAR to "navbar",
    "زبان" to "language",
    "تاریخ و رقم" to "digits",
    "قفل برنامه" to "app-lock",
    "کد شاگرد" to "staff-code",
    "دستگاه‌های وارد شده" to "sessions",
    "تغییر رمز حساب" to "password",
    "کالای منقضی" to "expiry",
    SettingsLayout.PRINTER to "printers",
    SettingsLayout.CAMERA to "camera",
    "همگام‌سازی" to "sync",
    "داده‌ها" to "backup",
    "درباره برنامه" to "app",
  )

  fun group(title: String): String = ROOT + "/" + (GROUP_SLUGS[title] ?: RoutePaths.encode(title))

  /** مسیرِ یک بخش؛ پیوندها (`LINKS`) به صفحهٔ خودشان می‌روند */
  fun section(title: String): String = when (title) {
    SettingsLayout.NAV_BAR -> NAVBAR
    SettingsLayout.PRINTER -> DEVICES_PRINTERS
    else -> {
      val g = SettingsLayout.groupOf(title)
      if (g == SettingsLayout.ABOUT) ABOUT else group(g ?: "") + "/" + (SECTION_SLUGS[title] ?: RoutePaths.encode(title))
    }
  }

  const val NAVBAR = "settings/general/navbar"
  const val ABOUT = "settings/about"
  const val DEVICES = "devices"
  const val DEVICES_PRINTERS = "devices?tab=printers"
  /** نسخه و به‌روزرسانی — برای همه، حتی کسی که تنظیمات را باز نمی‌کند */
  const val UPDATES = "app/updates"

  fun groupOfSlug(slug: String): String? = GROUP_SLUGS.entries.firstOrNull { it.value == slug }?.key
  fun sectionOfSlug(group: String, slug: String): String? =
    SECTION_SLUGS.entries.firstOrNull { it.value == slug && SettingsLayout.groupOf(it.key) == group }?.key
}

/** صفحهٔ تنظیماتِ امروز، روی ریشه/یک گروه/یک بخش (`SettingsTarget`) */
@Composable
private fun RouteScope.Settings(target: SettingsTarget) {
  CompositionLocalProvider(LocalSettingsTarget provides target) {
    if (Platform.DESKTOP) DeskSettings(deskSettingsSections()) {
      SettingsScreen(store, data, snackbar, theme, onTheme, onBack = { back() }, onUpdates = { nav.open(SettingsPaths.UPDATES) })
    } else SettingsScreen(store, data, snackbar, theme, onTheme, onBack = { back() }, onUpdates = { nav.open(SettingsPaths.UPDATES) })
  }
}

/**
 *  گروهِ تنظیمات (G9، `settings.md`). امروز همان `SettingsScreen` است که روی گروه
 *  یا بخشِ خواسته‌شده باز می‌شود؛ G9 صفحه‌های تازه را در همین پوشه می‌سازد.
 */
val SettingsModule = AppModule(
  id = "settings",
  title = { Str.settings },
  icon = Icons.Outlined.Settings,
  entries = listOf(
    ModuleEntry("settings", { Str.settings }, SettingsPaths.ROOT, Icons.Outlined.Settings, setOf(Place.NAV), needs = setOf(Need.MANAGE), order = 90),
    ModuleEntry("devices", { Str.devices }, SettingsPaths.DEVICES, Icons.Outlined.Devices, setOf(Place.MORE), MoreGroup.SHOP, V3Hue.TEAL, order = 40),
  ),
  routes = listOf(
    V3Route(SettingsPaths.ROOT, RouteKind.PAGE, { Str.settings }, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) { Settings(SettingsTarget()) },
    V3Route(SettingsPaths.NAVBAR, RouteKind.PAGE, { Str.settings }, parent = SettingsPaths.group(SettingsLayout.GENERAL), aliases = listOf("nav-editor"), chrome = Chrome.OWN) {
      NavEditorScreen(shell.navAllowed) { back() }
    },
    V3Route(SettingsPaths.ABOUT, RouteKind.PAGE, { Str.aboutApp }, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) { Settings(SettingsTarget(section = "درباره برنامه")) },
    V3Route("settings/{group}", RouteKind.PAGE, { Str.settings }, parent = SettingsPaths.ROOT, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      val group = SettingsPaths.groupOfSlug(args["group"].orEmpty())
      if (group == null) LandOn(SettingsPaths.ROOT) else Settings(SettingsTarget(group = group))
    },
    V3Route("settings/{group}/{section}", RouteKind.PAGE, { Str.settings }, parent = "settings/{group}", needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      val group = SettingsPaths.groupOfSlug(args["group"].orEmpty())
      val section = group?.let { SettingsPaths.sectionOfSlug(it, args["section"].orEmpty()) }
      if (section == null) LandOn(SettingsPaths.ROOT) else Settings(SettingsTarget(section = section))
    },
    V3Route("settings/security/app-lock/pin", RouteKind.SHEET, { Str.appLock }, parent = "settings/security/app-lock", needs = setOf(Need.MANAGE)) {
      LandOn(SettingsPaths.section("قفل برنامه"))
    },
    V3Route(SettingsPaths.DEVICES, RouteKind.PAGE, { Str.devices }) { DevicesScreen(data, snackbar) },
  ),
)
