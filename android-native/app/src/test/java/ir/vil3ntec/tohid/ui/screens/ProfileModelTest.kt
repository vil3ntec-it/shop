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
  fun `نامِ نقش — بندِ B گوشی نسخهٔ ۳`() {
    assertEquals("صاحب دکان", ProfileModel.roleLabel("owner"))
    assertEquals("مدیر", ProfileModel.roleLabel(" Manager "))
    assertEquals("فروشنده", ProfileModel.roleLabel("staff"))
    assertEquals("صاحب دکان", ProfileModel.roleLabel(""))
  }

  /* ------------------------- کارتِ r4 ------------------------- */

  private val owner = ProfileModel.Account(signedIn = true, name = "  احمد ولی‌زاده ", email = "a@b.af", phone = "0700123456", role = "owner")
  private val guest = ProfileModel.Account(signedIn = false, name = "نامِ کهنهٔ روی گوشی", email = "x@y.af", phone = "0799", role = "staff")

  @Test
  fun `واردشده — نام، شماره پیش از ایمیل، نشانِ نقش`() {
    assertEquals("احمد ولی‌زاده", ProfileModel.displayName(owner))
    assertEquals("0700123456", ProfileModel.contact(owner, "دکان"))
    assertEquals("a@b.af", ProfileModel.contact(owner.copy(phone = " "), "دکان"))
    assertEquals("صاحب دکان", ProfileModel.badge(owner))
    assertEquals("فروشنده", ProfileModel.badge(owner.copy(role = "staff")))
    assertEquals("بی‌نام", ProfileModel.displayName(owner.copy(name = "  ")))
  }

  @Test
  fun `مهمان — «مهمان»، بی نشان، خطِ دوم نامِ دکان`() {
    assertEquals(ProfileModel.GUEST, ProfileModel.displayName(guest))
    assertNull(ProfileModel.badge(guest))
    assertEquals("دکانِ نمونه", ProfileModel.contact(guest, " دکانِ نمونه "))
    assertEquals("", ProfileModel.contact(guest, ""))
  }

  @Test
  fun `نوارِ تیره همیشه حالِ واقعیِ اشتراک را می‌گوید`() {
    val out = ProfileModel.banner(signedIn = false, p = null, warnDays = 7)
    assertEquals("وارد حساب نشده‌اید", out.title); assertEquals("ورود", out.button); assertEquals(ProfileModel.Action.SIGN_IN, out.action)
    val active = ProfileModel.banner(true, plan(localActive = true, localDays = 12), 7)
    assertEquals("اشتراک: 12 روزِ دیگر", active.title); assertEquals("تمدید", active.button); assertEquals(ProfileModel.Action.RENEW, active.action)
    assertEquals("همهٔ بخش‌ها باز است", active.subtitle)
    val soon = ProfileModel.banner(true, plan(localActive = true, localDays = 3), 7)
    assertTrue(soon.subtitle.contains("به‌زودی"))
    val last = ProfileModel.banner(true, plan(localActive = true, localDays = 0), 7)
    assertEquals("امروز آخرین روزِ اشتراک", last.title); assertEquals(ProfileModel.Action.RENEW, last.action)
  }

  @Test
  fun `نوار — تمام‌شده، آزمایشی، بی‌اشتراک، و رقمِ برنامه`() {
    val expired = ProfileModel.banner(true, plan(localExpired = true), 7)
    assertEquals("اشتراک تمام شده", expired.title); assertEquals(ProfileModel.Action.RENEW, expired.action)
    val trial = ProfileModel.banner(true, plan(serverActive = true, serverDays = 5, serverTrial = true), 7) { "#$it" }
    assertEquals("آزمایشی: #5 روزِ دیگر", trial.title); assertEquals("پلن‌ها", trial.button); assertEquals(ProfileModel.Action.PLANS, trial.action)
    val none = ProfileModel.banner(true, plan(), 7)
    assertEquals("بدونِ اشتراک", none.title); assertEquals(ProfileModel.Action.PLANS, none.action)
  }

  @Test
  fun `فهرستِ r4 — ترتیب، مسیرها، و آنچه به حساب و نقش بند است`() {
    val full = ProfileModel.menu(signedIn = true, canSettings = true)
    assertEquals(
      listOf("ویرایش حساب و دکان", "دستگاه‌ها", "اشتراک و پرداخت", "آمار", "تنظیمات", "پشتیبانی", "خروج از حساب"),
      full.map { it.label },
    )
    assertEquals(listOf(null, "devices", "myplan", "reports", "settings", "support", null), full.map { it.route })
    assertEquals(listOf(ProfileModel.SIGN_OUT), full.filter { it.danger }.map { it.id })
    //  شاگرد: بی «تنظیمات»؛ مهمان: بی ویرایش و بی خروج
    assertFalse(ProfileModel.menu(true, canSettings = false).any { it.route == "settings" })
    val out = ProfileModel.menu(signedIn = false, canSettings = true).map { it.id }
    assertFalse(ProfileModel.EDIT in out); assertFalse(ProfileModel.SIGN_OUT in out)
    assertEquals(listOf("devices", "plan", "stats", "settings", "support"), out)
  }
}
