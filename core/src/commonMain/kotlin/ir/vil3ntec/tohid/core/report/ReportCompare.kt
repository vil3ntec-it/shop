package ir.vil3ntec.tohid.core.report

/**
 *  RPT-13 — «ستونِ مقایسه در خودِ جدول»: برگهٔ گزارشِ همین دوره کنارِ برگهٔ همان گزارش در دورهٔ قبل، **ردیف‌به‌ردیف با کلیدِ
 *  ستونِ اول** (شرح، فروشنده…) و دو ستونِ تازه در انتها: «دورهٔ قبل» (عددِ همان ستون در دورهٔ قبل) و «تغییر» (این − قبل).
 *
 *  فقط برگه‌ای جفت می‌شود که ستونِ اولش کلیدِ یکتا و ناتهی است و سرخطِ هر دو برگه یکی است؛ برگهٔ فهرستیِ سند (فاکتور، تاریخ)
 *  کلیدِ مشترک ندارد ⇒ `null` و جدول همان امروز (خطِ مقایسهٔ زیرِ کارت‌ها می‌ماند). ردیفی که فقط در دورهٔ قبل بود در انتها
 *  با خانه‌های خالیِ این دوره می‌آید (هیچ عددی گم نمی‌شود). خالص، commonMain.
 */
object ReportCompare {

  private fun num(s: String?): Double? = s?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()

  /** همان شکلِ برگه‌های `ReportCsv`: عددِ درست بی اعشار، وگرنه تا دو رقم بی صفرِ پایانی */
  fun format(v: Double): String {
    if (v == kotlin.math.floor(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
    val r = kotlin.math.round(v * 100) / 100.0
    return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString().trimEnd('0').trimEnd('.')
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
}
