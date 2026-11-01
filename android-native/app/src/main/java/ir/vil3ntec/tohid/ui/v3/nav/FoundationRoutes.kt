package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Menu
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrNav
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.screens.MoreAppPage
import ir.vil3ntec.tohid.ui.screens.MoreScreen
import ir.vil3ntec.tohid.ui.screens.PromosScreen
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.gallery.KitGallery

/** پرچمِ صفحهٔ نمونهٔ کیت (فقط پیش‌نمایش و توسعه) */
const val KIT_GALLERY_FLAG = "dev.kit"

/**
 *  مسیرهای خودِ پایه (foundation): «بیشتر» (فهرستِ همهٔ بخش‌ها از روی ماژول‌ها)،
 *  «نسخه و به‌روزرسانی» برای همه، و صفحهٔ نمونهٔ کیت پشتِ پرچم.
 */
val FoundationModule = AppModule(
  id = "foundation",
  title = { Str.more },
  icon = Icons.Outlined.Menu,
  modes = setOf(AppMode.SHOP),
  entries = listOf(
    ModuleEntry("more", { Str.more }, "more", Icons.Outlined.Menu, setOf(Place.NAV), order = 50),
  ),
  routes = listOf(
    V3Route("more", RouteKind.TAB, { Str.more }, chrome = Chrome.OWN) { MoreScreen(store, data) { nav.open(it) } },
    V3Route("app/updates", RouteKind.PAGE, { StrNav.appUpdate }, aliases = listOf("app"), chrome = Chrome.OWN) {
      MoreAppPage(data) { back() }
    },
    V3Route("dev/kit", RouteKind.PAGE, { "V3 kit" }, flag = KIT_GALLERY_FLAG, chrome = Chrome.OWN) { KitGallery(onBack = { back() }) },
  ),
)

/**
 *  تخفیف‌های دکان (PRM، از main): صاحب و مدیر (`settings.write` = `Need.MANAGE`؛ سرور هم همین
 *  را می‌سنجد). جای‌نگهدارِ پایه تا گروهِ فروش آن را بگیرد؛ امروز همان `PromosScreen`.
 */
val PromosModule = AppModule(
  id = "promos",
  title = { StrKit.promos },
  icon = Icons.Outlined.LocalOffer,
  modes = setOf(AppMode.SHOP),
  entries = listOf(
    ModuleEntry("promos", { StrKit.promos }, "promos", Icons.Outlined.LocalOffer, setOf(Place.MORE), MoreGroup.SALES, V3Hue.TEAL, needs = setOf(Need.MANAGE), order = 30),
  ),
  routes = listOf(
    V3Route("promos", RouteKind.PAGE, { StrKit.promos }, parent = "more", needs = setOf(Need.MANAGE)) { PromosScreen(data, snackbar) },
  ),
)
