package ir.vil3ntec.tohid

import ir.vil3ntec.tohid.core.calendar.ShopDay
import ir.vil3ntec.tohid.data.ReportBridge
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

/**
 *  LOC-03: «پایانِ روزِ کاری» تاریخِ ثبت (`todayIso`: فروش، برگشت، شیفت، مصارف…) و روزِ
 *  فاکتور در گزارشِ تازه (`ReportBridge.deviceClock`) را با هم جابه‌جا می‌کند، تا فروشی
 *  که ۱:۳۰ ثبت شد در گزارش هم مالِ همان روزِ کاری باشد. پیش‌فرضِ نیمه‌شب هیچ‌کدام را عوض نمی‌کند.
 */
class ShopDayWiringTest {

  private val kabul: TimeZone = TimeZone.getTimeZone("Asia/Kabul")

  private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Calendar =
    GregorianCalendar(kabul).apply { clear(); set(y, m - 1, d, h, min) }

  @Test
  fun `نیمه‌شب همان تاریخِ تقویمی است`() {
    for (h in 0..23) for (min in listOf(0, 30, 59)) {
      assertEquals("$h:$min", "2026-10-14", todayIsoAt(at(2026, 10, 14, h, min), ShopDay.OFF))
    }
  }

  @Test
  fun `پیش از پایانِ روزِ کاری هنوز دیروز است`() {
    assertEquals("2026-10-13", todayIsoAt(at(2026, 10, 14, 0, 0), 2))
    assertEquals("2026-10-13", todayIsoAt(at(2026, 10, 14, 1, 30), 2))
    assertEquals("2026-10-13", todayIsoAt(at(2026, 10, 14, 1, 59), 2))
    assertEquals("2026-10-14", todayIsoAt(at(2026, 10, 14, 2, 0), 2))
    assertEquals("2026-10-14", todayIsoAt(at(2026, 10, 14, 23, 59), 2))
  }

  @Test
  fun `گذر از ماه و سال و روزِ کبیسه`() {
    assertEquals("2026-12-31", todayIsoAt(at(2027, 1, 1, 0, 30), 1))
    assertEquals("2024-02-29", todayIsoAt(at(2024, 3, 1, 3, 59), 4))
    //  شبِ نوروزِ ۱۴۰۵: ۰:۱۰ ِ اولِ حمل هنوز حوتِ ۱۴۰۴ است
    assertEquals("2026-03-20", todayIsoAt(at(2026, 3, 21, 0, 10), 1))
  }

  @Test
  fun `لحظهٔ داده‌شده دست نمی‌خورد`() {
    val c = at(2026, 10, 14, 1, 30)
    assertEquals("2026-10-13", todayIsoAt(c, 2))
    assertEquals(14, c.get(Calendar.DAY_OF_MONTH))
  }

  @Test
  fun `عددِ ناشناخته مثلِ نیمه‌شب است`() {
    for (bad in listOf(-1, 5, 7, 24)) assertEquals("$bad", "2026-10-14", todayIsoAt(at(2026, 10, 14, 1, 30), bad))
  }

  @Test
  fun `روزِ فاکتور در گزارش همان روزِ ثبت است`() {
    val prev = TimeZone.getDefault()
    TimeZone.setDefault(kabul)
    try {
      for (h in ShopDay.CHOICES) for (hour in 0..23) {
        val c = at(2026, 10, 14, hour, 15)
        val ms = c.timeInMillis
        assertEquals("پایانِ $h ساعتِ $hour", todayIsoAt(c, h), ReportBridge.deviceClock(ms, h).date(ms))
      }
      val ms = at(2026, 10, 14, 1, 30).timeInMillis
      assertEquals("2026-10-14", ReportBridge.deviceClock(ms, ShopDay.OFF).date(ms))
      assertEquals("2026-10-13", ReportBridge.deviceClock(ms, 2).date(ms))
      //  ساعتِ دیواری همان می‌ماند؛ فقط روز جابه‌جا می‌شود
      assertEquals(1, ReportBridge.deviceClock(ms, 2).hour(ms))
    } finally {
      TimeZone.setDefault(prev)
    }
  }
}
