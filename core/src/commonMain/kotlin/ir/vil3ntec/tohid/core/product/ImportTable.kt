package ir.vil3ntec.tohid.core.product

/*
 *  جدولِ خامِ «ورودِ کالا از فایل» — PRD-20 (§۶.۳).
 *
 *  • `csv`: همان خوانندهٔ همیشگیِ `ProductImport.parse` (بایت‌به‌بایت؛ مسیرِ پرچمِ خاموش از همین می‌خواند).
 *  • `readCsv`/`readXlsx`: مسیرِ تازه (پشتِ `ShopPolicy.productTools`): جداکنندهٔ تب هم، برگهٔ اولِ xlsx از
 *    XML ِ خام (رشتهٔ مشترک، رشتهٔ درون‌خط، عدد، درست/نادرست، فرمول با مقدارِ ذخیره) و سقف‌ها.
 *    بازکردنِ ZIP کارِ سکوست (`ProductImport.read` در برنامه)؛ این‌جا فقط متن، بی java.* و android.*.
 *  • ⛔ بی DTD و بی بسطِ موجودیت جز پنج موجودیتِ XML و `&#…;` — نه XXE، نه «میلیارد خنده».
 *  • سقف‌ها کلِ فایل را رد می‌کنند، نه تکه‌ای را بی‌صدا: بیش از `MAX_ROWS` ردیفِ داده یا دادهٔ ستونِ
 *    `MAX_COLS` به بعد. ردیف و ستونِ خالیِ قالب‌دار (اکسل تا ردیفِ ۱۰۴۸۵۷۶ می‌نویسد) شمرده نمی‌شوند.
 *  • ردیفِ خالیِ میانی نگه داشته می‌شود تا شمارهٔ ردیف همان شمارهٔ اکسل بماند؛ خانه و ردیفِ خالیِ ته برداشته.
 *  • `fingerprint`: اثرِ انگشتِ دادهٔ فایل (نه بایت‌ها) برای «این فایل پیش‌تر وارد شده».
 *  آزمون: `ImportTableTest` (commonTest).
 */
object ImportTable {

  /** فایلِ برداشته‌شده (CSV یا xlsx ِ فشرده) */
  const val MAX_FILE_BYTES: Int = 5 * 1024 * 1024
  /** هر بخشِ xlsx پس از بازشدن (برگه و رشته‌های مشترک) */
  const val MAX_PART_BYTES: Int = 16 * 1024 * 1024
  /** ردیفِ داده، بی سرستون */
  const val MAX_ROWS: Int = 5_000
  const val MAX_COLS: Int = 64
  const val DEFAULT_SHEET: String = "xl/worksheets/sheet1.xml"

  sealed interface Read {
    data class Table(val rows: List<List<String>>) : Read
    data class Failed(val reason: String) : Read
  }

  /* ------------------------------ CSV ------------------------------ */

  /**
   *  CSVِ استاندارد: گیومه، گیومهٔ دوتایی، خطِ تازه درونِ گیومه، BOM؛ جداکنندهٔ «;» اگر سرستون بیشتر
   *  «;» دارد تا «,». `tabs` (فقط مسیرِ تازه): «ذخیره به متنِ یونیکد»ِ اکسل تب‌دار است.
   *  ردیفِ خالی نگه داشته می‌شود تا شمارهٔ ردیف با اکسل یکی بماند.
   */
  fun csv(text: String, tabs: Boolean = false): List<List<String>> {
    val src = text.removePrefix("\uFEFF")
    val firstLine = src.lineSequence().firstOrNull().orEmpty()
    val semi = firstLine.count { it == ';' }
    val comma = firstLine.count { it == ',' }
    val sep = when {
      tabs && firstLine.count { it == '\t' } > maxOf(semi, comma) -> '\t'
      semi > comma -> ';'
      else -> ','
    }
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < src.length) {
      val c = src[i]
      if (quoted) {
        if (c == '"') {
          if (i + 1 < src.length && src[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
        } else cell.append(c)
      } else when (c) {
        '"' -> quoted = true
        sep -> { row += cell.toString(); cell.clear() }
        '\r' -> {}
        '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = ArrayList() }
        else -> cell.append(c)
      }
      i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
    return rows
  }

  fun readCsv(text: String): Read {
    val sparse = HashMap<Int, List<String>>()
    csv(text, tabs = true).forEachIndexed { i, r -> sparse[i] = r }
    return bounded(sparse)
  }

  /* ------------------------------ xlsx ------------------------------ */

  /** مسیرِ برگهٔ اول در ZIP: `<sheet r:id>` ِ نخست در workbook ⇐ `Target` در rels؛ نبود ⇒ `DEFAULT_SHEET` */
  fun sheetPath(workbookXml: String?, relsXml: String?): String {
    if (workbookXml == null || relsXml == null) return DEFAULT_SHEET
    var rid: String? = null
    Xml(workbookXml).scan { e -> if (rid == null && e.start && e.local == "sheet") rid = e.attrEndingWith(":id") }
    val id = rid ?: return DEFAULT_SHEET
    var target: String? = null
    Xml(relsXml).scan { e -> if (target == null && e.start && e.local == "Relationship" && e.attr("Id") == id) target = e.attr("Target") }
    val t = target?.trim()?.takeIf { it.isNotEmpty() } ?: return DEFAULT_SHEET
    return if (t.startsWith("/")) t.removePrefix("/") else "xl/" + t.removePrefix("./")
  }

  /** «AB12» ⇐ ۲۷ (از صفر)؛ بی حرف ⇒ ۱- */
  fun column(ref: String): Int {
    var n = 0
    var letters = 0
    for (ch in ref) {
      val u = ch.uppercaseChar()
      if (u !in 'A'..'Z') break
      n = n * 26 + (u - 'A' + 1)
      letters++
      if (letters > 4) return -1
    }
    return if (letters == 0) -1 else n - 1
  }

  /**
   *  برگهٔ xlsx ⇒ ردیف‌ها. فقط خانه‌های `<c>` در `<sheetData>`؛ نوع از `t`:
   *  `s` رشتهٔ مشترک، `inlineStr`، `str` (نتیجهٔ فرمول)، `b` (TRUE/FALSE)، `e`، و عدد (بی `t`) که
   *  نمادِ علمیِ عددِ درست را باز می‌کند (`1.2E+3` ⇐ `1200`). قالب و تاریخ نادیده‌اند — ورود عدد و متن می‌خواهد.
   */
  fun readXlsx(sheetXml: String, sharedStringsXml: String?): Read {
    val shared = sharedStringsXml?.let(::sharedStrings).orEmpty()
    val rows = HashMap<Int, MutableList<String>>()
    var failed: String? = null
    var rowIndex = -1
    var nextCol = 0
    var cellCol = 0
    var cellType = ""
    var inCell = false
    var inV = false
    var inIs = false
    var inT = false
    var inRph = 0
    val v = StringBuilder()
    val inline = StringBuilder()

    fun put(col: Int, value: String) {
      if (value.isBlank()) return
      if (col >= MAX_COLS) { failed = failed ?: colsMessage(); return }
      if (rowIndex > MAX_ROWS) { failed = failed ?: rowsMessage(); return }
      val r = rows.getOrPut(rowIndex) { ArrayList() }
      while (r.size <= col) r += ""
      r[col] = value
    }

    Xml(sheetXml).scan { e ->
      if (failed != null) return@scan
      when {
        e.start && e.local == "row" -> {
          rowIndex = e.attr("r")?.toIntOrNull()?.minus(1)?.takeIf { it >= 0 } ?: (rowIndex + 1)
          nextCol = 0
        }
        e.start && e.local == "c" -> {
          cellCol = e.attr("r")?.let(::column)?.takeIf { it >= 0 } ?: nextCol
          nextCol = cellCol + 1
          cellType = e.attr("t").orEmpty()
          if (rowIndex < 0) rowIndex = 0
          if (!e.selfClosing) { inCell = true; v.clear(); inline.clear() }
        }
        e.end && e.local == "c" -> {
          inCell = false
          val raw = v.toString()
          //  رشتهٔ مشترک پیش‌تر از `_xHHHH_` باز شده؛ دوباره باز نمی‌شود
          val value = when (cellType) {
            "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
            "inlineStr" -> ooxml(inline.toString())
            "b" -> if (raw.trim() == "1") "TRUE" else if (raw.trim() == "0") "FALSE" else raw
            "str", "e", "d" -> ooxml(raw)
            else -> number(raw.trim())
          }
          put(cellCol, value)
        }
        inCell && e.start && e.local == "v" -> inV = !e.selfClosing
        inCell && e.end && e.local == "v" -> inV = false
        inCell && e.start && e.local == "is" -> inIs = !e.selfClosing
        inCell && e.end && e.local == "is" -> inIs = false
        inIs && e.start && e.local == "rPh" -> if (!e.selfClosing) inRph++
        inIs && e.end && e.local == "rPh" -> inRph--
        inIs && e.start && e.local == "t" -> inT = !e.selfClosing
        inIs && e.end && e.local == "t" -> inT = false
        e.text != null -> {
          if (inV) v.append(e.text)
          else if (inIs && inT && inRph == 0) inline.append(e.text)
        }
      }
    }
    failed?.let { return Read.Failed(it) }
    return bounded(rows)
  }

  /** رشته‌های مشترک: متنِ همهٔ `<t>`های هر `<si>` پشتِ هم، جز آوانویسیِ `<rPh>` */
  private fun sharedStrings(xml: String): List<String> {
    val out = ArrayList<String>()
    val cur = StringBuilder()
    var inSi = false
    var inT = false
    var inRph = 0
    Xml(xml).scan { e ->
      when {
        e.start && e.local == "si" -> { if (e.selfClosing) out += "" else { inSi = true; cur.clear() } }
        e.end && e.local == "si" -> { inSi = false; out += ooxml(cur.toString()) }
        inSi && e.start && e.local == "rPh" -> if (!e.selfClosing) inRph++
        inSi && e.end && e.local == "rPh" -> inRph--
        inSi && e.start && e.local == "t" -> inT = !e.selfClosing
        inSi && e.end && e.local == "t" -> inT = false
        e.text != null -> if (inSi && inT && inRph == 0) cur.append(e.text)
      }
    }
    return out
  }

  /** عددِ خامِ xlsx: نمادِ علمیِ عددِ درستِ دقیق (زیرِ ۲^۵۳) باز می‌شود؛ بقیه همان متن */
  private fun number(raw: String): String {
    if (raw.none { it == 'E' || it == 'e' }) return raw
    val d = raw.toDoubleOrNull() ?: return raw
    if (d.isNaN() || d.isInfinite() || d != kotlin.math.floor(d) || kotlin.math.abs(d) >= 9.007199254740991E15) return raw
    return d.toLong().toString()
  }

  /** `_x000D_` ِ OOXML ⇐ نویسه */
  private fun ooxml(s: String): String {
    if (!s.contains("_x")) return s
    val sb = StringBuilder()
    var i = 0
    while (i < s.length) {
      if (i + 6 < s.length && s[i] == '_' && s[i + 1] == 'x' && s[i + 6] == '_') {
        val code = s.substring(i + 2, i + 6).toIntOrNull(16)
        if (code != null) { sb.append(code.toChar()); i += 7; continue }
      }
      sb.append(s[i]); i++
    }
    return sb.toString()
  }

  /* ------------------------------ سقف و پرداخت ------------------------------ */

  private fun rowsMessage() = "فایل بیش از $MAX_ROWS ردیف دارد — آن را چند تکه کنید"
  private fun colsMessage() = "فایل بیش از $MAX_COLS ستون دارد — فقط ستون‌های کالا را نگه دارید"

  /** ردیف‌های پراکنده ⇒ فهرستِ پیوسته؛ خانه‌های خالیِ ته و ردیف‌های خالیِ ته برداشته */
  private fun bounded(sparse: Map<Int, List<String>>): Read {
    var last = -1
    val trimmed = HashMap<Int, List<String>>()
    for ((i, r) in sparse) {
      var end = r.size
      while (end > 0 && r[end - 1].isBlank()) end--
      if (end == 0) continue
      if (end > MAX_COLS) return Read.Failed(colsMessage())
      if (i > MAX_ROWS) return Read.Failed(rowsMessage())
      trimmed[i] = if (end == r.size) r else r.subList(0, end)
      if (i > last) last = i
    }
    return Read.Table((0..last).map { trimmed[it] ?: emptyList() })
  }

  /** FNV-1a ِ ۶۴بیتی روی خانه‌های پیراسته؛ ردیفِ خالی و خانهٔ خالیِ ته اثری ندارند */
  fun fingerprint(rows: List<List<String>>): String {
    var h = -0x340d631b7bdddcdbL          //  0xcbf29ce484222325
    val prime = 0x100000001b3L
    fun eat(c: Int) { h = (h xor c.toLong()) * prime }
    for (r in rows) {
      val cells = r.map { it.trim() }.dropLastWhile { it.isEmpty() }
      if (cells.isEmpty()) continue
      cells.forEachIndexed { i, cell ->
        if (i > 0) eat(0x1F)
        for (ch in cell) eat(ch.code)
      }
      eat(0x1E)
    }
    return h.toULong().toString(16).padStart(16, '0')
  }

  /* ------------------------------ XML ِ کمینه ------------------------------ */

  /**
   *  پویشگرِ کوچکِ XML برای همین سه بخشِ xlsx: برچسبِ آغاز/پایان (نامِ محلی بی پیشوند)، ویژگی‌ها و متن.
   *  دستورِ پردازش، توضیح و DOCTYPE نادیده؛ CDATA متن است؛ فقط پنج موجودیتِ XML و `&#…;` باز می‌شوند.
   */
  internal class Xml(private val s: String) {

    class Event(
      val local: String = "",
      val start: Boolean = false,
      val end: Boolean = false,
      val selfClosing: Boolean = false,
      private val attrs: String = "",
      val text: String? = null,
    ) {
      fun attr(name: String): String? = attrs(attrs).firstOrNull { it.first == name }?.second
      fun attrEndingWith(suffix: String): String? = attrs(attrs).firstOrNull { it.first.endsWith(suffix) }?.second
    }

    fun scan(onEvent: (Event) -> Unit) {
      var i = 0
      val n = s.length
      while (i < n) {
        val lt = s.indexOf('<', i)
        if (lt < 0) { emitText(s.substring(i), onEvent); return }
        if (lt > i) emitText(s.substring(i, lt), onEvent)
        when {
          s.startsWith("<!--", lt) -> i = s.indexOf("-->", lt + 4).let { if (it < 0) n else it + 3 }
          s.startsWith("<![CDATA[", lt) -> {
            val e = s.indexOf("]]>", lt + 9)
            onEvent(Event(text = s.substring(lt + 9, if (e < 0) n else e)))
            i = if (e < 0) n else e + 3
          }
          s.startsWith("<!", lt) -> i = skipDecl(lt)
          s.startsWith("<?", lt) -> i = s.indexOf("?>", lt + 2).let { if (it < 0) n else it + 2 }
          else -> {
            val gt = tagEnd(lt)
            if (gt < 0) return
            val inner = s.substring(lt + 1, gt)
            if (inner.startsWith("/")) {
              onEvent(Event(local = localName(inner.substring(1).trim()), end = true))
            } else {
              val self = inner.endsWith("/")
              val body = if (self) inner.dropLast(1) else inner
              val nameEnd = body.indexOfFirst { it.isWhitespace() }.let { if (it < 0) body.length else it }
              //  برچسبِ خودبسته فقط یک رویدادِ آغاز با `selfClosing` است — پایانِ جدا ندارد
              onEvent(Event(local = localName(body.substring(0, nameEnd)), start = true, selfClosing = self,
                attrs = body.substring(nameEnd)))
            }
            i = gt + 1
          }
        }
      }
    }

    /** `>` ِ پایانِ برچسب، با گذر از مقدارِ ویژگیِ گیومه‌دار */
    private fun tagEnd(from: Int): Int {
      var q = 0.toChar()
      var i = from + 1
      while (i < s.length) {
        val c = s[i]
        if (q != 0.toChar()) { if (c == q) q = 0.toChar() }
        else if (c == '"' || c == '\'') q = c
        else if (c == '>') return i
        i++
      }
      return -1
    }

    /** `<!DOCTYPE … [ … ]>`: کروشهٔ درونی و متنِ گیومه‌دار (که `]>` هم می‌تواند داشته باشد) گذر می‌شوند */
    private fun skipDecl(from: Int): Int {
      var depth = 0
      var q = 0.toChar()
      var i = from + 2
      while (i < s.length) {
        val c = s[i]
        if (q != 0.toChar()) { if (c == q) q = 0.toChar() }
        else when (c) {
          '"', '\'' -> q = c
          '[' -> depth++
          ']' -> depth--
          '>' -> if (depth <= 0) return i + 1
        }
        i++
      }
      return s.length
    }

    private fun emitText(raw: String, onEvent: (Event) -> Unit) = onEvent(Event(text = decode(raw)))

    companion object {
      fun localName(qname: String): String = qname.substringAfterLast(':')

      private fun attrs(src: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var i = 0
        while (i < src.length) {
          while (i < src.length && src[i].isWhitespace()) i++
          val eq = src.indexOf('=', i)
          if (eq < 0) break
          val name = src.substring(i, eq).trim()
          var j = eq + 1
          while (j < src.length && src[j].isWhitespace()) j++
          if (j >= src.length) break
          val q = src[j]
          if (q != '"' && q != '\'') break
          val close = src.indexOf(q, j + 1)
          if (close < 0) break
          out += name to decode(src.substring(j + 1, close))
          i = close + 1
        }
        return out
      }

      /** فقط `&lt; &gt; &amp; &quot; &apos;` و `&#…;`؛ هر موجودیتِ دیگر همان‌طور می‌ماند */
      fun decode(raw: String): String {
        if (!raw.contains('&')) return raw
        val sb = StringBuilder()
        var i = 0
        while (i < raw.length) {
          val c = raw[i]
          if (c == '&') {
            val semi = raw.indexOf(';', i + 1)
            if (semi > i && semi - i <= 10) {
              val name = raw.substring(i + 1, semi)
              val rep: String? = when {
                name == "lt" -> "<"
                name == "gt" -> ">"
                name == "amp" -> "&"
                name == "quot" -> "\""
                name == "apos" -> "'"
                name.startsWith("#x") || name.startsWith("#X") -> name.substring(2).toIntOrNull(16)?.let(::codePoint)
                name.startsWith("#") -> name.substring(1).toIntOrNull()?.let(::codePoint)
                else -> null
              }
              if (rep != null) { sb.append(rep); i = semi + 1; continue }
            }
          }
          sb.append(c)
          i++
        }
        return sb.toString()
      }

      private fun codePoint(cp: Int): String? = when {
        cp < 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF -> null
        cp < 0x10000 -> cp.toChar().toString()
        else -> {
          val v = cp - 0x10000
          charArrayOf((0xD800 + (v shr 10)).toChar(), (0xDC00 + (v and 0x3FF)).toChar()).concatToString()
        }
      }
    }
  }
}
