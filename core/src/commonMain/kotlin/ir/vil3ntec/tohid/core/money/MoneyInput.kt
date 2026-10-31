package ir.vil3ntec.tohid.core.money

import kotlin.math.roundToLong

/**
 *  متنِ کادرِ مبلغ از عددِ دفتر — کاتلینِ خالص در commonMain تا اندروید، کامپیوتر و iOS یک حساب داشته باشند.
 *
 *  ⛔ «تسویهٔ کامل» هرگز به افغانی گرد نمی‌شود. افغانی دو رقمِ اعشار دارد (۱ پول = ۱/۱۰۰ افغانی)؛
 *  گردِ افغانی از ماندهٔ 899.6 رسیدِ 900 می‌ساخت (۰٫۴ پیش‌پرداختِ ساختگی) و از 1200.4 پرداختِ 1200
 *  (۰٫۴ بدهیِ باز). این‌جا مانده دقیقاً تا پول نوشته می‌شود.
 */
object MoneyInput {

  /** همان `Currency.AFN`: دو رقمِ اعشار، ۱۰۰ پول در هر افغانی */
  private const val DECIMALS = 2
  private const val MINOR_PER_MAJOR = 100L

  /** همان `MAX_EXACT` ِ Money.kt: بیرون از ۲^۵۳−۱ پول عددِ دقیقی نیست که بشود نوشت */
  private const val MAX_EXACT_MINOR = 9_007_199_254_740_991L

  /**
   *  متنِ کادر برای «تسویهٔ کامل» (رسیدِ قرض‌دار و پرداختِ تأمین‌کننده): همهٔ مانده تا پول، بی گردِ افغانی.
   *  899.6 ⇒ "899.6"، 1200.4 ⇒ "1200.4"، 1200 ⇒ "1200"، 0.1 + 0.2 ⇒ "0.3".
   *
   *  تبدیل به پول همان `Money.fromLegacy` است (`round(x × 100)`، نیمه به سمتِ +∞)، پس متن دقیقاً همان
   *  مانده‌ای است که `DebtBook.minor` می‌بیند. مانده‌ای که به یک پول نمی‌رسد، منفی، ناعدد یا بیرون از
   *  بازهٔ دقیق ⇒ null (چیزی برای تسویه نیست؛ میان‌بُر نشان داده نمی‌شود).
   */
  fun settleInput(balance: Double): String? {
    if (!balance.isFinite()) return null
    val minor = (balance * MINOR_PER_MAJOR).roundToLong()
    if (minor <= 0) return null
    return ofMinor(minor)
  }

  /**
   *  متنِ کادر از مبلغِ پول، دقیقاً تا پول و بی نمادِ علمی: 89960 ⇒ "899.6"، 120000 ⇒ "1200"، 5 ⇒ "0.05"، 0 ⇒ "0".
   *  منفی یا بیرون از بازهٔ دقیق (۲^۵۳−۱ پول) ⇒ null. (دکمه‌های اسکناسِ پنلِ پرداخت، `CashKeys.addNote`.)
   */
  fun ofMinor(minor: Long): String? {
    if (minor < 0 || minor > MAX_EXACT_MINOR) return null
    val frac = (minor % MINOR_PER_MAJOR).toString().padStart(DECIMALS, '0').trimEnd('0')
    return (minor / MINOR_PER_MAJOR).toString() + if (frac.isEmpty()) "" else ".$frac"
  }
}
