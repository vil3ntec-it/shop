package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.report.ReportBoard
import ir.vil3ntec.tohid.data.DocGapsSheet
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** RPT-17 — «شماره‌های جاافتاده» در «گزارشِ تازه» ِ کامپیوتر: همان برگهٔ «گزارش‌ها»، تعداد عدد و بقیه متن؛ خاموش ⇒ همان زبانه‌ها */
class DocGapsFreshTest {

  @After fun reset() { I18n.source = { Lang.FA } }

  private val base = listOf(ReportBoard.TabDef("sales", "فروش", true), ReportBoard.TabDef("debts", "بدهی", false))
  private fun sale(id: String, no: Int, series: String = "A1") = Sale(id = id, invoiceNumber = no, series = series, date = "2026-10-20")
  private val d = ShopData(sales = listOf(sale("s1", 1), sale("s2", 2), sale("s5", 5)))

  @Test
  fun tabOnlyWithTheSettingAndAtTheEnd() {
    assertEquals(base, DocGapsFresh.tabs(base, on = false))
    val on = DocGapsFresh.tabs(base, on = true)
    assertEquals(base, on.dropLast(1))
    assertEquals(DocGapsFresh.TAB_ID, on.last().id)
    assertEquals(StrReports.docGaps, on.last().label)
    assertFalse("کلِ دفتر، بی بازه", on.last().ranged)
  }

  @Test
  fun tableIsTheReportsSheetWithANumericCountOnly() {
    I18n.source = { Lang.FA }
    val sheet = DocGapsSheet.sheet(d, "2026-10-31").rows
    val t = DocGapsFresh.tables(d, "2026-10-31").single()
    assertEquals(sheet[0], t.columns.map { it.label })
    assertEquals(sheet.size - 1, t.rows.size)
    val count = t.columns.indexOfFirst { it.label == StrReports.colCount }
    assertEquals("count", t.columns[count].kind)
    assertTrue(t.columns.filterIndexed { i, _ -> i != count }.all { it.kind == "text" })
    //  ۳ و ۴ ِ A1 جاافتاده ⇒ یک ردیف با تعدادِ ۲ (عدد) و «از/تا» ِ متنی
    val row = t.rows.single()
    assertEquals(JsonPrimitive(2L), row[count])
    assertTrue(row.filterIndexed { i, _ -> i != count }.all { it.isString })
    assertEquals(sheet[1], row.map { it.content })
  }
}
