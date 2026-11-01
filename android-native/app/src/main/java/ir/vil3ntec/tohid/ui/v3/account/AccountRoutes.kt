package ir.vil3ntec.tohid.ui.v3.account

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.WorkspacePremium
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.nav.MoreGroup
import ir.vil3ntec.tohid.core.nav.Need
import ir.vil3ntec.tohid.core.nav.Place
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileEditRoute
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileRoute
import ir.vil3ntec.tohid.ui.v3.account.profile.SignOutRoute
import ir.vil3ntec.tohid.ui.v3.account.subscription.PlansRoute
import ir.vil3ntec.tohid.ui.v3.account.subscription.RedeemRoute
import ir.vil3ntec.tohid.ui.v3.account.subscription.SubscriptionRoute
import ir.vil3ntec.tohid.ui.v3.account.support.SupportRoute
import ir.vil3ntec.tohid.ui.v3.account.team.BlockDeviceRoute
import ir.vil3ntec.tohid.ui.v3.account.team.MemberRoute
import ir.vil3ntec.tohid.ui.v3.account.team.NewCodeRoute
import ir.vil3ntec.tohid.ui.v3.account.team.RemoveMemberRoute
import ir.vil3ntec.tohid.ui.v3.account.team.RevealRoute
import ir.vil3ntec.tohid.ui.v3.account.team.TeamRoute
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.ModuleEntry
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** مسیرهای گروهِ حساب (G7) */
object AccountPaths {
  const val PROFILE = "profile"
  const val EDIT = "profile/edit"
  const val SIGN_OUT = "profile/signout"
  const val TEAM = "team"
  const val NEW_CODE = "team/codes/new"
  const val REVEAL = "team/codes/reveal"
  fun member(id: String) = "team/members/" + RoutePaths.encode(id)
  fun removeMember(id: String) = member(id) + "/remove"
  fun blockDevice(id: String) = "team/devices/" + RoutePaths.encode(id) + "/block"
  const val SUBSCRIPTION = "subscription"
  const val PLANS = "subscription/plans"
  const val REDEEM = "subscription/redeem"
  const val SUPPORT = "support"
}

/**
 *  گروهِ حساب و کارمندان (G7، `team-account.md`): «حساب من» (r4 روی کیتِ نسخهٔ ۳،
 *  با سوییچرِ حالت)، ویرایش و خروج، کارمندان/کدها/مجوز/دستگاه‌ها، اشتراک، پلن‌ها
 *  (بی طلا)، کدِ اشتراک و پشتیبانی. همهٔ صفحه‌ها `V3Scaffold` خودشان را دارند.
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
    V3Route(AccountPaths.PROFILE, RouteKind.TAB, { Str.myAccount }, chrome = Chrome.OWN) { ProfileRoute() },
    V3Route(AccountPaths.EDIT, RouteKind.PAGE, { StrAccount.menuEdit }, parent = AccountPaths.PROFILE, chrome = Chrome.OWN) { ProfileEditRoute() },
    V3Route(AccountPaths.SIGN_OUT, RouteKind.SHEET, { StrAccount.menuSignOut }, parent = AccountPaths.PROFILE, chrome = Chrome.OWN) { SignOutRoute() },
    V3Route(AccountPaths.TEAM, RouteKind.PAGE, { Str.staff }, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) { TeamRoute() },
    V3Route(AccountPaths.NEW_CODE, RouteKind.SHEET, { StrAccount.newCodeTitle }, parent = AccountPaths.TEAM, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) { NewCodeRoute() },
    V3Route(AccountPaths.REVEAL, RouteKind.SHEET, { StrAccount.revealTitle }, parent = AccountPaths.TEAM, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) { RevealRoute() },
    V3Route("team/members/{id}", RouteKind.PAGE, { StrAccount.staffMember }, parent = AccountPaths.TEAM, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      MemberRoute(args["id"].orEmpty())
    },
    V3Route("team/members/{id}/remove", RouteKind.SHEET, { StrAccount.remove }, parent = "team/members/{id}", needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      RemoveMemberRoute(args["id"].orEmpty())
    },
    V3Route("team/devices/{id}/block", RouteKind.SHEET, { StrAccount.block }, parent = AccountPaths.TEAM, needs = setOf(Need.MANAGE), chrome = Chrome.OWN) {
      BlockDeviceRoute(args["id"].orEmpty())
    },
    V3Route(AccountPaths.SUBSCRIPTION, RouteKind.PAGE, { Str.mySubscription }, aliases = listOf("myplan"), chrome = Chrome.OWN) { SubscriptionRoute() },
    V3Route(AccountPaths.PLANS, RouteKind.PAGE, { Str.plans }, parent = AccountPaths.SUBSCRIPTION, aliases = listOf("vip"), chrome = Chrome.OWN) { PlansRoute() },
    V3Route(AccountPaths.REDEEM, RouteKind.SHEET, { StrAccount.haveCode }, parent = AccountPaths.PLANS, chrome = Chrome.OWN) { RedeemRoute() },
    V3Route(AccountPaths.SUPPORT, RouteKind.PAGE, { Str.support }, chrome = Chrome.OWN) { SupportRoute() },
  ),
)
