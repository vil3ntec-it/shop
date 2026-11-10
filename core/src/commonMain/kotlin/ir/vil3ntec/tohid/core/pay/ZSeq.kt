package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.report.DocGaps

/**
 *  RPT-04 — شمارهٔ گزارشِ Z و پیوستگیِ شماره‌ها. X ِ میانِ شیفت شماره ندارد (چیزی نمی‌بندد)؛ فقط Z ِ بسته.
 *
 *  ── شمارش ([next]) ─────────────────────────────────────────────────────────────────────
 *  هر Z هنگامِ بستن یک شماره در **سریِ سندِ دستگاهِ بندنده** می‌گیرد — همان سریِ فاکتور (`DocSeries`، F0-03: A1، B1،
 *  …؛ سریِ خالی = دستگاهی که هنوز سری نگرفته): بلندترین شمارهٔ همان سری در دفتر + ۱. شمارنده مالِ همان دستگاه است، پس
 *  دو دستگاهِ آفلاین هرگز یک شماره نمی‌سازند (همان قاعدهٔ `InvoiceNumber.next`)؛ صندوقِ شناوری که روی دستگاهِ دیگر بسته
 *  شد شمارهٔ همان دستگاه را می‌گیرد. شماره در خودِ Z ِ ذخیره‌شده است و پس از بستن عوض نمی‌شود (Z ثابت است؛ سرور هم با
 *  پرچمِ `shop.doc_chain` ویرایشِ سرِ جایش را نمی‌پذیرد، `lib/finalized-sales.js`).
 *
 *  ── پیوستگی ([check]) ───────────────────────────────────────────────────────────────────
 *  در هر سری جای خالیِ میانِ دو شماره = Z ای که پاک شده یا هنوز از دستگاهِ دیگری نرسیده؛ شمارهٔ تکراری = دفترِ برگشته
 *  از پشتیبانِ کهنه یا دستکاری. پیش از نخستین و پس از آخرین شمارهٔ هر سری چیزی گزارش نمی‌شود (همان مرزهای `DocGaps`)؛
 *  Z ِ کهنهٔ پیش از این قاعده (بی شماره) فقط شمرده می‌شود.
 *
 *  کاتلینِ خالص (commonMain)؛ آزمون: `ZSeqTest`.
 */
object ZSeq {

  /** یک Z ِ ذخیره‌شده: شناسهٔ ردیفِ بستن، سریِ دستگاهِ بندنده و شماره (۰ = Z ِ کهنهٔ بی شماره) */
  data class Z(val id: String, val series: String, val no: Long)

  /** شماره‌های `from` تا `to` (هر دو شامل) در سری نیستند */
  data class Gap(val series: String, val from: Long, val to: Long)

  /** یک شماره با بیش از یک Z (شناسه‌ها مرتب) */
  data class Dup(val series: String, val no: Long, val ids: List<String>)

  data class Report(val gaps: List<Gap>, val duplicates: List<Dup>, val unnumbered: Int) {
    val clean: Boolean get() = gaps.isEmpty() && duplicates.isEmpty()
  }

  /** شمارهٔ Z ِ بعدی در سریِ [series]: بلندترین شمارهٔ همان سری + ۱؛ سری‌های دیگر و Z ِ بی شماره اثری ندارند */
  fun next(zs: List<Z>, series: String): Long {
    val s = series.trim()
    return (zs.filter { it.no > 0 && it.series.trim() == s }.maxOfOrNull { it.no } ?: 0L) + 1
  }

  /** «A1-7»، یا «7» برای سریِ خالی — همان برچسبِ شمارهٔ فاکتور */
  fun label(series: String, no: Long): String = DocGaps.label(series, no)

  fun check(zs: List<Z>): Report {
    val gaps = ArrayList<Gap>()
    val dups = ArrayList<Dup>()
    val bySeries = zs.filter { it.no > 0 }.groupBy { it.series.trim() }
    for (series in bySeries.keys.sorted()) {
      val byNo = bySeries.getValue(series).groupBy { it.no }
      val nos = byNo.keys.sorted()
      for (i in 1 until nos.size) {
        if (nos[i] - nos[i - 1] > 1) gaps += Gap(series, nos[i - 1] + 1, nos[i] - 1)
      }
      for (n in nos) {
        val ids = byNo.getValue(n).map { it.id }.distinct().sorted()
        if (ids.size > 1) dups += Dup(series, n, ids)
      }
    }
    return Report(gaps, dups, zs.count { it.no <= 0 })
  }
}
