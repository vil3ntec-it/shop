package ir.vil3ntec.tohid.core.safe

import ir.vil3ntec.tohid.core.money.Currency

/**
 *  ارزِ ردیفِ گاوصندوق (PAY-18، خواستهٔ صاحب‌کار ۱۴۰۵/۰۸/۲۴: «شاید ارزهای دیگر جز افغانی در گاوصندوق بگذارم»).
 *
 *  ── قاعده ──────────────────────────────────────────────────────────
 *  • هر ردیف یک ارز دارد: `currency` ِ ردیف، و **نبودنش یعنی افغانی** — همهٔ ردیف‌های پیشین و هر ساختی که این فیلد را
 *    نمی‌شناسد بی تغییر افغانی‌اند. ردیفِ افغانیِ تازه هم `currency` نمی‌نویسد (`SafeCore.build`)، پس دو شکل نداریم.
 *  • **هیچ تبدیلی میانِ ارزها نیست**: هر ارز ماندهٔ خودش را دارد و هیچ جمعی دو ارز را با هم نمی‌شمارد. نرخِ ساختگی نه.
 *  • کد سه حرفِ بزرگِ لاتین است (مثلِ ISO 4217: `AFN`، `USD`، `EUR`، `IRR`، `PKR`) — همان `CODE` ِ سرور
 *    (`server/src/lib/safe-ledger.js`) و بخشِ `shape` ِ `core/test-vectors/safe-v1.json`.
 *  • مبلغ عددِ صحیح به واحدِ کوچکِ همان ارز است، مثلِ افغانی. شمارِ رقمِ اعشار **فقط از کد** می‌آید (جدولِ
 *    [Currency.TABLE]: ریال بی اعشار؛ هر کدِ دیگر دو رقم) تا هر دستگاه یک ردیف را یک‌جور بخواند — هرگز از تنظیمِ دکان.
 *  Kotlin خالص است — بی android.*، بی ساعت.
 */
object SafeCurrency {

  const val AFN = "AFN"

  /** ارزهای همیشه‌آماده، افغانی اول — بقیه را دکان خودش می‌افزاید (`SafeCurrencies` ِ برنامه) */
  val BUILT_IN: List<String> = listOf(AFN, "USD", "EUR", "IRR", "PKR")

  private val CODE = Regex("[A-Z]{3}")

  fun isCode(s: String?): Boolean = s != null && CODE.matches(s)

  /** رقمِ اعشارِ واحدِ کوچک: از [Currency.TABLE] اگر آن‌جاست، وگرنه دو */
  fun decimals(code: String): Int = Currency.TABLE.firstOrNull { it.code == code }?.decimals ?: 2

  /** چند واحدِ کوچک = یک واحدِ اصلی (افغانی و دالر ۱۰۰، ریال ۱) */
  fun minorPerMajor(code: String): Long = (1..decimals(code)).fold(1L) { acc, _ -> acc * 10 }

  /**
   *  مبلغِ خوانده‌شده با دو رقمِ اعشار (`CashKeys.cashMinor`، صدم) ⇒ واحدِ کوچکِ [code]؛ اعشاری که آن ارز ندارد
   *  (ریالِ ۱۲٫۵) ⇒ null، نه گردکردن.
   */
  fun fromCents(cents: Long, code: String): Long? {
    val d = decimals(code)
    if (d >= 2) {
      var v = cents
      repeat(d - 2) { if (v > Long.MAX_VALUE / 10) return null; v *= 10 }
      return v
    }
    var div = 1L
    repeat(2 - d) { div *= 10 }
    return if (cents % div == 0L) cents / div else null
  }

  /** واحدِ کوچک ⇒ عددِ نمایش (فقط برای نوشتن روی صفحه، نه حساب) */
  fun major(minor: Long, code: String): Double = minor.toDouble() / minorPerMajor(code)
}
