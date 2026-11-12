package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.account.PhoneClaimForm
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

/** ویرایشِ حساب (نام آزاد، شماره یک‌بار) و مراحلِ برگهٔ خروج (SYN-10) */
class ProfileEditModelTest {

  @Test
  fun `ذخیره — نام لازم، بی‌اینترنت فقط روی گوشی، شماره اینترنت می‌خواهد`() {
    assertEquals(Step.NAME_MISSING, ProfileEditModel.step(" ", "", ready = true, online = true))
    assertEquals(Step.LOCAL_ONLY, ProfileEditModel.step("احمد", "", ready = true, online = false))
    assertEquals(Step.LOCAL_ONLY, ProfileEditModel.step("احمد", "", ready = false, online = true))
    assertEquals(Step.PHONE_NEEDS_INTERNET, ProfileEditModel.step("احمد", "0700", ready = true, online = false))
    assertEquals(Step.SERVER, ProfileEditModel.step("احمد", "0700", ready = true, online = true))
  }

  @Test
  fun `شمارهٔ ثبت‌شده قفل است و نوشتهٔ ذخیره‌نشده دیده می‌شود`() {
    assertTrue(ProfileEditModel.phoneLocked("0700123456"))
    assertFalse(ProfileEditModel.phoneLocked(""))
    assertFalse(ProfileEditModel.dirty("احمد ", "احمد", "دکان", " دکان", ""))
    assertTrue(ProfileEditModel.dirty("احمد", "احمد", "دکان", "دکان", "07"))
    assertTrue(ProfileEditModel.dirty("کریم", "احمد", "دکان", "دکان", ""))
    assertEquals("شماره ثبت شد و قفل شد", ProfileEditModel.savedMessage("0700"))
    assertEquals("ذخیره شد", ProfileEditModel.savedMessage(""))
    assertEquals("شما با شماره وارد شده‌اید", ProfileEditModel.emailNote(""))
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
  fun `کدِ شمارهٔ تازه — خطای درجای روشن و ارسالِ دوباره با مهلتِ سرور`() {
    I18n.source = { Lang.FA }
    assertNull(ProfileEditModel.codeError(null))
    assertEquals("کد کامل نیست؛ همان کدی را بنویسید که به این شماره پیامک شد", ProfileEditModel.codeError(PhoneClaimForm.Problem.FORMAT))
    assertEquals("کد درست نیست؛ کدِ پیامک‌شده را دوباره نگاه کنید", ProfileEditModel.codeError(PhoneClaimForm.Problem.WRONG))
    assertEquals("این کد دیگر کار نمی‌کند (وقتش گذشته یا بیش از اندازه امتحان شده)", ProfileEditModel.codeError(PhoneClaimForm.Problem.DEAD))
    assertEquals("ارسال دوبارهٔ کد تا 42 ثانیه", ProfileEditModel.resendLabel(42))
    assertEquals("ارسال دوبارهٔ کد", ProfileEditModel.resendLabel(0))
    I18n.source = { Lang.EN }
    assertEquals("That code isn't right — check the texted code again", ProfileEditModel.codeError(PhoneClaimForm.Problem.WRONG))
    I18n.source = { Lang.FA }
  }

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")

  @Test
  fun `گامِ کدِ شمارهٔ تازه در ویرایشِ حسابِ نسخهٔ ۳ سیم دارد`() {
    val s = src("ui/v3/account/profile/ProfileEditScreen.kt")
    //  کدِ نادرست ⇒ هیچ درخواستی؛ کد فقط با همان شماره
    assertTrue(s.contains("val phoneCode = form.codeToSend() ?: run { form = form.badCode(); return }"))
    assertTrue(s.contains("account.updateProfile(clean, wanted, phoneCode)"))
    //  گامِ بعد فقط از `PhoneClaimForm` (کدِ ماشین‌خوانِ سرور)
    assertTrue(s.contains("val (next, what) = form.afterSave(f.code, wanted)"))
    assertTrue(s.contains("PhoneClaimForm.Next.REQUEST_CODE -> requestCode(wanted)"))
    assertTrue(s.contains("account.requestPhoneCode(phone)"))
    assertTrue(s.contains(".onSuccess { seconds -> form = form.sent(phone, seconds)"))
    assertTrue(s.contains("val (next, what) = form.sendFailed(f.code, phone)"))
    //  شماره عوض شد ⇒ کادرِ کد بسته؛ کادرِ کد فقط وقتی سرور کد خواست
    assertTrue(s.contains("onNewPhone = { claim = it; phoneError = null; form = form.phone(it) }"))
    assertTrue(s.contains("if (ui.codeShown) {"))
    assertTrue(s.contains("enabled = !ui.busy && ui.resendWait == 0"))
    //  هیچ سقفِ جدا در برنامه: نه شمارشِ تلاش، نه قفلِ محلی
    for (bad in listOf("attempts", "maxTries", "tries++")) assertFalse(bad, s.contains(bad))
    //  گامِ کهنهٔ بی‌صدازننده برداشته شد
    assertFalse(listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .any { File(it, "ir/vil3ntec/tohid/ui/screens/PhoneClaimStep.kt").exists() })
  }
}
