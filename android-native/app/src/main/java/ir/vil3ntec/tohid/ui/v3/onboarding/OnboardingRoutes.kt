package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Login
import androidx.compose.runtime.LaunchedEffect
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.nav.RoutePaths
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.ui.v3.nav.AppModule
import ir.vil3ntec.tohid.ui.v3.nav.Chrome
import ir.vil3ntec.tohid.ui.v3.nav.RouteKind
import ir.vil3ntec.tohid.ui.v3.nav.V3Route

/** مسیرهای گروهِ ورود (G1) */
object OnboardingPaths {
  fun auth(mode: String? = null) = RoutePaths.withQuery("auth", "mode" to mode)
  const val CODE = "auth/code"
  const val TERMS = "auth/terms"
  const val SHOP_SETUP = "setup/shop"
  const val LOCK = "lock"
  const val LOCK_SETUP = "lock/setup"
}

/**
 *  گروهِ ورود و دروازه‌ها (G1، `onboarding.md`). دروازه‌ها (`GATE`) هرگز در پشته نیستند:
 *  تمام‌صفحه روی برنامه، و پس از عبور پیوندِ نگه‌داشته باز می‌شود. دروازه‌های خودکارِ سرِ باز شدن
 *  (قفل، ساختِ دکان، پیشنهادِ رمز) را `AppRoot` با همین صفحه‌ها (از راهِ نام‌های پیشین در
 *  `ui/screens`) نشان می‌دهد؛ این مسیرها برای باز کردنِ عمدی‌اند («ورود» در «حساب من»).
 */
val OnboardingModule = AppModule(
  id = "onboarding",
  title = { Str.signIn },
  icon = Icons.Outlined.Login,
  routes = listOf(
    V3Route("auth", RouteKind.GATE, { Str.signIn }, chrome = Chrome.OWN) {
      AuthGate(store, mode = args.query("mode"), onClose = { nav.back() }) { nav.passGate(); shell.afterSignIn() }
    },
    V3Route(OnboardingPaths.TERMS, RouteKind.PAGE, { StrAuth.termsShort }, chrome = Chrome.OWN) {
      TermsPage(accepting = false, onClose = { back() }, onAccept = { back() })
    },
    V3Route(OnboardingPaths.CODE, RouteKind.SHEET, { StrAuth.emailSignIn }, chrome = Chrome.OWN) {
      EmailCodeSheet(onClose = { back() }, onSignedIn = { shell.afterSignIn() })
    },
    V3Route(OnboardingPaths.SHOP_SETUP, RouteKind.GATE, { StrAuth.setupShop }, chrome = Chrome.OWN) {
      ShopSetupGate(store) { nav.passGate() }
    },
    V3Route(OnboardingPaths.LOCK, RouteKind.GATE, { Str.appLock }, chrome = Chrome.OWN) {
      LockGate { nav.passGate() }
    },
    V3Route(OnboardingPaths.LOCK_SETUP, RouteKind.GATE, { Str.appLock }, chrome = Chrome.OWN) {
      LockSetupGate { nav.passGate() }
    },
    //  «تأییدِ دوبارهٔ هویت» را `ReauthHost` خودش برای هر درخواستِ حساس باز می‌کند (`ReauthSheet`)
    V3Route("reauth", RouteKind.SHEET, { StrAuth.reauthTitle }) { LaunchedEffect(args.path) { back() } },
  ),
)
