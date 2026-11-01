package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.calendar.CivilDays

/**
 *  POS-26 — سفارشِ ویژهٔ مشتری (کالایی که موجود نیست) با پیش‌پرداخت. منطقِ خالص؛ پول به یک‌صدمِ افغانی (`Long`).
 *
 *  ── سند ────────────────────────────────────────────────────────────
 *  کالای فهرست یا شرحِ دلخواه، مقدار، مبلغِ کل، پیش‌پرداخت، تاریخِ تحویل و وضعیت (باز، آماده، تحویل، لغو).
 *  مشتری اجباری است: پیش‌پرداخت رسیدِ حسابِ همان مشتری است (ردیفِ سیستمیِ قرض‌دار با نوعِ خودش)، پس پولش مثلِ هر
 *  رسید به دخل می‌آید و تا تحویل «پیش‌پرداختِ» حسابِ اوست — جدولِ تازه‌ای در کار نیست.
 *
 *  ── پول ────────────────────────────────────────────────────────────
 *    • تحویل فروشِ عادیِ صندوق است ([settle]): از جمعِ فاکتور تا اندازهٔ پیش‌پرداختِ نگه‌داشته «به حساب» می‌رود
 *      (همان پیش‌پرداخت را می‌بندد) و فقط باقی نقد گرفته می‌شود؛ پیش‌پرداختِ بیش از جمع در حسابِ مشتری می‌ماند.
 *    • لغو یعنی برگرداندنِ همهٔ پیش‌پرداختِ نگه‌داشته ([held]) با دلیلِ [REASON_MIN] تا [REASON_MAX] نویسه.
 *    • مبلغ‌ها افغانیِ درست‌اند (مضربِ [WHOLE] پول)، مثلِ جمعِ فاکتورِ صندوق (`SalesEngine.lineTotal`): فروشِ تحویل
 *      همان جمع را می‌سازد و رسیدِ پیش‌پرداخت و بردِ تحویل در دفترِ قرض‌دار دقیقاً هم را می‌بندند.
 *
 *  ── وضعیت ──────────────────────────────────────────────────────────
 *  وضعیت از رویدادها ساخته می‌شود ([fold])، نه از فیلدی که ویرایش شود: هر تغییر ردیفِ تازهٔ سابقه است و دو دستگاه
 *  چیزی از هم رونویسی نمی‌کنند. ترتیب (زمان، شناسه)؛ جابه‌جاییِ نامجاز کنار می‌رود و اگر پایانی بود (تحویل و لغو
 *  روی دو دستگاهِ بی‌خبر از هم) سفارش «نیازمندِ بررسی» است ([review]).
 *    باز ⇒ آماده | تحویل | لغو؛ آماده ⇒ تحویل | لغو؛ تحویل و لغو پایانی‌اند.
 *
 *  کاتلینِ خالص (commonMain): بی ساعتِ دستگاه؛ «امروز» روزِ کاریِ صندوق است و از بیرون می‌آید.
 *  بردار: `core/test-vectors/special-order-v1.json` (`SpecialOrderVectorsTest`).
 */
object SpecialOrder {
  const val ITEM_MAX = 120
  const val REASON_MIN = 3
  const val REASON_MAX = 120
  /** سقفِ هر مبلغ: ۱۰۰ میلیون افغانی — همان `TillReceive.MAX_MINOR` */
  const val MAX_MINOR = 10_000_000_000L
  /** سقفِ مقدار: یک میلیون واحد (به هزارم) */
  const val MAX_QTY_MILLI = 1_000_000_000L
  /** یک افغانی به پول */
  const val WHOLE = 100L

  enum class Status(val code: String) {
    OPEN("open"), READY("ready"), DELIVERED("delivered"), CANCELLED("cancelled");

    val final: Boolean get() = this == DELIVERED || this == CANCELLED

    companion object {
      fun of(code: String?): Status? = entries.firstOrNull { it.code == code }
    }
  }

  /** کادرهای «سفارشِ تازه»؛ عددِ نادرست یا خالی `null` است */
  data class Draft(
    val hasCustomer: Boolean,
    val item: String?,
    val qtyMilli: Long?,
    val totalMinor: Long?,
    val depositMinor: Long?,
    val due: String,
    val today: String,
  )

  private val SPACES = Regex("\\s+")

  /** شرحِ کالا: یک خط، حداکثر [ITEM_MAX]؛ خالی ⇒ null */
  fun item(text: String?): String? = text?.replace(SPACES, " ")?.trim()?.take(ITEM_MAX)?.ifEmpty { null }

  /** دلیلِ لغو: یک خط؛ خالی ⇒ null (اندازه را [moveProblem] می‌سنجد) */
  fun reason(text: String?): String? = text?.replace(SPACES, " ")?.trim()?.ifEmpty { null }

  /**
   *  چرا ساخته نمی‌شود: NO_CUSTOMER، NO_ITEM، BAD_QTY، BAD_TOTAL، BAD_DEPOSIT، NOT_WHOLE، DEPOSIT_OVER_TOTAL، BAD_DUE،
   *  DUE_PAST؛ null ⇒ ساخته می‌شود. پیش‌پرداختِ صفر پذیرفته است (سفارشِ بی پیش‌پرداخت)؛ تحویلِ امروز هم.
   */
  fun draftProblem(d: Draft): String? {
    if (!d.hasCustomer) return "NO_CUSTOMER"
    if (item(d.item) == null) return "NO_ITEM"
    val q = d.qtyMilli
    if (q == null || q <= 0 || q > MAX_QTY_MILLI) return "BAD_QTY"
    val t = d.totalMinor
    if (t == null || t <= 0 || t > MAX_MINOR) return "BAD_TOTAL"
    val p = d.depositMinor
    if (p == null || p < 0 || p > MAX_MINOR) return "BAD_DEPOSIT"
    if (t % WHOLE != 0L || p % WHOLE != 0L) return "NOT_WHOLE"
    if (p > t) return "DEPOSIT_OVER_TOTAL"
    val due = CivilDays.jdnOfIso(d.due) ?: return "BAD_DUE"
    val today = CivilDays.jdnOfIso(d.today) ?: return "BAD_DUE"
    if (due < today) return "DUE_PAST"
    return null
  }

  fun canMove(from: Status, to: Status): Boolean = when (from) {
    Status.OPEN -> to == Status.READY || to == Status.DELIVERED || to == Status.CANCELLED
    Status.READY -> to == Status.DELIVERED || to == Status.CANCELLED
    Status.DELIVERED, Status.CANCELLED -> false
  }

  /** چرا این جابه‌جایی پذیرفته نیست: NOT_ALLOWED، BAD_REASON (لغو بی دلیلِ ۳ تا ۱۲۰ نویسه)؛ null ⇒ پذیرفته */
  fun moveProblem(from: Status, to: Status, reason: String?): String? {
    if (!canMove(from, to)) return "NOT_ALLOWED"
    if (to == Status.CANCELLED) {
      val r = reason(reason)
      if (r == null || r.length < REASON_MIN || r.length > REASON_MAX) return "BAD_REASON"
    }
    return null
  }

  /** یک ردیفِ «تغییرِ وضعیت»؛ [void] ⇒ اثرش برداشته شده (فروشِ تحویلی که بعد لغو شد) و شمرده نمی‌شود */
  data class Event(val id: String, val at: Long, val to: String, val void: Boolean = false)

  /** [applied] شناسهٔ رویدادهای پذیرفته به ترتیب؛ [skipped] نامجازها؛ [review] نامجازی پایانی یا ناشناخته بود */
  data class Folded(val status: Status, val applied: List<String>, val skipped: List<String>, val review: Boolean)

  fun fold(events: List<Event>): Folded {
    var s = Status.OPEN
    val applied = ArrayList<String>()
    val skipped = ArrayList<String>()
    var review = false
    for (e in events.sortedWith(compareBy<Event>({ it.at }, { it.id }))) {
      if (e.void) continue
      val to = Status.of(e.to)
      if (to != null && canMove(s, to)) {
        s = to
        applied += e.id
      } else {
        skipped += e.id
        //  «آماده»ِ دوباره از دستگاهِ دیگر بی‌اثر است؛ تحویل/لغوِ کنارگذاشته پول جابه‌جا کرده بود
        if (to == null || to.final) review = true
      }
    }
    return Folded(s, applied, skipped, review)
  }

  /** پیش‌پرداختِ نگه‌داشته: رسیدهای پیش‌پرداخت − بازپرداخت‌ها؛ هرگز منفی نه */
  fun held(depositsMinor: Long, refundsMinor: Long): Long = (depositsMinor - refundsMinor).coerceAtLeast(0L)

  /**
   *  نیازمندِ بررسیِ مدیر: جابه‌جاییِ پایانیِ کنارگذاشته، یا پولی که با وضعیت نمی‌خواند — تحویل‌شده‌ای که بازپرداخت
   *  هم دارد (لغو روی دستگاهِ دیگر)، یا لغوشده‌ای که هنوز پیش‌پرداختِ نگه‌داشته دارد.
   */
  fun review(f: Folded, depositsMinor: Long, refundsMinor: Long): Boolean =
    f.review ||
      (f.status == Status.DELIVERED && refundsMinor > 0) ||
      (f.status == Status.CANCELLED && held(depositsMinor, refundsMinor) > 0)

  /** [applyMinor] به حساب (از پیش‌پرداخت)، [payNowMinor] نقدِ همین لحظه، [leftMinor] پیش‌پرداختی که در حساب می‌ماند */
  data class Settle(val applyMinor: Long, val payNowMinor: Long, val leftMinor: Long)

  fun settle(saleTotalMinor: Long, heldMinor: Long): Settle {
    val total = saleTotalMinor.coerceAtLeast(0L)
    val h = heldMinor.coerceAtLeast(0L)
    val apply = minOf(total, h)
    return Settle(apply, total - apply, h - apply)
  }

  /** «دیر شده»: هنوز باز یا آماده و روزِ تحویل گذشته (خودِ روزِ تحویل دیر نیست)؛ تاریخِ نادرست ⇒ نه */
  fun overdue(status: Status, due: String, today: String): Boolean {
    if (status.final) return false
    val d = CivilDays.jdnOfIso(due) ?: return false
    val t = CivilDays.jdnOfIso(today) ?: return false
    return d < t
  }
}
