package ir.vil3ntec.tohid.ui.v3.account

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WorkspacePremium
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.ui.screens.MySubscriptionScreen
import ir.vil3ntec.tohid.ui.screens.ProfileScreen
import ir.vil3ntec.tohid.ui.screens.SupportScreen
import ir.vil3ntec.tohid.ui.screens.TeamScreen
import ir.vil3ntec.tohid.ui.screens.VipScreen
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** مسیرهای گروهِ حساب (G7) */
object AccountPaths {
  const val PROFILE = "profile"
  const val TEAM = "team"
  const val SUBSCRIPTION = "subscription"
  const val PLANS = "subscription/plans"
  const val SUPPORT = "support"
}

/**
 *  گروهِ حساب و کارمندان (G7، `team-account.md`): «حساب من» (r4)، کارمندان، اشتراک،
 *  پلن‌ها، پشتیبانی. نشانِ VIP دیگر در سربرگ نیست: حالِ اشتراک در «حساب من» است و
 *  قرصِ تمدید بالای نوار.
 */
val AccountModule = AppModule(
  id = "account",
  title = { Str.myAccount },
  icon = Icons.Outlined.AccountCircle,
  entries = listOf(
    ModuleEntry("account", { Str.myAccount }, AccountPaths.PROFILE, Icons.Outlined.AccountCircle, setOf(Place.NAV), order = 60),
    ModuleEntry("team", { Str.staff }, AccountPaths.TEAM, Icons.Outlined.Badge, setOf(Place.NAV, Place.MORE, Place.HOME), MoreGroup.SHOP, V3Hue.BLUE, needs = setOf(Need.MANAGE), order = 20),
    ModuleEntry("myplan", { Str.mySubscription }, AccountPaths.SUBSCRIPTION, Icons.Outlined.WorkspacePremium, setOf(Place.MORE), MoreGroup.ACCOUNT, V3Hue.ORANGE, order = 10),
    ModuleEntry("support", { Str.support }, AccountPaths.SUPPORT, Icons.Outlined.SupportAgent, setOf(Place.MORE), MoreGroup.ACCOUNT, V3Hue.TEAL, order = 20),
  ),
  routes = listOf(
    V3Route(AccountPaths.PROFILE, RouteKind.TAB, { Str.myAccount }, chrome = Chrome.OWN) {
      ProfileScreen(
        store = store,
        snackbar = snackbar,
        onBack = { back() },
        onSignIn = { nav.open("auth") },
        onSubscription = { nav.open(AccountPaths.PLANS) },
      )
    },
    V3Route("profile/edit", RouteKind.PAGE, { Str.myAccount }, parent = AccountPaths.PROFILE) { LandOn(AccountPaths.PROFILE) },
    V3Route("profile/signout", RouteKind.SHEET, { Str.myAccount }, parent = AccountPaths.PROFILE) { LandOn(AccountPaths.PROFILE) },
    V3Route(AccountPaths.TEAM, RouteKind.PAGE, { Str.staff }, needs = setOf(Need.MANAGE)) { TeamScreen(snackbar) },
    V3Route("team/codes/new", RouteKind.SHEET, { Str.staff }, parent = AccountPaths.TEAM) { LandOn(AccountPaths.TEAM) },
    V3Route("team/codes/reveal", RouteKind.SHEET, { Str.staff }, parent = AccountPaths.TEAM) { LandOn(AccountPaths.TEAM) },
    V3Route("team/members/{id}", RouteKind.PAGE, { Str.staff }, parent = AccountPaths.TEAM) { LandOn(AccountPaths.TEAM) },
    V3Route("team/members/{id}/remove", RouteKind.SHEET, { Str.staff }, parent = "team/members/{id}") { LandOn(AccountPaths.TEAM) },
    V3Route("team/devices/{id}/block", RouteKind.SHEET, { Str.staff }, parent = AccountPaths.TEAM) { LandOn(AccountPaths.TEAM) },
    V3Route(AccountPaths.SUBSCRIPTION, RouteKind.PAGE, { Str.mySubscription }, aliases = listOf("myplan")) { MySubscriptionScreen() },
    V3Route(AccountPaths.PLANS, RouteKind.PAGE, { Str.plans }, parent = AccountPaths.SUBSCRIPTION, aliases = listOf("vip")) { VipScreen { back() } },
    V3Route("subscription/redeem", RouteKind.SHEET, { Str.plans }, parent = AccountPaths.PLANS) { LandOn(AccountPaths.PLANS) },
    V3Route(AccountPaths.SUPPORT, RouteKind.PAGE, { Str.support }) { SupportScreen { back() } },
  ),
)
