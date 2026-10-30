package ir.vil3ntec.tohid.core.onboarding

/*
 *  حالتِ خالصِ دروازهٔ ورود (`auth?mode=signin|signup|join|forgot`) — بی Compose و بی شبکه.
 *
 *  همان حالت‌هایی که `WelcomeScreen` ِ پیشین در دهها `var` نگه می‌داشت (emailMode، codeSent،
 *  resetting، signupStep، showMore، courierJoin…) این‌جا یک دادهٔ تغییرناپذیر است و هر جابه‌جایی
 *  یک تابع؛ آزمون (`AuthModelTest`) همان رفتارِ پیشین را قفل می‌کند. درخواست‌های شبکه در
 *  `AuthController` و نمایش در `*Step.kt` است.
 */

/** گام‌های دروازه؛ `depth` جهتِ حرکت را می‌گوید (عمیق‌تر ⇐ رفتن، کم‌عمق‌تر ⇐ برگشت) */
enum class AuthStep(val depth: Int) {
  /** ورود یا ساختِ حساب (گامِ ۱ از ۳) — کلیدِ دوتکه همین‌جا جابه‌جا می‌کند */
  FORM(0),
  FORGOT(1),
  JOIN(1),
  /** صفحهٔ ورودِ پیک (مالِ گروهِ آنلاین) با همان کدِ شاگرد */
  COURIER(2),
  CODE(2),
  TERMS(3),
  TERMS_TEXT(4),
}

/** کدِ شش‌رقمی برای کدام کار رفته — هر سه به یک صفحه می‌رسند */
enum class CodePurpose { REGISTER, RESET, OTP }

enum class EmailIssue { EMPTY, TOO_LONG, INCOMPLETE }

enum class PasswordIssue { EMPTY, SHORT }

/** پیام‌های آگاهی (نه خطا) */
enum class AuthNote { RESENT, PREVIOUS_VALID, ACCOUNT_CREATED, PHONE_ACCOUNT }

/**
 *  یک لحظه از دروازه. فیلدهای بالایی با چرخاندنِ گوشی می‌مانند (`saved`)؛ پایینی‌ها مثلِ
 *  پیش فقط در حافظهٔ صفحه‌اند. `failures` فقط شمارندهٔ لرزشِ خطاست.
 */
data class AuthUi(
  val step: AuthStep = AuthStep.FORM,
  val register: Boolean = false,
  val purpose: CodePurpose = CodePurpose.OTP,
  val name: String = "",
  val email: String = "",
  val password: String = "",
  val password2: String = "",
  val staffCode: String = "",
  val ticket: String = "",
  val accepted: Boolean = false,
  val busy: Boolean = false,
  val error: String? = null,
  val note: AuthNote? = null,
  val emailIssue: EmailIssue? = null,
  val passwordIssue: PasswordIssue? = null,
  val termsMissing: Boolean = false,
  val failures: Int = 0,
) {
  /** «ورود» ⇄ «ساخت حساب»: خطاهای راهِ دیگر پاک؛ تکرارِ رمز فقط در ساختِ حساب معنی دارد */
  fun switchMode(toRegister: Boolean): AuthUi =
    if (toRegister == register) this
    else copy(
      register = toRegister, error = null, note = null, emailIssue = null, passwordIssue = null,
      password2 = if (toRegister) password2 else "",
    )

  fun openJoin(): AuthUi = copy(step = AuthStep.JOIN, error = null)
  fun openForgot(): AuthUi = copy(step = AuthStep.FORGOT, error = null, note = null, emailIssue = null)
  fun openCourier(): AuthUi = copy(step = AuthStep.COURIER, error = null)

  /** برگشت از «کد شاگرد» یا «فراموشیِ رمز» به فرم */
  fun backToForm(): AuthUi = copy(step = AuthStep.FORM, error = null, note = null)

  fun backFromCourier(): AuthUi = copy(step = AuthStep.JOIN, error = null)

  /** یک پله به عقب؛ null یعنی ریشهٔ دروازه (برگشت مالِ صدازننده است) */
  fun back(): AuthUi? = when (step) {
    AuthStep.FORM -> null
    AuthStep.FORGOT, AuthStep.JOIN -> backToForm()
    AuthStep.COURIER -> backFromCourier()
    AuthStep.CODE -> backFromCode()
    AuthStep.TERMS -> backFromTerms()
    AuthStep.TERMS_TEXT -> closeTermsText()
  }

  /** کد رفت (یا کدِ قبلی هنوز معتبر است) ⇒ صفحهٔ کد */
  fun codeSent(purpose: CodePurpose, note: AuthNote? = this.note): AuthUi =
    copy(step = AuthStep.CODE, purpose = purpose, note = note)

  /** «برگشت و عوض کردن نشانی»: بازیابی ⇐ صفحهٔ ایمیلِ بازیابی؛ ثبت‌نام و ورود با کد ⇐ فرم */
  fun backFromCode(): AuthUi =
    copy(step = if (purpose == CodePurpose.RESET) AuthStep.FORGOT else AuthStep.FORM, note = null, error = null)

  /** کد درست بود: بلیت برای پلهٔ سوم (حساب هنوز ساخته نشده) */
  fun toTerms(ticket: String): AuthUi = copy(step = AuthStep.TERMS, ticket = ticket, accepted = false, termsMissing = false)

  fun backFromTerms(): AuthUi =
    copy(step = AuthStep.CODE, purpose = CodePurpose.REGISTER, error = null, accepted = false, termsMissing = false)

  fun openTermsText(): AuthUi = copy(step = AuthStep.TERMS_TEXT)
  fun closeTermsText(): AuthUi = copy(step = AuthStep.TERMS)
  fun acceptTerms(): AuthUi = copy(step = AuthStep.TERMS, accepted = true, termsMissing = false)
  fun setAccepted(v: Boolean): AuthUi = copy(accepted = v, termsMissing = if (v) false else termsMissing)

  /** بلیتِ منقضی یا خرج‌شده ⇒ از پلهٔ اولِ ثبت‌نام (خطای سرور می‌ماند) */
  fun ticketExpired(): AuthUi = copy(step = AuthStep.FORM, register = true, ticket = "")

  /** سرورِ قدیمی حساب را ساخت ولی نشست نداد ⇒ فقط یک ورودِ ساده مانده؛ رمز نگه داشته می‌شود */
  fun signedUpNeedsLogin(): AuthUi =
    copy(step = AuthStep.FORM, register = false, ticket = "", password2 = "", note = AuthNote.ACCOUNT_CREATED)

  /** حساب ساخته و وارد شد: رمز از حافظهٔ صفحه پاک می‌شود */
  fun signedUp(): AuthUi = copy(ticket = "", password = "", password2 = "")

  /** «ورودهای ذخیره شده»: ایمیل پر می‌شود و رمز را می‌خواهد (هیچ ورودِ بی‌رمزی نیست) */
  fun pickSaved(identifier: String, shop: String): AuthUi {
    val byEmail = identifier.contains("@")
    return copy(
      register = false,
      error = null,
      email = if (byEmail) identifier else email,
      emailIssue = if (byEmail) null else emailIssue,
      note = if (byEmail) null else AuthNote.PHONE_ACCOUNT,
      name = name.ifBlank { shop },
    )
  }

  fun failed(message: String?): AuthUi = copy(error = message, failures = failures + 1)

  /** آنچه با چرخاندنِ گوشی می‌ماند — همان‌هایی که پیش‌تر `rememberSaveable` بودند */
  fun saved(): List<Any> = listOf(step.name, register, purpose.name, name, email, password, password2, staffCode, ticket, accepted)

  companion object {
    fun restore(v: List<Any>): AuthUi = AuthUi(
      step = AuthStep.valueOf(v[0] as String),
      register = v[1] as Boolean,
      purpose = CodePurpose.valueOf(v[2] as String),
      name = v[3] as String,
      email = v[4] as String,
      password = v[5] as String,
      password2 = v[6] as String,
      staffCode = v[7] as String,
      ticket = v[8] as String,
      accepted = v[9] as Boolean,
    )
  }
}

/**
 *  سنجشِ صفحه — ⚠️ امنیت نیست، ادبِ صفحه است: کاربر پیش از یک رفت‌وبرگشتِ شبکه بفهمد
 *  `ali@gmail` نصفه است. حرفِ آخر با سرور است؛ الگو عمداً همان الگوی سرور است، نه سخت‌گیرتر.
 */
object AuthRules {
  private val EMAIL_SHAPE = Regex("^[^\\s@]+@[^\\s@.]+(\\.[^\\s@.]+)+$")
  const val EMAIL_MAX = 190
  const val PASSWORD_MIN = 8
  const val CODE_LENGTH = 6
  const val SIGNUP_STAGES = 3

  /** نامِ دستگاه وقتی خودِ سیستم نامی نمی‌دهد — دادهٔ سرور است، همان واژهٔ پیشین (نه نوشتهٔ صفحه) */
  const val DEVICE_FALLBACK = "گوشی"

  fun emailIssue(raw: String): EmailIssue? {
    val s = raw.trim()
    return when {
      s.isEmpty() -> EmailIssue.EMPTY
      s.length > EMAIL_MAX -> EmailIssue.TOO_LONG
      !EMAIL_SHAPE.matches(s) -> EmailIssue.INCOMPLETE
      else -> null
    }
  }

  fun passwordIssue(password: String, registering: Boolean): PasswordIssue? = when {
    password.isBlank() -> PasswordIssue.EMPTY
    registering && password.length < PASSWORD_MIN -> PasswordIssue.SHORT
    else -> null
  }

  /**
   *  دکمهٔ اصلیِ فرم: بی سرور یا در حالِ کار خاموش. ورود فقط ایمیل و رمز می‌خواهد (نام هیچ‌جا
   *  نمی‌رود)؛ ساختِ حساب نام هم می‌خواهد و تا دو رمز یکی نشوند باز نمی‌شود.
   */
  fun canSubmitForm(ui: AuthUi, ready: Boolean): Boolean = ready && !ui.busy && if (ui.register) {
    ui.name.isNotBlank() && ui.email.isNotBlank() && ui.password.isNotBlank() && ui.password == ui.password2
  } else {
    ui.email.isNotBlank() && ui.password.isNotBlank()
  }

  /** null تا تکرارِ رمز خالی است؛ بعد یکی بودن یا نبودن، زنده */
  fun passwordsMatch(ui: AuthUi): Boolean? = if (ui.password2.isBlank()) null else ui.password == ui.password2

  /** رقمِ فارسی/عربی ⇐ لاتین، فقط رقم، حداکثر شش (چسباندن از پیامک هم) */
  fun codeDigits(raw: String): String = OnboardingText.latinDigits(raw).filter { it in '0'..'9' }.take(CODE_LENGTH)

  /** با ششمین رقم خودش می‌فرستد — جز بازیابی، که رمزِ تازه هم باید نوشته شود */
  fun autoSubmit(code: String, busy: Boolean, askPassword: Boolean): Boolean =
    code.length == CODE_LENGTH && !busy && !askPassword

  fun canSubmitCode(code: String, fresh: String, busy: Boolean, askPassword: Boolean): Boolean =
    code.length == CODE_LENGTH && !busy && (!askPassword || fresh.length >= PASSWORD_MIN)

  /** گامِ چندمِ ثبت‌نام (۱ تا ۳)؛ null یعنی این صفحه بخشی از ثبت‌نام نیست */
  fun signupStage(ui: AuthUi): Int? = when {
    ui.step == AuthStep.FORM && ui.register -> 1
    ui.step == AuthStep.CODE && ui.purpose == CodePurpose.REGISTER -> 2
    ui.step == AuthStep.TERMS || ui.step == AuthStep.TERMS_TEXT -> 3
    else -> null
  }

  /** نقطهٔ پیداشده، چپ‌به‌راست و با ممیزِ لاتین (پنج رقمِ اعشار ≈ یک متر) */
  fun coordinates(lat: Double, lng: Double): String = "${OnboardingText.fixed5(lat)}, ${OnboardingText.fixed5(lng)}"

  /** `auth?mode=` ⇒ گامِ آغاز */
  fun start(mode: String?): AuthUi = when (mode) {
    "signup" -> AuthUi(register = true)
    "join" -> AuthUi(step = AuthStep.JOIN)
    "forgot" -> AuthUi(step = AuthStep.FORGOT)
    else -> AuthUi()
  }
}
