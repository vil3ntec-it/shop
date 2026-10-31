package ir.vil3ntec.tohid.core.debt

/**
 *  دریافتِ قسط یا بدهیِ مشتری از خودِ صندوق (POS-39، F7) — منطقِ خالص، همه به پول (۱/۱۰۰ افغانی).
 *
 *  قاعده‌ها:
 *    • پیشنهادِ مبلغ = همهٔ بدهی تا پول ([suggestedMinor])؛ بستانکار یا صفر ⇒ ۰ (پیشنهادی نیست).
 *    • خالی/نادرست، صفر یا منفی و بزرگ‌تر از [MAX_MINOR] رد است.
 *    • بیش از بدهی **پیش‌پرداخت** است (`prepayMinor`) و فقط با تأییدِ صریح ثبت می‌شود ([needsConfirm]) —
 *      یک صفرِ اضافه در صندوق بدهیِ مشتری را منفی نکند.
 *    • هر دریافت یک کلید دارد (شناسهٔ همان ردیفِ `receive` که پیش از ثبت ساخته می‌شود)؛ کلیدی که در
 *      دفتر هست دوباره ثبت نمی‌شود ([alreadyRecorded]) — دو ضربهٔ پشتِ‌سرِهم یک رسید.
 */
object TillReceive {

  /** سقفِ یک دریافت: ۱۰۰ میلیون افغانی — همان مرزی که عددِ دقیق در Double می‌ماند */
  const val MAX_MINOR = 10_000_000_000L

  enum class Problem { NOT_A_NUMBER, NOT_POSITIVE, TOO_LARGE }

  sealed interface Verdict {
    /** [leftMinor] = بدهیِ پس از این دریافت (هرگز منفی نه؛ اضافه همان [prepayMinor] است) */
    data class Ok(val amountMinor: Long, val prepayMinor: Long, val leftMinor: Long) : Verdict
    data class Bad(val problem: Problem) : Verdict
  }

  fun suggestedMinor(balanceMinor: Long): Long = if (balanceMinor > 0) balanceMinor else 0L

  /** [amountMinor] null = خالی یا نادرست */
  fun judge(balanceMinor: Long, amountMinor: Long?): Verdict {
    val a = amountMinor ?: return Verdict.Bad(Problem.NOT_A_NUMBER)
    if (a <= 0) return Verdict.Bad(Problem.NOT_POSITIVE)
    if (a > MAX_MINOR) return Verdict.Bad(Problem.TOO_LARGE)
    val debt = suggestedMinor(balanceMinor)
    val prepay = if (a > debt) a - debt else 0L
    return Verdict.Ok(amountMinor = a, prepayMinor = prepay, leftMinor = debt - (a - prepay))
  }

  fun needsConfirm(v: Verdict): Boolean = v is Verdict.Ok && v.prepayMinor > 0

  fun alreadyRecorded(txIds: Iterable<String>, key: String): Boolean = key.isNotEmpty() && txIds.any { it == key }
}
