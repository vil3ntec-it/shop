package ir.vil3ntec.tohid.core.pos

/**
 *  POS-41 — «چاپ برای همین فاکتور: روشن یا خاموش» (Alt+P در پنلِ پرداختِ کامپیوتر، `sale.payment.togglePrint`).
 *
 *  [override] `null` = همان تنظیمِ «چاپِ خودکار» ِ همین دستگاه (امروز)؛ `true`/`false` فقط برای فاکتوری که الان ثبت می‌شود.
 *  برگرداندن به همان تنظیم دوباره `null` است، پس فاکتورِ بعدی (و هر صندوقی که کلید را نزد) همان امروز می‌ماند.
 *  ⛔ فقط کاغذِ رسید: ضربهٔ کشو جداست (`DrawerRule`) و «این یکی نه» کشو را نمی‌بندد.
 */
object PrintToggle {

  /** پس از ثبت چاپ شود؟ */
  fun auto(override: Boolean?, autoPrint: Boolean): Boolean = override ?: autoPrint

  /** یک بار زدنِ کلید: وارونهٔ آنچه الان می‌شود؛ برابر با تنظیم ⇒ `null` */
  fun toggle(override: Boolean?, autoPrint: Boolean): Boolean? {
    val next = !auto(override, autoPrint)
    return if (next == autoPrint) null else next
  }
}
