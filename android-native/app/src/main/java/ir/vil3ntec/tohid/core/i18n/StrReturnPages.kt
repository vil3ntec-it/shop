package ir.vil3ntec.tohid.core.i18n

/**
 *  برگشت و تعویض در صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ هنوز صدا می‌زند (`ReturnsV2`: برگهٔ برگشت، رسیدِ برگشت و تنظیم) و
 *  درخواستِ تأییدِ برگشت (`ui/components/staff/ReturnApprovalRequest`). این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی
 *  نمی‌گذشتند. فارسی کلمه‌به‌کلمه همان پیشین است؛ بیشترِ جمله‌ها همان جملهٔ جدولِ صندوقِ نسخهٔ ۳ (`StrSale`) و تنظیمات
 *  (`StrSettings`) است و همان دوباره به کار رفته. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrReturnPages {
  // ── `ReturnsV2.kt` ──
  fun returnOfInvoice(no: Any?): String = t("برگشت — فاکتور #${asIs(no)}", "Return — invoice #$no", "ستنېدنه — بل #$no")
  fun returnable(qty: Any?, unit: Any?, price: Any?): String = t(
    "قابلِ برگشت: ${asIs(qty)} ${asIs(unit)} · فی ${asIs(price)}",
    "Returnable: $qty $unit · unit price $price",
    "د ستنېدو وړ: $qty $unit · د یوه بیه $price",
  )
  val unitPriceAfn: String get() = t("فی (؋)", "Unit price (؋)", "د یوه بیه (؋)")
  fun backToMethodNotDrawer(method: Any?): String = t(
    "پس به ${asIs(method)} (نه از دخل)",
    "Back to $method (not from the drawer)",
    "$method ته بېرته (نه له دراز څخه)",
  )
  val returnAmount: String get() = t("مبلغِ برگشت", "Return amount", "د ستنېدنې مبلغ")
  fun approvedBy(by: Any?): String = t("تأیید شد — ${asIs(by)}", "Approved — $by", "تایید شو — $by")
  fun approvalNeededWhy(who: Any?, why: Any?): String = t(
    "تأییدِ ${asIs(who)} لازم است (${asIs(why)})",
    "Approval by $who is needed ($why)",
    "د $who تایید اړین دی ($why)",
  )
  fun approverPosts(who: Any?): String = t(
    "این برگشت را ${asIs(who)} با حسابِ خودش ثبت کند.",
    "$who should record this return with their own account.",
    "دا ستنېدنه دې $who په خپل حساب ثبت کړي.",
  )
  fun exchangeReceive(amount: Any?): String = t(
    "تعویض ثبت شد — دریافت ${asIs(amount)} ؋",
    "Exchange recorded — receive $amount ؋",
    "بدلون ثبت شو — ترلاسه $amount ؋",
  )
  fun exchangePay(amount: Any?): String = t(
    "تعویض ثبت شد — پرداخت ${asIs(amount)} ؋",
    "Exchange recorded — pay $amount ؋",
    "بدلون ثبت شو — ورکړه $amount ؋",
  )
  fun returnRecorded(amount: Any?): String = t("برگشت ثبت شد — ${asIs(amount)} ؋", "Return recorded — $amount ؋", "ستنېدنه ثبت شوه — $amount ؋")
  val limitAfn: String get() = t("سقف (؋)", "Limit (؋)", "سقف (؋)")
  fun returnReceiptOf(label: Any?): String = t("رسیدِ برگشت ${asIs(label)}", "Return receipt $label", "د ستنېدنې رسید $label")
  fun fromInvoice(no: Any?): String = t("از فاکتور #${asIs(no)}", "From invoice #$no", "له بل #$no څخه")
  fun newPurchaseInvoice(no: Any?): String = t("خریدِ تازه — فاکتور #${asIs(no)}", "New purchase — invoice #$no", "نوی پېر — بل #$no")
  fun backToMethod(method: Any?): String = t("پس به ${asIs(method)}", "Back to $method", "$method ته بېرته")
  fun reasonOf(reason: Any?): String = t("دلیل: ${asIs(reason)}", "Reason: $reason", "دلیل: $reason")
  fun approvedByLine(by: Any?): String = t("تأیید: ${asIs(by)}", "Approved by: $by", "تایید: $by")

  // ── `ReturnApprovalRequest.kt` ──
  val approveReturn: String get() = t("تأییدِ برگشت", "Approve return", "د ستنېدنې تایید")
  fun returnOf(amount: Any?, why: Any?): String = t(
    "برگشتِ ${asIs(amount)} ؋ (${asIs(why)}) — ",
    "Return of $amount ؋ ($why) — ",
    "د $amount ؋ ستنېدنه ($why) — ",
  )
  fun approvalNeeded(who: Any?): String = t("تأییدِ ${asIs(who)} لازم است.", "Approval by $who is needed.", "د $who تایید اړین دی.")
}
