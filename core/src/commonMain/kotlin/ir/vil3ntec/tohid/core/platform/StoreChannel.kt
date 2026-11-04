package ir.vil3ntec.tohid.core.platform

/**
 *  راهِ پخشِ همین ساخت (`BuildConfig.STORE`، پرچمِ ساختِ `-Pstore=` در `android-native/app/build.gradle.kts`).
 *
 *  فروشگاهی که خودش به‌روزرسانی و پرداخت را می‌گرداند — کافه‌بازار و Google Play — دو قاعدهٔ مشترک دارد:
 *  برنامه خودش را به‌روز نمی‌کند (نه بنرِ به‌روزرسانی، نه اجازهٔ `REQUEST_INSTALL_PACKAGES`) و راهِ پرداختِ بیرونی
 *  (خرید با واتساپ) نشان نمی‌دهد. «direct» همان APK ِ سایت و گیت‌هاب است.
 */
object StoreChannel {
  const val DIRECT = "direct"
  const val BAZAAR = "bazaar"
  const val PLAY = "play"

  /** به‌روزرسانی و پرداخت فقط از خودِ فروشگاه */
  fun storeManaged(store: String): Boolean = store == BAZAAR || store == PLAY
}
