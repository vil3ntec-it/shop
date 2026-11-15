package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.purchase.PoSeasonality

/**
 *  PUR-03 — نشانِ اطمینانِ پیشنهادِ سفارش (از درازای تاریخچهٔ فروش و نوسان؛ `core/purchase/PoSeasonality`) در صفحهٔ
 *  «پیشنهادِ سفارش» (`ui/screens/PurchaseSuggestView.kt`) و هر صفحهٔ دیگری که همان پیشنهاد را نشان می‌دهد.
 */
@Suppress("unused")
object StrPoSeason {
  val low: String get() = t("اطمینانِ کم", "Low confidence", "لږ باور")
  val medium: String get() = t("اطمینانِ متوسط", "Medium confidence", "منځنی باور")
  val high: String get() = t("اطمینانِ زیاد", "High confidence", "ډېر باور")

  fun label(c: PoSeasonality.Confidence): String = when (c) {
    PoSeasonality.Confidence.LOW -> low
    PoSeasonality.Confidence.MEDIUM -> medium
    PoSeasonality.Confidence.HIGH -> high
  }

  val shortHistory: String get() = t(
    "کمتر از ۱۲ ماه فروش؛ فصل حساب نشد",
    "Less than 12 months of sales; season not counted",
    "له ۱۲ میاشتو لږ پلور؛ موسم حساب نه شو",
  )
}
