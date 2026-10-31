package ir.vil3ntec.tohid.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  LOC-03: پایانِ روزِ کاری. پیش‌فرض نیمه‌شب است و هیچ تاریخی جابه‌جا نمی‌شود؛
 *  با «۲ بامداد» فروشِ ۱:۵۹ مالِ دیروز است و فروشِ ۲:۰۰ مالِ امروز.
 */
class ShopDayTest {

  @Test
  fun midnightNeverShiftsAnyMinute() {
    for (m in 0 until 24 * 60) assertEquals(0, ShopDay.dayShift(m, ShopDay.OFF), "دقیقهٔ $m")
  }

  @Test
  fun beforeTheEndIsStillYesterday() {
    assertEquals(-1, ShopDay.dayShift(0, 2))
    assertEquals(-1, ShopDay.dayShift(90, 2))
    assertEquals(-1, ShopDay.dayShift(119, 2))
    assertEquals(-1, ShopDay.dayShift(0, 1))
    assertEquals(-1, ShopDay.dayShift(59, 1))
    assertEquals(-1, ShopDay.dayShift(239, 4))
  }

  @Test
  fun theEndHourItselfIsToday() {
    assertEquals(0, ShopDay.dayShift(120, 2))
    assertEquals(0, ShopDay.dayShift(60, 1))
    assertEquals(0, ShopDay.dayShift(240, 4))
    assertEquals(0, ShopDay.dayShift(23 * 60 + 59, 4))
    assertEquals(0, ShopDay.dayShift(12 * 60, 3))
  }

  @Test
  fun everyChoiceShiftsExactlyItsOwnMinutes() {
    for (h in ShopDay.CHOICES) {
      val shifted = (0 until 24 * 60).count { ShopDay.dayShift(it, h) == -1 }
      assertEquals(h * 60, shifted, "پایانِ ساعتِ $h")
    }
  }

  @Test
  fun minutesOutsideTheDayAreNotShifted() {
    assertEquals(0, ShopDay.dayShift(-1, 4))
    assertEquals(0, ShopDay.dayShift(-60, 2))
    assertEquals(0, ShopDay.dayShift(24 * 60, 4))
  }

  @Test
  fun unknownStoredHoursFallBackToMidnight() {
    assertEquals(listOf(0, 1, 2, 3, 4), ShopDay.CHOICES)
    for (h in ShopDay.CHOICES) assertEquals(h, ShopDay.clamp(h))
    for (bad in listOf(-1, 5, 6, 12, 24, Int.MAX_VALUE, Int.MIN_VALUE)) {
      assertEquals(ShopDay.OFF, ShopDay.clamp(bad), "ساعتِ $bad")
      assertEquals(0, ShopDay.dayStartMinutes(bad), "ساعتِ $bad")
      assertEquals(0, ShopDay.dayShift(30, bad), "ساعتِ $bad")
    }
  }

  @Test
  fun dayStartIsInMinutes() {
    assertEquals(0, ShopDay.dayStartMinutes(0))
    assertEquals(60, ShopDay.dayStartMinutes(1))
    assertEquals(120, ShopDay.dayStartMinutes(2))
    assertEquals(240, ShopDay.dayStartMinutes(4))
  }

  @Test
  fun labelsAreClockTimes() {
    assertEquals(listOf("00:00", "01:00", "02:00", "03:00", "04:00"), ShopDay.CHOICES.map(ShopDay::label))
    assertEquals("00:00", ShopDay.label(9))
  }
}
