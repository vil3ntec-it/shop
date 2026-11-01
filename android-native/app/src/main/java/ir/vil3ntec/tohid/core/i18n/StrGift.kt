package ir.vil3ntec.tohid.core.i18n

/**
 *  رسیدِ هدیهٔ بی قیمت (POS-34).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrGift {
  val section: String get() = t("رسیدِ هدیه", "Gift receipt", "د ډالۍ رسید")
  val note: String get() = t(
    "روشن: در فاکتورِ هر فروش دکمهٔ «رسیدِ هدیه» دیده می‌شود. رسیدِ هدیه نامِ دکان، شمارهٔ فاکتور، تاریخ و نام و مقدارِ اقلام را دارد و هیچ قیمت، جمع، پرداخت یا QR ندارد. فقط روی همین دستگاه.",
    "On: each sale's invoice shows a \"Gift receipt\" button. A gift receipt has the shop name, invoice number, date and item names with quantities, and no prices, totals, payments or QR code. This device only.",
    "روښانه: د هر پلور په بل کې د «د ډالۍ رسید» تڼۍ ښکاري. د ډالۍ رسید د دوکان نوم، د بل شمېره، نېټه او د توکو نومونه له اندازې سره لري او هېڅ بیه، ټولټال، تادیه یا QR نه لري. یوازې همدا وسیله.",
  )
  val button: String get() = t("رسیدِ هدیه", "Gift receipt", "د ډالۍ رسید")
  val sending: String get() = t("رسیدِ هدیه به چاپگر می‌رود…", "Sending the gift receipt to the printer…", "د ډالۍ رسید چاپګر ته ځي…")
  val printed: String get() = t("رسیدِ هدیه چاپ شد", "Gift receipt printed", "د ډالۍ رسید چاپ شو")
  fun failed(a: Any): String = t("رسیدِ هدیه کشیده نشد: $a", "The gift receipt couldn't be drawn: $a", "د ډالۍ رسید جوړ نه شو: $a")
}
