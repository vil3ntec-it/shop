package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 *  نویسندهٔ اکسل — رفتارِ امروزِ گزارش‌های تک‌برگه قفل است.
 *
 *  `Xlsx.write` در شش صفحهٔ کامپیوتر (کالا، فاکتور، گزارش، خرید،
 *  مشتری…) خروجیِ اکسل می‌سازد. وقتی نوشتنِ چندبرگه (خروجیِ کاملِ مالک —
 *  OPS-08) روی همین نویسنده نشست، این آزمون پیش از آن تغییر از روی
 *  خروجیِ همان روز نوشته شد تا هیچ بایتی از برگهٔ گزارش‌ها جابه‌جا نشود.
 */
class XlsxTest {

  internal fun unzip(bytes: ByteArray): LinkedHashMap<String, String> {
    val out = LinkedHashMap<String, String>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
      while (true) {
        val e = z.nextEntry ?: break
        out[e.name] = z.readBytes().toString(Charsets.UTF_8)
      }
    }
    return out
  }

  private val rows = listOf(
    listOf("کالا", "تعداد", "مبلغ", "یادداشت"),
    listOf("برنج", "2.5", "1٬250", "a<b & \"c\""),
    listOf("روغن", "3", "-40", ""),
  )

  @Test
  fun `برگهٔ تک‌برگه همان شکلِ پیشین را دارد`() {
    val files = unzip(Xlsx.write("فروش: روزانه/ماه", rows))
    assertEquals(
      listOf(
        "[Content_Types].xml", "_rels/.rels", "xl/workbook.xml",
        "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/worksheets/sheet1.xml",
      ),
      files.keys.toList(),
    )
    assertEquals(SHEET1, files["xl/worksheets/sheet1.xml"])
    assertEquals(WORKBOOK, files["xl/workbook.xml"])
  }

  companion object {
    const val SHEET1 =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheetViews><sheetView rightToLeft="1" workbookViewId="0"><pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols><col min="1" max="1" width="8" customWidth="1"/><col min="2" max="2" width="8" customWidth="1"/><col min="3" max="3" width="8" customWidth="1"/><col min="4" max="4" width="11" customWidth="1"/></cols><sheetData><row r="1"><c r="A1" t="inlineStr" s="1"><is><t xml:space="preserve">کالا</t></is></c><c r="B1" t="inlineStr" s="1"><is><t xml:space="preserve">تعداد</t></is></c><c r="C1" t="inlineStr" s="1"><is><t xml:space="preserve">مبلغ</t></is></c><c r="D1" t="inlineStr" s="1"><is><t xml:space="preserve">یادداشت</t></is></c></row><row r="2"><c r="A2" t="inlineStr"><is><t xml:space="preserve">برنج</t></is></c><c r="B2"><v>2.5</v></c><c r="C2" s="2"><v>1250</v></c><c r="D2" t="inlineStr"><is><t xml:space="preserve">a&lt;b &amp; &quot;c&quot;</t></is></c></row><row r="3"><c r="A3" t="inlineStr"><is><t xml:space="preserve">روغن</t></is></c><c r="B3" s="2"><v>3</v></c><c r="C3" s="2"><v>-40</v></c><c r="D3" t="inlineStr"><is><t xml:space="preserve"></t></is></c></row></sheetData></worksheet>"""
    const val WORKBOOK =
      """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="فروش  روزانه ماه" sheetId="1" r:id="rId1"/></sheets></workbook>"""
  }
}
