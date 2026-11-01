package ir.vil3ntec.tohid.core.product

/*
 *  جدولِ ویرایشِ درجای کالا (DeskGrid) — PRD-22، §۵ بندِ ۱۳ و §۶.۳.
 *
 *  هر ردیف یک کالای موجود، هر ستون یک فیلد؛ متنِ خانه همان است که کاربر می‌بیند و می‌نویسد. این‌جا فقط
 *  منطقِ خالص: نوشتن در خانه، چسباندن از Excel (متنِ جداشده با تب، گیومه‌دار برای خانهٔ چندخطی)،
 *  «تکرارِ ردیف» (Ctrl+D)، برگرداندن (Ctrl+Z)، «کدام خانه عوض شد» برای پیش‌نمایش، و ایرادِ هر خانه.
 *  ثبت کارِ برنامه است (یا همه یا هیچ، با همان موتورِ ویرایشِ کالا و سابقه‌اش).
 *
 *  ── قاعده‌ها ────────────────────────────────────────────────────────
 *  • جدول ردیفِ تازه نمی‌سازد: چسباندنِ بلندتر از جدول بریده و شمرده می‌شود ([Pasted.cutRows])، ستونِ
 *    اضافه هم ([Pasted.cutCols]) — هیچ‌چیز بی‌صدا گم نمی‌شود.
 *  • «تکرارِ ردیف» همهٔ خانه‌های ردیفِ بالا را می‌آورد جز نام و بارکد، که باید یکتا بمانند.
 *  • هر تغییر (یک خانه، یک چسباندن، یک تکرار) یک گامِ برگرداندن است؛ حداکثر [MAX_UNDO] گام.
 *  • پول به یک‌صدم و مقدار به هزارم با `DecimalText` (رقمِ فارسی، «٬» و «,» ِ هزارگان، «٫»)؛ تهی ⇒ صفر.
 *  • نامِ تهی، نامِ تکراری (با ردیفِ دیگرِ جدول یا کالای بیرونِ جدول، همان کلیدِ `SimilarNames.exactKey`
 *    ِ موتور)، عددِ نادرست یا منفی، و بارکدِ کالای دیگر (یا دو ردیفِ جدول) ⇒ ایرادِ همان خانه.
 *
 *  منطقِ خالص (commonMain، بی java.* و android.*)؛ آزمون: `DeskGridTest`.
 */
object DeskGrid {

  /** ستون‌ها به ترتیبِ نمایش */
  enum class Col { NAME, CATEGORY, UNIT, BUY, SELL, MIN, BARCODE }

  val COLS: List<Col> = Col.entries

  /** ستون‌هایی که «تکرارِ ردیف» می‌آورد — نام و بارکد یکتایند */
  private val COPYABLE = setOf(Col.CATEGORY, Col.UNIT, Col.BUY, Col.SELL, Col.MIN)

  const val MAX_UNDO = 100

  /** سقفِ نویسهٔ هر خانه — متنِ چسبانده‌شدهٔ بی‌حد فرم و حافظه را سنگین می‌کند */
  const val MAX_CELL = 200

  enum class Problem { NAME_EMPTY, NAME_TAKEN, BAD_NUMBER, NEGATIVE, BARCODE_TAKEN }

  /** یک ردیف: شناسهٔ کالا و متنِ هر ستون به ترتیبِ [COLS] */
  data class Row(val id: String, val cells: List<String>) {
    init { require(cells.size == COLS.size) { "cells" } }
    operator fun get(c: Col): String = cells[c.ordinal]
    fun with(c: Col, text: String): Row =
      if (this[c] == text) this else copy(cells = cells.toMutableList().also { it[c.ordinal] = text })
  }

  data class Cell(val row: Int, val col: Col)

  /** نتیجهٔ چسباندن: جدولِ تازه، خانه‌های نوشته‌شده و آنچه بیرونِ جدول افتاد */
  data class Pasted(val sheet: Sheet, val cells: List<Cell>, val cutRows: Int, val cutCols: Int)

  /** حالتِ جدول — تغییرناپذیر؛ هر کار جدولِ تازه‌ای برمی‌گرداند */
  class Sheet internal constructor(
    val rows: List<Row>,
    private val base: Map<String, Row>,
    private val history: List<List<Row>>,
  ) {
    val canUndo: Boolean get() = history.isNotEmpty()

    /** ردیف‌هایی که با آغاز فرق دارند — همان‌ها ثبت می‌شوند */
    val changed: List<Row> get() = rows.filter { base[it.id] != it }

    fun isChanged(row: Int, col: Col): Boolean = base[rows[row].id]?.get(col) != rows[row][col]

    /** ردیفِ آغازینِ همین کالا (برای «قدیم ⇐ تازه») */
    fun original(id: String): Row? = base[id]

    private fun next(newRows: List<Row>): Sheet =
      if (newRows == rows) this else Sheet(newRows, base, (history + listOf(rows)).takeLast(MAX_UNDO))

    /** نوشتن در یک خانه؛ همان متن ⇒ همان جدول (گامِ برگرداندنِ تهی نمی‌سازد) */
    fun set(row: Int, col: Col, text: String): Sheet {
      if (row !in rows.indices) return this
      return next(rows.toMutableList().also { it[row] = it[row].with(col, cell(text)) })
    }

    /**
     *  چسباندن از [row]/[col]: هر خطِ [clip] یک ردیف و هر تب یک خانه، به راست و پایین. خانهٔ تهیِ
     *  چسبانده‌شده هم نوشته می‌شود (همان رفتارِ Excel). کلِ چسباندن یک گامِ برگرداندن است.
     */
    fun paste(row: Int, col: Col, clip: String): Pasted {
      val table = parseClip(clip)
      if (row !in rows.indices || table.isEmpty()) return Pasted(this, emptyList(), 0, 0)
      val out = rows.toMutableList()
      val touched = ArrayList<Cell>()
      var cutCols = 0
      val fit = minOf(table.size, rows.size - row)
      for (i in 0 until fit) {
        val values = table[i]
        for ((j, v) in values.withIndex()) {
          val ci = col.ordinal + j
          if (ci >= COLS.size) { cutCols++; continue }
          out[row + i] = out[row + i].with(COLS[ci], cell(v))
          touched += Cell(row + i, COLS[ci])
        }
      }
      return Pasted(next(out), touched, table.size - fit, cutCols)
    }

    /** «تکرارِ ردیف» (Ctrl+D): خانه‌های ردیفِ بالا جز نام و بارکد؛ ردیفِ نخست ⇒ همان جدول */
    fun duplicateAbove(row: Int): Sheet {
      if (row !in rows.indices || row == 0) return this
      val above = rows[row - 1]
      var r = rows[row]
      for (c in COPYABLE) r = r.with(c, above[c])
      return next(rows.toMutableList().also { it[row] = r })
    }

    /** یک گام برگرداندن؛ پشتهٔ تهی ⇒ همان جدول */
    fun undo(): Sheet = if (history.isEmpty()) this else Sheet(history.last(), base, history.dropLast(1))
  }

  /** جدولِ تازه از ردیف‌های آغازین (همان‌ها «بی تغییر» شمرده می‌شوند) */
  fun sheet(rows: List<Row>): Sheet = Sheet(rows, rows.associateBy { it.id }, emptyList())

  /** متنِ یک خانه: خطِ تازه و تب به فاصله، فاصلهٔ دو سر بیرون، حداکثر [MAX_CELL] */
  fun cell(text: String): String =
    text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim().take(MAX_CELL)

  /** متنِ کلیپ‌بوردِ Excel ⇒ ردیف‌ها و خانه‌ها؛ همیشه تب (نه حدسِ «,» که «۱,۲۵۰» را دو خانه کند) */
  fun parseClip(text: String): List<List<String>> =
    ImportTable.csv(text, forceSep = '\t').let { rows -> if (rows.lastOrNull()?.all { it.isEmpty() } == true) rows.dropLast(1) else rows }

  /** پولِ خانه به یک‌صدم؛ تهی ⇒ ۰، نادرست ⇒ null */
  fun money(text: String): Long? = if (text.isBlank()) 0L else DecimalText.scaled(text, 2)

  /** مقدارِ خانه به هزارم؛ تهی ⇒ ۰، نادرست ⇒ null */
  fun quantity(text: String): Long? = if (text.isBlank()) 0L else DecimalText.scaled(text, 3)

  /**
   *  ایرادِ هر خانه برای پیش‌نمایش و ثبت؛ تهی ⇒ جدول ثبت‌شدنی است.
   *  @param outsideNames کلیدِ نامِ کالاهای بیرونِ جدول (`SimilarNames.exactKey`)
   *  @param barcodeOwner بارکد ⇒ شناسهٔ کالای دارنده در کلِ دکان (بارکدِ بسته هم)
   */
  fun problems(sheet: Sheet, outsideNames: Set<String>, barcodeOwner: (String) -> String?): Map<Cell, Problem> {
    val out = LinkedHashMap<Cell, Problem>()
    val nameRows = HashMap<String, Int>()
    val codeRows = HashMap<String, Int>()
    for ((i, r) in sheet.rows.withIndex()) {
      val key = SimilarNames.exactKey(r[Col.NAME])
      when {
        r[Col.NAME].isBlank() -> out[Cell(i, Col.NAME)] = Problem.NAME_EMPTY
        key in outsideNames || nameRows.containsKey(key) -> out[Cell(i, Col.NAME)] = Problem.NAME_TAKEN
        else -> nameRows[key] = i
      }
      for (c in listOf(Col.BUY, Col.SELL)) money(r[c]).let { v ->
        if (v == null) out[Cell(i, c)] = Problem.BAD_NUMBER else if (v < 0) out[Cell(i, c)] = Problem.NEGATIVE
      }
      quantity(r[Col.MIN]).let { v ->
        if (v == null) out[Cell(i, Col.MIN)] = Problem.BAD_NUMBER else if (v < 0) out[Cell(i, Col.MIN)] = Problem.NEGATIVE
      }
      val code = r[Col.BARCODE]
      if (code.isNotEmpty()) {
        //  فقط بارکدِ عوض‌شده سنجیده می‌شود — تعارضِ آفلاینِ قدیمی ردیف را قفل نمی‌کند (همان قاعدهٔ موتور)
        val fresh = sheet.original(r.id)?.get(Col.BARCODE) != code
        val owner = barcodeOwner(code)
        if ((fresh && owner != null && owner != r.id) || codeRows.containsKey(code)) out[Cell(i, Col.BARCODE)] = Problem.BARCODE_TAKEN
        else codeRows[code] = i
      }
    }
    return out
  }
}
