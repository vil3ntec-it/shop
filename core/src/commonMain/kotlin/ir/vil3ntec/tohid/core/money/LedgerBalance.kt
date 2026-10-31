package ir.vil3ntec.tohid.core.money

/**
 *  ماندهٔ حساب از ردیف‌های اعشاریِ دفترِ امروز (`DebtTransaction.amount`، به واحدِ اصلی؛ برد +، رسید −)
 *  — به واحدِ کوچک و بی خطای ممیزِ شناور.
 *
 *  جمعِ Double پسماند می‌گذارد: 966.77 + 4963.8 + 1920.32 − 7850.89 = −9.09e-13، و حسابی که دقیقاً
 *  تسویه شده «۰ افغانی موجودی دارد» یا «بدهکار» خوانده می‌شد، نه «حساب صاف». این‌جا هر ردیف جدا به
 *  واحدِ کوچک گرد می‌شود (همان `Money.fromLegacy` و `DebtBook.minor`) و جمع با عددِ صحیح است: صفر یعنی
 *  صفرِ دقیق، و ماندهٔ ناصفر دست‌کم یک پول است.
 *
 *  ⛔ هرگز پرتاب نمی‌کند (فهرستِ قرض‌داران و داشبورد به آن بندند): ناعدد و بی‌نهایت صفر — همان
 *  `DebtBook.minor` — و جمع در کرانِ `Long` می‌ایستد.
 */
object LedgerBalance {

  /** یک مبلغ ⇒ واحدِ کوچک؛ ناعدد یا بی‌نهایت ⇒ ۰ */
  fun minorOf(major: Double, currency: Currency = Currency.AFN): Long =
    if (major.isNaN() || major.isInfinite()) 0L else mathRound(major * currency.minorPerMajor)

  /** جمعِ ردیف‌های با نشانه، به واحدِ کوچک */
  fun minor(signed: Iterable<Double>, currency: Currency = Currency.AFN): Long =
    signed.fold(0L) { acc, x -> plus(acc, minorOf(x, currency)) }

  /** واحدِ کوچک ⇒ عددِ صفحه؛ صفر دقیقاً `0.0` است */
  fun major(minor: Long, currency: Currency = Currency.AFN): Double = minor.toDouble() / currency.minorPerMajor

  /** همان جمع، برگشته به واحدِ اصلی — جای `sumOf { … }` ِ Double روی ردیف‌های حساب */
  fun sum(signed: Iterable<Double>, currency: Currency = Currency.AFN): Double = major(minor(signed, currency), currency)

  /**
   *  نشانهٔ هر مانده — حتی Double ِ خامی که از جای دیگر آمده — به واحدِ کوچک:
   *  ۱ بدهکار، −۱ موجودی (طلبکار)، ۰ حسابِ صاف.
   */
  fun sign(major: Double, currency: Currency = Currency.AFN): Int {
    val m = minorOf(major, currency)
    return if (m > 0) 1 else if (m < 0) -1 else 0
  }

  private fun plus(a: Long, b: Long): Long = when {
    b > 0 && a > Long.MAX_VALUE - b -> Long.MAX_VALUE
    b < 0 && a < Long.MIN_VALUE - b -> Long.MIN_VALUE
    else -> a + b
  }
}
