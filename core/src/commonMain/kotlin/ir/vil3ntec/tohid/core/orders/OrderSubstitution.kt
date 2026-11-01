package ir.vil3ntec.tohid.core.orders

/**
 *  ترجیحِ جایگزینیِ سفارش (CHK-15) — همان مقدارهای `server/src/lib/order-substitution.js`:
 *  یکی کلی (سفارش) و یکی برای هر قلم. سرور قلمِ بی ترجیح را در ثبت با ترجیحِ کلی پر
 *  می‌کند؛ پس کارت فقط ترجیحِ خودِ قلم را نشان می‌دهد. «کالای مشخص» فقط برای قلم است و
 *  نامِ جایگزین دارد.
 *
 *  خالص (commonMain، بی `java.*`/`android.*`) تا برنامهٔ آیفون همین را بخواند (§۴۰.۳).
 */
object OrderSubstitution {
  /** هر قلم — '' یعنی «بی ترجیح» */
  val LINE_CODES = listOf("", "best_match", "specific", "none", "refund")

  /** کلِ سفارش — بی «کالای مشخص» */
  val ORDER_CODES = listOf("", "best_match", "none", "refund")

  private val LABEL = mapOf(
    "best_match" to "بهترین مشابه",
    "specific" to "کالای مشخص",
    "none" to "بی جایگزین",
    "refund" to "بازپرداخت",
  )

  /** نامِ کوتاهِ هر ترجیح؛ '' ⇒ «هرچه دکان صلاح بداند» (برای انتخاب در فرم) */
  fun label(code: String): String = LABEL[code] ?: if (code.isEmpty()) "هرچه دکان صلاح بداند" else code

  /** برچسبِ زیرِ نامِ قلم در کارتِ سفارش — بی ترجیح ⇒ null (هیچ نشان داده نمی‌شود) */
  fun lineLabel(code: String, substituteName: String = ""): String? = when {
    code.isEmpty() -> null
    code == "specific" && substituteName.isNotBlank() -> "اگر نبود: «${substituteName.trim()}»"
    code == "none" -> "اگر نبود: بی جایگزین"
    code == "refund" -> "اگر نبود: بازپرداخت"
    else -> "اگر نبود: ${label(code)}"
  }

  /** خطِ «ترجیحِ کلی» بالای اقلام — بی ترجیح ⇒ null */
  fun orderLabel(code: String): String? = if (code.isEmpty()) null else "اگر کالایی نبود: ${label(code)}"

  /** مقدارِ ناشناس (سرورِ تازه‌تر، دستکاری) هرگز به سرور فرستاده نمی‌شود */
  fun orderCodeOrEmpty(code: String): String = if (code in ORDER_CODES) code else ""
}
