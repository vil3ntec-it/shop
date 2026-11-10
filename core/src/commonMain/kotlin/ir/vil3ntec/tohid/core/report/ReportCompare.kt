package ir.vil3ntec.tohid.core.report

/**
 *  RPT-13 — «ستونِ مقایسه در خودِ جدول»: برگهٔ گزارشِ همین دوره کنارِ برگهٔ همان گزارش در دورهٔ قبل، **ردیف‌به‌ردیف با کلیدِ
 *  ستونِ اول** (شرح، فروشنده…) و دو ستونِ تازه در انتها: «دورهٔ قبل» (عددِ همان ستون در دورهٔ قبل) و «تغییر» (این − قبل).
 *
 *  فقط برگه‌ای جفت می‌شود که ستونِ اولش کلیدِ یکتا و ناتهی است و سرخطِ هر دو برگه یکی است؛ برگهٔ فهرستیِ سند (فاکتور، تاریخ)
 *  کلیدِ مشترک ندارد ⇒ `null` در [column]؛ برای آن [grouped] ردیف‌ها را با یک ستونِ دسته‌ای گروه می‌کند و همان دو ستون را
 *  کنارِ جمعِ هر گروه می‌گذارد. ردیفی که فقط در دورهٔ قبل بود در انتها با خانه‌های خالیِ این دوره می‌آید (هیچ عددی گم
 *  نمی‌شود). خالص، commonMain.
 */
object ReportCompare {

  private fun num(s: String?): Double? = s?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

  /**
   *  همان شکلِ برگه‌های `ReportCsv`: عددِ درست بی اعشار، وگرنه تا دو رقم بی صفرِ پایانی — همیشه رقمِ ساده، هرگز `1.2E7`
   *  (`Double.toString` ِ عددِ ده میلیون به بالا با اعشار چنین بود)؛ گرد کردن با `mathRound` (نیمه به بالا، همان JVM و آیفون).
   */
  fun format(v: Double): String {
    if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
    return minorText(ir.vil3ntec.tohid.core.money.mathRound(v * 100))
  }

  /** پولِ صدمی ([m] = مبلغ × ۱۰۰) به همان شکلِ [format]: «1500»، «-149.5»، «0.05» */
  private fun minorText(m: Long): String {
    val a = if (m < 0) -m else m
    val frac = (a % 100).toInt()
    val text = if (frac == 0) (a / 100).toString() else "${a / 100}." + (if (frac < 10) "0$frac" else "$frac").trimEnd('0')
    return if (m < 0) "-$text" else text
  }

  /** کلیدهای ستونِ اول یکتا و ناتهی‌اند؟ */
  private fun keyed(body: List<List<String>>): Boolean {
    val keys = body.map { it.firstOrNull()?.trim().orEmpty() }
    return keys.none { it.isEmpty() } && keys.toSet().size == keys.size
  }

  /**
   *  @param rows برگهٔ این دوره (ردیفِ اول سرخط)
   *  @param prev برگهٔ همان گزارش در دورهٔ قبل
   *  @param valueCol ستونی که مقایسه می‌شود (عددی؛ ۱ به بعد)
   *  @return برگه با دو ستونِ [prevTitle] و [changeTitle]، یا `null` وقتی جفت‌شدنی نیست
   */
  fun column(rows: List<List<String>>, prev: List<List<String>>, valueCol: Int, prevTitle: String, changeTitle: String): List<List<String>>? {
    val head = rows.firstOrNull() ?: return null
    if (valueCol !in 1 until head.size || prev.firstOrNull() != head) return null
    val body = rows.drop(1)
    val past = prev.drop(1)
    if (!keyed(body) || !keyed(past)) return null
    if ((body + past).any { r -> r.getOrNull(valueCol)?.let { it.isNotBlank() && num(it) == null } == true }) return null
    val before = past.associate { it[0].trim() to num(it.getOrNull(valueCol)) }
    val out = ArrayList<List<String>>(body.size + past.size + 1)
    out += head + prevTitle + changeTitle
    val seen = HashSet<String>()
    body.forEach { r ->
      val k = r[0].trim()
      seen += k
      val p = before[k]
      val c = num(r.getOrNull(valueCol))
      val cells = List(head.size) { r.getOrNull(it).orEmpty() }
      out += cells + (p?.let(::format).orEmpty()) + if (c == null && p == null) "" else format((c ?: 0.0) - (p ?: 0.0))
    }
    past.forEach { r ->
      val k = r[0].trim()
      if (k in seen) return@forEach
      val p = before[k] ?: return@forEach
      out += List(head.size) { if (it == 0) r[0] else "" } + format(p) + format(-p)
    }
    return out
  }

  /**
   *  RPT-13 — برگهٔ فهرستیِ سند (فاکتور، خرید، مصرف) کلیدِ ردیف ندارد: هر سند فقط در دورهٔ خودش است. برای مقایسه ردیف‌ها با
   *  ستونِ دسته‌ایِ [keyCol] (روشِ پرداخت، تأمین‌کننده، دسته) گروه می‌شوند ⇒ برگهٔ کلیددارِ [سرخطِ keyCol، [countTitle]،
   *  سرخطِ valueCol] با شمار و جمعِ ستونِ عددیِ [valueCol]. جمع به صدم با عددِ صحیح است، پس جمعِ گروه‌ها دقیقاً جمعِ همان ستون
   *  است. ترتیب: جمعِ بزرگ‌تر اول، سپس کلید. کلیدِ خالی ⇒ [blankKey]. ردیفی که [include] نپذیرد (فاکتورِ لغوشده) شمرده نمی‌شود.
   *  `null` وقتی سرخط نیست، دو ستون یکی یا بیرون از سرخط‌اند، [countTitle] یا [blankKey] خالی است، یا خانهٔ ناتهیِ [valueCol]
   *  عدد نیست (برگهٔ دیگر، نه همان فهرست).
   */
  fun group(
    rows: List<List<String>>, keyCol: Int, valueCol: Int, countTitle: String, blankKey: String,
    include: (List<String>) -> Boolean = { true },
  ): List<List<String>>? {
    val head = rows.firstOrNull() ?: return null
    if (keyCol !in head.indices || valueCol !in head.indices || keyCol == valueCol) return null
    if (countTitle.isBlank() || blankKey.isBlank()) return null
    val count = LinkedHashMap<String, Int>()
    val sum = LinkedHashMap<String, Long>()
    for (r in rows.drop(1)) {
      if (!include(r)) continue
      val cell = r.getOrNull(valueCol)?.trim().orEmpty()
      val v = if (cell.isEmpty()) 0.0 else cell.toDoubleOrNull() ?: return null
      val k = r.getOrNull(keyCol)?.trim().orEmpty().ifEmpty { blankKey }
      count[k] = (count[k] ?: 0) + 1
      sum[k] = (sum[k] ?: 0L) + ir.vil3ntec.tohid.core.money.mathRound(v * 100)
    }
    val order = sum.keys.sortedWith(compareByDescending<String> { sum.getValue(it) }.thenBy { it })
    return listOf(listOf(head[keyCol], countTitle, head[valueCol])) +
      order.map { listOf(it, count.getValue(it).toString(), minorText(sum.getValue(it))) }
  }

  /**
   *  ستونِ «دورهٔ قبل» و «تغییر» برای برگهٔ فهرستی: هر دو دوره با [group] گروه و سپس با [column] ردیف‌به‌ردیف (کلید = گروه)
   *  جفت می‌شوند. سرخطِ دو برگه باید یکی باشد؛ گروهی که فقط در دورهٔ قبل بود در انتها می‌آید.
   */
  fun grouped(
    rows: List<List<String>>, prev: List<List<String>>, keyCol: Int, valueCol: Int, countTitle: String, blankKey: String,
    prevTitle: String, changeTitle: String, include: (List<String>) -> Boolean = { true },
  ): List<List<String>>? {
    val head = rows.firstOrNull() ?: return null
    if (prev.firstOrNull() != head) return null
    val cur = group(rows, keyCol, valueCol, countTitle, blankKey, include) ?: return null
    val past = group(prev, keyCol, valueCol, countTitle, blankKey, include) ?: return null
    return column(cur, past, 2, prevTitle, changeTitle)
  }
}
