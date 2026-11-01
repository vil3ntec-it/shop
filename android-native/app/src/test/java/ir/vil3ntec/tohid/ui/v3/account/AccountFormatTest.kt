package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountFormatTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  @Test
  fun `ثانیه و میلی‌ثانیه هر دو`() {
    assertEquals(1_700_000_000_000L, AccountFormat.millis(1_700_000_000L))
    assertEquals(1_700_000_000_000L, AccountFormat.millis(1_700_000_000_000L))
    assertEquals("—", AccountFormat.date(0))
  }

  @Test
  fun `مدتِ عضویت`() {
    val day = 86_400_000L
    val now = 1_800_000_000_000L
    assertEquals("امروز", AccountFormat.span(now - 1000, now))
    assertEquals("12 روز", AccountFormat.span(now - 12 * day, now))
    assertEquals("2 ماه", AccountFormat.span(now - 65 * day, now))
    assertEquals("1 سال", AccountFormat.span(now - 366 * day, now))
    assertEquals("1 سال و 2 ماه", AccountFormat.span(now - 430 * day, now))
    assertEquals("—", AccountFormat.span(0, now))
    I18n.source = { Lang.EN }
    assertEquals("12 days", AccountFormat.span(now - 12 * day, now))
  }

  @Test
  fun `نقش‌ها`() {
    assertEquals(AccountFormat.Role.MANAGER, AccountFormat.roleOf(" Manager "))
    assertEquals(AccountFormat.Role.OWNER, AccountFormat.roleOf(""))
    assertEquals("شاگرد", AccountFormat.teamRole("staff"))
    assertEquals("پیک", AccountFormat.teamRole("courier"))
    I18n.source = { Lang.EN }
    assertEquals("Shop owner", AccountFormat.teamRole("owner"))
  }

  @Test
  fun `پیوندها به مسیرِ دقیقِ بخش‌های دیگر`() {
    assertEquals("reports?tab=profit&range=month", AccountLinks.STATS)
    assertEquals("auth?mode=signin", AccountLinks.SIGN_IN)
    assertEquals("team/members/7/remove", AccountPaths.removeMember("7"))
    assertEquals("team/devices/d1/block", AccountPaths.blockDevice("d1"))
  }
}
