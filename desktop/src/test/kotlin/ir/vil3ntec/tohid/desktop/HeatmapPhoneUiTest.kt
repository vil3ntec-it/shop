package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPanelPages
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.HeatmapModel
import ir.vil3ntec.tohid.ui.screens.HeatmapPhone
import ir.vil3ntec.tohid.ui.screens.HeatmapSummary
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 *  RPT-08 — آزمونِ خودکارِ صفحهٔ «ساعت و روز» (`HeatmapPhone` ِ گوشی و `HeatmapSummary` ِ کامپیوتر) به جای «دیدن روی دستگاه»:
 *  پرفروش‌ترین ساعت با روز و ساعتِ دیواریِ دکان (کابل، +۴:۳۰)، فروشِ پس از نیمه‌شب در روزِ کاریِ پیشین وقتی روزِ کاری ۰۴:۰۰ آغاز
 *  می‌شود، فروشِ لغوشده نه، ردیف‌ها از شنبه (آغازِ هفتهٔ دکان) و بازه‌های ۷ روز / این ماه / این سه‌ماهه.
 */
class HeatmapPhoneUiTest {

  @get:Rule val rule = createComposeRule()

  private val kabul = 270
  private fun at(iso: String): Long = LocalDateTime.parse(iso).toInstant(ZoneOffset.ofHoursMinutes(4, 30)).toEpochMilli()

  //  ۱۴۰۵/۰۸/۱۶ = شنبه ۲۰۲۶-۱۱-۰۷: دو فروشِ ۱۰ صبح، یکی ۱۴، یکی لغوشدهٔ ۱۰؛ یکشنبه ۰۲:۳۰ (پس از نیمه‌شب)
  private val d = ShopData(
    sales = listOf(
      Sale(id = "a", date = "2026-11-07", createdAt = at("2026-11-07T10:05"), finalTotal = 100.0),
      Sale(id = "b", date = "2026-11-07", createdAt = at("2026-11-07T10:40"), finalTotal = 50.0),
      Sale(id = "c", date = "2026-11-07", createdAt = at("2026-11-07T14:10"), finalTotal = 70.0),
      Sale(id = "x", date = "2026-11-07", createdAt = at("2026-11-07T10:20"), finalTotal = 999.0, status = "cancelled"),
      Sale(id = "n", date = "2026-11-08", createdAt = at("2026-11-08T02:30"), finalTotal = 30.0),
    ),
  )
  private val range = ReportBoard.Range("2026-11-01", "2026-11-12")

  private val spans = ArrayList<String>()

  private fun showPhone(dayStart: Int) = HeatmapModel.build(d, range, kabul, dayStart).also { h ->
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { HeatmapPhone(h, "week") { spans += it } }
      }
    }
  }

  @Test fun busiestHourIsSaturdayTenOnTheShopClockAndCancelledSalesAreNotCounted() {
    val h = showPhone(dayStart = 0)
    val peak = HeatmapModel.peakLabel(h)!!
    assertEquals("${HeatmapModel.dayName(6)} ${HeatmapModel.hourLabel(10)}", peak)
    rule.onNodeWithText(peak).assertExists()
    rule.onNodeWithText(StrPanelPages.salesN(2)).assertExists()
    //  چهار فروشِ لغونشده (لغوشده بیرون)
    rule.onNodeWithText("4").assertExists()
    //  ردیف‌ها از شنبه: هر هفت روز دیده می‌شوند و نخستین ردیف شنبه است
    assertEquals(6, h.weekdays.first())
    for (wd in 1..7) assertTrue(rule.onAllNodesWithText(HeatmapModel.dayName(wd)).fetchSemanticsNodes().isNotEmpty())
  }

  @Test fun afterMidnightSaleBelongsToTheBusinessDayBeforeWhenTheDayStartsAtFour() {
    val late = HeatmapModel.build(d, range, kabul, 4 * 60)
    val plain = HeatmapModel.build(d, range, kabul, 0)
    val sat = late.weekdays.indexOf(6)
    val sun = plain.weekdays.indexOf(7)
    //  روزِ کاریِ ۰۴:۰۰ ⇒ ۰۲:۳۰ ِ یکشنبه در ردیفِ شنبه، همان ساعتِ ۲
    assertTrue(late.level(sat, 2, 4) > 0)
    assertTrue(plain.level(sun, 2, 4) > 0)
    assertEquals(0, plain.level(plain.weekdays.indexOf(6), 2, 4))
    showPhone(dayStart = 4 * 60)
    rule.onNodeWithText(HeatmapModel.peakLabel(late)!!).assertExists()
  }

  @Test fun phoneSpansAreSevenDaysThisMonthAndThisQuarter() {
    showPhone(dayStart = 0)
    for (s in HeatmapModel.PHONE_SPANS) rule.onNodeWithText(ReportBoard.SPAN_LABELS.getValue(s)).assertExists()
    rule.onNodeWithText(ReportBoard.SPAN_LABELS.getValue("month")).performClick()
    rule.waitForIdle()
    assertEquals(listOf("month"), spans)
  }

  @Test fun desktopSummaryShowsPeakCountAndNetAmount() {
    val h = HeatmapModel.build(d, range, kabul, 0)
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { HeatmapSummary(h) }
      }
    }
    rule.onNodeWithText(StrPanelPages.peakHour).assertExists()
    rule.onNodeWithText(HeatmapModel.peakLabel(h)!!).assertExists()
    rule.onNodeWithText(StrPanelPages.salesCount).assertExists()
    rule.onNodeWithText(StrPanelPages.netAmount).assertExists()
    assertEquals(25_000L, h.total.totalMinor)
  }
}
