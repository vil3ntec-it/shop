package ir.vil3ntec.tohid.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  `ShopDay` (commonMain) و `BusinessDay` (jvmMain، دوقلوی `license/calendar-core.js` با
 *  بردارهای `core/test-vectors/calendar.json`) یک قاعده‌اند: برای هر گزینه و هر دقیقهٔ
 *  شبانه‌روز، «تاریخِ تقویمی + جابه‌جاییِ ShopDay» همان `BusinessDay.businessDate` است —
 *  از جمله گذر از ماه و سال. `BusinessDay` هنوز در jvmMain است، پس این‌جا و نه در commonTest.
 */
class ShopDayParityTest {

  private fun two(n: Int) = n.toString().padStart(2, '0')

  @Test
  fun shiftMatchesBusinessDateEveryMinute() {
    for (date in listOf("2026-10-14", "2027-01-01", "2026-03-01", "2024-03-01", "2026-03-21")) {
      for (h in ShopDay.CHOICES) {
        for (m in 0 until 24 * 60) {
          val local = date + "T" + two(m / 60) + ":" + two(m % 60)
          val expected = BusinessDay.businessDate(local, ShopDay.dayStartMinutes(h))
          assertEquals(expected, CivilDays.addDays(date, ShopDay.dayShift(m, h)), "$local با پایانِ $h")
        }
      }
    }
  }

  @Test
  fun shiftMatchesBusinessDateAtFromEpoch() {
    //  ۲۰۲۶-۱۰-۱۳ ۲۰:۳۰ ِ UTC = ۱:۰۰ ِ بامدادِ ۱۴ اکتبر در کابل (+۴:۳۰)
    val at = 1_791_925_200_000L
    for (h in ShopDay.CHOICES) {
      val minute = 60
      val expected = BusinessDay.businessDateAt(at, 270, ShopDay.dayStartMinutes(h))
      assertEquals(expected, CivilDays.addDays("2026-10-14", ShopDay.dayShift(minute, h)), "پایانِ $h")
    }
  }
}
