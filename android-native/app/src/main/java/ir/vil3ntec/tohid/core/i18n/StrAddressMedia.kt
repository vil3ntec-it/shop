package ir.vil3ntec.tohid.core.i18n

/**
 *  CRM-09/CHK-08 — «عکسِ دروازه و یادداشتِ صوتی» ِ نشانیِ سفارشِ ویترین در جزئیاتِ سفارش (`OrderAddressMediaAndroid.kt`).
 */
@Suppress("unused")
object StrAddressMedia {
  val title: String get() = t("نشانی: عکس و صدای مشتری", "Address: customer's photo and voice", "پته: د پېرودونکي انځور او غږ")
  val note: String get() = t(
    "مشتری برای پیدا کردنِ خانه گذاشته است؛ فقط برای همین سفارش.",
    "Left by the customer to help find the place; for this order only.",
    "پېرودونکي د کور موندلو لپاره ایښی؛ یوازې د همدې فرمایش لپاره.",
  )
  val gatePhoto: String get() = t("عکسِ دروازه", "Gate photo", "د دروازې انځور")
  val voiceNote: String get() = t("یادداشتِ صوتی", "Voice note", "غږيز یادښت")
  val play: String get() = t("پخش", "Play", "غږول")
  val stop: String get() = t("بس", "Stop", "بس")
  val loading: String get() = t("در حالِ آمدن…", "Loading…", "راځي…")
  val failed: String get() = t("نیامد؛ برای تلاشِ دوباره لمس کنید.", "Did not load; tap to retry.", "رانغی؛ د بیا هڅې لپاره یې ووهئ.")
}
