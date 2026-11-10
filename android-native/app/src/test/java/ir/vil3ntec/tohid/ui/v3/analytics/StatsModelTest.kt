package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.core.calendar.ShopWeek
import ir.vil3ntec.tohid.core.calendar.WeekRules
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** صفحهٔ آمار: بازه‌های ستون‌ها، هفتهٔ دکان از شنبه، پخشِ ساعتی و محور */
class StatsModelTest {
  //  دوشنبه ۴ عقرب ۱۴۰۵؛ شنبهٔ همین هفته ۲۰۲۶-۱۰-۲۴
  private val today = "2026-10-26"

  private fun sale(id: String, date: String, total: Double, hour: Int, status: String = "completed") =
    Sale(id, date = date, total = total, finalTotal = total, status = status, createdAt = hour * 1000L + 1)

  @Test
  fun `ستون‌های روز هفت روزِ تا امروزند و جاری آخر`() {
    val b = StatsModel.buckets(StatsPeriod.DAY, today)
    assertEquals(7, b.size)
    assertEquals("2026-10-20" to "2026-10-20", b.first())
    assertEquals(today to today, b.last())
  }

  @Test
  fun `هفته از شنبه است و هفتهٔ جاری تا امروز می‌رسد`() {
    val b = StatsModel.buckets(StatsPeriod.WEEK, today)
    assertEquals(6, b.size)
    assertEquals("2026-10-24" to today, b.last())
    assertEquals("2026-10-17" to "2026-10-23", b[4])
  }

  @Test
  fun `ماه و سالِ خورشیدی به امروز ختم می‌شوند`() {
    val m = StatsModel.buckets(StatsPeriod.MONTH, today)
    assertEquals(6, m.size)
    assertEquals(today, m.last().second)
    assertEquals("2026-10-23", m.last().first) // ۱ عقرب ۱۴۰۵
    val y = StatsModel.buckets(StatsPeriod.YEAR, today)
    assertEquals(5, y.size)
    assertEquals(today, y.last().second)
    assertTrue(y.zipWithNext().all { (a, b) -> a.second < b.first })
  }

  @Test
  fun `دورهٔ پیوند و پیش‌فرضِ روز`() {
    assertEquals(StatsPeriod.MONTH, StatsPeriod.of("month"))
    assertEquals(StatsPeriod.DAY, StatsPeriod.of(null))
    assertEquals(StatsPeriod.DAY, StatsPeriod.of("decade"))
  }

  @Test
  fun `ستون‌ها فروشِ خالص و شمارِ فاکتورِ لغونشده را از موتور می‌گیرند`() {
    val d = ShopData(sales = listOf(sale("a", today, 100.0, 9), sale("b", today, 50.0, 10, status = "cancelled"), sale("c", "2026-10-25", 30.0, 9)))
    val bars = StatsModel.bars(d, StatsPeriod.DAY, today)
    assertEquals(100.0, bars.last().net, 0.0)
    assertEquals(1, bars.last().count)
    assertEquals(30.0, bars[5].net, 0.0)
  }

  @Test
  fun `روزهای فروشِ این هفته با روزهای آینده`() {
    val d = ShopData(sales = listOf(sale("a", "2026-10-24", 10.0, 9), sale("b", today, 10.0, 9), sale("x", "2026-10-25", 10.0, 9, status = "cancelled")))
    val s = StatsModel.streak(d, today)
    assertEquals(7, s.size)
    assertEquals("2026-10-24", s.first().date)
    assertEquals(listOf(true, false, true, false, false, false, false), s.map { it.sold })
    assertEquals(4, s.count { it.future })
  }

  @Test
  fun `جمعهٔ بی فروش تعطیلِ دکان است نه روزِ بی فروش - LOC-03`() {
    val friday = "2026-10-30"
    val quiet = StatsModel.streak(ShopData(sales = listOf(sale("a", "2026-10-24", 10.0, 9))), friday)
    assertEquals(listOf(false, false, false, false, false, false, true), quiet.map { it.closed })
    assertEquals("روزِ کاریِ گذشته: شنبه تا پنجشنبه", 6, StatsModel.pastWorkingDays(quiet))
    //  دکانی که جمعه هم فروخت: آن روز فروش دارد و در شمار است
    val open = StatsModel.streak(ShopData(sales = listOf(sale("a", friday, 10.0, 9))), friday)
    assertTrue(open.last().sold)
    assertFalse(open.last().closed)
    assertEquals(7, StatsModel.pastWorkingDays(open))
    //  جمعهٔ آینده هنوز «آینده» است، نه تعطیل
    assertFalse(StatsModel.streak(ShopData(), today).last().closed)
    assertEquals(3, StatsModel.pastWorkingDays(StatsModel.streak(ShopData(), today)))
  }

  @Test
  fun `هفتهٔ دکان از تنظیم - آغازِ یکشنبه یا دوشنبه و پیش‌فرض همان شنبه - LOC-03`() {
    //  تنظیم‌نشده همان قاعدهٔ پیشین است
    assertEquals(StatsModel.buckets(StatsPeriod.WEEK, today), StatsModel.buckets(StatsPeriod.WEEK, today, ShopWeek.rules(6, null)))
    val sunday = WeekRules(weekStart = 7)
    val b = StatsModel.buckets(StatsPeriod.WEEK, today, sunday)
    assertEquals("2026-10-25" to today, b.last())
    assertEquals("2026-10-18" to "2026-10-24", b[4])
    val monday = WeekRules(weekStart = 1)
    assertEquals(today to today, StatsModel.buckets(StatsPeriod.WEEK, today, monday).last())
    val s = StatsModel.streak(ShopData(), today, monday)
    assertEquals(listOf("2026-10-26", "2026-11-01"), listOf(s.first().date, s.last().date))
    assertEquals(6, s.count { it.future })
    //  روز و ماه و سال به آغازِ هفته کاری ندارند
    assertEquals(StatsModel.buckets(StatsPeriod.MONTH, today), StatsModel.buckets(StatsPeriod.MONTH, today, monday))
  }

  @Test
  fun `روزهای تعطیل از تنظیم - پنجشنبه و جمعه، یا هر هفت روز باز - LOC-03`() {
    val friday = "2026-10-30"
    val d = ShopData(sales = listOf(sale("a", "2026-10-24", 10.0, 9)))
    val twoOff = StatsModel.streak(d, friday, ShopWeek.rules(6, "4,5"))
    assertEquals(listOf(false, false, false, false, false, true, true), twoOff.map { it.closed })
    assertEquals(5, StatsModel.pastWorkingDays(twoOff))
    val open = StatsModel.streak(d, friday, ShopWeek.rules(6, ""))
    assertTrue(open.none { it.closed })
    assertEquals(7, StatsModel.pastWorkingDays(open))
    //  آغازِ دوشنبه با تعطیلِ شنبه و یکشنبه: جمعه روزِ کاری است و شنبه/یکشنبهٔ آینده «آینده»اند، نه تعطیل
    val weekend = StatsModel.streak(d, friday, WeekRules(weekStart = 1, weekend = setOf(6, 7)))
    assertEquals("2026-10-26", weekend.first().date)
    assertTrue(weekend.none { it.closed })
    assertEquals(5, StatsModel.pastWorkingDays(weekend))
  }

  @Test
  fun `پخشِ ساعتی دست‌کم شش خانه دارد و لغوشده را نمی‌شمارد`() {
    val d = ShopData(sales = listOf(sale("a", today, 10.0, 9), sale("b", today, 20.0, 9), sale("c", today, 5.0, 14), sale("x", today, 99.0, 20, status = "cancelled")))
    val bins = StatsModel.hours(d, today, today) { (it / 1000).toInt() }
    assertEquals((9..14).toList(), bins.map { it.hour })
    assertTrue(bins.all { it.span == 1 })
    val peak = StatsModel.peak(bins)!!
    assertEquals(9, peak.hour)
    assertEquals(2, peak.count)
    assertEquals(30.0, peak.net, 0.0)
  }

  @Test
  fun `بیش از دوازده ساعت خانه‌های دوساعته می‌شود و خالی یعنی بی خانه`() {
    val d = ShopData(sales = listOf(sale("a", today, 10.0, 6), sale("b", today, 10.0, 20)))
    val bins = StatsModel.hours(d, today, today) { (it / 1000).toInt() }
    assertEquals(listOf(6, 8, 10, 12, 14, 16, 18, 20), bins.map { it.hour })
    assertTrue(bins.all { it.span == 2 })
    assertEquals(1, bins.last().count)
    val early = StatsModel.hours(ShopData(sales = listOf(sale("a", today, 1.0, 0))), today, today) { (it / 1000).toInt() }
    assertEquals((0..5).toList(), early.map { it.hour })
    assertTrue(StatsModel.hours(ShopData(), today, today) { 0 }.isEmpty())
    assertNull(StatsModel.peak(emptyList()))
  }

  @Test
  fun `محورِ گرد و برچسبِ کوتاه`() {
    assertEquals(40_000.0, StatsModel.axisMax(36_500.0), 0.0)
    assertEquals(4.0, StatsModel.axisMax(0.0), 0.0)
    assertTrue(StatsModel.axisMax(12_345_678.0) >= 12_345_678.0)
    assertEquals("10K", StatsModel.compact(10_000.0))
    assertEquals("1.5M", StatsModel.compact(1_500_000.0))
    assertEquals("950", StatsModel.compact(950.0))
  }
}
