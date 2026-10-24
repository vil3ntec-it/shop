package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Login
import androidx.compose.runtime.LaunchedEffect
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.ui.screens.AppLockScreen
import ir.vil3ntec.tohid.ui.screens.CodeLoginSheet
import ir.vil3ntec.tohid.ui.screens.LockSetupScreen
import ir.vil3ntec.tohid.ui.screens.ShopSetupScreen
import ir.vil3ntec.tohid.ui.screens.WelcomeScreen
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.LandOn
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** مسیرهای گروهِ ورود (G1) */
object OnboardingPaths {
  fun auth(mode: String? = null) = RoutePaths.withQuery("auth", "mode" to mode)
  const val CODE = "auth/code"
  const val SHOP_SETUP = "setup/shop"
  const val LOCK = "lock"
  const val LOCK_SETUP = "lock/setup"
}

/**
 *  گروهِ ورود و دروازه‌ها (G1، `onboarding.md`). دروازه‌ها (`GATE`) هرگز در پشته
 *  نیستند: تمام‌صفحه روی برنامه، و پس از عبور پیوندِ نگه‌داشته باز می‌شود.
 *  دروازه‌های خودکارِ سرِ باز شدن (قفل، ساختِ دکان، پیشنهادِ رمز) همان‌طور در
 *  `AppRoot` می‌مانند؛ این مسیرها برای باز کردنِ عمدی‌اند («ورود» در «حساب من»).
 */
val OnboardingModule = AppModule(
  id = "onboarding",
  title = { Str.signIn },
  icon = Icons.Outlined.Login,
  routes = listOf(
    V3Route("auth", RouteKind.GATE, { Str.signIn }, chrome = Chrome.OWN) {
      WelcomeScreen(store) { nav.passGate(); shell.afterSignIn() }
    },
    V3Route("auth/terms", RouteKind.PAGE, { Str.signIn }) { LandOn("auth") },
    V3Route(OnboardingPaths.CODE, RouteKind.SHEET, { Str.signIn }, chrome = Chrome.OWN) {
      CodeLoginSheet(onClose = { back() }, onSignedIn = { back(); shell.afterSignIn() })
    },
    V3Route(OnboardingPaths.SHOP_SETUP, RouteKind.GATE, { Str.shop }, chrome = Chrome.OWN) {
      ShopSetupScreen(store) { nav.passGate() }
    },
    V3Route(OnboardingPaths.LOCK, RouteKind.GATE, { Str.appLock }, chrome = Chrome.OWN) {
      AppLockScreen { nav.passGate() }
    },
    V3Route(OnboardingPaths.LOCK_SETUP, RouteKind.GATE, { Str.appLock }, chrome = Chrome.OWN) {
      LockSetupScreen { nav.passGate() }
    },
    //  «رمز را دوباره بزنید» را `ReauthHost` خودش برای هر درخواستِ حساس باز می‌کند
    V3Route("reauth", RouteKind.SHEET, { Str.signIn }) { LaunchedEffect(args.path) { back() } },
  ),
)
