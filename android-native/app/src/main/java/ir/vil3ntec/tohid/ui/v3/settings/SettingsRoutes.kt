package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.settings.devices.DevicesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.AboutScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.NavBarScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PinSheet
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncScreen

/**
 *  نشانی‌های تنظیمات (G9): هر گروه و هر بخش مسیرِ خودش را دارد تا «بیشتر ⇐
 *  تنظیمات ⇐ امنیت» مستقیم سرِ صفحهٔ «امنیت» برود و «برگشت» به همان جایی که از
 *  آن آمده. نام‌های نشانی در `SettingsLayout` ثابت‌اند.
 */
object SettingsPaths {
  const val ROOT = "settings"
  const val NAVBAR = "settings/general/navbar"
  /** «نسخه و به‌روزرسانی» — برای همه (کارمند هم) */
  const val ABOUT = "settings/about"
  const val APP_LOCK = "settings/security/app-lock"
  const val PIN = "settings/security/app-lock/pin"
  const val SYNC = "settings/data/sync"
  const val DEVICES = "devices"
  const val DEVICES_PRINTERS = "devices?tab=printers"
  /** ردیفِ «نسخه و به‌روزرسانی»ِ «بیشتر» */
  const val UPDATES = ABOUT

  val GROUP_SLUGS: Map<String, String> get() = SettingsLayout.GROUP_SLUGS
  val SECTION_SLUGS: Map<String, String> get() = SettingsLayout.SECTION_SLUGS

  fun group(title: String): String = ROOT + "/" + (GROUP_SLUGS[title] ?: RoutePaths.encode(title))

  /** مسیرِ یک بخش؛ پیوندها (`LINKS`) به صفحهٔ خودشان می‌روند */
  fun section(title: String): String {
    SettingsLayout.LINKS[title]?.let { return it }
    val g = SettingsLayout.groupOf(title)
    return if (g == SettingsLayout.ABOUT) ABOUT else group(g ?: "") + "/" + (SECTION_SLUGS[title] ?: RoutePaths.encode(title))
  }

  fun devices(tab: String? = null): String = RoutePaths.withQuery(DEVICES, "tab" to tab)

  fun groupOfSlug(slug: String): String? = GROUP_SLUGS.entries.firstOrNull { it.value == slug }?.key
  fun sectionOfSlug(group: String, slug: String): String? =
    SECTION_SLUGS.entries.firstOrNull { it.value == slug && SettingsLayout.groupOf(it.key) == group }?.key
}

/** یک بخش روی گوشی صفحهٔ خودش است؛ روی صفحهٔ پهن (کامپیوتر، تبلتِ افقی) همان صفحه در ستونِ پایانِ دوستونه */
@Composable
private fun RouteScope.SettingsSection(key: String) {
  if (wide) DeskSettingsScreen(selected = key) else SettingsPages.of(key)?.content?.invoke(this) { back() }
}

/**
 *  گروهِ تنظیمات (G9، `settings.md`): ریشه، شش صفحهٔ گروه، صفحهٔ هر بخش،
 *  برگهٔ رمز، «نسخه و به‌روزرسانی» و «دستگاه‌های جانبی». دسترسی: کلِ بخش
 *  `ShopRole.canOpenSettings` (`Need.MANAGE`)؛ بی‌دسترسی ⇐ صفحهٔ «دسترسی ندارید»
 *  با برگشت (پوسته). نوارِ پایین، «درباره»، وضعیتِ همگام‌سازی و دستگاه‌ها برای همه.
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
    V3Route(SettingsPaths.ROOT, RouteKind.PAGE, { Str.settings }, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      if (wide) DeskSettingsScreen(selected = null) else SettingsIndexScreen(onBack = { back() })
    },
    V3Route(SettingsPaths.NAVBAR, RouteKind.PAGE, { SettingsLayout.labelOf(SettingsLayout.NAV_BAR) }, parent = SettingsPaths.group(SettingsLayout.GENERAL), aliases = listOf("nav-editor"), chrome = Chrome.OWN) {
      NavBarScreen(allowed = shell.navAllowed, onBack = { back() })
    },
    V3Route(SettingsPaths.ABOUT, RouteKind.PAGE, { SettingsLayout.labelOf(SettingsLayout.ABOUT_APP) }, chrome = Chrome.OWN) {
      if (wide && access.allows(setOf(Need.MANAGE))) DeskSettingsScreen(selected = SettingsLayout.ABOUT_APP)
      else AboutScreen(data, onBack = { back() })
    },
    //  وضعیتِ همگام‌سازی فقط‌خواندنی و مالِ همه است (کارمند هم) — هشدارِ «همگام‌سازی خطا دارد» این‌جا را باز می‌کند
    V3Route(SettingsPaths.SYNC, RouteKind.PAGE, { SettingsLayout.labelOf("همگام‌سازی") }, chrome = Chrome.OWN) {
      if (wide && access.allows(setOf(Need.MANAGE))) DeskSettingsScreen(selected = "همگام‌سازی") else SyncScreen(onBack = { back() })
    },
    V3Route("settings/{group}", RouteKind.PAGE, { Str.settings }, parent = SettingsPaths.ROOT, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      val group = SettingsPaths.groupOfSlug(args["group"].orEmpty())
      when {
        group == null -> LandOn(SettingsPaths.ROOT)
        wide -> DeskSettingsScreen(selected = SettingsLayout.GROUPS.first { it.title == group }.sections.firstOrNull { !SettingsLayout.isLink(it) })
        else -> SettingsGroupScreen(group, onBack = { back() })
      }
    },
    V3Route("settings/{group}/{section}", RouteKind.PAGE, { Str.settings }, parent = "settings/{group}", needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      val group = SettingsPaths.groupOfSlug(args["group"].orEmpty())
      val section = group?.let { SettingsPaths.sectionOfSlug(it, args["section"].orEmpty()) }
      if (section == null) LandOn(SettingsPaths.ROOT) else SettingsSection(section)
    },
    V3Route(SettingsPaths.PIN, RouteKind.SHEET, { Str.appLock }, parent = SettingsPaths.APP_LOCK, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      PinSheet(onDone = { back() })
    },
    V3Route(SettingsPaths.DEVICES, RouteKind.PAGE, { Str.devices }, chrome = Chrome.OWN) {
      DevicesScreen(data, tab = args.query("tab"), onBack = if (nav.canBack) ({ back() }) else null)
    },
  ),
)
