package ir.vil3ntec.tohid.core.storefront

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** سنجشِ فرمِ «ویترین و بازار» پیش از صف — همان قاعده‌های سرور */
class StorefrontRulesTest {

  private val R = StorefrontRules

  private fun draft(
    enabled: Boolean = true, title: String = "دکانِ احمد", slug: String = "ahmad-shop",
    days: Map<Int, String> = mapOf(6 to "08:00-12:00، 14:00-20:00"), closed: String = "",
    fee: String = "۵۰", min: String = "", delivery: Boolean = true, pickup: Boolean = true,
  ) = StorefrontRules.SettingsDraft(slug = slug, title = title, enabled = enabled, days = days, closed = closed,
    fee = fee, minOrder = min, delivery = delivery, pickup = pickup)

  @Test fun validDraftParsesEverything() {
    val c = R.checkSettings(draft(closed = "2026-11-01، 2026-10-20 2026-10-20"))
    assertTrue(c.ok, c.errors.toString())
    assertEquals(listOf(480 to 720, 840 to 1200), c.hours[6])
    assertEquals(emptyList(), c.hours[0])
    assertEquals(listOf("2026-10-20", "2026-11-01"), c.closedDates)
    assertEquals(5_000, c.feeMinor)
    assertEquals(0, c.minOrderMinor)
  }

  @Test fun errorsAreKeyedByField() {
    val c = R.checkSettings(draft(
      enabled = true, title = " ", slug = "API", days = mapOf(1 to "10:00-09:00", 2 to "8-9"),
      closed = "2027-02-29", fee = "abc", delivery = false, pickup = false,
    ))
    assertFalse(c.ok)
    assertEquals(setOf("slug", "title", "day1", "day2", "closed", "fee", "fulfillment"), c.errors.keys)
    assertEquals("برای روشن کردن، نامِ ویترین لازم است", c.errors["title"])
    assertEquals("این نشانی رزرو است", c.errors["slug"])
  }

  @Test fun disabledStorefrontMayHaveNoTitle() {
    assertTrue(R.checkSettings(draft(enabled = false, title = "")).ok)
  }

  @Test fun lengthLimitsMirrorServerTruncation() {
    val c = R.checkSettings(draft(title = "ا".repeat(61)).copy())
    assertTrue(c.errors["title"]!!.contains("۶۱"))
    assertTrue(R.checkSettings(draft(title = "ا".repeat(60))).ok)
    val d = draft().copy(tagline = "x".repeat(121), notice = "y".repeat(121), accent = "red", timezone = " ")
    assertEquals(setOf("tagline", "notice", "accent", "timezone"), R.checkSettings(d).errors.keys)
  }

  @Test fun overlappingAndTooManyRanges() {
    assertEquals("بازه‌ها روی هم افتاده‌اند", R.parseDay("08:00-12:00، 11:00-13:00").error)
    assertEquals("حداکثر ۴ بازه در روز", R.parseDay("1:00-2:00,3:00-4:00,5:00-6:00,7:00-8:00,9:00-10:00").error)
    assertEquals(listOf(0 to 1440), R.parseDay("۰۰:۰۰-۲۴:۰۰").ranges)
  }

  @Test fun calendarWithoutJdk() {
    assertTrue(R.validDate("2028-02-29"))
    assertTrue(R.validDate("2000-02-29"))
    assertFalse(R.validDate("1900-02-29"))
    assertFalse(R.validDate("2026-02-29"))
    assertFalse(R.validDate("2026-04-31"))
    assertTrue(R.validDate("2026-12-31"))
    assertFalse(R.validDate("2026-13-01"))
    assertFalse(R.validDate("2026-1-01"))
  }

  @Test fun marketFields() {
    val keys = listOf("grocery", "bakery")
    assertEquals("برای نمایش در بازارچه، دستهٔ دکان را انتخاب کنید", R.categoryError("", true, keys))
    assertNull(R.categoryError("", false, keys))
    assertEquals("این دسته در فهرستِ بازارچه نیست", R.categoryError("cars", false, keys))
    assertNull(R.categoryError("bakery", true, keys))
    assertEquals("کارتهٔ سه", R.cleanArea("  کارتهٔ\u0000 سه \n"))
    assertEquals("😀x", R.cleanArea("😀\u001Fx"))
    assertNull(R.areaError("ا".repeat(60)))
    assertEquals("ناحیه حداکثر ۶۰ نویسه است (الان ۶۱)", R.areaError("ا".repeat(61)))
    assertEquals(34.53, R.coarse(34.525))
    assertEquals(-0.01, R.coarse(-0.015))
  }
}
