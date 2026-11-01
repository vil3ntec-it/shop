package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.DeskGrid.Cell
import ir.vil3ntec.tohid.core.product.DeskGrid.Col
import ir.vil3ntec.tohid.core.product.DeskGrid.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 *  PRD-22 — جدولِ ویرایشِ درجا: نوشتن، چسباندن از Excel، تکرارِ ردیف، برگرداندن و ایرادِ هر خانه.
 *  پیش از این کالا فقط یکی‌یکی در پنل ویرایش می‌شد و ستونی از Excel نمی‌شد چسباند.
 */
class DeskGridTest {

  private fun row(id: String, name: String, cat: String = "", unit: String = "دانه", buy: String = "10", sell: String = "12", min: String = "0", code: String = "") =
    DeskGrid.Row(id, listOf(name, cat, unit, buy, sell, min, code))

  private val start = DeskGrid.sheet(listOf(row("a", "چای", code = "111"), row("b", "شکر"), row("c", "نمک")))

  @Test
  fun setMarksOnlyThatCellAndSameTextIsNoStep() {
    val s = start.set(1, Col.SELL, " ۱۵ ")
    assertEquals("۱۵", s.rows[1][Col.SELL])
    assertTrue(s.isChanged(1, Col.SELL))
    assertFalse(s.isChanged(1, Col.BUY))
    assertEquals(listOf("b"), s.changed.map { it.id })
    //  همان متن ⇒ همان جدول، بی گامِ برگرداندن
    assertSame(start, start.set(0, Col.NAME, "چای"))
    assertFalse(start.canUndo)
    assertSame(start, start.set(9, Col.NAME, "x"))
  }

  @Test
  fun pasteFromExcelFillsRightAndDownAndCountsWhatFellOff() {
    //  دو ستون (خرید، فروش) × چهار ردیف از اکسل؛ جدول سه ردیف دارد و ستونِ سوم از بارکد بیرون می‌زند
    val clip = "20\t25\r\n30\t35\r\n40\t45\r\n50\t55\r\n"
    val p = start.paste(0, Col.BUY, clip)
    assertEquals(listOf("20", "30", "40"), p.sheet.rows.map { it[Col.BUY] })
    assertEquals(listOf("25", "35", "45"), p.sheet.rows.map { it[Col.SELL] })
    assertEquals(6, p.cells.size)
    assertEquals(1, p.cutRows)
    assertEquals(0, p.cutCols)
    val wide = start.paste(2, Col.MIN, "1\t222\textra")
    assertEquals("222", wide.sheet.rows[2][Col.BARCODE])
    assertEquals(1, wide.cutCols)
  }

  @Test
  fun clipAlwaysSplitsOnTabsAndKeepsQuotedMultilineCell() {
    //  «۱,۲۵۰» ِ تک‌خانه یک خانه است، نه دو
    assertEquals(listOf(listOf("۱,۲۵۰")), DeskGrid.parseClip("۱,۲۵۰\r\n"))
    //  خانهٔ چندخطیِ اکسل گیومه‌دار است و خطِ تازه‌اش به فاصله می‌رود
    val p = start.paste(0, Col.NAME, "\"چای\nسبز\"\tنوشیدنی")
    assertEquals("چای سبز", p.sheet.rows[0][Col.NAME])
    assertEquals("نوشیدنی", p.sheet.rows[0][Col.CATEGORY])
    assertEquals(0, start.paste(0, Col.NAME, "").cells.size)
  }

  @Test
  fun duplicateAboveCopiesAllButNameAndBarcode() {
    val edited = start.set(0, Col.CATEGORY, "نوشیدنی").set(0, Col.SELL, "99")
    val d = edited.duplicateAbove(1)
    assertEquals("نوشیدنی", d.rows[1][Col.CATEGORY])
    assertEquals("99", d.rows[1][Col.SELL])
    assertEquals("شکر", d.rows[1][Col.NAME])
    assertEquals("", d.rows[1][Col.BARCODE])
    assertSame(edited, edited.duplicateAbove(0))
  }

  @Test
  fun undoStepsBackOneActionAtATime() {
    val s1 = start.set(0, Col.SELL, "13")
    val s2 = s1.paste(1, Col.BUY, "1\n2")
    val s3 = s2.sheet.duplicateAbove(2)
    assertEquals(s2.sheet.rows, s3.undo().rows)
    assertEquals(s1.rows, s3.undo().undo().rows)
    assertEquals(start.rows, s3.undo().undo().undo().rows)
    assertFalse(s3.undo().undo().undo().canUndo)
    assertTrue(s3.undo().undo().undo().changed.isEmpty())
    //  پشته سقف دارد
    var s = start
    repeat(DeskGrid.MAX_UNDO + 20) { s = s.set(0, Col.SELL, "$it") }
    repeat(DeskGrid.MAX_UNDO) { s = s.undo() }
    assertFalse(s.canUndo)
    assertEquals("19", s.rows[0][Col.SELL])
  }

  @Test
  fun problemsFlagEachBadCell() {
    val s = start
      .set(0, Col.NAME, "")
      .set(1, Col.NAME, "نمک")
      .set(1, Col.BUY, "abc")
      .set(2, Col.SELL, "-5")
      .set(2, Col.MIN, "۱٫۵")
      .set(1, Col.BARCODE, "999")
    val owner = mapOf("999" to "z", "111" to "a")
    val got = DeskGrid.problems(s, outsideNames = emptySet()) { owner[it] }
    assertEquals(Problem.NAME_EMPTY, got[Cell(0, Col.NAME)])
    //  دو ردیف به نامِ «نمک»: دومی (به ترتیبِ جدول) ایراد دارد
    assertEquals(Problem.NAME_TAKEN, got[Cell(2, Col.NAME)])
    assertNull(got[Cell(1, Col.NAME)])
    assertEquals(Problem.BAD_NUMBER, got[Cell(1, Col.BUY)])
    assertEquals(Problem.NEGATIVE, got[Cell(2, Col.SELL)])
    assertNull(got[Cell(2, Col.MIN)])
    assertEquals(Problem.BARCODE_TAKEN, got[Cell(1, Col.BARCODE)])
    //  بارکدِ خودِ کالا ایراد نیست
    assertNull(got[Cell(0, Col.BARCODE)])
    //  نامِ کالای بیرونِ جدول
    val out = DeskGrid.problems(start, setOf(SimilarNames.exactKey("شکر"))) { null }
    assertEquals(mapOf(Cell(1, Col.NAME) to Problem.NAME_TAKEN), out)
    assertTrue(DeskGrid.problems(start, emptySet()) { owner[it] }.isEmpty())
  }

  @Test
  fun unchangedOfflineBarcodeConflictDoesNotLockTheRow() {
    //  دو کالا یک بارکد دارند (تعارضِ آفلاین)؛ تا بارکد عوض نشده ردیف ثبت‌شدنی است
    val s = DeskGrid.sheet(listOf(row("a", "چای", code = "111")))
    assertTrue(DeskGrid.problems(s, emptySet()) { "z" }.isEmpty())
    assertEquals(Problem.BARCODE_TAKEN, DeskGrid.problems(s.set(0, Col.BARCODE, "112"), emptySet()) { "z" }[Cell(0, Col.BARCODE)])
  }

  @Test
  fun numbersReadPersianDigitsAndSeparators() {
    assertEquals(125000L, DeskGrid.money("۱٬۲۵۰"))
    assertEquals(0L, DeskGrid.money(" "))
    assertNull(DeskGrid.money("ده"))
    assertEquals(1500L, DeskGrid.quantity("۱٫۵"))
  }
}
