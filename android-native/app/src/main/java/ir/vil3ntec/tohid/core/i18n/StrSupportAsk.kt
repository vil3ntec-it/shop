package ir.vil3ntec.tohid.core.i18n

/**
 *  «از پشتیبانی بخواه» — دکمهٔ یک‌لمسیِ هر حالتِ بسته‌ای که کارش با پشتیبانی است (`ui/v3/account/support/AskSupport.kt`) و نوارِ
 *  نتیجه در گفت‌وگوی پشتیبانی. سرخطِ مشکل از همان فهرستِ `core/support/SupportProblem` (آینهٔ سرور، `SupportAskModel.title`)؛ کد و نامِ درونیِ پرچم هرگز
 *  روی صفحه نمی‌آیند (`SupportAskUiTest`، `InfraLeakGuardTest`).
 */
@Suppress("unused")
object StrSupportAsk {
  val button: String get() = t("از پشتیبانی بخواه", "Ask support", "له ملاتړ وغواړئ")
  val sending: String get() = t("درخواست فرستاده می‌شود…", "Sending your request…", "غوښتنه لېږل کېږي…")
  val sent: String get() = t(
    "درخواست به پشتیبانی رسید. همین‌جا جواب می‌گیرید.",
    "Your request reached support. You will get the answer right here.",
    "ستاسو غوښتنه ملاتړ ته ورسېده. ځواب همدلته ترلاسه کوئ.",
  )
  val already: String get() = t(
    "همین درخواست پیش‌تر فرستاده شده و پشتیبانی در جریان است.",
    "This request was already sent and support is on it.",
    "دا غوښتنه مخکې لېږل شوې او ملاتړ پرې کار کوي.",
  )
  val colleague: String get() = t(
    "همکارتان در همین دکان همین را از پشتیبانی خواسته؛ پشتیبانی در جریان است.",
    "A colleague in this shop already asked support for this; support is on it.",
    "ستاسو یوه همکار په همدې دوکان کې همدا له ملاتړ غوښتي؛ ملاتړ پرې کار کوي.",
  )
  val failed: String get() = t(
    "درخواست نرفت. دوباره بزنید یا همین‌جا بنویسید.",
    "The request did not go through. Try again or write here.",
    "غوښتنه ونه لېږل شوه. بیا یې هڅه وکړئ یا همدلته ولیکئ.",
  )
  val retry: String get() = t("دوباره بفرست", "Send again", "بیا یې ولېږئ")

}
