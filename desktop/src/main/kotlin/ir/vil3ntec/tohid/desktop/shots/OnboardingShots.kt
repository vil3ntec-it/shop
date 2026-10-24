package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.data.LocationFix
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.ui.screens.frosted
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthActions
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthEnv
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthFlow
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthStep
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthUi
import ir.vil3ntec.tohid.ui.v3.onboarding.CodePurpose
import ir.vil3ntec.tohid.ui.v3.onboarding.CodeStep
import ir.vil3ntec.tohid.ui.v3.onboarding.EmailCodeBody
import ir.vil3ntec.tohid.ui.v3.onboarding.EmailCodeUi
import ir.vil3ntec.tohid.ui.v3.onboarding.EmailIssue
import ir.vil3ntec.tohid.ui.v3.onboarding.LockBody
import ir.vil3ntec.tohid.ui.v3.onboarding.ReauthBody
import ir.vil3ntec.tohid.ui.v3.onboarding.ReauthProof
import ir.vil3ntec.tohid.ui.v3.onboarding.ShopChoice
import ir.vil3ntec.tohid.ui.v3.onboarding.ShopSetupBody
import ir.vil3ntec.tohid.ui.v3.onboarding.TermsFrame
import ir.vil3ntec.tohid.ui.v3.onboarding.TermsPage

/**
 *  عکس‌های گروهِ G1 ورود: همهٔ دروازه‌ها از روی حالتِ ساختگی (بی شبکه، بی سرور). صفحه‌ها همان
 *  نمایشِ بی‌حالتِ برنامه‌اند (`AuthFlow`، `TermsFrame`، `ShopSetupBody`، `LockBody`، `ReauthBody`)
 *  و کارها تهی. نام‌ها و ایمیل‌ها نمونه‌اند، نه دادهٔ واقعیِ دکانی.
 *
 *  حرکت‌های هر صفحه (README): گام‌ها shared-axis X (`V3PageSwitch`)، ورودِ پلکانیِ سر/کارت/پا،
 *  کلیدِ دوتکهٔ فنری، باز و بسته شدنِ نرمِ کادرها (`V3Collapse`)، لرزشِ خطا (`v3Shake`)، تیکِ موفقیت
 *  (`V3SuccessCheck`)، نوارِ گام با فنر، خانه‌های کد (حلقهٔ نوبت و پریدنِ رقم)، نقطه‌های رمز با فنرِ
 *  شاد، فشارِ ۰٫۹۲ روی کلیدها و ۰٫۹۷ روی ردیف‌ها.
 */
object OnboardingShots : GroupShots {
  override val group = "onboarding"
  override val shots = listOf(
    Shot("signin", content = { Flow(AuthUi()) }),
    Shot("signin-error", content = {
      Flow(AuthUi(email = "karim@gmail", password = "secret", emailIssue = EmailIssue.INCOMPLETE))
    }),
    Shot("signin-saved", content = { Flow(AuthUi(), saved = SAVED) }),
    Shot("signup-1", content = {
      Flow(AuthUi(register = true, name = "کریم", email = "karim@gmail.com", password = "12345678", password2 = "12345678"))
    }),
    Shot("signup-2-code", content = {
      CodeStep(AuthUi(step = AuthStep.CODE, purpose = CodePurpose.REGISTER, register = true, email = "karim@gmail.com"), env(), NoActions, {}, initialCode = "4821")
    }),
    Shot("signup-3-found", content = {
      TermsFrame(AuthUi(step = AuthStep.TERMS, register = true, accepted = true), env(), NoActions, {}, FIX, looking = false, onRetry = {}, onMap = {})
    }),
    Shot("signup-3-failed", content = {
      TermsFrame(AuthUi(step = AuthStep.TERMS, register = true, termsMissing = true, failures = 1), env(), NoActions, {}, null, looking = false, onRetry = {}, onMap = {})
    }),
    Shot("forgot", content = { Flow(AuthUi(step = AuthStep.FORGOT, email = "karim@gmail.com")) }),
    Shot("reset-code", content = {
      CodeStep(AuthUi(step = AuthStep.CODE, purpose = CodePurpose.RESET, email = "karim@gmail.com"), env(), NoActions, {}, initialCode = "482193")
    }),
    Shot("join", content = { Flow(AuthUi(step = AuthStep.JOIN, name = "احمد", staffCode = "F6D75A07")) }),
    Shot("terms", content = { TermsPage(accepting = true, onClose = {}, onAccept = {}) }),
    Shot("code-sheet", content = {
      SheetMock {
        EmailCodeBody(
          EmailCodeUi(email = "karim@gmail.com", requestId = "r1", masked = "k***@gmail.com", code = "48"),
          left = 37, focus = remember { FocusRequester() }, showTitle = true,
          onEmail = {}, onCode = {}, onSend = {}, onSubmit = {}, onResend = {}, onOtherEmail = {}, onClose = {},
        )
      }
    }),
    Shot("shop-owner", content = {
      ShopSetupBody(ShopChoice.OWNER, "فروشگاه نور", "", busy = false, error = null, failures = 0, onChoice = {}, onName = {}, onCode = {}, onCreate = {}, onJoin = {}, onLater = {})
    }),
    Shot("shop-staff-error", content = {
      ShopSetupBody(ShopChoice.STAFF, "", "SHG-12", busy = false, error = StrAuth.joinFailed, failures = 0, onChoice = {}, onName = {}, onCode = {}, onCreate = {}, onJoin = {}, onLater = {})
    }),
    Shot("lock-wrong", content = {
      LockBody(StrAuth.lockTitle, StrAuth.lockWrong, error = true, length = LockStore.NEW_PIN_LEN, filled = 0, shake = null, onDigit = {}, onErase = {}, onFinger = {})
    }),
    Shot("lock-setup", content = {
      LockBody(StrAuth.lockAgain, StrAuth.lockSetupNote, error = false, length = LockStore.NEW_PIN_LEN, filled = 3, shake = null, onDigit = {}, onErase = {}, onFinger = null, footer = {})
    }),
    Shot("reauth", content = {
      SheetMock {
        ReauthBody(ReauthProof.PASSWORD, "secret", busy = false, note = null, error = null, failures = 0, onSecret = {}, onUseCode = {}, onResend = {}, onSubmit = {}, onCancel = {})
      }
    }),
  )

  private val SAVED = listOf(SavedLogins.Entry("karim@gmail.com", "دکان کریم"), SavedLogins.Entry("noor.store@example.com", "فروشگاه نور"))
  private val FIX = LocationFix(lat = 34.528128, lng = 69.172331)

  private fun env(saved: List<SavedLogins.Entry> = emptyList()) =
    AuthEnv(ready = true, otpReady = true, saved = saved, done = false, secondsLeft = { 45 })

  @Composable
  private fun Flow(ui: AuthUi, saved: List<SavedLogins.Entry> = emptyList()) = AuthFlow(ui, env(saved), NoActions, onClose = null)

  /** برگه روی صفحهٔ مه‌گرفته — ModalBottomSheet در پنجرهٔ عکس جدا کشیده می‌شود، پس قابِ همان شکل */
  @Composable
  private fun SheetMock(content: @Composable () -> Unit) {
    val c = PhoneV3.colors
    Box(Modifier.fillMaxSize().frosted(c).background(c.black.copy(alpha = 0.32f)), contentAlignment = Alignment.BottomCenter) {
      Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(c.card)
          .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 28.dp),
      ) { content() }
    }
  }

  private object NoActions : AuthActions {
    override fun edit(change: (AuthUi) -> AuthUi) {}
    override fun submitForm() {}
    override fun requestReset() {}
    override fun requestOtp() {}
    override fun resend() {}
    override fun submitCode(code: String, newPassword: String) {}
    override fun completeSignup(fix: LocationFix?) {}
    override fun joinWithStaffCode() {}
    override fun google() {}
    override fun skip() {}
    override fun pickSaved(entry: SavedLogins.Entry) {}
    override fun forgetSaved(entry: SavedLogins.Entry) {}
  }
}
