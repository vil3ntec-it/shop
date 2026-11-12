package ir.vil3ntec.tohid.core.i18n

/**
 *  دفترِ قرض در صفحه‌های قدیمیِ `ui/screens` که نسخهٔ ۳ هنوز صدا می‌زند: نوارِ سررسید و سقفِ اعتبار، ردیفِ دفتر، رسیدِ قرض، کادرِ
 *  سررسید و سقف (`DebtBookUi`) و دکمهٔ تصویرِ صورت‌حساب (`StatementImageButton`). این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از
 *  هیچ زبانی نمی‌گذشتند. فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته.
 *  قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrDebtPages {
  // ── `DebtBookUi.kt` ──
  fun overdueBanner(n: Any?, amount: Any?): String = t(
    "${asIs(n)} برد از سررسید گذشته — ${asIs(amount)} افغانی",
    "$n credit sales past due — $amount AFN",
    "$n پورونه له نېټې تېر شوي — $amount افغانۍ",
  )
  fun nextDueBanner(date: Any?, amount: Any?): String = t(
    "سررسیدِ بعدی ${asIs(date)} — ${asIs(amount)} افغانی",
    "Next due $date — $amount AFN",
    "بله نېټه $date — $amount افغانۍ",
  )
  fun overLimitBanner(excess: Any?, limit: Any?): String = t(
    "${asIs(excess)} افغانی بیش از سقفِ اعتبار (${asIs(limit)})",
    "$excess AFN over the credit limit ($limit)",
    "$excess افغانۍ د پور له سقف ($limit) څخه زیات",
  )
  fun limitBanner(limit: Any?, room: Any?): String = t(
    "سقفِ اعتبار ${asIs(limit)} — تا سقف ${asIs(room)} افغانی",
    "Credit limit $limit — $room AFN left",
    "د پور سقف $limit — تر سقف $room افغانۍ پاتې",
  )
  fun returnedTail(qty: Any?): String = t(" (مرجوعی ${asIs(qty)})", " (returned $qty)", " (بېرته $qty)")
  fun moreLines(n: Any?): String = t("+${asIs(n)} قلمِ دیگر — در رسید", "+$n more lines — on the receipt", "+$n نور توکي — په رسید کې")
  fun discountOf(amount: Any?): String = t("تخفیف ${asIs(amount)}", "Discount $amount", "تخفیف $amount")
  fun paidOnSpot(amount: Any?): String = t("پرداختِ همان لحظه ${asIs(amount)}", "Paid on the spot $amount", "سمدستي تادیه $amount")
  fun dueOn(date: Any?, tail: Any?): String = t("سررسید ${asIs(date)}${asIs(tail)}", "Due $date$tail", "نېټه $date$tail")
  val overdueTail: String get() = t(" — گذشته", " — overdue", " — تېره")
  fun openAmount(amount: Any?): String = t("باز ${asIs(amount)}", "Open $amount", "پرانیستی $amount")
  val forPrefix: String get() = t("بابتِ ", "For ", "بابت ")
  fun invoiceNo(no: Any?): String = t("فاکتور #${asIs(no)}", "Invoice #$no", "بل #$no")
  fun creditSaleOn(date: Any?): String = t("برد ${asIs(date)}", "Credit sale $date", "پور $date")
  fun prepaidAmount(amount: Any?): String = t("پیش‌پرداخت ${asIs(amount)}", "Prepaid $amount", "مخکې تادیه $amount")
  fun balanceAfterLine(state: Any?): String = t(
    "مانده پس از این ردیف: ${asIs(state)}",
    "Balance after this line: $state",
    "تر دې کرښې وروسته پاتې: $state",
  )
  fun badDate(sample: Any?): String = t(
    "تاریخ خوانده نشد — مثل ${asIs(sample)} بنویسید",
    "Date not understood — write it like $sample",
    "نېټه ونه لوستل شوه — لکه $sample یې ولیکئ",
  )
  fun entryLine(date: Any?, amount: Any?): String = t("${asIs(date)} — ${asIs(amount)} افغانی", "$date — $amount AFN", "$date — $amount افغانۍ")
  fun invoiceTail(no: Any?): String = t(" — فاکتور #${asIs(no)}", " — invoice #$no", " — بل #$no")
  val overLimitTail: String get() = t(
    ".\nاگر ثبت شود، در «دفترچهٔ ثبت» با همین مبلغ می‌ماند.",
    ".\nIf recorded, it stays in the \"record book\" with this amount.",
    ".\nکه ثبت شي، په «د ثبت کتابچه» کې په همدې مبلغ پاتې کېږي.",
  )
  val recordAnyway: String get() = t("باز هم ثبت شود", "Record anyway", "بیا هم دې ثبت شي")
  val limitNote: String get() = t(
    "خالی یعنی بی سقف. بردِ بیش از سقف فقط با تأیید ثبت می‌شود.",
    "Empty means no limit. A credit sale over the limit is recorded only with approval.",
    "تش یعنې بې سقفه. له سقف څخه زیات پور یوازې په تایید ثبتېږي.",
  )

  // ── `StatementImageButton.kt` ──
  val statementPng: String get() = t("تصویرِ صورت‌حساب (PNG)", "Statement image (PNG)", "د حساب د صورت انځور (PNG)")

  // ── `DebtorListModel.kt` ──

  // ── `Debtors.kt`/`DebtorTxSheet.kt` (فقط پیش‌نمایشِ گوشیِ `DevShot`؛ نسخهٔ ۳ دفترِ قرضِ `ui/v3/money` را دارد) ──
  val colPhone: String get() = t("شماره", "Phone", "شمېره")
  val colBalance: String get() = t("مانده", "Balance", "پاتې")
  val colAge: String get() = t("مدت", "Age", "موده")
  val receiveMenu: String get() = t("ثبتِ رسید (پول گرفتم)", "Record a payment (money received)", "رسید ثبت کړئ (پیسې مې واخیستې)")
  val newDebtMenu: String get() = t("قرضِ تازه", "New credit", "نوی پور")
  fun returnedDot(qty: Any?): String = t(" · مرجوعی ${asIs(qty)}", " · returned $qty", " · بېرته $qty")
}
