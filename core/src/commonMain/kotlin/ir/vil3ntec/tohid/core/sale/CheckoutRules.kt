package ir.vil3ntec.tohid.core.sale

/**
 *  قاعده‌های پیش از پایانِ فاکتور (POS-24) — منطقِ خالص، همه به واحدِ کوچکِ پول (پول = ۱/۱۰۰ افغانی).
 *
 *  قاعده‌ها، به همین ترتیب (نخستین شکسته گفته می‌شود):
 *    1. [CheckoutBreach.CREDIT_NEEDS_CUSTOMER] — **ثابت و همیشه روشن**، همان قاعدهٔ امروزِ
 *       `SalesEngine.record`: فروشِ نسیه با ماندهٔ پرداخت‌نشده بی مشتری ثبت نمی‌شود.
 *    2. [CheckoutBreach.ZERO_TOTAL] — با [CheckoutRules.blockZeroTotal]: فاکتورِ دارای قلم با
 *       مبلغِ نهاییِ صفر (همه رایگان یا تخفیفِ کامل) ثبت نمی‌شود؛ مشتری داشتن درستش نمی‌کند.
 *    3. [CheckoutBreach.CUSTOMER_REQUIRED] — با [CheckoutRules.customerFromMinor]: فاکتوری که
 *       مبلغِ نهایی‌اش (پس از تخفیف) **برابر یا بیشتر** از آن است مشتری می‌خواهد. `0` یعنی هر فاکتور.
 *
 *  پیش‌فرض [CheckoutRules.OFF] ⇒ فقط قاعدهٔ ۱ = رفتارِ امروز. سبدِ خالی کارِ این‌جا نیست
 *  (پیش‌تر رد می‌شود). هیچ قاعده‌ای فاکتورِ ثبت‌شده را دوباره نمی‌سنجد؛ فقط پیش از ثبت.
 */
data class CheckoutRules(
  /** آستانهٔ «مشتری لازم است» به پول؛ `null` = خاموش */
  val customerFromMinor: Long? = null,
  /** فاکتورِ صفر رد شود */
  val blockZeroTotal: Boolean = false,
) {
  val active: Boolean get() = customerFromMinor != null || blockZeroTotal

  companion object {
    val OFF = CheckoutRules()

    /** از مقدارِ ذخیره‌شده؛ آستانهٔ منفی قاعده نیست (خاموش) */
    fun of(customerFromMinor: Long?, blockZeroTotal: Boolean): CheckoutRules =
      CheckoutRules(customerFromMinor?.takeIf { it >= 0 }, blockZeroTotal)
  }
}

/** آن‌چه قاعده‌ها از فاکتورِ آمادهٔ ثبت می‌خوانند — همه از همان جمع‌های `SalesEngine.record` */
data class CheckoutFacts(
  val lines: Int,
  val finalTotalMinor: Long,
  val remainingMinor: Long,
  /** روشِ پرداخت نسیه است */
  val credit: Boolean,
  val hasCustomer: Boolean,
)

enum class CheckoutBreach(val code: String) {
  CREDIT_NEEDS_CUSTOMER("credit_needs_customer"),
  ZERO_TOTAL("zero_total"),
  CUSTOMER_REQUIRED("customer_required"),
}

object CheckoutGuard {

  /** نخستین قاعدهٔ شکسته، یا null */
  fun check(rules: CheckoutRules, f: CheckoutFacts): CheckoutBreach? {
    if (f.credit && f.remainingMinor > 0 && !f.hasCustomer) return CheckoutBreach.CREDIT_NEEDS_CUSTOMER
    if (rules.blockZeroTotal && f.lines > 0 && f.finalTotalMinor == 0L) return CheckoutBreach.ZERO_TOTAL
    val from = rules.customerFromMinor
    if (from != null && from >= 0 && f.finalTotalMinor >= from && !f.hasCustomer) return CheckoutBreach.CUSTOMER_REQUIRED
    return null
  }

  /** پیامِ صندوق؛ [threshold] آستانهٔ قالب‌شده (فقط برای [CheckoutBreach.CUSTOMER_REQUIRED]) */
  fun message(b: CheckoutBreach, threshold: String): String = when (b) {
    //  همان متنِ امروزِ `SalesEngine.record` — آزمونِ «نسیه بدون قرض دار ثبت نمی شود»
    CheckoutBreach.CREDIT_NEEDS_CUSTOMER -> "برای فروش نسیه، قرض‌دار را انتخاب کنید"
    CheckoutBreach.ZERO_TOTAL -> "قاعدهٔ دکان: فاکتورِ با مبلغِ صفر ثبت نمی‌شود"
    CheckoutBreach.CUSTOMER_REQUIRED -> "قاعدهٔ دکان: فاکتورِ $threshold و بالاتر مشتری می‌خواهد — مشتری را انتخاب کنید"
  }
}
