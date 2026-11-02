package ir.vil3ntec.tohid.core.pos

/**
 *  «درخواستِ تأیید» برای تغییرِ قیمتِ ردیف — S45، STF-02/STF-03 (سقفِ «تغییرِ قیمت»).
 *
 *  «هر عملِ ممنوع یا نیازمندِ تأیید پنجرهٔ درخواستِ تأیید باز می‌کند، نه پیامِ خطای مبهم. در سابقه،
 *  درخواست‌کننده و تأییدکننده هر دو ثبت می‌شوند.»
 *
 *  با «قیمت و تخفیفِ ردیف» ِ روشن (`LinePricing.Policy.on`) قاعدهٔ جهت و سقفِ قلمِ آزاد قیمتِ دستی را رد
 *  می‌کنند. حالا ردِ همین قاعده‌ها ([askable]) «درخواستِ تأیید» دارد: مدیر یا صاحب روی همین دستگاه با PIN و
 *  دلیل تأیید می‌کند (همان راهِ «فروشِ نسیه بیش از سقف»، `CreditGate`)، و تأیید **فقط همان قاعده** را برمی‌دارد
 *  ([lifted]) — «تخفیف ممنوع» و سقفِ تخفیف (`DiscountGate.basis`) سرِ جایشان می‌مانند.
 *  سرور سقفِ قیمت را نمی‌سنجد، پس برگهٔ امضاشده ساخته نمی‌شود؛ تأیید با نامِ هر دو و دلیل در سابقه می‌ماند.
 */
object PriceApproval {

  /** نوعِ ردیفِ سابقه */
  const val AUDIT_TYPE = "price_override"

  /** ردهایی که مدیر می‌تواند بردارد — نه «تخفیف ممنوع» (`REDUCTION_DENIED`) */
  val LIFTABLE: Set<String> = setOf(
    LinePricing.PRICE_UP_BLOCKED, LinePricing.PRICE_DOWN_BLOCKED,
    LinePricing.FREE_LINE_CLOSED, LinePricing.FREE_LINE_OVER_CAP,
  )

  /** این رد «درخواستِ تأیید» دارد؟ پرچمِ خاموش یا صاحب (معاف) ⇒ هرگز ردی نیست */
  fun askable(p: LinePricing.Policy, code: String?): Boolean = p.on && !p.exempt && code in LIFTABLE

  /** سیاستِ پس از تأیید: فقط قاعدهٔ ردکننده برداشته می‌شود */
  fun lifted(p: LinePricing.Policy, code: String): LinePricing.Policy = when (code) {
    LinePricing.PRICE_UP_BLOCKED, LinePricing.PRICE_DOWN_BLOCKED -> p.copy(direction = LinePricing.Direction.BOTH)
    LinePricing.FREE_LINE_CLOSED, LinePricing.FREE_LINE_OVER_CAP -> p.copy(freeLineCapMinor = null)
    else -> p
  }

  /**
   *  یک تأییدِ گرفته‌شده.
   *  @param code ردی که برداشته شد
   *  @param by نامِ تأییدکننده
   *  @param role نقشِ تأییدکننده
   *  @param requester نامِ درخواست‌کننده (کاربرِ صندوق)
   */
  data class Grant(
    val code: String,
    val by: String,
    val role: String,
    val reason: String,
    val requester: String,
    val listMinor: Long,
    val priceMinor: Long,
  )

  /** تأیید هنوز به این قیمت می‌خورد؟ (قیمت پس از تأیید عوض نشده باشد) */
  fun covers(g: Grant?, priceMinor: Long?): Boolean = g != null && priceMinor != null && g.priceMinor == priceMinor
}
