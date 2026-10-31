package ir.vil3ntec.tohid.core.hw

/**
 *  HW-03 — کِی کشوی پول پس از فروش خودکار باز شود (فقط وقتی تنظیمِ «کشو پس از فروشِ نقدی»
 *  روشن است؛ آن تنظیم جدا و پیش‌فرض خاموش است).
 *
 *  قاعده: کشو فقط وقتی باز می‌شود که **نقد به دخل آمده** باشد.
 *    • فروشِ لغوشده ⇒ نه.
 *    • فاکتورِ سفارشِ اینترنتی (`online`/`cod` یا کانالِ آنلاین) ⇒ نه؛ پولش دستِ دخل نیست.
 *    • فروش با ردیف‌های پرداخت (`payments`، تسویهٔ نسخهٔ ۲): دست‌کم یک ردیفِ نقدِ با مبلغ
 *      که امانتِ کسی نیست (`custody` خالی؛ نقدِ پیک `courier` است) و شکست/لغو نخورده ⇒ بله.
 *      پس «نقد + کارت» و «بخشی نقد، باقی نسیه» کشو را باز می‌کنند؛ کارت/حواله/کیف‌پول/نسیهٔ
 *      خالص نه.
 *    • فروشِ بی ردیف (نسخهٔ ۱) ⇒ همان رفتارِ همیشگی: فقط `paymentMethod == "cash"`.
 *
 *  خالص است (بی اندروید) تا بی چاپگر و بی کشو سنجیده شود؛ فرستادنِ `ESC p` همان
 *  `EscPos.drawerOnly` و «همین حالا یا هرگز»ِ `KickOnce` است و روی کشوی واقعی آزموده نشده.
 */
object DrawerRule {

  /** یک ردیفِ پرداختِ فاکتور — همان فیلدهای `SalePayment` که این تصمیم لازم دارد */
  data class Payment(
    val kind: String,
    val amountMinor: Long,
    val status: String = "confirmed",
    val custody: String? = null,
  )

  /** فقط آنچه تصمیم لازم دارد؛ صدازننده از فاکتورِ خودش پر می‌کند */
  data class Sale(
    /** `paymentMethod`ِ قدیمیِ فاکتور: `cash`، `credit`، `card`، `mixed`، `online`، `cod`، … */
    val paymentMethod: String,
    val cancelled: Boolean = false,
    /** فاکتورِ سریِ W یا کانالِ آنلاین (`CashShift.isOnline`) */
    val online: Boolean = false,
    /** ردیف‌های پرداخت؛ `null` یا خالی یعنی فاکتورِ نسخهٔ ۱ */
    val payments: List<Payment>? = null,
  )

  const val CASH = "cash"
  private val NOT_TILL_METHODS = setOf("online", "cod")
  private val DEAD_STATUSES = setOf("failed", "cancelled")

  /** آیا این ردیف نقدی است که همین حالا در دخل گذاشته می‌شود */
  fun isTillCash(p: Payment): Boolean =
    p.kind == CASH && p.amountMinor > 0 && p.custody.isNullOrEmpty() && p.status !in DEAD_STATUSES

  /**
   *  درِ `PrintSpooler`: قاعدهٔ تازه فقط با «ابزارهای تازهٔ دستگاه» (`HwTools`، `hw_tools_v2`، پیش‌فرض
   *  خاموش). خاموش ⇒ دقیقاً رفتارِ main: `paymentMethod == "cash" && status != "cancelled"` (`legacy`).
   */
  fun decide(s: Sale, hwTools: Boolean): Boolean = if (hwTools) opensAfter(s) else legacy(s)

  /** رفتارِ پیش از HW-03، بی هیچ تغییری: فقط `cash` و لغونشده — ردیف‌ها، امانت و کانال دیده نمی‌شوند */
  fun legacy(s: Sale): Boolean = s.paymentMethod == CASH && !s.cancelled

  /** کشو پس از این فروش باز شود؟ (فقط وقتی تنظیمِ کشو روشن است صدا زده می‌شود) */
  fun opensAfter(s: Sale): Boolean {
    if (s.cancelled || s.online || s.paymentMethod in NOT_TILL_METHODS) return false
    val rows = s.payments.orEmpty()
    if (rows.isEmpty()) return s.paymentMethod == CASH
    return rows.any(::isTillCash)
  }
}
