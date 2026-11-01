package ir.vil3ntec.tohid.ui.v3.analytics

import ir.vil3ntec.tohid.data.ReportCsv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** جدولِ برگهٔ کامپیوتر: ستونِ عدد/تاریخ، جمع فقط برای مبلغ، و ستونِ پهن */
class SheetModelTest {
  private val sheet = ReportCsv.Sheet(
    "x",
    listOf(
      listOf("تاریخ", "نام", "تعداد", "مبلغ"),
      listOf("2026-10-24", "برنج", "2", "240"),
      listOf("2026-10-23", "شکر", "1", "85"),
    ),
  )

  @Test
  fun `گونهٔ ستون‌ها و جمعِ مبلغ`() {
    val c = SheetModel.columns(sheet)
    assertEquals(listOf(false, false, true, true), c.numeric)
    assertEquals(listOf(true, false, false, false), c.dates)
    assertEquals(listOf(false, false, false, true), c.summable)
    assertEquals(listOf("جمع", "", "", "325"), SheetModel.footer(sheet, c, "جمع"))
  }

  @Test
  fun `یک ستونِ متنی بقیهٔ جا را می‌گیرد و یک ردیف جمع ندارد`() {
    val c = SheetModel.columns(sheet)
    assertEquals(listOf(0, 66, 120, 120), c.widths)
    val one = ReportCsv.Sheet("y", sheet.rows.take(2))
    assertNull(SheetModel.footer(one, SheetModel.columns(one), "جمع"))
  }
}
