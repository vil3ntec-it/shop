package ir.vil3ntec.tohid.core.i18n

/**
 *  کارت یا بارکدِ مشتری (CRM-16) — پروندهٔ مشتری، صندوق و تنظیماتِ همین دستگاه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/crm/CustomerCard` (commonMain) و `data/CustomerCards`. ترجمه‌های
 *  نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrCustomerCard {
  val settingsTitle: String get() = t("کارت یا بارکدِ مشتری", "Customer card or barcode", "د پېرودونکي کارت یا بارکوډ")
  val settingsNote: String get() = t(
    "هر مشتری یک کدِ یکتا می‌گیرد که چاپ می‌شود. خواندنِ همان کد در صندوق مشتری را انتخاب می‌کند، نه کالا؛ کدی که بارکدِ کالاست همیشه کالا می‌ماند.",
    "Each customer gets a unique printable code. Scanning it at the till selects the customer instead of adding a product; a code that is a product barcode always stays the product.",
    "هر پېرودونکی یو بې‌ساری کوډ اخلي چې چاپېږي. په صندوق کې د همدې کوډ لوستل پېرودونکی ټاکي، نه توکی؛ هغه کوډ چې د توکي بارکوډ دی تل توکی پاتې کېږي.",
  )
  val title: String get() = t("کارتِ مشتری", "Customer card", "د پېرودونکي کارت")
  fun current(code: Any): String = t("کارت: $code", "Card: $code", "کارت: $code")
  val none: String get() = t("بی کارت", "No card", "کارت نشته")
  val generate: String get() = t("ساختنِ کدِ تازه", "Generate a new code", "نوی کوډ جوړ کړئ")
  val manual: String get() = t("کدِ کارتِ چاپ‌شده (۸ تا ۲۰ رقم)", "Printed card code (8 to 20 digits)", "د چاپ شوي کارت کوډ (۸ تر ۲۰ عددونه)")
  val useManual: String get() = t("ثبتِ همین کد", "Use this code", "همدا کوډ ثبت کړئ")
  val remove: String get() = t("برداشتنِ کارت", "Remove card", "کارت لرې کړئ")
  val print: String get() = t("چاپِ کارت", "Print card", "کارت چاپ کړئ")
  val saved: String get() = t("کارتِ مشتری ذخیره شد", "Customer card saved", "د پېرودونکي کارت خوندي شو")
  val removed: String get() = t("کارت برداشته شد", "Card removed", "کارت لرې شو")
  val notSaved: String get() = t("ذخیره نشد — دوباره بزنید", "Not saved — try again", "خوندي نه شو — بیا هڅه وکړئ")
  val close: String get() = t("بستن", "Close", "بندول")
  fun picked(name: Any): String = t("مشتری انتخاب شد: $name", "Customer selected: $name", "پېرودونکی وټاکل شو: $name")
  fun printLabel(name: Any): String = t("کارتِ $name", "Card of $name", "د $name کارت")

  val reasonEmpty: String get() = t("کد خالی است", "The code is empty", "کوډ تش دی")
  val reasonDigits: String get() = t("کد فقط رقم باشد", "Digits only", "کوډ دې یوازې عددونه وي")
  val reasonLength: String get() = t("کد ۸ تا ۲۰ رقم باشد", "The code must be 8 to 20 digits", "کوډ دې له ۸ تر ۲۰ عددونو وي")
  val reasonCheck: String get() = t("رقمِ کنترلِ این کد درست نیست — کد را دوباره بخوانید", "The check digit is wrong — scan the code again", "د دې کوډ کنټرولي عدد سم نه دی — کوډ بیا ولولئ")
  val reasonCustomer: String get() = t("این کد کارتِ مشتریِ دیگری است", "This code is another customer's card", "دا کوډ د بل پېرودونکي کارت دی")
  val reasonProduct: String get() = t("این کد بارکدِ یک کالاست", "This code is a product barcode", "دا کوډ د یوه توکي بارکوډ دی")
  val reasonExhausted: String get() = t("کدِ آزادی پیدا نشد", "No free code found", "ازاد کوډ ونه موندل شو")
}
