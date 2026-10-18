package ir.vil3ntec.tohid.data

import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 *  فایلِ اکسلِ واقعی (‎.xlsx‎) — بی کتابخانه.
 *
 *  ‎.xlsx‎ یک ZIP است با چند فایلِ XML. این‌جا کمترینِ لازم ساخته می‌شود:
 *  یک یا چند برگه (`book` — خروجیِ کاملِ مالک، OPS-08)، راست‌به‌چپ، سطرِ اول پررنگ و ثابت (freeze)، ستون‌های عددی عدد
 *  (نه متن — تا جمع و فرمول در اکسل کار کند)، و پهنای ستون از روی متن.
 *  اکسل، لیبره‌آفیس و گوگل‌شیت همه بازش می‌کنند.
 *
 *  ورودی همان `ReportCsv.Sheet` است که چاپ و CSV هم از آن می‌خوانند —
 *  هر گزارشی که چاپ می‌شود، اکسل هم می‌شود.
 */
object Xlsx {

  /**
   *  یک خانه. نوعش را **خودِ داده** می‌گوید، نه حدس از روی متن.
   *
   *  ⚠️ چرا `Text` جداست: تلفنِ «0700»، بارکد و شناسه شبیهِ عدد‌اند؛ اگر
   *  عدد نوشته شوند، اکسل صفرِ اولشان را می‌خورد و بارکدِ سیزده‌رقمی را
   *  به نمادِ علمی می‌برد. خروجیِ کامل (OPS-08) هر رشته را رشته می‌نویسد.
   */
  sealed class Cell {
    data class Text(val s: String) : Cell()
    data class Num(val v: Double) : Cell()
    data class Bool(val v: Boolean) : Cell()
    /** زمان (میلی‌ثانیهٔ یونیکس) ⇐ تاریخ و ساعتِ اکسل، به وقتِ UTC */
    data class Time(val epochMs: Long) : Cell()
    /** متنِ گزارش‌ها: اگر عدد بنماید عدد می‌شود — همان رفتارِ همیشگیِ `write` */
    internal data class Guess(val raw: String) : Cell()
  }

  /** یک برگه؛ ردیفِ اول سرستون است (پررنگ و ثابت) */
  class Sheet(val name: String, val rows: List<List<Cell?>>)

  fun of(sheet: ReportCsv.Sheet): ByteArray = write(sheet.name, sheet.rows)

  fun write(title: String, rows: List<List<String>>): ByteArray =
    book(listOf(Sheet(title, rows.map { row -> row.map { Cell.Guess(it) } })))

  fun book(sheets: List<Sheet>): ByteArray {
    val out = ByteArrayOutputStream()
    book(sheets, out)
    return out.toByteArray()
  }

  /**
   *  کتابِ چندبرگه، مستقیم روی جریانِ خروجی (فایلِ بزرگ دو بار در حافظه
   *  نمی‌نشیند). جریان بسته **نمی‌شود** — مالکش صدازننده است.
   */
  fun book(sheets: List<Sheet>, sink: OutputStream) {
    val list = sheets.ifEmpty { listOf(Sheet("Sheet1", emptyList())) }
    val names = uniqueNames(list.map { it.name })
    //  بستنِ ZIP فشرده‌ساز را آزاد می‌کند ولی جریانِ صدازننده باز می‌ماند
    val keepOpen = object : FilterOutputStream(sink) {
      override fun write(b: ByteArray, off: Int, len: Int) = sink.write(b, off, len)
      override fun close() = sink.flush()
    }
    ZipOutputStream(keepOpen).use { zip -> writeBook(zip, list, names) }
  }

  private fun writeBook(zip: ZipOutputStream, list: List<Sheet>, names: List<String>) {
    fun put(name: String, text: String) {
      zip.putNextEntry(ZipEntry(name))
      zip.write(text.toByteArray(Charsets.UTF_8))
      zip.closeEntry()
    }
    put("[Content_Types].xml", contentTypes(list.size))
    put("_rels/.rels", RELS)
    put("xl/workbook.xml", workbook(names))
    put("xl/_rels/workbook.xml.rels", workbookRels(list.size))
    put("xl/styles.xml", STYLES)
    list.forEachIndexed { i, sheet ->
      zip.putNextEntry(ZipEntry("xl/worksheets/sheet${i + 1}.xml"))
      val w = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8), 64 * 1024)
      sheetXml(sheet.rows, w)
      w.flush()
      zip.closeEntry()
    }
  }

  /** نامِ برگه: حداکثر ۳۱ نویسه و بی نویسه‌های ممنوعِ اکسل */
  private fun sheetName(title: String): String =
    title.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim().take(31).ifBlank { "Sheet1" }

  /** اکسل دو برگهٔ هم‌نام (بی توجه به بزرگی و کوچکیِ حروف) را باز نمی‌کند */
  private fun uniqueNames(titles: List<String>): List<String> {
    val seen = HashSet<String>()
    return titles.map { t ->
      val base = sheetName(t)
      var name = base
      var n = 2
      while (!seen.add(name.lowercase())) {
        val tail = " ($n)"
        name = base.take(31 - tail.length) + tail
        n++
      }
      name
    }
  }

  private val NUMBER = Regex("^-?[0-9]+(\\.[0-9]+)?$")

  /** سقفِ متنِ یک خانه در اکسل؛ بیشتر از این، فایل باز نمی‌شود */
  const val MAX_CELL_TEXT = 32767

  private fun shown(c: Cell?): String = when (c) {
    null -> ""
    is Cell.Text -> c.s
    is Cell.Guess -> c.raw
    is Cell.Num -> num(c.v)
    is Cell.Bool -> "TRUE"
    is Cell.Time -> "0000-00-00 00:00"
  }

  /** عددِ درست بی ممیزِ اضافه («12» نه «12.0») */
  private fun num(v: Double): String =
    if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()

  private fun clip(s: String): String {
    if (s.length <= MAX_CELL_TEXT) return s
    var cut = MAX_CELL_TEXT - 1
    if (Character.isHighSurrogate(s[cut - 1])) cut--
    return s.substring(0, cut) + "…"
  }

  private fun sheetXml(rows: List<List<Cell?>>, w: Appendable) {
    val cols = rows.maxOfOrNull { it.size } ?: 0
    w.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    w.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
    w.append("""<sheetViews><sheetView rightToLeft="1" workbookViewId="0">""")
    if (rows.size > 1) w.append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
    w.append("</sheetView></sheetViews>")
    if (cols > 0) {
      val width = IntArray(cols)
      for (row in rows) row.forEachIndexed { c, cell -> width[c] = maxOf(width[c], shown(cell).length) }
      w.append("<cols>")
      for (c in 0 until cols) {
        val wd = width[c].coerceIn(6, 60) + 2
        w.append("""<col min="${c + 1}" max="${c + 1}" width="$wd" customWidth="1"/>""")
      }
      w.append("</cols>")
    }
    w.append("<sheetData>")
    rows.forEachIndexed { r, row ->
      w.append("""<row r="${r + 1}">""")
      row.forEachIndexed { c, cell ->
        if (cell != null) cellXml(col(c) + (r + 1), cell, r == 0, w)
      }
      w.append("</row>")
    }
    w.append("</sheetData></worksheet>")
  }

  private fun text(ref: String, s: String, head: Boolean, w: Appendable) {
    w.append("""<c r="$ref" t="inlineStr"${if (head) " s=\"1\"" else ""}><is><t xml:space="preserve">""")
    w.append(esc(clip(s)))
    w.append("</t></is></c>")
  }

  private fun cellXml(ref: String, cell: Cell, head: Boolean, w: Appendable) {
    if (head) return text(ref, shown(cell), true, w)
    when (cell) {
      is Cell.Text -> text(ref, cell.s, false, w)
      is Cell.Guess -> {
        val v = cell.raw.trim().replace("٬", "").replace(",", "")
        if (v.isNotEmpty() && NUMBER.matches(v)) {
          w.append("""<c r="$ref"${if (v.contains('.')) "" else " s=\"2\""}><v>$v</v></c>""")
        } else text(ref, cell.raw, false, w)
      }
      is Cell.Num ->
        if (cell.v.isNaN() || cell.v.isInfinite()) text(ref, cell.v.toString(), false, w)
        else {
          val v = num(cell.v)
          w.append("""<c r="$ref"${if (v.contains('.') || v.contains('E')) "" else " s=\"2\""}><v>$v</v></c>""")
        }
      is Cell.Bool -> w.append("""<c r="$ref" t="b"><v>${if (cell.v) 1 else 0}</v></c>""")
      //  ۲۵۵۶۹ = روزهای ۱۹۰۰/۰۱/۰۰ تا ۱۹۷۰/۰۱/۰۱ در تقویمِ اکسل
      is Cell.Time -> w.append("""<c r="$ref" s="3"><v>${cell.epochMs / 86_400_000.0 + 25569}</v></c>""")
    }
  }

  /** ۰ ⇐ A، ۲۵ ⇐ Z، ۲۶ ⇐ AA */
  private fun col(i: Int): String {
    var n = i + 1
    val sb = StringBuilder()
    while (n > 0) { val m = (n - 1) % 26; sb.insert(0, ('A' + m)); n = (n - 1) / 26 }
    return sb.toString()
  }

  private fun esc(s: String): String = buildString {
    for (ch in s) when {
      ch == '&' -> append("&amp;")
      ch == '<' -> append("&lt;")
      ch == '>' -> append("&gt;")
      ch == '"' -> append("&quot;")
      ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> {}
      else -> append(ch)
    }
  }

  private fun workbook(names: List<String>) = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
    names.forEachIndexed { i, n -> append("""<sheet name="${esc(n)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
    append("</sheets></workbook>")
  }

  private fun contentTypes(n: Int) = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
    for (i in 1..n) append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
    append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
  }

  /** برگه‌ها rId1…rIdN، و سبک‌ها پس از آن‌ها */
  private fun workbookRels(n: Int) = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
    for (i in 1..n) append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
    append("""<Relationship Id="rId${n + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
  }

  private const val RELS =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

  /** ۰ عادی، ۱ سرستون (پررنگ با زمینه)، ۲ عدد با جداکنندهٔ هزارگان، ۳ تاریخ و ساعت */
  private const val STYLES =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="yyyy-mm-dd hh:mm"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Tahoma"/></font><font><b/><sz val="11"/><name val="Tahoma"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE8E6F5"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="4"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/><xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
