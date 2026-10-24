package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileEditModel
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileEditModel.Step
import ir.vil3ntec.tohid.ui.v3.account.profile.SignOutModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
