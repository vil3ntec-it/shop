package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportCoreVectorsTest
import ir.vil3ntec.tohid.core.report.ReportLedger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  RPT-13 — − / + و «مقایسه با دورهٔ قبل» در «گزارشِ تازه» ِ کامپیوتر (`ReportFreshModel.rangeOf`/`canShift`/`keysSpan`):
 *  همان `ReportPeriod` ِ «گزارش‌ها»، بی دورهٔ آینده؛ سه‌ماهه و «همه» جابه‌جا نمی‌شوند؛ زبانهٔ جابه‌جاشده همان گزارشِ هسته
 *  روی همان بازه است. روی دفترِ آزمون‌های مرجع (`ReportCoreVectorsTest`).
 */
class ReportFreshKeysTest {
  private val l = ReportLedger.parse(
    Json.parseToJsonElement(ReportCoreVectorsTest.vectorFile().readText()).jsonObject.getValue("ledgers").jsonObject.getValue("kabul").jsonObject,
  )
  private val today = "2026-10-14"

  @Test fun `هر بازه به واحدِ خودش جابه‌جا می‌شود و دورهٔ آینده ساخته نمی‌شود`() {
    assertEquals(ReportBoard.Range(today, today), ReportFreshModel.rangeOf("today", today, 0))
    assertEquals(ReportBoard.Range("2026-10-13", "2026-10-13"), ReportFreshModel.rangeOf("today", today, -1))
    //  «۷ روز» = همان هفت روزِ پیش
    assertEquals(ReportBoard.Range("2026-10-08", "2026-10-14"), ReportFreshModel.rangeOf("week", today, 0))
    assertEquals(ReportBoard.Range("2026-10-01", "2026-10-07"), ReportFreshModel.rangeOf("week", today, -1))
    //  «این ماه» (تا امروز) ⇒ ماهِ میلادیِ کاملِ پیش، و دوباره + ⇒ همان «تا امروز»
    assertEquals(ReportBoard.Range("2026-09-01", "2026-09-30"), ReportFreshModel.rangeOf("month", today, -1))
    assertEquals(ReportBoard.Range("2026-08-01", "2026-08-31"), ReportFreshModel.rangeOf("month", today, -2))
    assertEquals(ReportBoard.Range("2026-10-01", today), ReportFreshModel.rangeOf("month", today, 0))
    for (s in listOf("today", "week", "month")) {
      assertTrue(s, ReportFreshModel.canShift(s, today, -1))
      assertFalse("$s: دورهٔ بعد از دورهٔ جاری آینده است", ReportFreshModel.canShift(s, today, 1))
    }
  }

  @Test fun `سه‌ماهه و همه جابه‌جا نمی‌شوند و مقایسه دورهٔ قبل ندارد`() {
    for (s in listOf("quarter", "lastQuarter", "all")) {
      assertEquals(s, "ALL", ReportFreshModel.keysSpan(s))
      assertFalse(s, ReportFreshModel.canShift(s, today, -1))
      assertEquals(s, ReportBoard.range(s, today), ReportFreshModel.rangeOf(s, today, -3))
    }
    assertEquals(listOf("TODAY", "WEEK", "MONTH"), listOf("today", "week", "month").map(ReportFreshModel::keysSpan))
    assertEquals(setOf("sales", "profit"), ReportFreshModel.COMPARABLE)
    assertTrue(ReportFreshModel.SHIFTABLE.containsAll(listOf("sales", "profit", "fraud", HeatmapModel.TAB_ID)))
  }

  @Test fun `زبانهٔ جابه‌جاشده همان گزارشِ هسته روی بازهٔ تازه است`() {
    val prev = ReportFreshModel.rangeOf("month", today, -1)
    val v = ReportFreshModel.view(l, "sales", "month", "product", today, null, range = prev)
    val core = ReportCore.sales(l, prev.from, prev.to, null, ReportFreshModel.costingOf(ir.vil3ntec.tohid.core.cost.CostPolicy.DEFAULT),
      ReportFreshModel.approvedOf(ReportFreshModel.costingOf(ir.vil3ntec.tohid.core.cost.CostPolicy.DEFAULT)))
    assertEquals(core.totals, v.sales!!.totals)
    //  بی بازهٔ صریح ⇒ همان «این ماه» ِ امروز (رفتارِ پیشین)
    val same = ReportFreshModel.view(l, "sales", "month", "product", today, null)
    assertEquals(ReportFreshModel.view(l, "sales", "month", "product", today, null, range = ReportBoard.range("month", today)).sales!!.totals, same.sales!!.totals)
  }

  @Test fun `صفحهٔ گزارشِ تازهٔ کامپیوتر همان کلیدهای دوره و مقایسه را دارد`() {
    val repo = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { File(it, "android-native").isDirectory }
    val dir = File(repo, "android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens")
    val src = File(dir, "ReportFresh.kt").readText()
    val bar = File(dir, "ReportKeys.kt").readText()
    for (k in listOf("KeyMap.REPORTS_PREV_PERIOD", "KeyMap.REPORTS_NEXT_PERIOD", "KeyMap.REPORTS_COMPARE")) {
      assertTrue(k, src.contains("e.matches($k)"))
      assertTrue("$k: برچسبِ کلید روی دکمه (`FreshPeriodBar`)", bar.contains("$k.hint"))
    }
    assertTrue(src.contains("FreshPeriodBar(") && src.contains("ReportCompareLine("))
    assertTrue("همان کلیدِ «گزارش‌ها»", src.contains("ReportKeysPref.on(context)"))
  }
}
