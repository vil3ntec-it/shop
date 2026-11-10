package ir.vil3ntec.tohid.ui.v3.shell

import ir.vil3ntec.tohid.core.calendar.SolarHijri
import ir.vil3ntec.tohid.core.calendar.WeekRules
import ir.vil3ntec.tohid.data.SafeBook
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** کادرِ بالای خانه (HOME-V3): بازه‌ها، «همین حالا»، فروشِ واقعی، ماندهٔ گاوصندوق و آمارِ امروز */
class HomeHeroTest {
  //  ۱۴۰۵/۰۸/۱۲، سه‌شنبه
  private val today = "2026-11-03"
  private val dayStart = 1_793_000_000_000L
  private val hour = HomeHero.HOUR_MS
  private val letters = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

  private fun sale(id: String, date: String, total: Double, at: Long = 0L, status: String = "completed") =
    Sale(id = id, finalTotal = total, total = total, date = date, createdAt = at, status = status)

  @Test
  fun `هفته از شنبه تا جمعه است`() {
    assertEquals(0, HomeHero.weekIndex("2026-10-31"))
    assertEquals(3, HomeHero.weekIndex(today))
    assertEquals(6, HomeHero.weekIndex("2026-11-06"))
    val (days, now) = HomeHero.days(HomeHero.Range.WEEK, today)
    assertEquals("2026-10-31", days.first())
    assertEquals("2026-11-06", days.last())
    assertEquals(3, now)
  }

  @Test
  fun `هفته از آغازِ هفتهٔ دکان است و حرفِ هر خانه همان روزِ خودش - LOC-03`() {
    //  یکشنبه: ۲۰۲۶-۱۱-۰۱ تا شنبه ۱۱-۰۷؛ سه‌شنبه خانهٔ ۲
    val sunday = WeekRules(weekStart = 7)
    assertEquals(0, HomeHero.weekIndex("2026-11-01", sunday))
    assertEquals(2, HomeHero.weekIndex(today, sunday))
    val (days, now) = HomeHero.days(HomeHero.Range.WEEK, today, sunday)
    assertEquals("2026-11-01", days.first())
    assertEquals("2026-11-07", days.last())
    assertEquals(2, now)
    assertEquals(listOf("ی", "د", "س", "چ", "پ", "ج", "ش"), HomeHero.ticks(HomeHero.Range.WEEK, 7, letters, sunday))
    //  دوشنبه: سه‌شنبه خانهٔ ۱
    val monday = WeekRules(weekStart = 1)
    val s = HomeHero.sales(listOf(sale("a", "2026-11-01", 70.0), sale("b", "2026-11-02", 30.0), sale("c", today, 20.0)), HomeHero.Range.WEEK, today, dayStart, dayStart, letters, monday)
    assertEquals(listOf(30.0, 20.0, 0.0, 0.0, 0.0, 0.0, 0.0), s.values)
    assertEquals(1, s.now)
    assertEquals(listOf("د", "س", "چ", "پ", "ج", "ش", "ی"), s.ticks)
    //  پیش‌فرض همان شنبه تا جمعه
    assertEquals(letters, HomeHero.ticks(HomeHero.Range.WEEK, 7, letters, WeekRules()))
    assertEquals(HomeHero.days(HomeHero.Range.WEEK, today), HomeHero.days(HomeHero.Range.WEEK, today, WeekRules()))
  }

  @Test
  fun `فروشِ هفته هر روز در خانهٔ خودش، لغوشده و بیرون از هفته نه`() {
    val s = HomeHero.sales(
      listOf(
        sale("a", "2026-10-30", 999.0), //  جمعهٔ هفتهٔ پیش
        sale("b", "2026-10-31", 100.0),
        sale("c", "2026-11-01", 200.0),
        sale("d", today, 250.0),
        sale("e", today, 50.0),
        sale("f", today, 777.0, status = "cancelled"),
        sale("g", "2026-11-05", 400.0), //  پس از امروز (ساعتِ دستگاهِ دیگر) — در «همین حالا» نیست
      ),
      HomeHero.Range.WEEK, today, dayStart, dayStart + 10 * hour, letters,
    )
    assertEquals(listOf(100.0, 200.0, 0.0, 300.0, 0.0, 0.0, 0.0), s.values)
    assertEquals(3, s.now)
    assertEquals(300.0, s.current, 0.0)
    assertEquals(letters, s.ticks)
    assertFalse(s.empty)
  }

  @Test
  fun `امروز جمعِ فروش تا هر ساعت است؛ فاکتورِ بی‌ساعت در همین حالا و نقطهٔ همین حالا همان فروشِ امروز`() {
    val now = dayStart + 10 * hour + 30 * 60_000
    val s = HomeHero.sales(
      listOf(
        sale("a", today, 50.0, dayStart + hour + 5 * 60_000),
        sale("b", today, 70.0, dayStart + 9 * hour),
        sale("c", today, 30.0),
        sale("y", "2026-11-02", 500.0, dayStart - hour),
      ),
      HomeHero.Range.TODAY, today, dayStart, now, letters,
    )
    assertEquals(24, s.values.size)
    assertEquals(10, s.now)
    assertEquals(0.0, s.values[0], 0.0)
    assertEquals(50.0, s.values[1], 0.0)
    assertEquals(50.0, s.values[8], 0.0)
    assertEquals(120.0, s.values[9], 0.0)
    assertEquals(150.0, s.values[10], 0.0)
    assertEquals(150.0, s.current, 0.0)
    assertEquals(0.0, s.values[11], 0.0) //  هنوز نیامده
    assertEquals(listOf("0", "", "", "", "4"), s.ticks.take(5))
  }

  @Test
  fun `ماه همان ماهِ خورشیدیِ جاری است`() {
    val j = SolarHijri.toSolar(today)!!
    val first = SolarHijri.fromSolar(j.year, j.month, 1)!!
    val s = HomeHero.sales(listOf(sale("a", first, 120.0), sale("b", today, 80.0)), HomeHero.Range.MONTH, today, dayStart, dayStart, letters)
    assertEquals(SolarHijri.monthLength(j.year, j.month), s.values.size)
    assertEquals(j.day - 1, s.now)
    assertEquals(120.0, s.values[0], 0.0)
    assertEquals(80.0, s.current, 0.0)
    assertEquals("1", s.ticks[0])
    assertEquals("5", s.ticks[4])
    assertEquals("", s.ticks[1])
  }

  @Test
  fun `بی فروش ⇒ خطِ صافِ آرام و نشانِ خالی`() {
    for (r in HomeHero.Range.entries) {
      val s = HomeHero.sales(emptyList(), r, today, dayStart, dayStart + 3 * hour, letters)
      assertTrue(r.name, s.empty)
      assertTrue(r.name, s.values.all { it == 0.0 })
    }
  }

  @Test
  fun `ماندهٔ گاوصندوق در پایانِ هر ساعت و ورود و خروجِ امروز`() {
    var n = 0
    val ids = { "01HERO${(++n).toString().padStart(20, '0')}" }
    var d = ShopData()
    d = (SafeBook.count(d, 100_000, null, "صاحب", dayStart - 30 * hour, ids) as SafeBook.Result.Ok).data
    d = (SafeBook.deposit(d, 20_000, "deposit", null, "صاحب", dayStart + 2 * hour + 1, ids) as SafeBook.Result.Ok).data
    d = (SafeBook.withdraw(d, 5_000, "owner_withdrawal", null, "صاحب", dayStart + 5 * hour + 1, ids) as SafeBook.Result.Ok).data
    val view = SafeBook.view(d)
    val s = HomeHero.safe(view.history, HomeHero.Range.TODAY, today, dayStart, dayStart + 8 * hour, letters)
    assertEquals(8, s.now)
    assertEquals(1000.0, s.values[1], 0.0)
    assertEquals(1200.0, s.values[2], 0.0)
    assertEquals(1200.0, s.values[4], 0.0)
    assertEquals(1150.0, s.values[5], 0.0)
    assertEquals(1150.0, s.current, 0.0)
    assertEquals(view.balanceMinor / 100.0, s.current, 0.0)
    assertEquals(0.0, s.values[9], 0.0) //  هنوز نیامده
    assertFalse(s.empty)
    //  هفته: دیروز پیش از ورود و خروجِ امروز
    val w = HomeHero.safe(view.history, HomeHero.Range.WEEK, today, dayStart, dayStart + 8 * hour, letters)
    assertEquals(1000.0, w.values[2], 0.0)
    assertEquals(1150.0, w.values[3], 0.0)
    //  شمارش پول جابه‌جا نمی‌کند ⇒ در ورود و خروجِ امروز نیست
    assertEquals(HomeHero.Flow(20_000, 5_000), HomeHero.safeToday(view.history, dayStart))
    assertTrue(HomeHero.safe(emptyList(), HomeHero.Range.WEEK, today, dayStart, dayStart, letters).empty)
  }

  @Test
  fun `ضررِ امروز همان سودِ منفیِ کادرِ پیشین است`() {
    assertEquals(50.0, HomeHero.loss(-50.0), 0.0)
    assertEquals(0.0, HomeHero.loss(20.0), 0.0)
    assertEquals(0.0, HomeHero.loss(0.0), 0.0)
  }
}
