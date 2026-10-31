package ir.vil3ntec.tohid.core.i18n

/**
 *  سیاستِ برگشت: مهلت به روز و اقلامِ غیرقابلِ برگشت، با استثنای مجاز (RET-09).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrRetPolicy {

  // تنظیمات
  val days: String get() = t("مهلتِ برگشت (روز)", "Return window (days)", "د بېرته ورکولو موده (ورځې)")
  val daysNote: String get() = t(
    "از روزِ فاکتور شمرده می‌شود؛ خالی یا ۰ = بی مهلت. برگشتِ بی رسید تاریخِ فروش ندارد و مهلت آن را نمی‌سنجد.",
    "Counted from the invoice day; empty or 0 = no limit. A return without a receipt has no sale date and isn't checked.",
    "د بل له ورځې شمېرل کېږي؛ تش یا ۰ = بې مودې. بې رسیده بېرته ورکول د پلور نېټه نه لري او موده یې نه سنجول کېږي.",
  )
  val blocked: String get() = t("اقلامِ غیرقابلِ برگشت", "Non-returnable items", "بېرته نه منل کېدونکي توکي")
  val blockedNote: String get() = t(
    "هر خط یکی: نامِ دسته، زیردسته، نامِ کالا یا بارکد. بیرون از سیاست، شاگرد تأییدِ مدیر می‌خواهد و مدیر و صاحبِ دکان خودشان استثنا می‌گذارند؛ هر استثنا در سابقهٔ سند می‌ماند. فقط روی همین دستگاه.",
    "One per line: a category, subcategory, item name or barcode. Outside the policy a cashier needs a manager's approval, while managers and the shop owner can make the exception themselves; every exception stays in the document's history. This device only.",
    "په هره کرښه کې یو: د ډلې نوم، فرعي ډله، د توکي نوم یا بارکوډ. له تګلارې بهر، شاګرد د مدیر تایید غواړي او مدیر او د دوکان خاوند پخپله استثنا ږدي؛ هره استثنا د سند په سابقه کې پاتې کېږي. یوازې همدا وسیله.",
  )

  // فرمِ برگشت
  val why: String get() = t("بیرون از سیاستِ برگشت", "Outside the return policy", "د بېرته ورکولو له تګلارې بهر")
  fun late(age: Int, days: Int): String = t(
    "بیرون از مهلت: $age روز از فاکتور گذشته (مهلت $days روز)",
    "Past the window: $age days since the invoice (window $days days)",
    "له مودې تېر: له بل څخه $age ورځې تېرې شوې (موده $days ورځې)",
  )
  fun blockedItems(names: String): String = t("غیرقابلِ برگشت: $names", "Non-returnable: $names", "بېرته نه منل کېدونکي: $names")
  val exception: String get() = t(
    "استثنا — با نامِ شما در سابقهٔ سند می‌ماند",
    "Exception — recorded in the document's history under your name",
    "استثنا — ستاسو په نوم د سند په سابقه کې پاتې کېږي",
  )
}
