package ir.vil3ntec.tohid.ui.v3.nav

import ir.vil3ntec.tohid.core.nav.RoutePaths
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavItemDef
import ir.vil3ntec.tohid.ui.v3.account.AccountModule
import ir.vil3ntec.tohid.ui.v3.analytics.AnalyticsModule
import ir.vil3ntec.tohid.ui.v3.example.ExampleModule
import ir.vil3ntec.tohid.ui.v3.money.MoneyModule
import ir.vil3ntec.tohid.ui.v3.onboarding.OnboardingModule
import ir.vil3ntec.tohid.ui.v3.online.CourierModule
import ir.vil3ntec.tohid.ui.v3.online.OnlineModule
import ir.vil3ntec.tohid.ui.v3.products.ProductsModule
import ir.vil3ntec.tohid.ui.v3.sales.SalesModule
import ir.vil3ntec.tohid.ui.v3.settings.SettingsModule
import ir.vil3ntec.tohid.ui.v3.shell.ShellModule
import ir.vil3ntec.tohid.ui.v3.shopper.ShopperModule
import ir.vil3ntec.tohid.ui.v3.stock.StockModule

/**
 *  فهرستِ یکتای بخش‌های برنامه — **تنها جایی** که بخشِ تازه اضافه می‌شود
 *  (`docs/MODULES-fa.md`). پوسته (مسیرها، پیوندها)، «بیشتر»، تنظیمات، فهرستِ
 *  نوارِ پایین و صفحهٔ دکان در حالتِ خرید همه از همین می‌خوانند.
 */
object V3Modules {
  val ALL: List<AppModule> by lazy {
    listOf(
      FoundationModule,
      ScanModule,
      PromosModule,
      ShellModule,
      OnboardingModule,
      SalesModule,
      ProductsModule,
      StockModule,
      MoneyModule,
      AnalyticsModule,
      AccountModule,
      OnlineModule,
      SettingsModule,
      CourierModule,
      ShopperModule,
      ExampleModule,
    )
  }

  val table: RouteTable by lazy { RouteTable(ALL) }

  init {
    //  ویرایشگرِ نوار موردهای ماژول‌های تازه را خودش پیدا می‌کند
    NavCatalog.extras = {
      entries(Place.NAV, Access.all(flags = ModuleFlags.on.value)).map { NavItemDef(it.id, it.label(), it.route) }
    }
  }

  /**
   *  رنگِ همراهِ ثابتِ هر بخش (THEME-V2) — یک جا، تا میان‌برهای خانه، «بیشتر» و هر فهرستِ دیگری
   *  همان رنگ را نشان دهند: فروش آبی، دریافت نعنایی، ورودِ کالا کهربایی، تاریخچه بنفش، مشتری‌ها مرجانی،
   *  مصارف صورتی، گزارش آسمانی. بخشی که این‌جا نیست رنگِ ثبت‌شدهٔ خودش (`ModuleEntry.hue`) را دارد.
   */
  val HUE_BY_ID: Map<String, ir.vil3ntec.tohid.ui.v3.V3Hue> = mapOf(
    "sale" to ir.vil3ntec.tohid.ui.v3.V3Hue.BRAND,
    "scan" to ir.vil3ntec.tohid.ui.v3.V3Hue.BRAND,
    "receipts" to ir.vil3ntec.tohid.ui.v3.V3Hue.MINT,
    "warehouse" to ir.vil3ntec.tohid.ui.v3.V3Hue.AMBER,
    "stock-entry" to ir.vil3ntec.tohid.ui.v3.V3Hue.AMBER,
    "sales" to ir.vil3ntec.tohid.ui.v3.V3Hue.VIOLET,
    "history" to ir.vil3ntec.tohid.ui.v3.V3Hue.VIOLET,
    "debtors" to ir.vil3ntec.tohid.ui.v3.V3Hue.CORAL,
    "customers" to ir.vil3ntec.tohid.ui.v3.V3Hue.CORAL,
    "expenses" to ir.vil3ntec.tohid.ui.v3.V3Hue.PINK,
    "reports" to ir.vil3ntec.tohid.ui.v3.V3Hue.SKY,
    "purchasing" to ir.vil3ntec.tohid.ui.v3.V3Hue.LIME,
  )

  fun hueOf(e: ModuleEntry): ir.vil3ntec.tohid.ui.v3.V3Hue? = HUE_BY_ID[e.id] ?: e.hue

  /** رنگِ همراهِ یک شناسه (میان‌برهای خانه که ورودیِ ماژول نیستند هم از همین می‌خوانند) */
  fun hueOf(id: String): ir.vil3ntec.tohid.ui.v3.V3Hue? =
    HUE_BY_ID[id] ?: ALL.asSequence().flatMap { it.entries.asSequence() }.firstOrNull { it.id == id }?.hue

  /** آیکنِ موردِ نوارِ یک ماژولِ تازه (ساخته‌شده‌ها آیکنِ خودشان را در `PhoneNav` دارند) */
  fun navIcon(id: String, selected: Boolean = false): androidx.compose.ui.graphics.vector.ImageVector? =
    ALL.asSequence().flatMap { it.entries.asSequence() }.firstOrNull { it.id == id && Place.NAV in it.places }
      ?.let { e -> if (selected) e.iconSelected ?: e.icon else e.icon }

  private fun visible(m: AppModule, access: Access) =
    access.mode in m.modes && access.allows(m.needs, m.flag)

  /** ورودی‌های یک جا (نوار، «بیشتر»، خانه) که این کاربر می‌بیند، به ترتیب */
  fun entries(place: Place, access: Access): List<ModuleEntry> =
    ALL.filter { visible(it, access) }
      .flatMap { m -> m.entries.filter { place in it.places && access.allows(it.needs, null) } }
      .sortedBy { it.order }

  /** گروه‌های «بیشتر» به ترتیبِ `MoreGroup`؛ گروهِ خالی نمی‌آید */
  fun moreGroups(access: Access): List<Pair<MoreGroup, List<ModuleEntry>>> {
    val all = entries(Place.MORE, access)
    return MoreGroup.entries.mapNotNull { g -> all.filter { it.group == g }.takeIf { it.isNotEmpty() }?.let { g to it } }
  }

  /** صفحه‌هایی که ماژول‌ها در تنظیمات گذاشته‌اند */
  fun settingsEntries(access: Access): List<SettingsEntry> =
    ALL.filter { visible(it, access) }.flatMap { m -> m.settings.filter { access.allows(it.needs, null) } }

  /** بخش‌هایی که ماژول‌ها روی صفحهٔ دکان در حالتِ خرید گذاشته‌اند */
  fun shopperSections(access: Access): List<ShopperSection> =
    ALL.filter { access.allows(it.needs, it.flag) }.flatMap { it.shopper }.sortedBy { it.order }

  /** شناسهٔ قدیمیِ یک مسیر (منوی کناری و سربرگِ کامپیوتر هنوز با آن کار می‌کنند) */
  /**
   *  شناسهٔ منوی کناریِ کامپیوتر برای هر مسیر (AUDIT-NAV N2): نامِ قدیمیِ **ریشهٔ زنجیره**،
   *  نه خودِ مسیر — `products/42` ⇐ `products`، `stock/…` ⇐ `warehouse`، `sale/quick` ⇐ `sale`.
   */
  fun railId(path: String): String {
    val root = table.chain(path).firstOrNull() ?: path
    val head = RoutePaths.normalize(root.substringBefore('?'))
    return legacyId(if ('/' in head) head.substringBefore('/') else head)
  }

  fun legacyId(path: String): String {
    val r = table.resolve(path) ?: return path.substringBefore('?')
    return r.route.aliases.firstOrNull() ?: r.route.pattern.substringBefore('/')
  }
}

/**
 *  پرچم‌های ماژول روی همین دستگاه — هر بخشِ تازه پشتِ پرچمِ خودش و پیش‌فرض
 *  **خاموش** (`AppModule.flag`). روشن/خاموش فقط برای توسعه و آزمایش؛ پرچمِ سرور
 *  (روشن شدن برای یک دکان) همان `Need`هاست.
 */
object ModuleFlags {
  private const val PREFS = "tohid-modules"
  private const val KEY = "on"

  val on = mutableStateOf<Set<String>>(emptySet())

  fun load(context: Context) {
    on.value = runCatching {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()).orEmpty().toSet()
    }.getOrDefault(emptySet())
  }

  fun set(context: Context, flag: String, enabled: Boolean) {
    val next = if (enabled) on.value + flag else on.value - flag
    on.value = next
    runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY, next).apply() }
  }
}
