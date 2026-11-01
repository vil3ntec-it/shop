package ir.vil3ntec.tohid.core.report

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  RPT-08 — نقشهٔ حرارتیِ ساعت × روز: ساعتِ دیواریِ کابل، روزِ کاری که از نیمه‌شب
 *  می‌گذرد، ترتیبِ هفته از شنبه، برگشتی در خانهٔ خودش، و شدتِ رنگ با عددِ صحیح.
 */
class SalesHeatmapTest {

  private val dayMs = 86_400_000L
  private val kabul = 270

  /** ۲۰۲۴-۰۱-۰۶ شنبه است (۲۰۲۴-۰۱-۰۱ دوشنبه، روزِ ۱۹۷۲۳ از ۱۹۷۰) */
  private val saturday = 19_728L

  /** زمانِ مطلقِ «روزِ epochDay، ساعتِ دیواریِ h:m به وقتِ کابل» */
  private fun kabulAt(epochDay: Long, h: Int, m: Int = 0): Long = epochDay * dayMs + (h * 60 + m - kabul) * 60_000L

  @Test
  fun weekdayOfEpochDays() {
    assertEquals(4, SalesHeatmap.weekdayOf(0))       // ۱۹۷۰-۰۱-۰۱ پنجشنبه
    assertEquals(3, SalesHeatmap.weekdayOf(-1))      // ۱۹۶۹-۱۲-۳۱ چهارشنبه
    assertEquals(6, SalesHeatmap.weekdayOf(saturday))
    assertEquals(1, SalesHeatmap.weekdayOf(19_723))  // ۲۰۲۴-۰۱-۰۱ دوشنبه
  }

  @Test
  fun kabulWallClockAndSaturdayFirst() {
    val map = SalesHeatmap.build(
      listOf(
        HeatSale(kabulAt(saturday, 10, 15), 12_050L),
        HeatSale(kabulAt(saturday, 10, 59), 5_000L),
        HeatSale(kabulAt(saturday + 6, 23, 59), 100L),   // جمعه، آخرین ردیف
        HeatSale(kabulAt(saturday + 1, 0, 0), 7L),        // یکشنبه ۰۰:۰۰
      ),
      offsetMinutes = kabul,
    )
    assertEquals(listOf(6, 7, 1, 2, 3, 4, 5), map.weekdays)
    assertEquals(HeatCell(2, 17_050L), map.cell(0, 10))
    assertEquals(HeatCell(1, 100L), map.cell(6, 23))
    assertEquals(HeatCell(1, 7L), map.cell(1, 0))
    assertEquals(HeatCell.EMPTY, map.cell(0, 9))
    //  همان رویداد بی جابه‌جاییِ کابل ساعتِ دیگری است (۰۵:۴۵ UTC)
    assertEquals(HeatCell(2, 17_050L), SalesHeatmap.build(listOf(HeatSale(kabulAt(saturday, 10, 15), 12_050L), HeatSale(kabulAt(saturday, 10, 20), 5_000L)), 0).cell(0, 5))
    assertEquals(HeatCell(4, 17_157L), map.total)
    assertEquals(HeatCell(2, 17_050L), map.rowTotals[0])
    assertEquals(HeatCell(2, 17_050L), map.hourTotals[10])
    assertEquals(0 to 10, map.peak)
  }

  @Test
  fun businessDayPastMidnightStaysOnYesterdayRow() {
    val late = HeatSale(kabulAt(saturday + 1, 1, 30), 900L)   // یکشنبه ۰۱:۳۰
    val atMidnight = SalesHeatmap.build(listOf(late), kabul, dayStartMinutes = 0)
    assertEquals(HeatCell(1, 900L), atMidnight.cell(1, 1), "پایانِ روز نیمه‌شب ⇒ یکشنبه")
    val twoAm = SalesHeatmap.build(listOf(late), kabul, dayStartMinutes = 120)
    assertEquals(HeatCell(1, 900L), twoAm.cell(0, 1), "روز تا ۲ بامداد ⇒ ردیفِ شنبه، ستونِ ساعتِ ۱")
    assertEquals(HeatCell.EMPTY, twoAm.cell(1, 1))
    //  درست سرِ پایانِ روز مالِ روزِ تازه است
    assertEquals(HeatCell(1, 1L), SalesHeatmap.build(listOf(HeatSale(kabulAt(saturday + 1, 2, 0), 1L)), kabul, 120).cell(1, 2))
  }

  @Test
  fun refundsSubtractInTheirOwnCellWithoutCounting() {
    val map = SalesHeatmap.build(
      listOf(
        HeatSale(kabulAt(saturday, 9), 10_000L),
        HeatSale(kabulAt(saturday + 2, 14), -4_000L, isSale = false),
      ),
      kabul,
    )
    assertEquals(HeatCell(1, 10_000L), map.cell(0, 9), "خانهٔ فاکتور دست نمی‌خورد")
    assertEquals(HeatCell(0, -4_000L), map.cell(2, 14), "برگشتی در روز و ساعتِ خودش")
    assertEquals(HeatCell(1, 6_000L), map.total)
    assertEquals(0, map.level(2, 14), "خانهٔ بی فروش رنگ ندارد")
  }

  @Test
  fun levelsAreIntegerCeilingOfMax() {
    val sales = mutableListOf<HeatSale>()
    repeat(8) { sales += HeatSale(kabulAt(saturday, 12), 1L) }
    repeat(1) { sales += HeatSale(kabulAt(saturday, 13), 1L) }
    repeat(2) { sales += HeatSale(kabulAt(saturday, 14), 1L) }
    repeat(4) { sales += HeatSale(kabulAt(saturday, 15), 1L) }
    repeat(5) { sales += HeatSale(kabulAt(saturday, 16), 1L) }
    val map = SalesHeatmap.build(sales, kabul)
    assertEquals(8, map.maxCount)
    assertEquals(4, map.level(0, 12))
    assertEquals(1, map.level(0, 13), "کم‌ترین خانهٔ پُر دست‌کم ۱")
    assertEquals(1, map.level(0, 14))
    assertEquals(2, map.level(0, 15))
    assertEquals(3, map.level(0, 16))
    assertEquals(0, map.level(0, 17))
    assertEquals(0, map.level(9, 99), "بیرون از نقشه ⇒ ۰")
    assertEquals(0 to 12, map.peak)
  }

  @Test
  fun emptyAndBadSettings() {
    val empty = SalesHeatmap.build(emptyList(), kabul)
    assertEquals(0, empty.maxCount)
    assertNull(empty.peak)
    assertEquals(HeatCell.EMPTY, empty.total)
    assertEquals(7, empty.cells.size)
    assertEquals(24, empty.cells[0].size)
    //  تنظیمِ نامعتبر ⇒ پیش‌فرضِ امن (بی جابه‌جایی، نیمه‌شب، شنبه)
    val odd = SalesHeatmap.build(listOf(HeatSale(saturday * dayMs + 3 * 3_600_000L, 1L)), offsetMinutes = 10_000, dayStartMinutes = -5, weekStart = 0)
    assertEquals(listOf(6, 7, 1, 2, 3, 4, 5), odd.weekdays)
    assertEquals(HeatCell(1, 1L), odd.cell(0, 3))
    //  هفته از دوشنبه
    val monday = SalesHeatmap.build(listOf(HeatSale(kabulAt(saturday, 8), 1L)), kabul, weekStart = 1)
    assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), monday.weekdays)
    assertEquals(HeatCell(1, 1L), monday.cell(5, 8))
    //  پیش از ۱۹۷۰ هم درست (تقسیمِ کف، نه بریدن)
    val before = SalesHeatmap.build(listOf(HeatSale(-1L, 1L)), 0)
    assertEquals(HeatCell(1, 1L), before.cell((3 - 6 + 7) % 7, 23), "۱۹۶۹-۱۲-۳۱ ۲۳:۵۹ چهارشنبه")
  }
}
