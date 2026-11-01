package ir.vil3ntec.tohid.core.i18n

/**
 *  اصلاحِ روشِ پرداخت پس از فروش (POS-40) در «فاکتورها» ِ صندوق (`PayFixDialog`) و تنظیماتِ همین دستگاه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/pay/PayFix` (commonMain) و `data/PayFixFlow`. ترجمه‌های نامطمئن در
 *  `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrPayFix {
  val menu: String get() = t("اصلاحِ روشِ پرداخت", "Correct payment method", "د ورکړې لاره سمول")
  fun title(invoice: Any): String = t("اصلاحِ روشِ پرداخت — فاکتور #$invoice", "Correct payment method — invoice #$invoice", "د ورکړې لاره سمول — بل #$invoice")
  val body: String get() = t(
    "فاکتور دست نمی‌خورد: یک سندِ اصلاح با نام، تأیید و دلیل در سابقه می‌ماند و جمعِ صندوق همان لحظه درست می‌شود. جمعِ فاکتور همان است.",
    "The invoice is not edited: a correction record with name, approval and reason is kept in the history and the register total is fixed at once. The invoice total stays the same.",
    "بل نه بدلېږي: د سمون یو سند د نوم، تایید او دلیل سره په تاریخچه کې پاتې کېږي او د صندوق ټولګه سمدستي سمېږي. د بل ټولګه همغه پاتې کېږي.",
  )
  val paidNow: String get() = t("پرداختِ همین لحظه", "Payment now", "اوسنۍ ورکړه")
  fun row(method: Any, amount: Any): String = t("$method: $amount ؋", "$method: $amount ؋", "$method: $amount ؋")
  val from: String get() = t("از روشِ", "From", "له لارې")
  val to: String get() = t("به روشِ", "To", "لارې ته")
  val amount: String get() = t("مبلغ (افغانی)", "Amount (AFN)", "اندازه (افغانۍ)")
  val ref: String get() = t("شمارهٔ مرجعِ روشِ تازه", "Reference number of the new method", "د نوې لارې د مرجع شمېره")
  val reason: String get() = t("دلیل (اجباری)", "Reason (required)", "دلیل (اړین)")
  val history: String get() = t("اصلاح‌های پیشین", "Earlier corrections", "پخواني سمونونه")
  fun historyRow(from: Any, to: Any, amount: Any, by: Any): String =
    t("$from ⇐ $to، $amount ؋ — $by", "$from → $to, $amount ؋ — $by", "$from ⇐ $to، $amount ؋ — $by")
  val needsApproval: String get() = t("با تأییدِ مدیر یا صاحبِ دکان ثبت می‌شود", "Recorded with a manager's or the owner's approval", "د مدیر یا د دوکان د خاوند په تایید ثبتېږي")
  fun approvalSummary(amount: Any, from: Any, to: Any): String = t(
    "اصلاحِ $amount ؋ از «$from» به «$to» — تأییدِ مدیر یا صاحب لازم است.",
    "Correcting $amount ؋ from “$from” to “$to” — a manager's or the owner's approval is needed.",
    "د $amount ؋ سمون له «$from» څخه «$to» ته — د مدیر یا خاوند تایید اړین دی.",
  )
  val save: String get() = t("ثبتِ اصلاح", "Record correction", "سمون ثبت کړئ")
  val cancel: String get() = t("انصراف", "Cancel", "لغوه")
  val done: String get() = t("روشِ پرداخت اصلاح شد", "Payment method corrected", "د ورکړې لاره سمه شوه")

  // پیامِ ردِ هسته (`PayFix.check`)
  val errDayClosed: String get() = t("صندوقِ روزِ این فاکتور بسته شده (Z)؛ روزِ بسته اصلاح نمی‌شود", "The register for this invoice's day is closed (Z); a closed day cannot be corrected", "د دې بل د ورځې صندوق تړل شوی (Z)؛ تړلې ورځ نه سمېږي")
  val errCancelled: String get() = t("فاکتورِ لغوشده اصلاح نمی‌شود", "A cancelled invoice cannot be corrected", "لغوه شوی بل نه سمېږي")
  val errOnline: String get() = t("پرداختِ فاکتورِ اینترنتی نزدِ درگاه یا پیک است و این‌جا اصلاح نمی‌شود", "An online invoice is paid through the gateway or courier and is not corrected here", "د انلاین بل ورکړه د ګېټوې یا پیک سره ده او دلته نه سمېږي")
  val errReturned: String get() = t("فاکتوری که برگشت خورده اصلاحِ روشِ پرداخت ندارد", "An invoice with returns cannot have its payment method corrected", "هغه بل چې بېرته راغلی د ورکړې لاره یې نه سمېږي")
  val errDenied: String get() = t("اجازهٔ اصلاحِ روشِ پرداخت را ندارید", "You are not allowed to correct payment methods", "تاسو د ورکړې د لارې د سمولو اجازه نه لرئ")
  val errApproval: String get() = t("تأییدِ مدیر یا صاحب لازم است", "A manager's or the owner's approval is required", "د مدیر یا خاوند تایید اړین دی")
  val errSelf: String get() = t("تأییدکننده باید کسِ دیگری باشد", "The approver must be someone else", "تاییدوونکی باید بل څوک وي")
  val errBy: String get() = t("نامِ کاربرِ این دستگاه معلوم نیست", "The user of this device is unknown", "د دې وسیلې کاروونکی نامعلوم دی")
  val errReason: String get() = t("دلیل لازم است (۳ تا ۱۲۰ نویسه، یک خط)", "A reason is required (3 to 120 characters, one line)", "دلیل اړین دی (۳ تر ۱۲۰ توري، یوه کرښه)")
  val errSame: String get() = t("روشِ تازه همان روشِ پیشین است", "The new method is the same as the old one", "نوې لاره همغه پخوانۍ ده")
  val errMethod: String get() = t("این روش برای اصلاح پذیرفته نیست", "This method cannot be used for a correction", "دا لاره د سمون لپاره نه منل کېږي")
  val errAmount: String get() = t("مبلغ درست نیست", "The amount is not valid", "اندازه سمه نه ده")
  fun errExceeds(method: Any): String = t("مبلغ از ماندهٔ «$method» در این فاکتور بیشتر است", "The amount is more than what is left in “$method” on this invoice", "اندازه په دې بل کې د «$method» له پاتې برخې ډېره ده")
  fun errRef(method: Any): String = t("شمارهٔ مرجعِ «$method» لازم است", "A reference number for “$method” is required", "د «$method» د مرجع شمېره اړینه ده")
  fun errRefBad(method: Any): String = t("شمارهٔ مرجعِ «$method» بیش از ۶۴ نویسه یا چندخطی است", "The reference for “$method” is longer than 64 characters or has several lines", "د «$method» مرجع له ۶۴ تورو اوږده یا څو کرښې ده")
  fun errLimit(method: Any): String = t("مبلغ از سقفِ «$method» بیشتر می‌شود", "The amount goes over the limit of “$method”", "اندازه د «$method» له حد څخه اوړي")
  val errConflict: String get() = t("این اصلاح با محتوای دیگری پیش‌تر ثبت شده؛ پنجره را دوباره باز کنید", "This correction was already recorded with other content; open the window again", "دا سمون مخکې په بل منځپانګه ثبت شوی؛ کړکۍ بیا پرانیزئ")
  val errUnread: String get() = t("دفترِ روی دیسک دوباره خوانده نشد، پس اصلاح ثبت نشد. دوباره امتحان کنید.", "The ledger on disk could not be re-read, so the correction was not recorded. Try again.", "پر ډیسک دفتر بیا ونه لوستل شو، نو سمون ثبت نه شو. بیا هڅه وکړئ.")
  val errOther: String get() = t("اصلاح ثبت نشد", "The correction was not recorded", "سمون ثبت نه شو")

  // تنظیمات (همین دستگاه)
  val settingsTitle: String get() = t("اصلاحِ روشِ پرداخت پس از فروش", "Correct payment method after a sale", "له پلور وروسته د ورکړې لاره سمول")
  val settingsNote: String get() = t(
    "در «فاکتورها» روشِ پرداختِ فاکتورِ ثبت‌شده با سندی جدا (نام، تأیید و دلیل) اصلاح می‌شود؛ فاکتور دست نمی‌خورد و روزِ بسته (Z) اصلاح نمی‌شود. شاگرد با تأییدِ مدیر. فقط روی همین دستگاه؛ نسخه‌های قدیمیِ برنامه اصلاح را در صندوقِ خودشان نمی‌شمارند.",
    "In “Invoices” the payment method of a recorded invoice is corrected with a separate record (name, approval and reason); the invoice is not edited and a closed day (Z) cannot be corrected. Staff need a manager's approval. Only on this device; older app versions do not count the correction in their own register.",
    "په «بلونو» کې د ثبت شوي بل د ورکړې لاره د جلا سند (نوم، تایید او دلیل) په مرسته سمېږي؛ بل نه بدلېږي او تړلې ورځ (Z) نه سمېږي. شاګرد د مدیر په تایید. یوازې په همدې وسیله؛ د اپ زاړه نسخې سمون په خپل صندوق کې نه شمېري.",
  )
}
