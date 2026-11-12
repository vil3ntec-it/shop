package ir.vil3ntec.tohid.core.backup

/**
 *  «پاک‌سازیِ کاملِ اطلاعات» — OPS-02 (§۶.۲۴). تصمیم‌ها، بی صفحه و بی `Context`، تا گوشیِ نسخهٔ ۳، رابطِ پیشین و آیفون یکی باشند.
 *
 *  ⛔ این کار برای هر ردیف DELETE به سرور می‌فرستد و دفترِ دکان روی **همهٔ** دستگاه‌ها و سرور خالی می‌شود
 *  (`ShopStore.replaceAll`)، پس:
 *   • ردیفش را فقط **صاحبِ** دکان می‌بیند ([visible]) — مدیر تنظیمات را باز می‌کند (`settings.write`) ولی این را نه؛
 *   • دو مرحله ([Step]): هشدار و «ادامه»، سپس نوشتنِ دقیقِ نامِ دکان ([matches]؛ دکانِ بی‌نام ⇒ واژهٔ دکمه، [expected]);
 *   • پیش از نوشتن نقش و نام دوباره سنجیده می‌شوند ([decide]) — نقش شاید در این فاصله عوض شده باشد؛
 *   • «پاک شد» فقط وقتی نوشتن موفق بود ([outcome])؛ قفلِ نرم، سدِ نقش یا خطای دیسک ⇒ «پاک نشد».
 */
object WipeAll {

  /** نقشِ صاحبِ دکان — همان `ShopRole.OWNER` و `PermCore.ROLES[0]` */
  const val OWNER: String = "owner"

  enum class Step { WARN, NAME }

  enum class Decision { NOT_OWNER, WRONG_NAME, ERASE }

  enum class Outcome { WIPED, FAILED }

  /** ردیفِ «پاک‌سازیِ کامل» دیده شود؟ فقط صاحبِ دکان */
  fun visible(role: String): Boolean = role == OWNER

  /** نامی که مرحلهٔ دوم می‌خواهد: نامِ دکان؛ دکانِ بی‌نام (پیش از ثبت) ⇒ [fallback] (واژهٔ دکمه، «پاک کن») */
  fun expected(shopName: String, fallback: String): String = shopName.trim().ifEmpty { fallback.trim() }

  /** سنجشِ نامِ نوشته‌شده — بی فاصله و نیم‌فاصله، بی اِعراب (کسرهٔ اضافه…)، «ي/ك» ِ عربی برابرِ «ی/ک»، بی بزرگی و کوچکیِ حرف */
  fun matches(typed: String, expected: String): Boolean {
    val want = norm(expected)
    return want.isNotEmpty() && norm(typed) == want
  }

  /** درست پیش از نوشتن: نقش، سپس نام */
  fun decide(role: String, typed: String, expected: String): Decision = when {
    !visible(role) -> Decision.NOT_OWNER
    !matches(typed, expected) -> Decision.WRONG_NAME
    else -> Decision.ERASE
  }

  /** نتیجهٔ `replaceAll` ⇒ پیام؛ «پاک شد» هرگز بی نوشتنِ موفق */
  fun outcome(written: Boolean): Outcome = if (written) Outcome.WIPED else Outcome.FAILED

  private fun norm(s: String): String =
    s.replace('ي', 'ی').replace('ك', 'ک')      //  ي ⇒ ی، ك ⇒ ک
      .filter { !it.isWhitespace() && it != '‌' && it !in 'ً'..'ٟ' && it != 'ٰ' }
      .lowercase()
}
