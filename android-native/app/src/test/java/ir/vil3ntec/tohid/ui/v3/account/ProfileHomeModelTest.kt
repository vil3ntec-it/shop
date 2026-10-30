package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileHomeModel
import ir.vil3ntec.tohid.ui.v3.account.profile.ProfileHomeModel.Item
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «حساب من» (r4): اشتراک، نوارِ تیره، فهرست و درها — هر کدام مسیرِ دقیقِ خودش */
class ProfileHomeModelTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private fun plan(localActive: Boolean = false, localDays: Int = 0, localExpired: Boolean = false, serverActive: Boolean = false, serverDays: Int = 0, serverTrial: Boolean = false) =
    ProfileHomeModel.plan(localActive, localDays, localExpired, serverActive, serverDays, serverTrial, warnDays = 7)

  @Test
  fun `مجوزِ گوشی مقدم، وگرنه حرفِ سرور`() {
    val local = plan(localActive = true, localDays = 40, serverActive = true, serverDays = 3, serverTrial = true)
    assertTrue(local.active); assertEquals(40, local.days); assertFalse(local.trial); assertFalse(local.urgent)
    val server = plan(serverActive = true, serverDays = 5, serverTrial = true)
    assertTrue(server.trial); assertTrue(server.urgent)
    assertFalse(plan(serverActive = true, serverDays = 0).active)
  }

  @Test
  fun `فهرست — ترتیبِ r4 و مسیرِ دقیقِ هر ردیف`() {
    val full = ProfileHomeModel.menu(signedIn = true, canSettings = true)
    assertEquals(listOf(Item.EDIT, Item.DEVICES, Item.PLAN, Item.STATS, Item.SETTINGS, Item.SUPPORT, Item.SIGN_OUT), full.map { it.item })
    assertEquals(
      listOf("profile/edit", "devices", "subscription", "reports?tab=profit&range=month", "settings", "support", "profile/signout"),
      full.map { it.route },
    )
    assertEquals(listOf(Item.SIGN_OUT), full.filter { it.danger }.map { it.item })
    assertFalse(ProfileHomeModel.menu(true, canSettings = false).any { it.item == Item.SETTINGS })
    val guest = ProfileHomeModel.menu(signedIn = false, canSettings = true).map { it.item }
    assertFalse(Item.EDIT in guest); assertFalse(Item.SIGN_OUT in guest)
  }

  @Test
  fun `سه کادرِ آمار درِ بخشِ خودشان‌اند`() {
    val doors = ProfileHomeModel.doors(sales = 3, products = 40, debtors = 2)
    assertEquals(listOf("sales", "products", "debtors"), doors.map { it.route })
    assertEquals(listOf(3, 40, 2), doors.map { it.count })
  }

  @Test
  fun `نوارِ تیره — حالِ واقعی و مقصدِ دکمه`() {
    val guest = ProfileHomeModel.banner(false, null, 7)
    assertEquals(ProfileHomeModel.Action.SIGN_IN, guest.action); assertEquals("auth?mode=signin", guest.action.route)
    val active = ProfileHomeModel.banner(true, plan(localActive = true, localDays = 12), 7)
    assertEquals("اشتراک: 12 روزِ دیگر", active.title); assertEquals("subscription/plans", active.action.route)
    assertTrue(ProfileHomeModel.banner(true, plan(localActive = true, localDays = 3), 7).subtitle.contains("به‌زودی"))
    assertEquals("اشتراک تمام شده", ProfileHomeModel.banner(true, plan(localExpired = true), 7).title)
    assertEquals(ProfileHomeModel.Action.PLANS, ProfileHomeModel.banner(true, plan(), 7).action)
    I18n.source = { Lang.EN }
    assertEquals("Trial: 5 more days", ProfileHomeModel.banner(true, plan(serverActive = true, serverDays = 5, serverTrial = true), 7).title)
  }

  @Test
  fun `نام، خطِ دوم و قرصِ نقش`() {
    val owner = ProfileHomeModel.Account(true, "  احمد ", "a@b.af", "0700123456", "owner")
    assertEquals("احمد", ProfileHomeModel.displayName(owner))
    assertEquals("0700123456", ProfileHomeModel.contact(owner, "دکان"))
    assertEquals("a@b.af", ProfileHomeModel.contact(owner.copy(phone = ""), "دکان"))
    assertEquals("صاحب دکان", ProfileHomeModel.badge(owner))
    assertEquals("فروشنده", ProfileHomeModel.badge(owner.copy(role = "staff")))
    assertEquals("بی‌نام", ProfileHomeModel.displayName(owner.copy(name = " ")))
    val guest = ProfileHomeModel.Account(false, "x", role = "staff")
    assertEquals("مهمان", ProfileHomeModel.displayName(guest))
    assertNull(ProfileHomeModel.badge(guest))
    assertEquals("دکانِ نمونه", ProfileHomeModel.contact(guest, " دکانِ نمونه "))
  }
}
