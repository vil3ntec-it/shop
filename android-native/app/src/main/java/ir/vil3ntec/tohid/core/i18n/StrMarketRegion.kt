package ir.vil3ntec.tohid.core.i18n

/**
 *  S109 — ولایت و ولسوالیِ دکان در «نمایش در بازارچه» (شمارشِ منطقه‌ای، §۲۰.۱۱) — فارسیِ معیار؛ رویهٔ دری جداست
 *  (`text/DariText.kt`). قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrMarketRegion {
  val title: String get() = t("ولایت و ولسوالی", "Province and district", "ولایت او ولسوالي")
  val body: String get() = t(
    "فقط برای شمارشِ دکان‌ها به تفکیکِ ولایت و ولسوالی و پیدا شدن در همان منطقه؛ نشانیِ دقیق نیست.",
    "Only for counting shops by province and district and being found in that area; it is not an exact address.",
    "یوازې د ولایت او ولسوالۍ په توپیر د دوکانونو شمېرلو او په هماغه سیمه کې موندل کېدو لپاره؛ دقیق پته نه ده.",
  )
  val province: String get() = t("ولایت", "Province", "ولایت")
  val pickProvince: String get() = t("ولایت را انتخاب کنید", "Choose the province", "ولایت وټاکئ")
  val noProvince: String get() = t("گفته نشده", "Not set", "نه دی ویل شوی")
  val district: String get() = t("ولسوالی (اختیاری)", "District (optional)", "ولسوالي (اختیاري)")
  val districtPlaceholder: String get() = t("مثلاً انجیل", "e.g. Injil", "لکه انجیل")
  val districtLabel: String get() = t("ولسوالی", "District", "ولسوالي")
}
