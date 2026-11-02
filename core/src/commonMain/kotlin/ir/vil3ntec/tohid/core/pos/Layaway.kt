package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.calendar.CivilDays

/**
 *  POS-27 — فروشِ امانی (کالا تا تسویه نزدِ دکان می‌ماند) یا اقساطی (کالا همین حالا می‌رود) با پیش‌پرداخت و جدولِ
 *  قسط. منطقِ خالص؛ پول به یک‌صدمِ افغانی (`Long`)، مثلِ `SpecialOrder`.
 *
 *  ── سند و پول ──────────────────────────────────────────────────────
 *  مشتری اجباری است: مانده بدهیِ حسابِ همان مشتری است و هر قسط رسیدِ همان حساب (ردیفِ سیستمیِ قرض‌دار با نوعِ
 *  خودش) — جدولِ تازه‌ای در کار نیست. مبلغ‌ها افغانیِ درست‌اند (مضربِ [WHOLE] پول)، مثلِ جمعِ فاکتورِ صندوق.
 *
 *  ── جدول ([schedule]) ─────────────────────────────────────────────
 *  باقی (جمع − پیش‌پرداخت) به [Draft.count] قسطِ افغانیِ درست بخش می‌شود؛ افغانیِ باقی‌مانده یکی‌یکی روی قسط‌های اول
 *  می‌نشیند، پس جمعِ قسط‌ها دقیقاً همان باقی است و هیچ قسطی از قسطِ پیش از خودش بزرگ‌تر نیست. سررسیدِ نخست
 *  [Draft.firstDue] و هر بعدی [Draft.everyDays] روز پس از قبلی.
 *
 *  ── پیشرفت ([progress]) ───────────────────────────────────────────
 *  پرداخت‌ها به ترتیبِ قسط‌ها می‌نشینند (کهنه‌ترین سررسید اول). قسطِ سررسیدگذشته‌ای که کامل نشده «دیر» است (خودِ روزِ
 *  سررسید دیر نیست). پرداختِ بیش از جمع در حسابِ مشتری می‌ماند ([Progress.overMinor])، هرگز گم نمی‌شود.
 *
 *  ── امانی ──────────────────────────────────────────────────────────
 *  کالای امانی فقط پس از تسویهٔ کامل تحویل می‌شود ([deliverProblem])؛ لغو همهٔ پرداختی را پس می‌دهد ([cancelRefund]).
 *  اقساطی همان لحظهٔ فروش تحویل شده است و لغوش راهِ برگشتِ کالاست، نه این‌جا.
 *
 *  کاتلینِ خالص (commonMain): بی ساعتِ دستگاه؛ «امروز» روزِ کاریِ صندوق است و از بیرون می‌آید.
 *  بردار: `core/test-vectors/layaway-v1.json` (`LayawayVectorsTest`).
 */
object Layaway {
  const val LAYAWAY = "layaway"
  const val INSTALLMENT = "installment"
  val KINDS: List<String> = listOf(LAYAWAY, INSTALLMENT)

  /** سقفِ جمع: ۱۰۰ میلیون افغانی — همان `SpecialOrder.MAX_MINOR` */
  const val MAX_MINOR = SpecialOrder.MAX_MINOR
  const val WHOLE = SpecialOrder.WHOLE
  const val MAX_COUNT = 36
  const val MAX_EVERY_DAYS = 92
  /** سررسیدِ نخست تا یک سال پس از امروز */
  const val MAX_FIRST_DAYS = 366

  /** کادرهای «فروشِ امانی/اقساطیِ تازه»؛ عددِ نادرست یا خالی `null` است */
  data class Draft(
    val kind: String,
    val hasCustomer: Boolean,
    val totalMinor: Long?,
    val depositMinor: Long?,
    val count: Int?,
    val everyDays: Int?,
    val firstDue: String,
    val today: String,
  )

  /**
   *  چرا ساخته نمی‌شود: BAD_KIND، NO_CUSTOMER، BAD_TOTAL، BAD_DEPOSIT، NOT_WHOLE، DEPOSIT_OVER_TOTAL، BAD_COUNT، BAD_EVERY،
   *  BAD_DUE، DUE_PAST، DUE_TOO_FAR، PART_TOO_SMALL (قسطی کمتر از یک افغانی)؛ null ⇒ ساخته می‌شود. پیش‌پرداختِ صفر
   *  پذیرفته است؛ پیش‌پرداختِ برابرِ جمع نه (سند بی قسط همان فروشِ نقد است).
   */
  fun draftProblem(d: Draft): String? {
    if (d.kind !in KINDS) return "BAD_KIND"
    if (!d.hasCustomer) return "NO_CUSTOMER"
    val t = d.totalMinor
    if (t == null || t <= 0 || t > MAX_MINOR) return "BAD_TOTAL"
    val p = d.depositMinor
    if (p == null || p < 0 || p > MAX_MINOR) return "BAD_DEPOSIT"
    if (t % WHOLE != 0L || p % WHOLE != 0L) return "NOT_WHOLE"
    if (p >= t) return "DEPOSIT_OVER_TOTAL"
    val n = d.count
    if (n == null || n < 1 || n > MAX_COUNT) return "BAD_COUNT"
    val every = d.everyDays
    if (n > 1 && (every == null || every < 1 || every > MAX_EVERY_DAYS)) return "BAD_EVERY"
    val due = CivilDays.jdnOfIso(d.firstDue) ?: return "BAD_DUE"
    val today = CivilDays.jdnOfIso(d.today) ?: return "BAD_DUE"
    if (due < today) return "DUE_PAST"
    if (due - today > MAX_FIRST_DAYS) return "DUE_TOO_FAR"
    if ((t - p) / WHOLE < n) return "PART_TOO_SMALL"
    return null
  }

  data class Installment(val no: Int, val due: String, val amountMinor: Long)

  /** جدولِ قسط‌ها؛ پیش‌نویسِ نادرست ([draftProblem]) ⇒ فهرستِ خالی */
  fun schedule(d: Draft): List<Installment> {
    if (draftProblem(d) != null) return emptyList()
    val rest = (d.totalMinor!! - d.depositMinor!!) / WHOLE
    val n = d.count!!
    val base = rest / n
    val extra = (rest % n).toInt()
    val first = CivilDays.jdnOfIso(d.firstDue)!!
    val every = if (n > 1) d.everyDays!! else 0
    return (0 until n).map { i ->
      Installment(i + 1, CivilDays.isoOf(first + i * every), (base + if (i < extra) 1 else 0) * WHOLE)
    }
  }

  enum class State(val code: String) { PAID("paid"), PARTIAL("partial"), DUE("due"), LATE("late") }

  data class Line(val no: Int, val due: String, val amountMinor: Long, val paidMinor: Long, val state: State)

  data class Progress(
    val lines: List<Line>,
    /** پرداختی برای قسط‌ها (پیش‌پرداخت جدا) تا سقفِ جمعِ قسط‌ها */
    val paidMinor: Long,
    val remainingMinor: Long,
    /** جمعِ کسریِ قسط‌های دیر */
    val lateMinor: Long,
    /** نخستین قسطِ کامل‌نشده؛ null یعنی تسویه */
    val next: Line?,
    /** پرداختِ بیش از جمع — در حسابِ مشتری می‌ماند */
    val overMinor: Long,
  ) {
    val settled: Boolean get() = next == null
  }

  /**
   *  پیشرفتِ جدول با [paidMinor] پرداختِ قسط‌ها (جمعِ رسیدهای همین سند، بی پیش‌پرداخت) در روزِ [today].
   *  مبلغِ منفی صفر شمرده می‌شود؛ تاریخِ نادرستِ [today] هیچ قسطی را دیر نمی‌کند.
   */
  fun progress(lines: List<Installment>, paidMinor: Long, today: String): Progress {
    val t = CivilDays.jdnOfIso(today)
    var left = paidMinor.coerceAtLeast(0L)
    val out = lines.sortedBy { it.no }.map { i ->
      val pay = minOf(left, i.amountMinor)
      left -= pay
      val late = t != null && (CivilDays.jdnOfIso(i.due)?.let { it < t } ?: false)
      val state = when {
        pay == i.amountMinor -> State.PAID
        late -> State.LATE
        pay > 0 -> State.PARTIAL
        else -> State.DUE
      }
      Line(i.no, i.due, i.amountMinor, pay, state)
    }
    val total = out.sumOf { it.amountMinor }
    val paid = out.sumOf { it.paidMinor }
    return Progress(
      lines = out,
      paidMinor = paid,
      remainingMinor = total - paid,
      lateMinor = out.filter { it.state == State.LATE }.sumOf { it.amountMinor - it.paidMinor },
      next = out.firstOrNull { it.state != State.PAID },
      overMinor = left,
    )
  }

  /** پرداختِ تازه: مبلغِ افغانیِ درست و نه بیش از مانده — BAD_AMOUNT، NOT_WHOLE، OVER_REMAINING، SETTLED؛ null ⇒ پذیرفته */
  fun payProblem(p: Progress, amountMinor: Long): String? = when {
    p.settled -> "SETTLED"
    amountMinor <= 0 || amountMinor > MAX_MINOR -> "BAD_AMOUNT"
    amountMinor % WHOLE != 0L -> "NOT_WHOLE"
    amountMinor > p.remainingMinor -> "OVER_REMAINING"
    else -> null
  }

  /** تحویلِ کالای امانی فقط پس از تسویه — NOT_LAYAWAY، NOT_SETTLED؛ null ⇒ پذیرفته */
  fun deliverProblem(kind: String, p: Progress): String? = when {
    kind != LAYAWAY -> "NOT_LAYAWAY"
    !p.settled -> "NOT_SETTLED"
    else -> null
  }

  /** لغوِ امانی: همهٔ پرداختی (پیش‌پرداخت + قسط‌ها) پس داده می‌شود؛ اقساطی این‌جا لغو نمی‌شود ⇒ null */
  fun cancelRefund(kind: String, depositMinor: Long, p: Progress): Long? =
    if (kind != LAYAWAY) null else depositMinor.coerceAtLeast(0L) + p.paidMinor + p.overMinor
}
