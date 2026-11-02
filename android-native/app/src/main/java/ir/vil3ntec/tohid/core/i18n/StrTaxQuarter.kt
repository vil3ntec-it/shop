package ir.vil3ntec.tohid.core.i18n

/**
 *  گزارشِ مالیاتِ سه‌ماهه و یادآورِ سررسید (RPT-10) — تنظیماتِ همین دستگاه، کارتِ یادآورِ خانه.
 *
 *  قاعده‌ها سرِ `I18n.kt`؛ منطق در `core/report/QuarterTax` (commonMain) و `data/TaxQuarter`. ترجمه‌های
 *  نامطمئن در `docs/i18n-glossary.md` برای بازبینیِ گویشور.
 */
@Suppress("unused")
object StrTaxQuarter {
  val settingsTitle: String get() = t("گزارشِ مالیاتِ سه‌ماهه", "Quarterly tax report", "د درېیو میاشتو د مالیې راپور")
  val settingsNote: String get() = t(
    "جمعِ فروش، برگشت و خریدِ هر سه‌ماهه از اسنادِ واقعی، با نرخی که این‌جا می‌نویسید، و یادآورِ سررسید روی خانه. نرخ را با حسابدارِ خود تأیید کنید؛ این گزارش ادعای انطباقِ قانونی ندارد.",
    "Each quarter's sales, returns and purchases from the real documents, at the rate you enter here, plus a due-date reminder on the home screen. Confirm the rate with your accountant; this report makes no claim of legal compliance.",
    "د هرې درېمیاشتنۍ پلور، بېرته راګرځېدنه او پېر له اصلي اسنادو، په هغه نرخ چې دلته یې لیکئ، او په کور پاڼه کې د موعد یادونه. نرخ له خپل محاسب سره تایید کړئ؛ دا راپور د قانوني سموالي ادعا نه کوي.",
  )
  val startMonth: String get() = t("ماهِ آغازِ سالِ مالی", "Fiscal year starts in", "د مالي کال پیل میاشت")
  val rate: String get() = t("نرخ (درصد)", "Rate (percent)", "نرخ (سلنه)")
  val rateBad: String get() = t("نرخ ۰ تا ۱۰۰ با حداکثر دو رقمِ اعشار", "Rate 0 to 100 with at most two decimals", "نرخ له ۰ تر ۱۰۰ تر دوو اعشاري عددونو پورې")
  val noRate: String get() = t("هنوز نرخی ثبت نشده — مالیات صفر نشان داده می‌شود", "No rate saved yet — tax shows as zero", "تر اوسه نرخ نه دی ثبت — مالیه صفر ښودل کېږي")
  val dueDay: String get() = t("روزِ سررسید در ماه", "Due day of the month", "د میاشتې د موعد ورځ")
  val dueMonths: String get() = t("سررسید چند ماه پس از سه‌ماهه", "Due months after the quarter", "موعد له درېیو میاشتو څو میاشتې وروسته")
  val remindDays: String get() = t("یادآور از چند روز پیش", "Remind how many days before", "څو ورځې مخکې یادونه")
  val save: String get() = t("ذخیرهٔ تنظیم", "Save settings", "تنظیم خوندي کړئ")
  val saved: String get() = t("ذخیره شد", "Saved", "خوندي شو")
  val prev: String get() = t("سه‌ماههٔ پیش", "Previous quarter", "مخکېنۍ درېمیاشتنۍ")
  val next: String get() = t("سه‌ماههٔ بعد", "Next quarter", "راتلونکې درېمیاشتنۍ")
  fun quarter(q: Any, year: Any, months: Any): String =
    t("سه‌ماههٔ $q ِ سالِ مالیِ $year ($months)", "Quarter $q of fiscal year $year ($months)", "د $year مالي کال $q درېمیاشتنۍ ($months)")
  val sales: String get() = t("فروشِ مشمول", "Taxable sales", "د مالیې وړ پلور")
  val returns: String get() = t("برگشتِ فروش", "Sales returns", "د پلور بېرته راګرځېدنه")
  val net: String get() = t("درآمدِ ناخالصِ خالص", "Net gross receipts", "خالص ناخالص عاید")
  val salesTax: String get() = t("مالیاتِ فروش", "Tax on sales", "د پلور مالیه")
  val returnsTax: String get() = t("مالیاتِ برگشت", "Tax on returns", "د بېرته راګرځېدنې مالیه")
  val netTax: String get() = t("مالیاتِ خالصِ دوره", "Net tax for the period", "د دورې خالصه مالیه")
  val purchases: String get() = t("خرید (فقط برای دیدن؛ مالیاتِ خرید ثبت نمی‌شود)", "Purchases (for reference; purchase tax is not recorded)", "پېر (یوازې د لیدو لپاره؛ د پېر مالیه نه ثبتېږي)")
  fun count(n: Any): String = t("$n سند", "$n documents", "$n سندونه")
  fun due(date: Any): String = t("سررسید: $date", "Due: $date", "موعد: $date")
  val export: String get() = t("خروجیِ CSV", "Export CSV", "CSV صادرول")
  val print: String get() = t("چاپ", "Print", "چاپ")
  val exported: String get() = t("فایل ذخیره شد", "File saved", "فایل خوندي شو")
  val exportFailed: String get() = t("فایل ذخیره نشد", "File not saved", "فایل خوندي نه شو")

  // یادآورِ خانه
  fun reminderTitle(days: Any): String = t("سررسیدِ مالیاتِ سه‌ماهه: $days روز مانده", "Quarterly tax due in $days days", "د درېمیاشتنۍ مالیې موعد: $days ورځې پاتې")
  val reminderToday: String get() = t("سررسیدِ مالیاتِ سه‌ماهه امروز است", "Quarterly tax is due today", "د درېمیاشتنۍ مالیې موعد نن دی")
  val open: String get() = t("دیدنِ گزارش", "Open report", "راپور وګورئ")
}
