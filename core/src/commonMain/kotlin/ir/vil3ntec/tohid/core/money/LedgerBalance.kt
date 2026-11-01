package ir.vil3ntec.tohid.core.money

/**
 *  ماندهٔ حساب از ردیف‌های اعشاریِ دفترِ امروز (`DebtTransaction`: مبلغِ هر ردیف به واحدِ اصلی، و جهتش:
 *  برد +، رسید −) — به واحدِ کوچک و بی خطای ممیزِ شناور.
 *
 *  جمعِ Double پسماند می‌گذارد: 966.77 + 4963.8 + 1920.32 − 7850.89 = −9.09e-13، و حسابی که دقیقاً
 *  تسویه شده «۰ افغانی موجودی دارد» یا «بدهکار» خوانده می‌شد، نه «حساب صاف».
 *
 *  ⛔ قاعده دقیقاً همان `DebtBook` است (`acc ± minor(amount)`): اول **مبلغِ خودِ ردیف** به واحدِ کوچک گرد
 *  می‌شود ([rowMinor] = `Money.fromLegacy`)، **بعد** نشانهٔ جهت می‌گیرد، و جمع با عددِ صحیح است. نه گردِ عددِ
 *  نشانه‌دار: `mathRound` نیمه را به سمتِ +∞ می‌برد (1262.5 ⇒ 1263 ولی −1262.5 ⇒ −1262)، پس برد و رسیدِ
 *  12.625 یک پول «بدهکار» می‌ساخت (بازبینیِ #610).
 *
 *  ⛔ هرگز پرتاب نمی‌کند (فهرستِ قرض‌داران و داشبورد به آن بندند): ناعدد و بی‌نهایت صفر — همان
 *  `DebtBook.minor` — و جمع در کرانِ `Long` می‌ایستد.
 */
object LedgerBalance {

  /** مبلغِ یک ردیف ⇒ واحدِ کوچک، همان `Money.fromLegacy` / `DebtBook.minor`؛ ناعدد یا بی‌نهایت ⇒ ۰ */
  fun rowMinor(major: Double, currency: Currency = Currency.AFN): Long =
    if (major.isNaN() || major.isInfinite()) 0L else mathRound(major * currency.minorPerMajor)

  /** ماندهٔ ردیف‌ها به واحدِ کوچک: هر ردیف [rowMinor] ِ [amount] ِ خودش، با نشانهٔ [plus] (برد +، رسید −) */
  fun <T> minor(rows: Iterable<T>, plus: (T) -> Boolean, amount: (T) -> Double, currency: Currency = Currency.AFN): Long =
    rows.fold(0L) { acc, r ->
      val m = rowMinor(amount(r), currency)
      add(acc, if (plus(r)) m else negate(m))
    }

  /** واحدِ کوچک ⇒ عددِ صفحه؛ صفر دقیقاً `0.0` است */
  fun major(minor: Long, currency: Currency = Currency.AFN): Double = minor.toDouble() / currency.minorPerMajor

  /** همان [minor]، برگشته به واحدِ اصلی — جای `sumOf { … }` ِ Double روی ردیف‌های حساب */
  fun <T> sum(rows: Iterable<T>, plus: (T) -> Boolean, amount: (T) -> Double, currency: Currency = Currency.AFN): Double =
    major(minor(rows, plus, amount, currency), currency)

  /**
   *  نشانهٔ یک مانده — حتی Double ِ خامی که از جای دیگر آمده: ۱ بدهکار، −۱ موجودی (طلبکار)، ۰ صاف.
   *  مثلِ [minor] اول اندازه گرد می‌شود و بعد نشانه، پس قرینه است: +0.005 ⇒ ۱ و −0.005 ⇒ −۱.
   */
  fun sign(major: Double, currency: Currency = Currency.AFN): Int {
    val m = rowMinor(if (major < 0) -major else major, currency)
    return if (m == 0L) 0 else if (major > 0) 1 else -1
  }

  private fun negate(m: Long): Long = if (m == Long.MIN_VALUE) Long.MAX_VALUE else -m

  private fun add(a: Long, b: Long): Long = when {
    b > 0 && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
    b < 0 && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
    else -> a + b
  }
}
