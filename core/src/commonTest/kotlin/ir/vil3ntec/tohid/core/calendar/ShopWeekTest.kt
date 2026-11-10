package ir.vil3ntec.tohid.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  LOC-03: تنظیمِ هفتهٔ دکان. تنظیم‌نشده همان `WeekRules()` است (شنبه، جمعه تعطیل)؛ مقدارِ ناشناخته هرگز هفته را
 *  جابه‌جا یا همه را «تعطیل» نمی‌کند. روزِ هفته به شمارِ ISO: ۱ دوشنبه … ۵ جمعه، ۶ شنبه، ۷ یکشنبه.
 */
class ShopWeekTest {

  @Test
  fun unsetIsExactlyTodaysRules() {
    assertEquals(WeekRules(), ShopWeek.rules(ShopWeek.DEFAULT_START, null))
    assertEquals(6, WeekRules().weekStart)
    assertEquals(setOf(5), ShopWeek.parseClosed(null))
  }

  @Test
  fun unknownStartFallsBackToSaturday() {
    for (bad in listOf(-1, 0, 2, 3, 4, 5, 8, 99)) assertEquals(6, ShopWeek.clampStart(bad), "آغازِ $bad")
    for (ok in ShopWeek.STARTS) assertEquals(ok, ShopWeek.clampStart(ok))
    assertEquals(WeekRules(weekStart = 6), ShopWeek.rules(3, null))
  }

  @Test
  fun closedDaysRoundTrip() {
    assertEquals("5", ShopWeek.encodeClosed(setOf(5)))
    assertEquals("4,5", ShopWeek.encodeClosed(setOf(5, 4)))
    assertEquals("", ShopWeek.encodeClosed(emptySet()))
    assertEquals("5", ShopWeek.encodeClosed(setOf(5, 0, 8)))
    for (s in listOf(setOf(5), setOf(4, 5), setOf(6, 7), setOf(1, 2, 3, 4, 5, 6), emptySet<Int>())) {
      assertEquals(s, ShopWeek.parseClosed(ShopWeek.encodeClosed(s)), "$s")
    }
  }

  @Test
  fun openEveryDayIsAnEmptyText() {
    assertEquals(emptySet<Int>(), ShopWeek.parseClosed(""))
    assertEquals(emptySet<Int>(), ShopWeek.parseClosed("  "))
    assertEquals(WeekRules(weekend = emptySet<Int>()), ShopWeek.rules(6, ""))
  }

  @Test
  fun brokenTextNeverClosesTheWholeWeek() {
    assertEquals(setOf(5), ShopWeek.parseClosed("abc"))
    assertEquals(setOf(5), ShopWeek.parseClosed("0,8,-1"))
    assertEquals(setOf(5), ShopWeek.parseClosed("1,2,3,4,5,6,7"))
    assertEquals(setOf(4), ShopWeek.parseClosed(" 4 , x , 9"))
    assertEquals(setOf(4, 5), ShopWeek.parseClosed("5,4,5"))
  }

  @Test
  fun toggleKeepsAtLeastOneWorkingDay() {
    assertEquals(setOf(4, 5), ShopWeek.toggle(setOf(5), 4))
    assertEquals(emptySet<Int>(), ShopWeek.toggle(setOf(5), 5))
    val six = setOf(1, 2, 3, 4, 5, 6)
    assertEquals(six, ShopWeek.toggle(six, 7), "آخرین روزِ کاری بسته نمی‌شود")
    assertEquals(setOf(1, 2, 3, 4, 5), ShopWeek.toggle(six, 6))
    assertEquals(setOf(5), ShopWeek.toggle(setOf(5), 0))
    assertEquals(setOf(5), ShopWeek.toggle(setOf(5), 8))
  }

  @Test
  fun orderStartsAtTheWeekStart() {
    assertEquals(listOf(6, 7, 1, 2, 3, 4, 5), ShopWeek.order(6))
    assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), ShopWeek.order(7))
    assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), ShopWeek.order(1))
    assertEquals(ShopWeek.order(6), ShopWeek.order(0))
    assertEquals(ShopWeek.order(6), ShopWeek.order(9))
  }

  @Test
  fun indexFollowsBusinessDayWeekStart() {
    //  ۲۰۲۶-۱۰-۱۷ شنبه، ۱۰-۱۶ جمعه، ۱۰-۱۸ یکشنبه، ۱۰-۱۲ دوشنبه
    assertEquals(0, ShopWeek.indexOf("2026-10-17"))
    assertEquals(6, ShopWeek.indexOf("2026-10-16"))
    assertEquals(1, ShopWeek.indexOf("2026-10-18"))
    assertEquals(0, ShopWeek.indexOf("2026-10-18", WeekRules(weekStart = 7)))
    assertEquals(6, ShopWeek.indexOf("2026-10-17", WeekRules(weekStart = 7)))
    assertEquals(0, ShopWeek.indexOf("2026-10-12", WeekRules(weekStart = 1)))
    assertEquals(6, ShopWeek.indexOf("2026-10-18", WeekRules(weekStart = 1)))
    assertNull(ShopWeek.indexOf("2026-02-30"))
    for (start in ShopWeek.STARTS) {
      val rules = WeekRules(weekStart = start)
      val first = BusinessDay.weekStartOf("2026-10-14", rules)!!
      for (i in 0 until 7) {
        val day = CivilDays.addDays(first, i)!!
        assertEquals(i, ShopWeek.indexOf(day, rules), "آغازِ $start، روزِ $day")
        assertEquals(ShopWeek.order(start)[i], CivilDays.isoWeekday(day), "آغازِ $start، روزِ $day")
      }
    }
  }

  @Test
  fun closedDaysDecideTheDayKind() {
    val thursdayToo = ShopWeek.rules(6, "4,5")
    assertEquals(DayKind.WEEKEND, BusinessDay.dayKind("2026-10-15", thursdayToo))
    assertEquals(DayKind.WEEKEND, BusinessDay.dayKind("2026-10-16", thursdayToo))
    assertEquals(DayKind.WORK, BusinessDay.dayKind("2026-10-17", thursdayToo))
    val openAllWeek = ShopWeek.rules(6, "")
    for (i in 0 until 7) assertEquals(DayKind.WORK, BusinessDay.dayKind(CivilDays.addDays("2026-10-17", i)!!, openAllWeek))
    val holiday = ShopWeek.rules(6, "", holidays = setOf("2026-10-15"))
    assertEquals(DayKind.HOLIDAY, BusinessDay.dayKind("2026-10-15", holiday))
  }
}
