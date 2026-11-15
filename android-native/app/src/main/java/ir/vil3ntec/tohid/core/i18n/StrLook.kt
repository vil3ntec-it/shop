package ir.vil3ntec.tohid.core.i18n

/**
 *  ظاهرِ آرامِ نسخهٔ ۳ (`claude/e-rows-63w`): کادرِ کشوییِ دوره، فهرستِ فشردهٔ عددها با «بیشتر». قاعده‌ها سرِ `I18n.kt`؛
 *  «امروز»/«دیروز»/«این ماه» همان `Str.today`، `StrKit.yesterday` و `StrHistory.thisMonth`.
 */
@Suppress("unused")
object StrLook {
  /** نامِ کادرِ کشوییِ دوره برای صفحه‌خوان */
  val period: String get() = t("دوره", "Period", "موده")

  /** «بیشتر» ِ فهرستِ فشردهٔ عددها: باز کردنِ باقیِ ردیف‌ها */
  fun moreMetrics(n: Any): String = t("${asIs(n)} عددِ دیگر", "$n more", "$n نور")

  /** بستنِ باقیِ ردیف‌ها */
  val fewerMetrics: String get() = t("کمتر", "Show less", "لږ")

  /** نامِ کادرِ کشوییِ پالایهٔ فهرست (همه/فعال/…) */
  val show: String get() = t("نمایش", "Show", "ښودل")
}
