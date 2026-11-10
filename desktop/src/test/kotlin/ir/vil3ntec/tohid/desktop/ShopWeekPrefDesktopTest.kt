package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.ShopDayChoice
import ir.vil3ntec.tohid.core.calendar.WeekRules
import ir.vil3ntec.tohid.data.ShopSettingsKeys
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

/**
 *  LOC-03 — هفتهٔ دکان (`ShopDayChoice.weekStart`/`closedDays`، `tohid-policy` ‹`week_start`، `closed_days`›) روی
 *  پیکربندیِ واقعیِ کامپیوتر: نبودن = شنبه و جمعه (رفتارِ پیشین)؛ «هر هفت روز باز» متنِ تهی است، نه پیش‌فرض؛
 *  مقدارِ نادرست هفته را جابه‌جا نمی‌کند؛ هر دو کلید «مالِ دکان» اند و با تنظیمِ دکان همگام می‌شوند.
 */
class ShopWeekPrefDesktopTest {

  private val prefs get() = DesktopContext.getSharedPreferences(ShopSettingsKeys.POLICY, 0)
  private val keys = listOf("week_start", "closed_days")
  private var saved: Map<String, Any?> = emptyMap()

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
      is Int -> e.putInt(k, v)
      is String -> e.putString(k, v)
    }
    e.commit()
    ShopDayChoice.load(DesktopContext)
  }

  @Test fun `تنظیم‌نشده همان شنبه و جمعه است`() {
    assertEquals(WeekRules(), ShopDayChoice.rules)
    assertFalse(prefs.contains("week_start"))
    assertFalse(prefs.contains("closed_days"))
  }

  @Test fun `آغاز و روزهای تعطیل می‌مانند و پس از بارِ دوباره همان‌اند`() {
    ShopDayChoice.setWeekStart(DesktopContext, 7)
    ShopDayChoice.setClosedDays(DesktopContext, setOf(5, 4))
    ShopDayChoice.load(DesktopContext)
    assertEquals(WeekRules(weekStart = 7, weekend = setOf(4, 5)), ShopDayChoice.rules)
    assertEquals(7, prefs.getInt("week_start", 0))
    assertEquals("4,5", prefs.getString("closed_days", null))
    //  هر هفت روز باز = متنِ تهی، نه برگشت به جمعه
    ShopDayChoice.setClosedDays(DesktopContext, emptySet())
    ShopDayChoice.load(DesktopContext)
    assertEquals(emptySet<Int>(), ShopDayChoice.closedDays)
    assertEquals("", prefs.getString("closed_days", null))
  }

  @Test fun `مقدارِ نادرستِ ذخیره‌شده هفته را جابه‌جا نمی‌کند`() {
    prefs.edit().putInt("week_start", 3).putString("closed_days", "1,2,3,4,5,6,7").commit()
    ShopDayChoice.load(DesktopContext)
    assertEquals(WeekRules(), ShopDayChoice.rules)
    ShopDayChoice.setWeekStart(DesktopContext, 9)
    assertEquals(6, ShopDayChoice.weekStart)
    //  نوعِ دیگر (رشته به جای عدد) ⇒ همان پیش‌فرض، بی پرتاب
    prefs.edit().putString("week_start", "7").commit()
    ShopDayChoice.load(DesktopContext)
    assertEquals(6, ShopDayChoice.weekStart)
  }

  @Test fun `هر دو کلید مالِ دکان اند و با تنظیمِ دکان همگام می‌شوند`() {
    for (k in keys) assertEquals(k, ShopSettingsKeys.Scope.SHOP, ShopSettingsKeys.scopeOf(ShopSettingsKeys.POLICY, k))
    assertEquals("i", ShopSettingsKeys.synced(ShopSettingsKeys.POLICY, "week_start")?.type)
    assertEquals("s", ShopSettingsKeys.synced(ShopSettingsKeys.POLICY, "closed_days")?.type)
  }
}
