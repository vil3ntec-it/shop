package ir.vil3ntec.tohid.ui.v3.onboarding

import ir.vil3ntec.tohid.core.onboarding.AuthNote
import ir.vil3ntec.tohid.core.onboarding.AuthRules
import ir.vil3ntec.tohid.core.onboarding.AuthStep
import ir.vil3ntec.tohid.core.onboarding.AuthUi
import ir.vil3ntec.tohid.core.onboarding.CodePurpose
import ir.vil3ntec.tohid.core.onboarding.EmailIssue
import ir.vil3ntec.tohid.core.onboarding.PasswordIssue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** دروازهٔ ورود: همان جابه‌جایی‌ها و سنجش‌های `WelcomeScreen` ِ پیشین، بی Compose */
class AuthModelTest {

  /* ------------------------------ آغاز و کلیدِ دوتکه ------------------------------ */

  @Test
  fun `حالت از mode ِ مسیر می آید`() {
    assertEquals(AuthUi(), AuthRules.start(null))
    assertEquals(AuthUi(), AuthRules.start("signin"))
    assertTrue(AuthRules.start("signup").register)
    assertEquals(AuthStep.JOIN, AuthRules.start("join").step)
    assertEquals(AuthStep.FORGOT, AuthRules.start("forgot").step)
  }

  @Test
  fun `عوض کردنِ راه خطاها را پاک می کند و تکرارِ رمز فقط در ساختِ حساب می ماند`() {
    val reg = AuthUi(register = true, password2 = "12345678", error = "x", emailIssue = EmailIssue.EMPTY, passwordIssue = PasswordIssue.SHORT, note = AuthNote.RESENT)
    val login = reg.switchMode(false)
    assertFalse(login.register)
    assertEquals("", login.password2)
    assertNull(login.error); assertNull(login.emailIssue); assertNull(login.passwordIssue); assertNull(login.note)
    assertEquals("12345678", AuthUi(password2 = "12345678").switchMode(true).password2)
    assertEquals(reg, reg.switchMode(true))
  }

  /* ------------------------------ سنجشِ صفحه ------------------------------ */

  @Test
  fun `ایمیل — خالی، بلند، نصفه و درست`() {
    assertEquals(EmailIssue.EMPTY, AuthRules.emailIssue("   "))
    assertEquals(EmailIssue.TOO_LONG, AuthRules.emailIssue("a".repeat(185) + "@b.com"))
    assertEquals(EmailIssue.INCOMPLETE, AuthRules.emailIssue("ali@gmail"))
    assertEquals(EmailIssue.INCOMPLETE, AuthRules.emailIssue("ali gmail.com"))
    assertNull(AuthRules.emailIssue("  ali@gmail.com "))
    assertNull(AuthRules.emailIssue("a.b@mail.co.uk"))
  }

  @Test
  fun `رمز — هشت نویسه فقط در ساختِ حساب`() {
    assertEquals(PasswordIssue.EMPTY, AuthRules.passwordIssue(" ", registering = false))
    assertNull(AuthRules.passwordIssue("123", registering = false))
    assertEquals(PasswordIssue.SHORT, AuthRules.passwordIssue("1234567", registering = true))
    assertNull(AuthRules.passwordIssue("12345678", registering = true))
  }

  @Test
  fun `دکمهٔ فرم — ورود بی نام، ساختِ حساب با نام و دو رمزِ یکی`() {
    val login = AuthUi(email = "a@b.co", password = "x")
    assertTrue(AuthRules.canSubmitForm(login, ready = true))
    assertFalse(AuthRules.canSubmitForm(login, ready = false))
    assertFalse(AuthRules.canSubmitForm(login.copy(busy = true), ready = true))
    assertFalse(AuthRules.canSubmitForm(login.copy(password = ""), ready = true))
    val reg = AuthUi(register = true, name = "کریم", email = "a@b.co", password = "12345678", password2 = "12345678")
    assertTrue(AuthRules.canSubmitForm(reg, ready = true))
    assertFalse(AuthRules.canSubmitForm(reg.copy(password2 = "1234567"), ready = true))
    assertFalse(AuthRules.canSubmitForm(reg.copy(name = " "), ready = true))
  }

  @Test
  fun `یکی بودنِ دو رمز زنده است و تا تکرار خالی است چیزی نمی گوید`() {
    assertNull(AuthRules.passwordsMatch(AuthUi(password = "a")))
    assertEquals(true, AuthRules.passwordsMatch(AuthUi(password = "abc", password2 = "abc")))
    assertEquals(false, AuthRules.passwordsMatch(AuthUi(password = "abc", password2 = "abd")))
  }

  /* ------------------------------ کدِ شش‌رقمی ------------------------------ */

  @Test
  fun `کد — رقمِ فارسی لاتین، فقط رقم، حداکثر شش`() {
    assertEquals("123456", AuthRules.codeDigits("۱۲۳-۴۵۶۷۸"))
    assertEquals("0912", AuthRules.codeDigits(" ٠٩a12 "))
  }

  @Test
  fun `با ششمین رقم خودش می فرستد جز در بازیابی`() {
    assertTrue(AuthRules.autoSubmit("123456", busy = false, askPassword = false))
    assertFalse(AuthRules.autoSubmit("123456", busy = true, askPassword = false))
    assertFalse(AuthRules.autoSubmit("123456", busy = false, askPassword = true))
    assertFalse(AuthRules.autoSubmit("12345", busy = false, askPassword = false))
    assertTrue(AuthRules.canSubmitCode("123456", "12345678", busy = false, askPassword = true))
    assertFalse(AuthRules.canSubmitCode("123456", "1234567", busy = false, askPassword = true))
  }

  /* ------------------------------ جابه‌جایی‌ها ------------------------------ */

  @Test
  fun `برگشت از صفحهٔ کد — بازیابی به ایمیلِ بازیابی، بقیه به فرم`() {
    val reset = AuthUi(step = AuthStep.CODE, purpose = CodePurpose.RESET, error = "x", note = AuthNote.RESENT)
    assertEquals(AuthStep.FORGOT, reset.backFromCode().step)
    assertNull(reset.backFromCode().error); assertNull(reset.backFromCode().note)
    val reg = AuthUi(step = AuthStep.CODE, purpose = CodePurpose.REGISTER, register = true)
    assertEquals(AuthStep.FORM, reg.back()!!.step)
    assertTrue(reg.back()!!.register)
  }

  @Test
  fun `گامِ سوم — برگشت به کدِ ثبت نام، تیکِ شرایط از نو`() {
    val terms = AuthUi(register = true, purpose = CodePurpose.REGISTER).toTerms("T1").setAccepted(true)
    assertEquals(AuthStep.TERMS, terms.step)
    assertEquals("T1", terms.ticket)
    val back = terms.backFromTerms()
    assertEquals(AuthStep.CODE, back.step)
    assertEquals(CodePurpose.REGISTER, back.purpose)
    assertFalse(back.accepted)
    assertEquals("T1", back.ticket)
  }

  @Test
  fun `متنِ کاملِ شرایط — «می پذیرم» تیک می زند و برمی گردد`() {
    val t = AuthUi(step = AuthStep.TERMS, termsMissing = true).openTermsText()
    assertEquals(AuthStep.TERMS_TEXT, t.step)
    assertEquals(AuthStep.TERMS, t.back()!!.step)
    val ok = t.acceptTerms()
    assertEquals(AuthStep.TERMS, ok.step)
    assertTrue(ok.accepted)
    assertFalse(ok.termsMissing)
  }

  @Test
  fun `بلیتِ منقضی — از پلهٔ اولِ ثبت نام، خطای سرور می ماند`() {
    val u = AuthUi(step = AuthStep.TERMS, ticket = "T", error = "منقضی").ticketExpired()
    assertEquals(AuthStep.FORM, u.step)
    assertTrue(u.register)
    assertEquals("", u.ticket)
    assertEquals("منقضی", u.error)
  }

  @Test
  fun `سرورِ قدیمی بی نشست — ورودِ ساده با همان رمز`() {
    val u = AuthUi(step = AuthStep.TERMS, register = true, password = "12345678", password2 = "12345678", ticket = "T").signedUpNeedsLogin()
    assertEquals(AuthStep.FORM, u.step)
    assertFalse(u.register)
    assertEquals("12345678", u.password)
    assertEquals("", u.password2)
    assertEquals(AuthNote.ACCOUNT_CREATED, u.note)
    val done = AuthUi(password = "p", password2 = "p", ticket = "T").signedUp()
    assertEquals("", done.password); assertEquals("", done.password2); assertEquals("", done.ticket)
  }

  @Test
  fun `ورودِ ذخیره شده — ایمیل پر، حسابِ شماره ای فقط پیام`() {
    val byEmail = AuthUi(register = true, emailIssue = EmailIssue.EMPTY, note = AuthNote.RESENT).pickSaved("a@b.co", "دکان نور")
    assertFalse(byEmail.register)
    assertEquals("a@b.co", byEmail.email)
    assertNull(byEmail.emailIssue); assertNull(byEmail.note)
    assertEquals("دکان نور", byEmail.name)
    val byPhone = AuthUi(email = "x@y.z", name = "کریم").pickSaved("0700123456", "دکان")
    assertEquals("x@y.z", byPhone.email)
    assertEquals(AuthNote.PHONE_ACCOUNT, byPhone.note)
    assertEquals("کریم", byPhone.name)
  }

  @Test
  fun `یک پله به عقب از هر گام و ریشه بی برگشت`() {
    assertNull(AuthUi().back())
    assertEquals(AuthStep.FORM, AuthUi(step = AuthStep.JOIN).back()!!.step)
    assertEquals(AuthStep.FORM, AuthUi(step = AuthStep.FORGOT).back()!!.step)
    assertEquals(AuthStep.JOIN, AuthUi(step = AuthStep.COURIER).back()!!.step)
  }

  @Test
  fun `نوارِ سه گام فقط در ثبت نام`() {
    assertEquals(1, AuthRules.signupStage(AuthUi(register = true)))
    assertEquals(2, AuthRules.signupStage(AuthUi(step = AuthStep.CODE, purpose = CodePurpose.REGISTER)))
    assertEquals(3, AuthRules.signupStage(AuthUi(step = AuthStep.TERMS_TEXT)))
    assertNull(AuthRules.signupStage(AuthUi()))
    assertNull(AuthRules.signupStage(AuthUi(step = AuthStep.CODE, purpose = CodePurpose.OTP)))
  }

  @Test
  fun `چرخاندنِ گوشی — نوشته ها می مانند و حالِ گذرا نه`() {
    val u = AuthUi(
      step = AuthStep.CODE, register = true, purpose = CodePurpose.REGISTER, name = "n", email = "e", password = "p",
      password2 = "q", staffCode = "S", ticket = "T", accepted = true, busy = true, error = "x", failures = 3,
    )
    val back = AuthUi.restore(u.saved())
    assertEquals(u.copy(busy = false, error = null, failures = 0), back)
  }

  @Test
  fun `لوکیشن چپ به راست با پنج رقمِ اعشار`() {
    assertEquals("34.52813, 69.17233", AuthRules.coordinates(34.528128, 69.172331))
  }

  /** مدل در `core` (بی `java.*`) است؛ نوشتهٔ لوکیشن باید همان `%.5f` ِ پیشین بماند */
  @Test
  fun `لوکیشن همان String format ِ Locale US است — مرزی و تصادفی`() {
    fun old(x: Double) = String.format(java.util.Locale.US, "%.5f", x)
    val edge = listOf(
      0.0, -0.0, 0.000005, -0.000005, 0.0000049, 1.000005, 34.528125, 69.999995, 9.999995, 99.999999,
      -179.999995, 180.0, 1e-7, -1e-7, 1.5e-5, 12345678.123456, 0.1 + 0.2, 33.000015, 64.123455,
    )
    val rnd = java.util.Random(390)
    val all = edge + List(20_000) { (rnd.nextDouble() * 360 - 180) } +
      List(5_000) { Math.round(rnd.nextDouble() * 36_000_000) / 1_000_000.0 + 0.0000005 }
    for (x in all) assertEquals("x=$x", "${old(x)}, ${old(-x)}", AuthRules.coordinates(x, -x))
  }
}
