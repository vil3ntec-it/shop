package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RPT-08 — زبانهٔ «ساعت و روز»: دفتر ⇒ رویدادها ⇒ نقشه و جدول‌ها */
class HeatmapModelTest {

  //  ساعتِ کابل (UTC+4:30)
  private val sat1820 = 1_793_454_600_000L // شنبه ۲۰۲۶-۱۰-۳۱ ۱۸:۲۰
  private val sat1845 = 1_793_456_100_000L // شنبه ۱۸:۴۵
  private val sat1900 = 1_793_457_000_000L // شنبه ۱۹:۰۰
  private val sun0910 = 1_793_508_000_000L // یکشنبه ۲۰۲۶-۱۱-۰۱ ۰۹:۱۰
  private val mon1805 = 1_793_626_500_000L // دوشنبه ۲۰۲۶-۱۱-۰۲ ۱۸:۰۵

  private val d = ShopData(
    sales = listOf(
      Sale(id = "a", finalTotal = 100.0, createdAt = sat1820, date = "2026-10-31"),
      Sale(id = "b", finalTotal = 250.5, createdAt = sat1845, date = "2026-10-31"),
      Sale(id = "c", finalTotal = 40.0, createdAt = sun0910, date = "2026-11-01"),
      Sale(id = "x", finalTotal = 999.0, createdAt = sat1900, date = "2026-10-31", status = "cancelled"),
      Sale(id = "old", finalTotal = 70.0, createdAt = 0, date = "2026-10-31"),
      Sale(id = "late", finalTotal = 70.0, createdAt = mon1805, date = "2026-11-02"),
    ),
    saleReturns = listOf(
      SaleReturn(id = "r1", saleId = "a", amount = 30.0, createdAt = sat1900, date = "2026-10-31"),
      SaleReturn(id = "r2", saleId = "x", amount = 500.0, createdAt = sat1900, date = "2026-10-31"),
    ),
  )
  private val range = ReportBoard.Range("2026-10-31", "2026-11-01")

  @Test
  fun eventsSkipCancelledUndatedAndOutOfRangeAndReturnsAreNegative() {
    val e = HeatmapModel.events(d, range.from, range.to)
    assertEquals(listOf(10_000L, 25_050L, 4_000L, -3_000L), e.map { it.amountMinor })
    assertEquals(listOf(true, true, true, false), e.map { it.isSale })
  }

  @Test
  fun tablesHoldTheGridAndTotalsByHourAndDay() {
    val h = HeatmapModel.build(d, range, offsetMinutes = 270, dayStartMinutes = 0)
    assertEquals("شنبه 18:00", HeatmapModel.peakLabel(h))
    val (grid, hours, days) = HeatmapModel.tables(h)
    assertEquals(26, grid.columns.size)
    assertEquals(7, grid.rows.size)
    assertEquals("شنبه", grid.rows[0][0].content)
    assertEquals(2, grid.rows[0][1 + 18].content.toInt())
    assertEquals(2, grid.rows[0][25].content.toInt()) // برگشتِ ۱۹:۰۰ فروش شمرده نمی‌شود
    assertEquals(3, grid.total!![25].content.toInt())
    //  ساعتِ ۱۸: ۱۰۰ + ۲۵۰٫۵؛ ساعتِ ۱۹: فقط برگشتِ −۳۰
    assertEquals(listOf("18:00", "2", "35050"), hours.rows[18].map { it.content })
    assertEquals(listOf("19:00", "0", "-3000"), hours.rows[19].map { it.content })
    assertEquals(listOf("جمع", "3", "36050"), hours.total!!.map { it.content })
    assertEquals(listOf("یکشنبه", "1", "4000"), days.rows[1].map { it.content })
    assertEquals("money", hours.columns[2].kind)
    //  برای اکسل/چاپ همان راهِ زبانه‌های دیگر
    assertTrue(ReportFreshModel.exportRows(listOf(grid, hours, days)).isNotEmpty())
    assertTrue(ReportTables.plain(grid).head.contains("00:00"))
  }

  @Test
  fun tabOnlyForMoneyAndEmptyLedgerHasNoPeak() {
    val base = listOf(ReportBoard.TabDef("today", "امروز", false))
    assertTrue(HeatmapModel.tabs(base, money = true).any { it.id == HeatmapModel.TAB_ID && it.ranged })
    assertFalse(HeatmapModel.tabs(base, money = false).any { it.id == HeatmapModel.TAB_ID })
    assertNull(HeatmapModel.peakLabel(HeatmapModel.build(ShopData(), range, 270, 0)))
  }

  /** RPT-08 روی گوشی: «گزارشِ تازه» ِ گوشی (پیش‌تر فقط امروز و بدهی) همان نقشه را با بازه‌های کوتاه نشان می‌دهد */
  @Test
  fun rowsStartAtTheShopWeekStartAndBothScreensPassIt() {
    //  LOC-03: پیش‌فرض شنبه (رفتارِ پیشین)؛ آغازِ یکشنبه ⇒ ردیفِ نخست یکشنبه و شنبه آخر، همان عددها
    assertEquals(HeatmapModel.build(d, range, 270, 0).weekdays, HeatmapModel.build(d, range, 270, 0, 6).weekdays)
    val h = HeatmapModel.build(d, range, 270, 0, weekStart = 7)
    assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), h.weekdays)
    val (grid, _, _) = HeatmapModel.tables(h)
    assertEquals("یکشنبه", grid.rows[0][0].content)
    assertEquals(1, grid.rows[0][25].content.toInt())
    assertEquals("شنبه", grid.rows[6][0].content)
    assertEquals(2, grid.rows[6][25].content.toInt())
    assertEquals("شنبه 18:00", HeatmapModel.peakLabel(h))
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/ui/screens/ReportFresh.kt") }.first { it.isFile }.readText()
    val calls = Regex("""HeatmapModel\.build\([^\n]*""").findAll(src).map { it.value }.toList()
    assertEquals(2, calls.size)
    assertTrue(calls.toString(), calls.all { it.contains("ShopDayChoice.weekStart)") })
  }

  @Test
  fun phoneShowsTheSameHeatmapForMoneyOnlyWithShortSpans() {
    assertEquals(listOf("week", "month", "quarter"), HeatmapModel.PHONE_SPANS)
    assertTrue(HeatmapModel.PHONE_SPANS.all { it in ReportBoard.SPANS && it in ReportBoard.SPAN_LABELS })
    //  «۷ روز» تا ۲ نوامبر شنبه و یکشنبه را دارد؛ همان عددهای زبانهٔ کامپیوتر
    val week = HeatmapModel.build(d, ReportBoard.range(HeatmapModel.PHONE_SPANS.first(), "2026-11-02"), 270, 0)
    assertEquals("شنبه 18:00", HeatmapModel.peakLabel(week))
    assertEquals(4, week.total.count)
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/ui/screens/ReportFresh.kt") }.first { it.isFile }.readText()
    val phone = src.substring(src.indexOf("fun PhoneFreshReports("))
    assertTrue("فقط با دیدنِ پول", phone.contains("if (money) HeatmapModel.build(d, ReportBoard.range(heatSpan, today)"))
    assertTrue(phone.contains("heat?.let { h -> item { HeatmapPhone(h, heatSpan) { heatSpan = it } } }"))
  }
}
