package ir.vil3ntec.tohid.ui.v3

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.analytics.ReportPeriod
import ir.vil3ntec.tohid.ui.v3.analytics.ReportsModel
import ir.vil3ntec.tohid.ui.v3.money.ExpensesModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** منطقِ خالصِ ظاهرِ ۶۳w: کارهای سربرگ (دو پیدا، بقیه در «…»)، ماه‌های کادرِ کشوییِ مصارف و بازه‌های گزارش */
class LookModelsTest {
  private fun action(label: String, dot: Boolean = false) = V3Action(Icons.Outlined.Add, label, dot = dot) {}

  @Test
  fun `سربرگ دو کار نشان می‌دهد و بقیه را نخستِ «…» می‌کند — هیچ کاری گم نمی‌شود`() {
    val menu = listOf(V3MenuItem(Icons.Outlined.Add, "خروجی") {})
    val split = V3HeaderActions.split(listOf(action("فیلتر"), action("صندوق"), action("جدول", dot = true)), menu)
    assertEquals(listOf("فیلتر", "صندوق"), split.shown.map { it.label })
    assertEquals(listOf("جدول", "خروجی"), split.menu.map { it.label })
    assertTrue("نقطهٔ کارِ رفته روی «…»", split.menuDot)
    val two = V3HeaderActions.split(listOf(action("الف"), action("ب")), emptyList())
    assertTrue(two.menu.isEmpty())
    assertFalse(two.menuDot)
  }

  @Test
  fun `کادرِ ماهِ مصارف — ماهِ جاری و یازده ماهِ پیش، ماه‌های دارای مصرف و ماهِ برگزیده، تازه‌ترین اول`() {
    val d = ShopData(expenses = listOf(Expense(id = "x", title = "برق", category = "برق", amount = 10.0, date = "2024-02-10")))
    val months = ExpensesModel.choices(d, "2026-11-15", "2026-11")
    assertEquals("2026-11", months.first())
    assertTrue("2025-12" in months)
    assertFalse("دوازده ماه پیش‌تر بی مصرف نه", "2025-11" in months)
    assertTrue("ماهِ دارای مصرف", "2024-02" in months)
    assertEquals(months.sortedDescending(), months)
    assertTrue("ماهِ برگزیدهٔ کهنه هم", "2023-05" in ExpensesModel.choices(d, "2026-11-15", "2023-05"))
    assertEquals(months.size, months.distinct().size)
  }

  @Test
  fun `بازهٔ گزارش — کلیدِ کادر رفت‌وبرگشت دارد و ماه‌های خورشیدیِ گذشته را می‌دهد`() {
    val keys = ReportsModel.choiceKeys(1405, 2)
    assertEquals(listOf("today", "week", "month", "jmonth:1405-2", "jmonth:1405-1", "jmonth:1404-12"), keys.take(6))
    assertEquals("all", keys.last())
    assertTrue("jyear:1403" in keys)
    val base = ReportPeriod(ReportEngine.Range.MONTH, 1405, 2)
    keys.forEach { k -> assertEquals(k, ReportsModel.choiceKey(ReportsModel.parseChoice(k, base))) }
    assertEquals(ReportPeriod(ReportEngine.Range.JMONTH, 1404, 12), ReportsModel.parseChoice("jmonth:1404-12", base))
    assertEquals("ناشناخته ⇒ همان", base, ReportsModel.parseChoice("jmonth:x", base))
    assertEquals(base, ReportsModel.parseChoice("نه", base))
  }
}
