package ir.vil3ntec.tohid.core.calendar

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  LOC-04: جدولِ تعطیلاتِ رسمیِ سرور در برنامه. بی جدول (هنوز نگرفته، بی‌شبکه، پاسخِ خراب) همان `WeekRules` ِ
 *  امروز است؛ با جدول فقط همان روزها «تعطیلِ رسمی» می‌شوند. بردارهای مشترک با سرور در `HolidayVectorsTest` (JVM).
 */
class HolidaysTest {

  private val doc = """{"version":3,"updatedAt":1,"holidays":[{"date":"2027-03-10","name":{"fa":"عید فطر"}},""" +
    """{"date":"2027-03-21","name":{"fa":"نوروز"}}]}"""

  @Test
  fun noTableIsExactlyTodaysRules() {
    assertEquals(HolidayTable.EMPTY, Holidays.parse(null))
    assertEquals(HolidayTable.EMPTY, Holidays.parse(""))
    assertEquals(HolidayTable.EMPTY, Holidays.parse("{oops"))
    assertEquals(WeekRules(), ShopWeek.rules(ShopWeek.DEFAULT_START, null, Holidays.parse(null).dates))
    assertEquals(DayKind.WORK, BusinessDay.dayKind("2027-03-10", WeekRules(holidays = Holidays.parse(null).dates)))
  }

  @Test
  fun tableMarksOnlyItsDays() {
    val t = Holidays.parse(doc)
    assertEquals(3L, t.version)
    assertEquals(listOf("2027-03-10", "2027-03-21"), t.dates.toList())
    val rules = ShopWeek.rules(ShopWeek.DEFAULT_START, null, t.dates)
    assertEquals(DayKind.HOLIDAY, BusinessDay.dayKind("2027-03-10", rules))
    assertEquals(DayKind.HOLIDAY, BusinessDay.dayKind("2027-03-21", rules))
    assertEquals(DayKind.WORK, BusinessDay.dayKind("2027-03-11", rules))
    //  جمعهٔ تعطیلِ هفتگی همان «آخرِ هفته» می‌ماند؛ تعطیلِ رسمی روی جمعه «تعطیلِ رسمی» است
    assertEquals(DayKind.WEEKEND, BusinessDay.dayKind("2027-03-12", rules))
    assertFalse(BusinessDay.isWorkingDay("2027-03-10", rules))
    //  هفتهٔ دکان دست نمی‌خورد
    assertEquals("2027-03-06", BusinessDay.weekStartOf("2027-03-10", rules))
  }

  @Test
  fun malformedResponseKeepsCache() {
    //  `of` ⇒ null یعنی «نسخهٔ ذخیره‌شده بماند»، نه «جدول را پاک کن»
    assertNull(Holidays.of(JsonObject(emptyMap())))
    val noList = Holidays.parse("""{"version":4,"holidays":{}}""")
    assertEquals(HolidayTable.EMPTY, noList)
    assertEquals(HolidayTable(0L, emptySet()), Holidays.parse("""{"version":0,"holidays":[]}"""))
  }

  @Test
  fun badRowsAreDroppedNotFatal() {
    val t = Holidays.parse(
      """{"version":7,"holidays":[null,{"date":"2027-02-30"},{"date":"۲۰۲۷-۰۳-۲۲"},{"date":"2027-03-21"},""" +
        """{"date":"2027-03-21"},{"date":1},{"date":"1405-01-01"},{"date":"2199-12-31"},{"date":"2200-01-01"}]}""",
    )
    assertEquals(7L, t.version)
    assertEquals(listOf("2027-03-21", "2199-12-31"), t.dates.toList())
  }

  @Test
  fun rowsBeyondTheServerCapAreIgnored() {
    val rows = (0 until 600).joinToString(",") { """{"date":"${CivilDays.addDays("2030-01-01", it)}"}""" }
    val t = Holidays.parse("""{"version":1,"holidays":[$rows]}""")
    assertEquals(Holidays.MAX_ENTRIES, t.dates.size)
    assertTrue("2030-01-01" in t.dates)
    assertFalse(CivilDays.addDays("2030-01-01", Holidays.MAX_ENTRIES)!! in t.dates)
  }

  @Test
  fun storedCopyIsSmallAndRoundTrips() {
    val t = Holidays.parse(doc)
    val text = Holidays.encode(t)
    assertEquals("""{"version":3,"holidays":[{"date":"2027-03-10"},{"date":"2027-03-21"}]}""", text)
    assertEquals(t, Holidays.parse(text))
    assertEquals(HolidayTable.EMPTY, Holidays.parse(Holidays.encode(HolidayTable.EMPTY)))
    //  جدولِ دست‌ساخته با روزِ نادرست: روزِ نادرست نوشته نمی‌شود و متن خراب نمی‌شود
    assertEquals(HolidayTable(1L, setOf("2027-03-21")), Holidays.parse(Holidays.encode(HolidayTable(1L, setOf("2027-03-21", "x\"]}")))))
  }

  @Test
  fun refreshOncePerDayAndAfterClockGoesBack() {
    val t0 = 1_767_225_600_000L
    assertTrue(Holidays.due(0L, t0))
    assertFalse(Holidays.due(t0, t0 + Holidays.CHECK_MS))
    assertFalse(Holidays.due(t0, t0 + Holidays.REFRESH_MS - 1))
    assertTrue(Holidays.due(t0, t0 + Holidays.REFRESH_MS))
    assertTrue(Holidays.due(t0, t0 - 1))
  }
}
