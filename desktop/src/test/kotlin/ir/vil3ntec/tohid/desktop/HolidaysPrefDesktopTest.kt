package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.core.calendar.BusinessDay
import ir.vil3ntec.tohid.core.calendar.DayKind
import ir.vil3ntec.tohid.core.calendar.WeekRules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 *  LOC-04 — نسخهٔ محلیِ جدولِ تعطیلاتِ سرور (`tohid-holidays` ‹`table`، `fetched_at`›) روی پیکربندیِ واقعیِ کامپیوتر:
 *  بی نسخه همان قاعدهٔ امروز است؛ نسخهٔ ذخیره‌شده پس از `load` روزهایش را «تعطیلِ رسمی» می‌کند؛ نسخهٔ خراب هیچ
 *  تعطیلی نمی‌سازد و پرتاب نمی‌کند؛ پیش از گذشتنِ یک روز هیچ درخواستی نمی‌رود و نسخه دست نمی‌خورد.
 */
class HolidaysPrefDesktopTest {

  private val prefs get() = DesktopContext.getSharedPreferences("tohid-holidays", 0)
  private val keys = listOf("table", "fetched_at")
  private var saved: Map<String, Any?> = emptyMap()

  private val table = """{"version":2,"updatedAt":1,"holidays":[{"date":"2027-03-10","name":{"fa":"عید فطر"}},""" +
    """{"date":"2027-03-21","name":{"fa":"نوروز"}}]}"""

  @Before fun before() {
    saved = prefs.all.filterKeys { it in keys }
    val e = prefs.edit()
    keys.forEach { e.remove(it) }
    e.commit()
    ShopDayChoice.load(DesktopContext)
  }

  @After fun after() {
    val e = prefs.edit()
    keys.forEach { e.remove(it) }
    for ((k, v) in saved) when (v) {
      is Long -> e.putLong(k, v)
      is String -> e.putString(k, v)
    }
    e.commit()
    ShopDayChoice.load(DesktopContext)
  }

  @Test fun `بی نسخهٔ ذخیره‌شده هیچ تعطیلِ رسمی نیست`() {
    assertEquals(emptySet<String>(), ShopDayChoice.holidays)
    assertEquals(WeekRules(weekStart = ShopDayChoice.weekStart, weekend = ShopDayChoice.closedDays), ShopDayChoice.rules)
  }

  @Test fun `نسخهٔ ذخیره‌شده پس از بارِ دوباره روزهایش را تعطیلِ رسمی می‌کند`() {
    prefs.edit().putString("table", table).putLong("fetched_at", System.currentTimeMillis()).commit()
    ShopDayChoice.load(DesktopContext)
    assertEquals(setOf("2027-03-10", "2027-03-21"), ShopDayChoice.holidays)
    assertEquals(DayKind.HOLIDAY, BusinessDay.dayKind("2027-03-10", ShopDayChoice.rules))
    assertEquals(DayKind.WORK, BusinessDay.dayKind("2027-03-11", ShopDayChoice.rules))
  }

  @Test fun `نسخهٔ خراب هیچ تعطیلی نمی‌سازد و پرتاب نمی‌کند`() {
    prefs.edit().putString("table", "{oops").commit()
    ShopDayChoice.load(DesktopContext)
    assertEquals(emptySet<String>(), ShopDayChoice.holidays)
    //  نوعِ دیگر زیرِ همان کلید (عدد به جای متن) ⇒ همان، بی پرتاب
    prefs.edit().remove("table").putLong("table", 5L).commit()
    ShopDayChoice.load(DesktopContext)
    assertEquals(emptySet<String>(), ShopDayChoice.holidays)
  }

  @Test fun `پیش از گذشتنِ یک روز هیچ درخواستی نمی‌رود و نسخه دست نمی‌خورد`() {
    val now = System.currentTimeMillis()
    prefs.edit().putString("table", table).putLong("fetched_at", now - 60_000L).commit()
    ShopDayChoice.load(DesktopContext)
    assertFalse(runBlocking { ShopDayChoice.refreshHolidays(DesktopContext, now) })
    assertEquals(table, prefs.getString("table", null))
    assertEquals(now - 60_000L, prefs.getLong("fetched_at", 0L))
    assertEquals(setOf("2027-03-10", "2027-03-21"), ShopDayChoice.holidays)
  }
}
