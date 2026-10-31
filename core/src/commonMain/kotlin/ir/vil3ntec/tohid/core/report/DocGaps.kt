package ir.vil3ntec.tohid.core.report

/**
 *  شماره‌های جاافتادهٔ اسناد — RPT-17 (§۳.۵ِ مشخصات: «گزارش شماره‌های جاافتاده وجود دارد»).
 *
 *  ورودی فقط فهرستِ سندهای شماره‌دار است (نوع، سری، شماره، شناسه، روز، لغوشده)؛ خروجی برای
 *  هر سری: نخستین و آخرین شماره، جاهای خالیِ میانِ آن دو، شماره‌های تکراری و شمارِ لغوشده‌ها.
 *  هیچ چیزی نوشته نمی‌شود؛ فقط خواندنی است.
 *
 *  ── چرا هر جای خالی «جاافتاده» نیست ─────────────────────────────────
 *  شمارندهٔ فاکتورِ فروش در دفترِ همهٔ دستگاه‌های یک دکان مشترک جلو می‌رود: پس از هر ادغام،
 *  `nextInvoiceNo` از بلندترین شماره‌ای که در دفتر دیده شده جلوتر می‌رود
 *  (`SyncEngine.withInvoiceCursor`). پس دستگاهِ «A1» که آخرین فاکتورش ۳ بود و پس از همگام‌سازی
 *  فاکتورِ «B1-10» را دید، فاکتورِ بعدی را ۱۱ می‌زند و ۴ تا ۱۰ در سریِ A1 هرگز ساخته نشده‌اند.
 *  چنین جای خالی‌ای «پرشِ شمارنده» ([Cause.JUMP]) است: شمارهٔ درست پیش از سندِ بعدی (`to`)
 *  در سریِ دیگری از همان نوع هست، یعنی همان سندی که شمارنده به خاطرش جلو پرید.
 *  هر جای خالیِ دیگر [Cause.MISSING] است: هیچ دلیلی در دفتر برایش نیست — سندی پاک شده،
 *  هنوز از دستگاهِ دیگری نرسیده، یا دفتر از پشتیبانِ کهنه برگشته است. گزارش همین‌ها را جلو می‌آورد.
 *
 *  خرید شمارندهٔ جدا ندارد (`PurchaseBook.nextNo` = بلندترین + ۱)، پس پرش ندارد و هر جای خالی‌اش
 *  [Cause.MISSING] است.
 *
 *  ── مرزها ──────────────────────────────────────────────────────────
 *  - پیش از نخستین شمارهٔ هر سری چیزی گزارش نمی‌شود: سریِ دستگاهی که دیرتر پیوست از ۱ شروع نمی‌شود.
 *  - پس از آخرین شماره هم نه: دستگاه نمی‌داند دیگران تا کجا رفته‌اند.
 *  - سندِ لغوشده شماره‌اش را نگه می‌دارد و جای خالی نیست؛ فقط شمرده می‌شود.
 *  - شمارهٔ صفر یا منفی (سندِ کهنهٔ بی شماره) بیرون می‌ماند و در [Report.unnumbered] شمرده می‌شود.
 *  - فهرستِ جاهای خالی سقف دارد ([of] با `maxGaps`)؛ جاافتاده‌ها پیش از پرش‌ها می‌آیند و شمارها همیشه کامل‌اند.
 *  - سری‌هایی که شماره‌شان از جای دیگری می‌آید (`untracked`، مثلاً «O» = شمارهٔ سفارشِ آنلاینِ سرور که
 *    هر سفارشی فروش نمی‌شود) سریِ گزارش نمی‌شوند، ولی شماره‌شان دلیلِ پرشِ شمارندهٔ دستگاه‌ها هست.
 *
 *  Kotlin ِ خالص (commonMain)، بی android.* و java.*.
 */
object DocGaps {

  enum class Kind { SALE, PURCHASE }

  /** `series` خالی = سندِ پیش از سریِ دستگاه (F0-03) یا نوعی که سری ندارد (خرید). */
  data class Doc(
    val kind: Kind,
    val series: String,
    val number: Long,
    val id: String,
    val date: String = "",
    val cancelled: Boolean = false,
  )

  enum class Cause { JUMP, MISSING }

  /**
   *  شماره‌های `from` تا `to` (هر دو شامل) در سری نیستند. `before`/`after` سندهای دو سوی جای خالی‌اند
   *  (شمارهٔ `from - 1` و `to + 1`)؛ برای [Cause.JUMP] `jumpSeries` سریِ سندی است که شمارهٔ `to` را دارد.
   */
  data class Gap(
    val from: Long,
    val to: Long,
    val cause: Cause,
    val beforeId: String,
    val beforeDate: String,
    val afterId: String,
    val afterDate: String,
    val jumpSeries: String = "",
  ) {
    val count: Long get() = to - from + 1
  }

  /** یک شماره در یک سری با بیش از یک سند (شناسه‌ها مرتب) */
  data class Dup(val number: Long, val ids: List<String>)

  data class Run(
    val kind: Kind,
    val series: String,
    val first: Long,
    val last: Long,
    /** شمارِ سندها (شناسهٔ یکتا) */
    val docs: Int,
    val cancelled: Int,
    /** جمعِ شماره‌های [Cause.MISSING] و [Cause.JUMP] — همیشه کامل، حتی وقتی فهرست بریده شد */
    val missing: Long,
    val jumped: Long,
    /** جاهای خالیِ فهرست‌شده (شاید بریده) به ترتیبِ شماره */
    val gaps: List<Gap>,
    val duplicates: List<Dup>,
  )

  data class Report(
    val runs: List<Run>,
    val missing: Long,
    val jumped: Long,
    /** شمارِ شماره‌های تکراری (هر شماره یک بار) */
    val duplicates: Int,
    val cancelled: Int,
    val unnumbered: Int,
    /** سندهای سری‌های `untracked` (فقط شمرده می‌شوند) */
    val external: Int,
    /** شمارِ همهٔ جاهای خالی و شمارِ فهرست‌شده‌ها؛ `listed < gaps` یعنی فهرست بریده شد */
    val gaps: Int,
    val listed: Int,
  ) {
    val truncated: Boolean get() = listed < gaps
    val clean: Boolean get() = missing == 0L && duplicates == 0
  }

  /** نوعی که شمارندهٔ مشترکش پس از همگام‌سازی جلو می‌پرد */
  private fun jumps(kind: Kind): Boolean = kind == Kind.SALE

  private class Slot(val kind: Kind, val series: String) {
    val byNumber = HashMap<Long, MutableList<Doc>>()
  }

  fun of(docs: List<Doc>, maxGaps: Int = 500, untracked: Set<String> = emptySet()): Report {
    var unnumbered = 0
    var external = 0
    val skip = untracked.map { it.trim() }.toSet()
    val slots = LinkedHashMap<Pair<Kind, String>, Slot>()
    //  شماره ⇒ سری‌هایی که آن را دارند، جدا برای هر نوع (برای تشخیصِ پرش؛ سری‌های `untracked` هم)
    val owners = HashMap<Pair<Kind, Long>, MutableSet<String>>()
    for (doc in docs) {
      if (doc.number <= 0) { unnumbered++; continue }
      val series = doc.series.trim()
      owners.getOrPut(doc.kind to doc.number) { HashSet() } += series
      if (series in skip) { external++; continue }
      val slot = slots.getOrPut(doc.kind to series) { Slot(doc.kind, series) }
      slot.byNumber.getOrPut(doc.number) { ArrayList() } += doc
    }

    class Draft(val kind: Kind, val series: String, val first: Long, val last: Long, val docs: Int, val cancelled: Int, val gaps: List<Gap>, val dups: List<Dup>)

    val drafts = slots.values
      .sortedWith(compareBy<Slot> { it.kind.ordinal }.thenBy { it.series })
      .map { s ->
        val numbers = s.byNumber.keys.sorted()
        fun pick(n: Long): Doc = s.byNumber.getValue(n).minBy { it.id }
        val gaps = ArrayList<Gap>()
        for (i in 1 until numbers.size) {
          val prev = numbers[i - 1]
          val next = numbers[i]
          if (next - prev <= 1) continue
          val to = next - 1
          val other = if (jumps(s.kind)) owners[s.kind to to].orEmpty().filter { it != s.series }.minOrNull() else null
          val before = pick(prev)
          val after = pick(next)
          gaps += Gap(
            from = prev + 1, to = to,
            cause = if (other != null) Cause.JUMP else Cause.MISSING,
            beforeId = before.id, beforeDate = before.date,
            afterId = after.id, afterDate = after.date,
            jumpSeries = other.orEmpty(),
          )
        }
        val ids = s.byNumber.values.flatten().distinctBy { it.id }
        val dups = numbers.mapNotNull { n ->
          val distinct = s.byNumber.getValue(n).map { it.id }.distinct().sorted()
          if (distinct.size > 1) Dup(n, distinct) else null
        }
        Draft(s.kind, s.series, numbers.first(), numbers.last(), ids.size, ids.count { it.cancelled }, gaps, dups)
      }

    //  سقفِ فهرست: اول جاافتاده‌ها (به ترتیبِ سری و شماره)، بعد پرش‌ها
    val all = drafts.flatMapIndexed { i, d -> d.gaps.map { i to it } }
    val cap = maxGaps.coerceAtLeast(0)
    val kept = all.sortedWith(compareBy<Pair<Int, Gap>> { if (it.second.cause == Cause.MISSING) 0 else 1 }.thenBy { it.first }.thenBy { it.second.from })
      .take(cap).toSet()

    val runs = drafts.mapIndexed { i, d ->
      Run(
        kind = d.kind, series = d.series, first = d.first, last = d.last, docs = d.docs, cancelled = d.cancelled,
        missing = d.gaps.filter { it.cause == Cause.MISSING }.sumOf { it.count },
        jumped = d.gaps.filter { it.cause == Cause.JUMP }.sumOf { it.count },
        gaps = d.gaps.filter { (i to it) in kept },
        duplicates = d.dups,
      )
    }
    return Report(
      runs = runs,
      missing = runs.sumOf { it.missing },
      jumped = runs.sumOf { it.jumped },
      duplicates = runs.sumOf { it.duplicates.size },
      cancelled = runs.sumOf { it.cancelled },
      unnumbered = unnumbered,
      external = external,
      gaps = all.size,
      listed = kept.size,
    )
  }

  /** «A1-5» یا «5» برای سندِ بی سری */
  fun label(series: String, number: Long): String = series.trim().let { if (it.isEmpty()) number.toString() else "$it-$number" }
}
