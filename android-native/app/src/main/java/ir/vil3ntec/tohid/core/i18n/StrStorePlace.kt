package ir.vil3ntec.tohid.core.i18n

/**
 *  نشانی و پینِ دکان برای مشتریِ ویترین (ممیزیِ ویترین با چشمِ مشتری ۱۴۰۵/۰۸/۱۷، مشکلِ ۶: مشتریِ «دریافت از دکان»
 *  نمی‌دانست کجا برود). سرور: مهاجرتِ ۲۱۵، `PUT /api/shop/storefront` با `pickupAddress` و `pickupPin`؛ وب: سربرگ، ثبت و پیگیری.
 *  جای ویرایش: «ویترین» ⇐ «تنظیمات» (`StorefrontSettingsModel`)؛ ردیفِ «تنظیمات ⇐ فروش و پرداخت ⇐ آنلاین» به همان‌جا
 *  می‌رود (`SettingsLayout.STORE_PLACE`). جدا از `StrOnline` تا آن زیرِ سقفِ اندازه بماند.
 */
@Suppress("unused")
object StrStorePlace {
  val section: String get() = t("نشانی و نقشهٔ دکان", "Shop address and map", "د دوکان پته او نقشه")
  val line: String get() = t(
    "مشتریِ «دریافت از دکان» نشانی و مسیرِ دکان را در ویترین می‌بیند",
    "Pickup customers see your address and directions on the storefront",
    "د «له دوکانه اخیستلو» پېرودونکی د دوکان پته او لار په ویترین کې ویني",
  )
  val address: String get() = t("نشانیِ دکان", "Shop address", "د دوکان پته")
  val addressHelper: String get() = t(
    "برای مشتریِ «دریافت از دکان»؛ مثلاً «کارتهٔ سه، سرکِ اول، کنارِ مسجد»",
    "For pickup customers; e.g. “Karte 3, 1st street, next to the mosque”",
    "د «له دوکانه اخیستلو» پېرودونکي لپاره؛ لکه «کارته درې، لومړی سړک، د جومات تر څنګ»",
  )
  val pin: String get() = t("پینِ دکان روی نقشه (اختیاری)", "Shop pin on the map (optional)", "په نقشه کې د دوکان پن (اختیاري)")
  val pinHelper: String get() = t(
    "عرض و طولِ در از نقشهٔ گوشی؛ مثلاً 34.5281, 69.1723",
    "Latitude and longitude of the door from your map app, e.g. 34.5281, 69.1723",
    "د دروازې عرض او طول د تلیفون له نقشې؛ لکه 34.5281, 69.1723",
  )
  val pinError: String get() = t("پین را به شکلِ «عرض، طول» بنویسید", "Write the pin as “latitude, longitude”", "پن د «عرض، طول» په بڼه ولیکئ")
}
