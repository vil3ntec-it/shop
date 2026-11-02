package ir.vil3ntec.tohid.core.i18n

/**
 *  وضعیتِ پرداخت (PAY-07) در صفحهٔ فاکتور، تراشهٔ فهرستِ فروش و فهرستِ «نیازمند بررسی» ِ صاحب و مدیر.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/pay/PayStatus` (commonMain) و `data/PayStatusFlow`. ترجمه‌های نامطمئن در
 *  `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrPayStatus {
  val pending: String get() = t("در انتظار", "Pending", "په تمه")
  val confirmed: String get() = t("تأییدشده", "Confirmed", "تایید شوی")
  val failed: String get() = t("ناموفق", "Failed", "ناکام")
  val cancelled: String get() = t("لغو", "Cancelled", "لغوه")
  val needsReview: String get() = t("نیازمند بررسی", "Needs review", "کتنې ته اړتیا")
  val refunded: String get() = t("بازپرداخت کامل", "Refunded", "بشپړ بېرته ورکړل شوی")
  val partiallyRefunded: String get() = t("بازپرداخت جزئی", "Partly refunded", "یوه برخه بېرته ورکړل شوې")

  val section: String get() = t("وضعیتِ پرداخت", "Payment status", "د ورکړې حالت")
  fun row(method: Any, amount: Any): String = t("${asIs(method)} · ${asIs(amount)} ؋", "$method · $amount ؋", "$method · $amount ؋")
  val serverOnly: String get() = t(
    "تأییدِ درگاه — وضعیتش را فقط سرور عوض می‌کند",
    "Confirmed by the gateway — only the server changes its status",
    "د ګېټوې تایید — حالت یې یوازې سرور بدلوي",
  )
  val sheetBody: String get() = t(
    "فاکتور دست نمی‌خورد: یک ردیفِ جدا با نامِ شما در سابقه می‌ماند. «در انتظار»، «ناموفق»، «لغو» و «نیازمند بررسی» در جمعِ دریافتیِ صندوق نیستند.",
    "The invoice is not edited: a separate record with your name stays in the history. Pending, failed, cancelled and needs-review payments are not in the register's received total.",
    "بل نه بدلېږي: یو جلا ریکارډ ستاسو د نوم سره په تاریخچه کې پاتې کېږي. «په تمه»، «ناکام»، «لغوه» او «کتنې ته اړتیا» د صندوق په ترلاسه شوې ټولګه کې نه دي.",
  )
  val toConfirmed: String get() = t("پول رسید — تأیید", "Money received — confirm", "پیسې ورسېدې — تایید")
  val toReview: String get() = t("نیازمند بررسی", "Needs review", "کتنې ته اړتیا")
  val toFailed: String get() = t("ناموفق — پول نرسید", "Failed — money not received", "ناکام — پیسې ونه رسېدې")
  val toCancelled: String get() = t("لغوِ پرداخت", "Cancel payment", "ورکړه لغوه کړئ")

  val noAction: String get() = t("کاری برای شما نیست؛ صاحب یا مدیر وضعیت را عوض می‌کند", "Nothing for you here; the owner or a manager changes the status", "دلته ستاسو لپاره کار نشته؛ خاوند یا مدیر حالت بدلوي")
  val done: String get() = t("وضعیتِ پرداخت ثبت شد", "Payment status recorded", "د ورکړې حالت ثبت شو")
  val errDenied: String get() = t("اجازهٔ این تغییر را ندارید", "You are not allowed to make this change", "تاسو د دې بدلون اجازه نه لرئ")
  val errMoved: String get() = t("وضعیتِ این پرداخت همین حالا عوض شده؛ دوباره ببینید", "This payment's status has just changed; look again", "د دې ورکړې حالت همدا اوس بدل شوی؛ بیا یې وګورئ")
  val errUnread: String get() = t("دفترِ روی دیسک دوباره خوانده نشد، پس چیزی ثبت نشد. دوباره امتحان کنید.", "The ledger on disk could not be re-read, so nothing was recorded. Try again.", "پر ډیسک دفتر بیا ونه لوستل شو، نو څه ثبت نه شول. بیا هڅه وکړئ.")
  val errOther: String get() = t("وضعیت ثبت نشد", "The status was not recorded", "حالت ثبت نه شو")

  val reviewTitle: String get() = t("پرداخت‌های نیازمند بررسی", "Payments to review", "هغه ورکړې چې کتنې ته اړتیا لري")
  val reviewEmpty: String get() = t("پرداختی نیازمند بررسی نیست", "No payment needs review", "هېڅ ورکړه کتنې ته اړتیا نه لري")
  fun reviewCount(n: Any): String = t("${asIs(n)} پرداخت نیازمند بررسی", "$n payments to review", "$n ورکړې کتنې ته اړتیا لري")
  fun reviewRow(invoice: Any, method: Any, amount: Any): String =
    t("فاکتور #${asIs(invoice)} — ${asIs(method)} · ${asIs(amount)} ؋", "Invoice #$invoice — $method · $amount ؋", "بل #$invoice — $method · $amount ؋")
  fun unreceivedLine(amount: Any): String = t(
    "غیرنقدِ در انتظار یا نیازمند بررسی (بیرون از جمع): ${asIs(amount)} ؋",
    "Non-cash pending or to review (not in the total): $amount ؋",
    "په تمه یا کتنې ته اړ غیر نغدي (له ټولګې بهر): $amount ؋",
  )
}
