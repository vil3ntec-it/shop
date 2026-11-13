package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.account.EmailClaimForm
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileEditModel
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileEditModel.Step
import ir.vil3ntec.tohid.ui.v3.account.profile.SignOutModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ویرایشِ حساب («فقط ایمیل»: نام و شمارهٔ تماس آزاد، ایمیل راهِ ورود و فقط با کد) و مراحلِ برگهٔ خروج (SYN-10) */
class ProfileEditModelTest {

  @Test
  fun `ذخیره — نام لازم، بی‌اینترنت فقط روی گوشی، ایمیل یا شمارهٔ تازه اینترنت می‌خواهد`() {
    assertEquals(Step.NAME_MISSING, ProfileEditModel.step(" ", "", ready = true, online = true))
    assertEquals(Step.LOCAL_ONLY, ProfileEditModel.step("احمد", "", ready = true, online = false))
    assertEquals(Step.LOCAL_ONLY, ProfileEditModel.step("احمد", "", ready = false, online = true))
    assertEquals(Step.NEEDS_INTERNET, ProfileEditModel.step("احمد", "a@b.co", ready = true, online = false))
    assertEquals(Step.SERVER, ProfileEditModel.step("احمد", "a@b.co", ready = true, online = true))
  }

  @Test
  fun `شمارهٔ تماس آزاد است، ایمیلِ ثبت‌شده قفل، حسابِ بی‌ایمیل ایمیل را می‌افزاید`() {
    I18n.source = { Lang.FA }
    assertTrue(ProfileEditModel.emailClaimable(""))
    assertFalse(ProfileEditModel.emailClaimable("a@b.co"))
    assertTrue(ProfileEditModel.phoneChanged("0700123456", ""))
    assertFalse(ProfileEditModel.phoneChanged(" 0700123456 ", "0700123456"))
    assertTrue(ProfileEditModel.phoneChanged("", "0700123456"))
    assertFalse(ProfileEditModel.dirty("احمد ", "احمد", "دکان", " دکان", ""))
    assertTrue(ProfileEditModel.dirty("احمد", "احمد", "دکان", "دکان", "a@"))
    assertTrue(ProfileEditModel.dirty("کریم", "احمد", "دکان", "دکان", ""))
    assertEquals("ایمیل افزوده شد؛ از این پس با همین ایمیل وارد می‌شوید", ProfileEditModel.savedMessage("a@b.co"))
    assertEquals("ذخیره شد", ProfileEditModel.savedMessage(""))
    assertEquals("ورود فقط با ایمیل است؛ ایمیل را بنویسید تا کدِ تأیید به همان برود", ProfileEditModel.emailNote(""))
  }

  @Test
  fun `خروج — صفِ پُر بسته، میانِ دو کلیک دوباره سنجیده می‌شود، اجباری با پشتیبان`() {
    assertEquals(SignOutModel.Stage.CONFIRM, SignOutModel.initial(queued = 0, legacy = 0))
    assertEquals(SignOutModel.Stage.BLOCKED, SignOutModel.initial(queued = 2, legacy = 1))
    assertEquals(3, SignOutModel.pending(2, 1))
    assertNull(SignOutModel.onConfirm(0, 0))
    assertEquals(SignOutModel.Stage.BLOCKED, SignOutModel.onConfirm(0, 1))
    assertTrue(SignOutModel.canForce(backupWritten = true))
    assertFalse(SignOutModel.canForce(backupWritten = false))
  }
  @Test
  fun `کدِ ایمیلِ تازه — خطای درجای روشن و ارسالِ دوباره با مهلتِ سرور`() {
    I18n.source = { Lang.FA }
    assertNull(ProfileEditModel.codeError(null))
    assertEquals("کد کامل نیست؛ همان کدی را بنویسید که به این ایمیل فرستاده شد", ProfileEditModel.codeError(EmailClaimForm.Problem.FORMAT))
    assertEquals("کد درست نیست؛ کدِ ایمیل‌شده را دوباره نگاه کنید", ProfileEditModel.codeError(EmailClaimForm.Problem.WRONG))
    assertEquals("این کد دیگر کار نمی‌کند (وقتش گذشته یا بیش از اندازه امتحان شده)", ProfileEditModel.codeError(EmailClaimForm.Problem.DEAD))
    assertEquals("ارسال دوبارهٔ کد تا 42 ثانیه", ProfileEditModel.resendLabel(42))
    assertEquals("ارسال دوبارهٔ کد", ProfileEditModel.resendLabel(0))
    I18n.source = { Lang.EN }
    assertEquals("That code isn't right — check the emailed code again", ProfileEditModel.codeError(EmailClaimForm.Problem.WRONG))
    I18n.source = { Lang.FA }
  }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")

  @Test
  fun `گامِ کدِ ایمیلِ تازه در ویرایشِ حسابِ نسخهٔ ۳ سیم دارد و شماره بی کد می‌رود`() {
    val s = src("ui/v3/account/profile/ProfileEditScreen.kt")
    //  کدِ نادرست ⇒ هیچ درخواستی؛ کد فقط با همان ایمیل؛ شمارهٔ تماس بی کد کنارِ همان ذخیره
    assertTrue(s.contains("val emailCode = form.codeToSend() ?: run { form = form.badCode(); return }"))
    assertTrue(s.contains("account.updateProfile(clean, phoneOut, wanted, emailCode)"))
    //  گامِ بعد فقط از `EmailClaimForm` (کدِ ماشین‌خوانِ سرور)
    assertTrue(s.contains("val (next, what) = form.afterSave(f.code, wanted)"))
    assertTrue(s.contains("EmailClaimForm.Next.REQUEST_CODE -> requestCode(wanted)"))
    assertTrue(s.contains("account.requestEmailCode(address)"))
    assertTrue(s.contains(".onSuccess { seconds -> form = form.sent(address, seconds)"))
    assertTrue(s.contains("val (next, what) = form.sendFailed(f.code, address)"))
    //  ایمیل عوض شد ⇒ کادرِ کد بسته؛ کادرِ ایمیل فقط برای حسابِ بی‌ایمیل
    assertTrue(s.contains("onNewEmail = { claim = it; emailError = null; form = form.email(it) }"))
    assertTrue(s.contains("if (ProfileEditModel.emailClaimable(ui.email)) {"))
    assertTrue(s.contains("if (ui.codeShown) {"))
    assertTrue(s.contains("enabled = !ui.busy && ui.resendWait == 0"))
    //  ⛔ هیچ کدِ پیامکیِ شماره: نه درِ کهنه، نه کدِ شماره
    for (bad in listOf("requestPhoneCode", "phoneCode", "PHONE_CODE", "PhoneClaim", "attempts", "maxTries", "tries++")) assertFalse(bad, s.contains(bad))
    val repo = src("data/repo/AccountRepository.kt")
    assertFalse(repo.contains("/me/phone/code") || repo.contains("PHONE_CODE"))
    //  گامِ کهنهٔ بی‌صدازننده برداشته شد
    assertFalse(listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .any { File(it, "ir/vil3ntec/tohid/ui/screens/PhoneClaimStep.kt").exists() })
  }
}
