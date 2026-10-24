package ir.vil3ntec.tohid.ui.v3.onboarding

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.model.SessionDto
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.userText
import ir.vil3ntec.tohid.data.GoogleSignIn
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.LocationFix
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.StaffCode
import ir.vil3ntec.tohid.data.Terms
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.OtpCooldown
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** گوگلی که منتظرِ اثباتِ روشِ قبلیِ حساب است (۴۰۹ `google_link_required`) */
data class GoogleLinkAsk(
  val idToken: String,
  val email: String,
  val message: String,
  val codeSent: Boolean = false,
  val problem: String? = null,
)

/**
 *  درخواست‌های دروازهٔ ورود — **عیناً** همان منطقِ `WelcomeScreen` ِ پیشین، فقط بیرون از صفحه:
 *  همان مسیرهای سرور، همان ترتیب (دفترِ گوشی پیش از نوشتنِ حساب و پیش از `onDone`)، همان مهلتِ
 *  ارسالِ دوباره روی گوشی (`OtpCooldown`)، همان جلوگیری از دو بار زدن، و پیامِ ردِ سرور همان‌طور
 *  که سرور می‌گوید (بی افشای بودن یا نبودنِ حساب). تنها تفاوت: کدِ آزمایشیِ سرور دیگر نشان داده
 *  نمی‌شود. صفحه‌ها فقط `ui` را می‌خوانند و این تابع‌ها را صدا می‌زنند.
 */
@Stable
class AuthController(
  private val context: Context,
  private val store: ShopStore,
  private val scope: CoroutineScope,
  /** حالتِ دروازه — `rememberSaveable` ِ صدازننده، تا با چرخاندنِ گوشی بماند */
  uiState: MutableState<AuthUi>,
  private val onDone: () -> Unit,
) : AuthActions {
  var ui by uiState
  var saved by mutableStateOf(SavedLogins.read(context))
    private set
  /** شناسهٔ گوگلِ سرور؛ خالی ⇒ ورود با گوگل روی این سرور باز نیست */
  var googleId by mutableStateOf("")
    private set
  /** سرور واقعاً کد می‌فرستد؟ (`/config` ⇒ `otpEnabled`) — بی آن «ورود با کد» ساخته نمی‌شود */
  var otpReady by mutableStateOf(false)
    private set
  var googleLink by mutableStateOf<GoogleLinkAsk?>(null)
  /** ورود انجام شد: تیکِ موفقیت روی دکمه و بعد برنامه */
  var done by mutableStateOf(false)
    private set

  private val sync = SyncStore(context)
  private val auth = Backend.auth(context)
  private val shops = Backend.shop(context)
  val cooldown = OtpCooldown(context)

  /** «می‌شود به سرور زد یا نه» را پیکربندی می‌گوید — نه چیزی که کاربر تایپ کرده */
  val ready: Boolean get() = AppConfig.isConfigured(context)

  /** خطاهای کادرها هم با هر تغییر پاک می‌شوند */
  override fun edit(change: (AuthUi) -> AuthUi) { ui = change(ui) }

  suspend fun loadServerConfig() {
    if (!AppConfig.isConfigured(context)) { googleId = ""; return }
    googleId = auth.googleClientId()
    //  همان یک درخواست، دو جواب: کلیدِ گوگل و اینکه کد فرستادنی است
    otpReady = auth.otpEnabled()
  }

  private fun fail(e: Throwable) { ui = ui.failed(e.userText(StrAuth.serverUnreachable)) }

  private fun start(): Boolean {
    if (ui.busy || done) return false
    ui = ui.copy(busy = true, error = null, note = null)
    return true
  }

  private fun idle() { ui = ui.copy(busy = false) }

  private suspend fun complete() {
    done = true
    if (Motion.enabled) delay(MotionTokens.EMPHASIZED.toLong())
    onDone()
  }

  /** «ادامه بدون حساب» — همان `onDone`، بی هیچ درخواستی */
  override fun skip() { if (!done) onDone() }

  /**
   *  ورود انجام شد. پیش از هر چیز دفترِ روی گوشی مالِ همین حساب و همین دکان می‌شود؛ حساب
   *  *بعد از* آن نوشته می‌شود و `onDone` (که همگام‌سازی را راه می‌اندازد) آخر از همه.
   */
  private fun finish(identifier: String, session: SessionDto) {
    val display = session.user.name.ifBlank { ui.name.trim() }
    SavedLogins.remember(context, identifier, display)
    session.shop?.role?.let { ShopRole.remember(context, it) }
    scope.launch {
      runCatching { LedgerOwner.signedIn(context, store, session.user.id, session.shop?.id.orEmpty()) }
      sync.rememberAccount(session.user, display)
      complete()
    }
  }

  /** دکمهٔ اصلیِ فرم: ورود، یا پلهٔ اولِ ثبت‌نام (کد به همان ایمیل؛ حساب هنوز ساخته نمی‌شود) */
  override fun submitForm() {
    if (ui.busy || done) return
    val emailIssue = AuthRules.emailIssue(ui.email)
    val passwordIssue = AuthRules.passwordIssue(ui.password, ui.register)
    ui = ui.copy(emailIssue = emailIssue, passwordIssue = passwordIssue)
    if (emailIssue != null || passwordIssue != null) { ui = ui.copy(failures = ui.failures + 1); return }
    val u = ui
    start()
    scope.launch {
      if (u.register) {
        auth.registerStart(u.name.trim(), u.email.trim(), u.password)
          .onSuccess {
            cooldown.start(u.email.trim(), it.resendSeconds)
            ui = ui.codeSent(CodePurpose.REGISTER)
          }
          .onFailure { e ->
            //  تأییدِ ایمیل شرطِ ساختنِ حساب است: سرورِ بی کدِ تأیید ⇒ ثبت‌نام از برنامه نه
            if (e is ApiFailure.NotFound) ui = ui.failed(StrAuth.noVerifyServer) else fail(e)
          }
      } else {
        auth.login(u.email.trim(), u.password)
          .onSuccess { finish(u.email.trim(), it) }
          .onFailure { fail(it) }
      }
      idle()
    }
  }

  /** «رمز را فراموش کرده‌اید؟» — کدِ بازیابی به همان ایمیل؛ ایمیلِ بدقالب سقفِ نرخ را خرج نمی‌کند */
  override fun requestReset() {
    val bad = AuthRules.emailIssue(ui.email)
    if (bad != null) { ui = ui.copy(emailIssue = bad, failures = ui.failures + 1); return }
    if (!start()) return
    val to = ui.email.trim()
    scope.launch {
      auth.forgotPassword(to)
        .onSuccess {
          cooldown.start(to, it.resendSeconds)
          ui = ui.codeSent(CodePurpose.RESET)
        }
        .onFailure { fail(it) }
      idle()
    }
  }

  /** «ورود با کد به‌جای رمز» — کدِ قبلیِ هنوز معتبر دوباره فرستاده نمی‌شود */
  override fun requestOtp() {
    val bad = AuthRules.emailIssue(ui.email)
    if (bad != null) { ui = ui.copy(emailIssue = bad, failures = ui.failures + 1); return }
    if (!start()) return
    scope.launch {
      val to = ui.email.trim()
      if (cooldown.secondsLeft(to) > 0) {
        ui = ui.codeSent(CodePurpose.OTP, AuthNote.PREVIOUS_VALID)
      } else {
        auth.requestOtp(to)
          .onSuccess {
            cooldown.start(to, it.resendSeconds)
            ui = ui.codeSent(CodePurpose.OTP, null)
          }
          .onFailure { fail(it) }
      }
      idle()
    }
  }

  /** «ارسال دوبارهٔ کد» — مهلت را پاسخِ سرور می‌نویسد؛ ارسالِ ناموفق مهلتی نمی‌گذارد */
  override fun resend() {
    if (ui.busy) return
    ui = ui.copy(busy = true, error = null)
    val u = ui
    val destination = u.email.trim()
    scope.launch {
      val asked = when (u.purpose) {
        CodePurpose.REGISTER -> auth.registerStart(u.name.trim(), destination, u.password)
        CodePurpose.RESET -> auth.forgotPassword(destination)
        CodePurpose.OTP -> auth.requestOtp(destination)
      }
      asked
        .onSuccess {
          cooldown.start(destination, it.resendSeconds)
          ui = ui.copy(note = AuthNote.RESENT)
        }
        .onFailure { fail(it) }
      idle()
    }
  }

  /** کدِ شش‌رقمی (و در بازیابی رمزِ تازه) به همان جایی که از آن آمده */
  override fun submitCode(code: String, newPassword: String) {
    if (!start()) return
    val u = ui
    val destination = u.email.trim()
    scope.launch {
      when (u.purpose) {
        //  کد درست بود، حساب هنوز ساخته نشده: بلیت و بعد لوکیشن و شرایط
        CodePurpose.REGISTER -> auth.registerVerify(destination, code)
          .onSuccess { ui = ui.toTerms(it.ticket) }
          .onFailure { fail(it) }
        CodePurpose.RESET -> auth.resetPassword(destination, code, newPassword)
          .onSuccess { finish(destination, it) }
          .onFailure { fail(it) }
        CodePurpose.OTP -> auth.verifyOtp(destination, code, u.name.trim())
          .onSuccess { finish(destination, it) }
          .onFailure { fail(it) }
      }
      idle()
    }
  }

  /** پلهٔ سوم: حساب همین‌جا ساخته می‌شود — پذیرشِ شرایط اجباری، لوکیشن نه */
  override fun completeSignup(fix: LocationFix?) {
    if (!ui.accepted) { ui = ui.copy(termsMissing = true, failures = ui.failures + 1); return }
    if (!start()) return
    val u = ui
    scope.launch {
      auth.registerComplete(
        ticket = u.ticket,
        name = u.name.trim(),
        password = u.password,
        termsVersion = Terms.VERSION,
        deviceUid = sync.deviceUid,
        deviceName = android.os.Build.MODEL ?: AuthRules.DEVICE_FALLBACK,
        location = fix,
      )
        .onSuccess { session ->
          val who = u.email.trim()
          if (session.isValid) {
            ui = ui.signedUp()
            finish(who, session)
          } else {
            ui = ui.signedUpNeedsLogin()
          }
        }
        .onFailure {
          fail(it)
          if (it.code == "register_ticket_invalid") ui = ui.ticketExpired()
        }
      idle()
    }
  }

  /**
   *  پیوستن با کدِ شاگرد (کدِ دعوتِ پیک هم همین است). کد خودش اعتبارنامه است: کسی که از قبل
   *  وارد است همان حسابش می‌پیوندد، وگرنه سرور برای همین دستگاه حسابِ شاگرد می‌سازد. نقش همین
   *  لحظه نوشته می‌شود تا شاگرد حتی یک لحظه تنظیمات را باز نبیند.
   */
  override fun joinWithStaffCode() {
    val entered = StaffCode.clean(ui.staffCode)
    //  قالبِ کد را سرور می‌شناسد؛ این‌جا فقط «چیزی زده شده؟»
    if (!StaffCode.usable(entered)) { ui = ui.failed(Str.codeIncomplete); return }
    if (!start()) return
    scope.launch {
      if (Backend.tokens(context).signedIn) {
        shops.join(entered)
          .onSuccess { shopState ->
            shopState.role?.let { ShopRole.remember(context, it) }
            runCatching { LedgerOwner.shopChanged(context, store, shopState.shop?.id.orEmpty()) }
            complete()
          }
          .onFailure { fail(it) }
      } else {
        auth.loginWithStaffCode(
          code = entered,
          name = ui.name.trim(),
          deviceUid = sync.deviceUid,
          deviceName = android.os.Build.MODEL ?: AuthRules.DEVICE_FALLBACK,
        )
          .onSuccess { session ->
            SavedLogins.remember(context, entered, session.user.name)
            session.shop?.role?.let { ShopRole.remember(context, it) }
            //  دفترِ روی گوشی مالِ همین حساب و دکان، وگرنه دفترِ قبلی داخلِ دکانِ تازه می‌رفت
            runCatching { LedgerOwner.signedIn(context, store, session.user.id, session.shop?.id.orEmpty()) }
            sync.rememberAccount(session.user)
            complete()
          }
          .onFailure { fail(it) }
      }
      idle()
    }
  }

  /** ورود با ایمیلِ گوگلِ خودِ گوشی؛ ایمیلِ دارای حساب ⇒ اثباتِ روشِ قبلی (`GoogleLinkSheet`) */
  override fun google() {
    if (googleId.isBlank()) { ui = ui.copy(note = null).failed(StrAuth.googleNotEnabled); return }
    if (!start()) return
    scope.launch {
      runCatching { GoogleSignIn.pick(context, googleId) }
        .onSuccess { account ->
          //  کاربر خودش بست — نه خطا، نه پیام
          if (account != null) {
            auth.loginWithGoogle(account.idToken)
              .onSuccess { finish(account.email, it) }
              .onFailure {
                if (it is ApiFailure.Conflict && it.code == "google_link_required") {
                  googleLink = GoogleLinkAsk(account.idToken, account.email, it.message.orEmpty())
                } else fail(it)
              }
          }
        }
        .onFailure { ui = ui.failed(it.userText(StrAuth.googleFailed)) }
      idle()
    }
  }

  fun googleSendCode(ask: GoogleLinkAsk) {
    ui = ui.copy(busy = true)
    scope.launch {
      auth.requestGoogleLinkCode(ask.idToken)
        .onSuccess { googleLink = ask.copy(codeSent = true, problem = null) }
        .onFailure { googleLink = ask.copy(problem = it.userText(StrAuth.codeNotSent)) }
      idle()
    }
  }

  /** ⛔ برابر بودنِ ایمیل کافی نیست: رمزِ همان حساب یا کدی که به ایمیلِ حساب رفته */
  fun googleProve(ask: GoogleLinkAsk, secret: String) {
    ui = ui.copy(busy = true)
    scope.launch {
      val isCode = ask.codeSent
      auth.loginWithGoogle(ask.idToken, password = secret.takeUnless { isCode }, code = secret.takeIf { isCode })
        .onSuccess { googleLink = null; finish(ask.email, it) }
        .onFailure { googleLink = ask.copy(problem = it.userText(StrAuth.linkFailed)) }
      idle()
    }
  }

  override fun pickSaved(entry: SavedLogins.Entry) { ui = ui.pickSaved(entry.identifier, entry.shop) }

  override fun forgetSaved(entry: SavedLogins.Entry) {
    SavedLogins.forget(context, entry.identifier)
    saved = SavedLogins.read(context)
  }
}
