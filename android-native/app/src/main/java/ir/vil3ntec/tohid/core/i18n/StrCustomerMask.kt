package ir.vil3ntec.tohid.core.i18n

/**
 *  CRM-15 — «چاپِ شمارهٔ کامل» در تنظیماتِ فاکتور (فقط صاحب؛ پرچمِ `shop.customer_role_mask`، `core/crm/CustomerMask`).
 */
@Suppress("unused")
object StrCustomerMask {
  val section: String get() = t("شمارهٔ مشتری روی چاپ", "Customer phone on prints", "په چاپ کې د پېرودونکي شمېره")
  val printFull: String get() = t("چاپِ شمارهٔ کامل", "Print the full number", "بشپړه شمېره چاپ کړئ")
  val helpLine: String get() = t("شمارهٔ مشتری: پوشیده یا کامل", "Customer number on receipts", "په رسید د پېرودونکي شمېره")
  val helpExample: String get() = t(
    "مثلاً: خاموش ⇒ «تلفن: •••• ۰۰۸»؛ روشن ⇒ همهٔ شماره.",
    "For example: off ⇒ “Phone: •••• 008”; on ⇒ the whole number.",
    "د بېلګې په توګه: بند ⇒ «تلیفون: •••• ۰۰۸»؛ چالان ⇒ ټوله شمېره.",
  )
  val printFullHint: String get() = t(
    "خاموش: روی رسید و صورت‌حسابِ چاپی شمارهٔ مشتری فقط با سه رقمِ آخر می‌آید. فقط صاحب عوضش می‌کند.",
    "Off: printed receipts and statements show only the last three digits of the customer's number. Only the owner can change it.",
    "بند: په چاپي رسید او حساب کې د پېرودونکي شمېره یوازې له وروستیو درېیو عددونو سره راځي. یوازې مالک یې بدلولی شي.",
  )
}
