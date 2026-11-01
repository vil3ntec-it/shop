package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.ImportTable.Read
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** PRD-20: جدولِ خامِ فایلِ ورود — CSV ِ همیشگی، برگهٔ اولِ xlsx، سقفِ اندازه و اثرِ انگشت. */
class ImportTableTest {

  private fun table(r: Read): List<List<String>> = (r as? Read.Table)?.rows ?: error("رد شد: $r")
  private fun failed(r: Read): String = (r as? Read.Failed)?.reason ?: error("پذیرفته شد: $r")

  /* ------------------------------ CSV ------------------------------ */

  @Test fun csvKeepsTheLegacyParserByteForByte() {
    //  همان موردهای `ProductImportTest` ِ اندروید: BOM، گیومه، «،» درونِ گیومه، گیومهٔ دوتایی، CRLF، ردیفِ خالی
    val rows = ImportTable.csv("\uFEFFنام محصول,قیمت\r\n\"روغن، ۵ لیتری\",\"۱٬۲۰۰\"\n,\n\"a \"\"b\"\"\",2")
    assertEquals(
      listOf(listOf("نام محصول", "قیمت"), listOf("روغن، ۵ لیتری", "۱٬۲۰۰"), listOf("", ""), listOf("a \"b\"", "2")),
      rows,
    )
    assertEquals(listOf(listOf("name", "unit"), listOf("صابون", "عدد")), ImportTable.csv("name;unit\nصابون;عدد\n"))
  }

  @Test fun tabSeparatorOnlyOnTheNewPath() {
    //  مسیرِ قدیمی (پرچمِ خاموش) جدولِ تب‌دار را یک ستون می‌خواند — همان رفتارِ امروز
    assertEquals(listOf(listOf("name\tprice"), listOf("a\t1")), ImportTable.csv("name\tprice\na\t1"))
    assertEquals(listOf(listOf("name", "price"), listOf("a", "1")), table(ImportTable.readCsv("name\tprice\na\t1")))
  }

  @Test fun newPathDropsTrailingBlankRowsAndCells() {
    assertEquals(listOf(listOf("name"), listOf("a")), table(ImportTable.readCsv("name,,,\na,,\n,,\n\n")))
  }

  /* ------------------------------ xlsx ------------------------------ */

  private val shared = """<?xml version="1.0" encoding="UTF-8"?>
    <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
      <si><t>نام محصول</t></si>
      <si><t xml:space="preserve">قیمت فروش</t></si>
      <si><r><rPr><b/></rPr><t>چای</t></r><r><t xml:space="preserve"> سبز</t></r><rPh sb="0" eb="1"><t>XX</t></rPh></si>
      <si><t>A &amp; B &lt;1&gt; _x000D_x</t></si>
    </sst>"""

  @Test fun xlsxSharedInlineNumbersGapsAndEntities() {
    val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><dimension ref="A1:D4"/>
      <sheetData>
        <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s" s="1"><v>1</v></c><c r="D1" t="inlineStr"><is><r><t>بار</t></r><r><t>کد</t></r><rPh sb="0" eb="1"><t>ZZ</t></rPh></is></c></row>
        <row r="3" spans="1:4"><c r="A3" t="s"><v>2</v></c><c r="B3"><v>1200</v></c><c r="C3" t="b"><v>1</v></c><c r="D3"><v>6260000000017</v></c></row>
        <row r="4"><c r="A4" t="s"><v>3</v></c><c r="B4"><f>B3*2</f><v>1.2E+3</v></c><c r="C4" t="str"><v>متن</v></c><c r="D4" s="2"/></row>
      </sheetData></worksheet>"""
    assertEquals(
      listOf(
        listOf("نام محصول", "قیمت فروش", "", "بارکد"),
        listOf(),
        listOf("چای سبز", "1200", "TRUE", "6260000000017"),
        listOf("A & B <1> \rx", "1200", "متن"),
      ),
      table(ImportTable.readXlsx(sheet, shared)),
    )
  }

  @Test fun xlsxWithNamespacePrefixAndNoCellReferences() {
    //  Open XML SDK پیشوندِ `x:` می‌گذارد؛ بعضی سازنده‌ها `r` را نمی‌نویسند ⇒ پشتِ هم
    val sheet = """<x:worksheet xmlns:x="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><x:sheetData>
      <x:row><x:c t="inlineStr"><x:is><x:t>name</x:t></x:is></x:c><x:c t="inlineStr"><x:is><x:t>price</x:t></x:is></x:c></x:row>
      <x:row><x:c t="inlineStr"><x:is><x:t>قند</x:t></x:is></x:c><x:c><x:v>45.5</x:v></x:c></x:row>
      </x:sheetData></x:worksheet>"""
    assertEquals(listOf(listOf("name", "price"), listOf("قند", "45.5")), table(ImportTable.readXlsx(sheet, null)))
  }

  @Test fun xlsxSheetPathFollowsTheFirstSheetRelationship() {
    val wb = """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>
      <sheet name="کالا" sheetId="3" r:id="rId7"/><sheet name="دیگر" sheetId="1" r:id="rId1"/></sheets></workbook>"""
    val rels = """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/>
      <Relationship Id="rId7" Type="…/worksheet" Target="worksheets/sheet3.xml"/></Relationships>"""
    assertEquals("xl/worksheets/sheet3.xml", ImportTable.sheetPath(wb, rels))
    assertEquals("xl/worksheets/a.xml", ImportTable.sheetPath(wb, """<Relationships><Relationship Id="rId7" Target="/xl/worksheets/a.xml"/></Relationships>"""))
    assertEquals(ImportTable.DEFAULT_SHEET, ImportTable.sheetPath(null, null))
    assertEquals(ImportTable.DEFAULT_SHEET, ImportTable.sheetPath(wb, "<Relationships/>"))
  }

  @Test fun xlsxSharedStringIndexOutOfRangeIsBlankNotACrash() {
    val sheet = """<worksheet><sheetData><row r="1"><c r="A1" t="s"><v>99</v></c><c r="B1" t="s"><v>x</v></c><c r="C1"><v>1</v></c></row></sheetData></worksheet>"""
    assertEquals(listOf(listOf("", "", "1")), table(ImportTable.readXlsx(sheet, shared)))
  }

  @Test fun xlsxIgnoresDoctypeAndCustomEntities() {
    //  بی DTD و بی بسطِ موجودیتِ دلخواه — XXE و «میلیارد خنده» راهی ندارند
    val sheet = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY a "AAAAAAAAAA"><!ENTITY b "&a;&a;&a;">]>
      <worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>&b;&#1587;&#x633;</t></is></c></row></sheetData></worksheet>"""
    assertEquals(listOf(listOf("&b;سس")), table(ImportTable.readXlsx(sheet, null)))
    //  نشانه‌گذاریِ درونِ DTD هرگز خانه نمی‌شود (کلِ `[ … ]` گذر می‌شود، نه تا نخستین `>`)
    val sneaky = """<!DOCTYPE x [<!ENTITY e "<x/><c r='B1' t='inlineStr'><is><t>EVIL</t></is></c>">]>
      <worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>name</t></is></c></row></sheetData></worksheet>"""
    assertEquals(listOf(listOf("name")), table(ImportTable.readXlsx(sneaky, null)))
    val quoted = """<!DOCTYPE x [<!ENTITY e "]><x/><c r='C1' t='inlineStr'><is><t>EVIL</t></is></c>">]>
      <worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>name</t></is></c></row></sheetData></worksheet>"""
    assertEquals(listOf(listOf("name")), table(ImportTable.readXlsx(quoted, null)))
    //  نشانه‌گذاریِ بی گیومه درونِ `[ … ]` هم (فایلِ دست‌ساز) — تا `]>` گذر می‌شود، نه تا نخستین `>`
    val bare = """<!DOCTYPE x [<!ELEMENT a ANY><c r='D1' t='inlineStr'><is><t>EVIL</t></is></c>]>
      <worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>name</t></is></c></row></sheetData></worksheet>"""
    assertEquals(listOf(listOf("name")), table(ImportTable.readXlsx(bare, null)))
  }

  /* ------------------------------ سقف‌ها ------------------------------ */

  @Test fun moreDataRowsThanTheLimitIsRejectedWhole() {
    val ok = buildString { append("name\n"); repeat(ImportTable.MAX_ROWS) { append("p$it\n") } }
    assertEquals(ImportTable.MAX_ROWS + 1, table(ImportTable.readCsv(ok)).size)
    val big = ok + "one-more\n"
    assertTrue(failed(ImportTable.readCsv(big)).contains("ردیف"))
  }

  @Test fun styledBlankRowsFarBelowDoNotCountButDataDoes() {
    val styled = """<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>name</t></is></c></row>
      <row r="1048576"><c r="A1048576" s="3"/><c r="B1048576" t="inlineStr"><is><t> </t></is></c></row></sheetData></worksheet>"""
    assertEquals(listOf(listOf("name")), table(ImportTable.readXlsx(styled, null)))
    val far = """<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>name</t></is></c></row>
      <row r="900000"><c r="A900000" t="inlineStr"><is><t>x</t></is></c></row></sheetData></worksheet>"""
    assertTrue(failed(ImportTable.readXlsx(far, null)).contains("ردیف"))
  }

  @Test fun tooManyColumnsWithDataIsRejectedBlankStyledColumnsAreNot() {
    val wide = """<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>name</t></is></c>
      <c r="CV1" s="1"/></row><row r="2"><c r="A2" t="inlineStr"><is><t>x</t></is></c></row></sheetData></worksheet>"""
    assertEquals(listOf(listOf("name"), listOf("x")), table(ImportTable.readXlsx(wide, null)))
    val bad = """<worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>name</t></is></c>
      <c r="BM1"><v>1</v></c></row></sheetData></worksheet>"""
    assertTrue(failed(ImportTable.readXlsx(bad, null)).contains("ستون"))
    val csv = "name" + ",x".repeat(ImportTable.MAX_COLS) + "\n"
    assertTrue(failed(ImportTable.readCsv(csv)).contains("ستون"))
  }

  @Test fun columnLettersAreBase26() {
    assertEquals(0, ImportTable.column("A1"))
    assertEquals(25, ImportTable.column("Z9"))
    assertEquals(26, ImportTable.column("AA10"))
    assertEquals(63, ImportTable.column("BL2"))
    assertEquals(-1, ImportTable.column("12"))
  }

  /* ------------------------------ اثرِ انگشت ------------------------------ */

  @Test fun fingerprintIsTheSameForTheSameDataInCsvOrXlsx() {
    val csv = table(ImportTable.readCsv("name,price\n چای ,100\n\n"))
    val xlsx = table(ImportTable.readXlsx("""<worksheet><sheetData>
      <row r="1"><c r="A1" t="inlineStr"><is><t>name</t></is></c><c r="B1" t="inlineStr"><is><t>price</t></is></c></row>
      <row r="2"><c r="A2" t="inlineStr"><is><t>چای</t></is></c><c r="B2"><v>100</v></c><c r="C2" s="1"/></row></sheetData></worksheet>""", null))
    assertEquals(ImportTable.fingerprint(csv), ImportTable.fingerprint(xlsx))
    assertEquals(16, ImportTable.fingerprint(csv).length)
    assertNotEquals(ImportTable.fingerprint(csv), ImportTable.fingerprint(table(ImportTable.readCsv("name,price\nچای,101\n"))))
    //  جابه‌جاییِ مرزِ خانه‌ها اثرِ انگشتِ دیگری است
    assertNotEquals(
      ImportTable.fingerprint(listOf(listOf("ab", "c"))),
      ImportTable.fingerprint(listOf(listOf("a", "bc"))),
    )
  }
}
