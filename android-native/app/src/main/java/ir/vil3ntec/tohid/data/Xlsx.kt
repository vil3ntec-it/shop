package ir.vil3ntec.tohid.data

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 *  فایلِ اکسلِ واقعی (‎.xlsx‎) — بی کتابخانه.
 *
 *  ‎.xlsx‎ یک ZIP است با چند فایلِ XML. این‌جا کمترینِ لازم ساخته می‌شود:
 *  یک برگه، راست‌به‌چپ، سطرِ اول پررنگ و ثابت (freeze)، ستون‌های عددی عدد
 *  (نه متن — تا جمع و فرمول در اکسل کار کند)، و پهنای ستون از روی متن.
 *  اکسل، لیبره‌آفیس و گوگل‌شیت همه بازش می‌کنند.
 *
 *  ورودی همان `ReportCsv.Sheet` است که چاپ و CSV هم از آن می‌خوانند —
 *  هر گزارشی که چاپ می‌شود، اکسل هم می‌شود.
 */
object Xlsx {

  fun of(sheet: ReportCsv.Sheet): ByteArray = write(sheet.name, sheet.rows)

  fun write(title: String, rows: List<List<String>>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
      fun put(name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
      }
      put("[Content_Types].xml", CONTENT_TYPES)
      put("_rels/.rels", RELS)
      put("xl/workbook.xml", workbook(sheetName(title)))
      put("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
      put("xl/styles.xml", STYLES)
      put("xl/worksheets/sheet1.xml", sheetXml(rows))
    }
    return out.toByteArray()
  }

  /** نامِ برگه: حداکثر ۳۱ نویسه و بی نویسه‌های ممنوعِ اکسل */
  private fun sheetName(title: String): String =
    title.replace(Regex("[\\\\/?*\\[\\]:]"), " ").trim().take(31).ifBlank { "Sheet1" }

  private val NUMBER = Regex("^-?[0-9]+(\\.[0-9]+)?$")

  private fun sheetXml(rows: List<List<String>>): String = buildString {
    val cols = rows.maxOfOrNull { it.size } ?: 0
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
    append("""<sheetViews><sheetView rightToLeft="1" workbookViewId="0">""")
    if (rows.size > 1) append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
    append("</sheetView></sheetViews>")
    if (cols > 0) {
      append("<cols>")
      for (c in 0 until cols) {
        val w = rows.maxOf { (it.getOrNull(c) ?: "").length }.coerceIn(6, 60) + 2
        append("""<col min="${c + 1}" max="${c + 1}" width="$w" customWidth="1"/>""")
      }
      append("</cols>")
    }
    append("<sheetData>")
    rows.forEachIndexed { r, row ->
      append("""<row r="${r + 1}">""")
      row.forEachIndexed { c, raw ->
        val ref = col(c) + (r + 1)
        val head = r == 0
        val v = raw.trim().replace("٬", "").replace(",", "")
        if (!head && v.isNotEmpty() && NUMBER.matches(v)) {
          append("""<c r="$ref"${if (v.contains('.')) "" else " s=\"2\""}><v>$v</v></c>""")
        } else {
          append("""<c r="$ref" t="inlineStr"${if (head) " s=\"1\"" else ""}><is><t xml:space="preserve">${esc(raw)}</t></is></c>""")
        }
      }
      append("</row>")
    }
    append("</sheetData></worksheet>")
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

  private fun workbook(name: String) =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="${esc(name)}" sheetId="1" r:id="rId1"/></sheets></workbook>"""

  private const val CONTENT_TYPES =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>"""

  private const val RELS =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

  private const val WORKBOOK_RELS =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>"""

  /** ۰ عادی، ۱ سرستون (پررنگ با زمینه)، ۲ عدد با جداکنندهٔ هزارگان */
  private const val STYLES =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Tahoma"/></font><font><b/><sz val="11"/><name val="Tahoma"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFE8E6F5"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""
}
