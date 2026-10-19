package ir.vil3ntec.tohid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «حساب من»: وضعیتِ اشتراک، نوارِ هشدار فقط وقتی واقعی است، و نامِ نقش (phone-ui). */
class ProfileModelTest {

  private fun plan(
    localActive: Boolean = false, localDays: Int = 0, localExpired: Boolean = false,
    serverActive: Boolean = false, serverDays: Int = 0, serverTrial: Boolean = false,
  ) = ProfileModel.plan(localActive, localDays, localExpired, serverActive, serverDays, serverTrial, warnDays = 7)

  @Test
  fun `مجوزِ روی گوشی مقدم است`() {
    val p = plan(localActive = true, localDays = 40, serverActive = true, serverDays = 3, serverTrial = true)
    assertTrue(p.active); assertEquals(40, p.days); assertFalse(p.trial); assertFalse(p.urgent)
    assertNull(ProfileModel.notice(p, 7))
  }

  @Test
  fun `بی مجوزِ گوشی حرفِ سرور — آزمایشیِ رو به پایان هشدار دارد`() {
    val p = plan(serverActive = true, serverDays = 5, serverTrial = true)
    assertTrue(p.active); assertTrue(p.trial); assertEquals(5, p.days); assertTrue(p.urgent)
    assertEquals(ProfileModel.Notice.Ending(5, true), ProfileModel.notice(p, 7))
  }

  @Test
  fun `سرورِ فعال با صفر روز فعال حساب نمی‌شود`() {
    val p = plan(serverActive = true, serverDays = 0)
    assertFalse(p.active); assertEquals(0, p.days); assertFalse(p.urgent)
    assertNull(ProfileModel.notice(p, 7))
  }

  @Test
  fun `تمام‌شده همیشه هشدار دارد، «بدون اشتراک» هیچ`() {
    assertEquals(ProfileModel.Notice.Expired, ProfileModel.notice(plan(localExpired = true), 7))
    assertNull(ProfileModel.notice(plan(), 7))
  }

  @Test
  fun `مرزِ هفت روز`() {
    assertEquals(ProfileModel.Notice.Ending(7, false), ProfileModel.notice(plan(localActive = true, localDays = 7), 7))
    assertNull(ProfileModel.notice(plan(localActive = true, localDays = 8), 7))
  }

  @Test
  fun `نامِ نقش`() {
    assertEquals("صاحب دکان", ProfileModel.roleLabel("owner"))
    assertEquals("مدیر", ProfileModel.roleLabel(" Manager "))
    assertEquals("شاگرد", ProfileModel.roleLabel("staff"))
    assertEquals("صاحب دکان", ProfileModel.roleLabel(""))
  }
}
