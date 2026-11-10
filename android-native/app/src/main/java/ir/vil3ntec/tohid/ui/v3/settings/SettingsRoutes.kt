package ir.vil3ntec.tohid.ui.v3.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrLateApology
import ir.vil3ntec.tohid.core.i18n.StrPromoHeld
import ir.vil3ntec.tohid.core.i18n.StrSettingsHelp
import ir.vil3ntec.tohid.core.i18n.StrSettingsHome
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.screens.SettingsSearchIndex
import ir.vil3ntec.tohid.ui.screens.lateApologyApi
import ir.vil3ntec.tohid.ui.screens.lateApologyRow
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.SettingsEntry
import ir.vil3ntec.tohid.ui.v3.nav.V3Route
import ir.vil3ntec.tohid.ui.v3.settings.devices.DevicesScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.AboutScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.LateApologyScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.NavBarScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.PinSheet
import ir.vil3ntec.tohid.ui.v3.settings.sections.PromoHeldScreen
import ir.vil3ntec.tohid.ui.v3.settings.sections.promoHeldRow
import ir.vil3ntec.tohid.ui.v3.settings.sections.SyncScreen

/**
 *  نشانی‌های تنظیمات (G9؛ نسخهٔ دوم ۱۴۰۵/۰۸/۱۱): هر دسته و هر بخش مسیرِ خودش را دارد تا «بیشتر ⇐ تنظیمات ⇐
 *  دکان و امنیت» مستقیم سرِ همان صفحه برود و «برگشت» به همان جایی که از آن آمده. نام‌های نشانی در `SettingsLayout`
 *  ثابت‌اند؛ نشانیِ گروه‌های پیشین (`settings/general/…`، `settings/security/…`) هم هنوز همان صفحه را باز می‌کند.
 */
object SettingsPaths {
  const val ROOT = "settings"
  const val NAVBAR = "settings/look/navbar"
  /** «نسخه و به‌روزرسانی» — برای همه (کارمند هم) */
  const val ABOUT = "settings/about"
  const val APP_LOCK = "settings/shop/app-lock"
  const val PIN = "settings/shop/app-lock/pin"
  const val SYNC = "settings/data/sync"
  /** «قابلیت‌ها»: کارت‌های بخش‌های اختیاریِ کار (پیوندِ کوچکِ ته صفحهٔ اول و جست‌وجو) */
  const val FEATURES = SettingsSearchIndex.FEATURES_KEY
  const val DEVICES = "devices"
  const val DEVICES_PRINTERS = "devices?tab=printers"
  /** پوزش و کوپنِ خودکارِ تأخیر (NTF-10) — صفحهٔ پشتِ پرچمِ سرور، بیرون از `SettingsLayout` (ردیفش از `SettingsModule.settings`) */
  const val LATE_APOLOGY = "settings/late-apology"
  /** PRM-02: آزادِ دستیِ کدِ تخفیفِ نگه‌داشته — پشتِ پرچمِ سرور و اجازهٔ لغوِ فاکتور، ردیفش از `SettingsModule.settings` */
  const val PROMO_HELD = "settings/promo-held"
  /** ردیفِ «نسخه و به‌روزرسانی»ِ «بیشتر» */
  const val UPDATES = ABOUT

  /** نشانیِ کهنهٔ پیش از نسخهٔ دوم — همان صفحه را باز می‌کنند */
  const val OLD_NAVBAR = "settings/general/navbar"
  const val OLD_PIN = "settings/security/app-lock/pin"

  val GROUP_SLUGS: Map<String, String> get() = SettingsLayout.GROUP_SLUGS
  val SECTION_SLUGS: Map<String, String> get() = SettingsLayout.SECTION_SLUGS

  fun group(title: String): String = ROOT + "/" + (GROUP_SLUGS[title] ?: RoutePaths.encode(title))

  /** ردیفِ یک دسته در صفحهٔ اول: «دستگاه‌ها» یک‌راست همهٔ دستگاه‌ها، بقیه صفحهٔ دستهٔ خودشان */
  fun home(title: String): String = SettingsLayout.GROUP_LINKS[title] ?: group(title)

  /** مسیرِ یک بخش؛ پیوندها (`LINKS`) به صفحهٔ خودشان می‌روند */
  fun section(title: String): String {
    SettingsLayout.LINKS[title]?.let { return it }
    if (title == SettingsLayout.ABOUT_APP) return ABOUT
    return group(SettingsLayout.groupOf(title) ?: "") + "/" + (SECTION_SLUGS[title] ?: RoutePaths.encode(title))
  }

  /** نتیجهٔ جست‌وجوی تنظیمات ⇐ صفحه‌اش */
  fun of(doc: SettingsSearchIndex.Doc): String = when (doc.kind) {
    SettingsSearchIndex.Kind.CATEGORY -> home(doc.key)
    SettingsSearchIndex.Kind.SECTION -> section(doc.key)
    SettingsSearchIndex.Kind.FEATURES -> FEATURES
    SettingsSearchIndex.Kind.EXTRA -> doc.key
  }

  fun devices(tab: String? = null): String = RoutePaths.withQuery(DEVICES, "tab" to tab)

  /** دستهٔ یک نامِ نشانی؛ نامِ گروهِ پیشین (`general`، `security`، `about`) هم به دستهٔ امروزش می‌رسد */
  fun groupOfSlug(slug: String): String? =
    GROUP_SLUGS.entries.firstOrNull { it.value == slug }?.key ?: SettingsLayout.GROUP_SLUG_ALIASES[slug]

  /**
   *  بخشِ یک نامِ نشانی. نام‌های بخش یکتا‌اند (`SettingsLayoutTest`)، پس پیوندِ کهنه‌ای که بخش را زیرِ گروهِ پیشینش
   *  دارد (`settings/sales/labels` ⇐ امروز `settings/goods/labels`) هم همان صفحه را باز می‌کند.
   */
  @Suppress("UNUSED_PARAMETER")
  fun sectionOfSlug(group: String, slug: String): String? = SECTION_SLUGS.entries.firstOrNull { it.value == slug }?.key
}

/** یک بخش روی گوشی صفحهٔ خودش است؛ روی صفحهٔ پهن (کامپیوتر، تبلتِ افقی) همان صفحه در ستونِ پایانِ دوستونه */
@Composable
private fun RouteScope.SettingsSection(key: String) {
  if (desk) DeskSettingsScreen(section = key)
  //  «این چه می‌کند؟» بالای صفحه (`SettingsPageFrame` از همین کلید می‌خواند)
  else CompositionLocalProvider(LocalSettingsKey provides key) { SettingsPages.of(key)?.content?.invoke(this, backOrNull) }
}

/**
 *  گروهِ تنظیمات (G9، `settings.md`؛ نسخهٔ دوم ۱۴۰۵/۰۸/۱۱): صفحهٔ اول (جست‌وجو + شش دسته)، صفحهٔ هر دسته،
 *  «قابلیت‌ها»، صفحهٔ هر بخش، برگهٔ رمز، «نسخه و به‌روزرسانی» و «دستگاه‌های جانبی». دسترسی: کلِ بخش
 *  `ShopRole.canOpenSettings` (`Need.MANAGE`)؛ بی‌دسترسی ⇐ صفحهٔ «دسترسی ندارید»
 *  با برگشت (پوسته). نوارِ پایین، «درباره»، وضعیتِ همگام‌سازی و دستگاه‌ها برای همه.
 */
val SettingsModule = AppModule(
  id = "settings",
  title = { Str.settings },
  icon = Icons.Outlined.Settings,
  //  NTF-10: ردیفِ «پوزش و کوپنِ خودکارِ تأخیر» زیرِ «فروش» فقط با پرچمِ روشنِ سرور (`lateApologyRow`، همان ردیفِ رابطِ امروز)
  settings = listOf(
    SettingsEntry({ StrLateApology.title }, SettingsPaths.LATE_APOLOGY, Icons.Outlined.Schedule, SettingsLayout.SALES, setOf(Need.MANAGE), shown = { lateApologyRow(lateApologyApi()) }, subtitle = { StrSettingsHelp.lateApologyLine }, part = "online"),
    //  PRM-02: «کدهای تخفیفِ نگه‌داشته» فقط با شمارشِ روشنِ سرور (`shop.promo_uses`) و اجازهٔ لغوِ فاکتور (`promoHeldRow`)
    SettingsEntry({ StrPromoHeld.title }, SettingsPaths.PROMO_HELD, Icons.Outlined.LocalOffer, SettingsLayout.SALES, setOf(Need.MANAGE), shown = { promoHeldRow() }, subtitle = { StrPromoHeld.line }, part = "receipt"),
  ),
  entries = listOf(
    ModuleEntry("settings", { Str.settings }, SettingsPaths.ROOT, Icons.Outlined.Settings, setOf(Place.NAV), needs = setOf(Need.MANAGE), order = 90),
    ModuleEntry("devices", { Str.devices }, SettingsPaths.DEVICES, Icons.Outlined.Devices, setOf(Place.MORE), MoreGroup.SHOP, V3Hue.TEAL, order = 40),
  ),
  routes = listOf(
    V3Route(SettingsPaths.ROOT, RouteKind.PAGE, { Str.settings }, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      if (desk) DeskSettingsScreen() else SettingsIndexScreen(onBack = backOrNull)
    },
    V3Route(SettingsPaths.NAVBAR, RouteKind.PAGE, { SettingsLayout.labelOf(SettingsLayout.NAV_BAR) }, parent = SettingsPaths.group(SettingsLayout.LOOK), aliases = listOf("nav-editor", SettingsPaths.OLD_NAVBAR), chrome = Chrome.OWN) {
      NavBarScreen(allowed = shell.navAllowed, onBack = backOrNull)
    },
    V3Route(SettingsPaths.ABOUT, RouteKind.PAGE, { SettingsLayout.labelOf(SettingsLayout.ABOUT_APP) }, chrome = Chrome.OWN) {
      if (desk && access.allows(setOf(Need.MANAGE))) DeskSettingsScreen(section = SettingsLayout.ABOUT_APP)
      else CompositionLocalProvider(LocalSettingsKey provides SettingsLayout.ABOUT_APP) { AboutScreen(data, onBack = backOrNull) }
    },
    //  وضعیتِ همگام‌سازی فقط‌خواندنی و مالِ همه است (کارمند هم) — هشدارِ «همگام‌سازی خطا دارد» این‌جا را باز می‌کند
    V3Route(SettingsPaths.SYNC, RouteKind.PAGE, { SettingsLayout.labelOf("همگام‌سازی") }, chrome = Chrome.OWN) {
      if (desk && access.allows(setOf(Need.MANAGE))) DeskSettingsScreen(section = "همگام‌سازی")
      else CompositionLocalProvider(LocalSettingsKey provides "همگام‌سازی") { SyncScreen(onBack = backOrNull) }
    },
    V3Route("settings/{group}", RouteKind.PAGE, { Str.settings }, parent = SettingsPaths.ROOT, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      val group = SettingsPaths.groupOfSlug(args["group"].orEmpty())
      when {
        group == null -> LandOn(SettingsPaths.ROOT)
        desk -> DeskSettingsScreen(group = group)
        //  «دستگاه‌ها» صفحهٔ تنظیماتِ جدا ندارد: همان همهٔ دستگاه‌ها
        group in SettingsLayout.GROUP_LINKS -> LandOn(SettingsLayout.GROUP_LINKS.getValue(group))
        else -> SettingsCategoryScreen(group, onBack = backOrNull)
      }
    },
    V3Route("settings/{group}/{section}", RouteKind.PAGE, { Str.settings }, parent = "settings/{group}", needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      val group = SettingsPaths.groupOfSlug(args["group"].orEmpty())
      val section = group?.let { SettingsPaths.sectionOfSlug(it, args["section"].orEmpty()) }
      if (section == null) LandOn(SettingsPaths.ROOT) else SettingsSection(section)
    },
    V3Route(SettingsPaths.LATE_APOLOGY, RouteKind.PAGE, { StrLateApology.title }, parent = SettingsPaths.group(SettingsLayout.SALES), needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      CompositionLocalProvider(LocalSettingsKey provides SettingsPaths.LATE_APOLOGY) { LateApologyScreen(onBack = backOrNull) }
    },
    V3Route(SettingsPaths.PROMO_HELD, RouteKind.PAGE, { StrPromoHeld.title }, parent = SettingsPaths.group(SettingsLayout.SALES), needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      PromoHeldScreen(onBack = backOrNull)
    },
    V3Route(SettingsPaths.FEATURES, RouteKind.PAGE, { StrSettingsHome.features }, parent = SettingsPaths.ROOT, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      if (desk) DeskSettingsScreen(group = SettingsPaths.FEATURES)
      else FeaturesScreen(onBack = backOrNull, onOptions = { nav.open(SettingsPaths.section(it)) })
    },
    V3Route(SettingsPaths.PIN, RouteKind.SHEET, { Str.appLock }, parent = SettingsPaths.APP_LOCK, aliases = listOf(SettingsPaths.OLD_PIN), needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      PinSheet(onDone = { back() })
    },
    V3Route(SettingsPaths.DEVICES, RouteKind.PAGE, { Str.devices }, chrome = Chrome.OWN) {
      DevicesScreen(data, tab = args.query("tab"), onBack = backOrNull)
    },
  ),
)
