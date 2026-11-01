package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.onboarding.AuthRules
import ir.vil3ntec.tohid.core.onboarding.AuthStep
import ir.vil3ntec.tohid.core.onboarding.AuthUi
import ir.vil3ntec.tohid.data.LocationFix
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.ui.courier.CourierJoinScreen
import ir.vil3ntec.tohid.ui.v3.V3PageSwitch

/** کارهای دروازهٔ ورود — `AuthController` در برنامه، کارِ تهی در عکس‌ها */
interface AuthActions {
  fun edit(change: (AuthUi) -> AuthUi)
  fun submitForm()
  fun requestReset()
  fun requestOtp()
  fun resend()
  fun submitCode(code: String, newPassword: String)
  fun completeSignup(fix: LocationFix?)
  fun joinWithStaffCode()
  fun google()
  fun skip()
  fun pickSaved(entry: SavedLogins.Entry)
  fun forgetSaved(entry: SavedLogins.Entry)
}

/** آنچه صفحه‌ها جز `AuthUi` می‌بینند */
@Immutable
data class AuthEnv(
  /** این نسخه به سروری بسته است (`AppConfig`) */
  val ready: Boolean,
  /** سرور کد می‌فرستد ⇒ «ورود با کد به‌جای رمز» */
  val otpReady: Boolean,
  val saved: List<SavedLogins.Entry>,
  /** ورود انجام شد ⇒ تیکِ موفقیت */
  val done: Boolean,
  /** ثانیه تا ارسالِ دوبارهٔ کد به این نشانی (از مهلتِ نوشته‌شده روی گوشی) */
  val secondsLeft: (String) -> Int,
)

/**
 *  دروازهٔ ورود (`auth?mode=`) — جای `WelcomeScreen` ِ پیشین. `onDone` همان قراردادِ پیشین است
 *  (ورود، پیوستن یا «ادامه بدون حساب»)؛ `onClose` برگشت از ریشهٔ دروازه وقتی از مسیر باز شده
 *  (برای بارِ اولِ برنامه null: برگشت مالِ سیستم است).
 */
@Composable
fun AuthGate(store: ShopStore, mode: String? = null, onClose: (() -> Unit)? = null, onDone: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val saver = remember { listSaver<AuthUi, Any>(save = { it.saved() }, restore = { AuthUi.restore(it) }) }
  val uiState = rememberSaveable(stateSaver = saver) { mutableStateOf(AuthRules.start(mode)) }
  val finish by rememberUpdatedState(onDone)
  val controller = remember { AuthController(context, store, scope, uiState) { finish() } }
  LaunchedEffect(Unit) { controller.loadServerConfig() }
  val env = AuthEnv(
    ready = controller.ready,
    otpReady = controller.otpReady,
    saved = controller.saved,
    done = controller.done,
    secondsLeft = controller.cooldown::secondsLeft,
  )
  AuthFlow(controller.ui, env, controller, onClose)
  controller.googleLink?.let { ask ->
    GoogleLinkSheet(
      ask = ask,
      busy = controller.ui.busy,
      onDismiss = { controller.googleLink = null },
      onSendCode = { controller.googleSendCode(ask) },
      onProve = { controller.googleProve(ask, it) },
    )
  }
}

/** همهٔ گام‌ها، بی حالتِ خودشان — برنامه و عکس‌ها هر دو همین را می‌کشند */
@Composable
internal fun AuthFlow(ui: AuthUi, env: AuthEnv, act: AuthActions, onClose: (() -> Unit)?) {
  val back = ui.back()
  //  برگشتِ گوشی/«Esc» ِ کامپیوتر: یک پله؛ در ریشه کارِ صدازننده. صفحهٔ پیک برگشتِ خودش را دارد.
  BackHandler(enabled = ui.step != AuthStep.COURIER && (back != null || onClose != null) && !env.done) {
    if (back != null) act.edit { it.back() ?: it } else onClose?.invoke()
  }
  val stepBack: (() -> Unit)? = when {
    back != null -> ({ act.edit { it.back() ?: it } })
    else -> onClose
  }
  //  متنِ کاملِ شرایط درونِ گامِ ۳ جابه‌جا می‌شود (لوکیشنِ پیداشده نگه داشته می‌شود)
  val page = if (ui.step == AuthStep.TERMS_TEXT) AuthStep.TERMS else ui.step
  V3PageSwitch(target = page, depth = { it.depth }) { step ->
    when (step) {
      AuthStep.FORM -> FormStep(ui, env, act, stepBack)
      AuthStep.FORGOT -> ForgotStep(ui, env, act, stepBack)
      AuthStep.JOIN -> JoinStep(ui, env, act, stepBack)
      AuthStep.CODE -> CodeStep(ui, env, act, stepBack)
      AuthStep.TERMS, AuthStep.TERMS_TEXT -> TermsStep(ui, env, act, stepBack)
      //  «ورودِ پیک» (گروهِ آنلاین): همان کدِ شاگرد و همان پیوستن؛ نقشِ پیک را سرور می‌دهد
      AuthStep.COURIER -> CourierJoinScreen(
        name = ui.name,
        onName = { v -> act.edit { it.copy(name = v, error = null) } },
        code = ui.staffCode,
        onCode = { v -> act.edit { it.copy(staffCode = v.uppercase(), error = null) } },
        busy = ui.busy,
        error = ui.error,
        onJoin = act::joinWithStaffCode,
        onBack = { act.edit { it.backFromCourier() } },
      )
    }
  }
}
