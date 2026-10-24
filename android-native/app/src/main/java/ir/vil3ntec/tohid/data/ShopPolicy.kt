package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  سیاست‌های دکان که دکان‌دار خودش انتخاب می‌کند — روی همین دستگاه.
 *
 *  `blockExpired`: کالای منقضی فروخته نشود. پیش‌فرض **خاموش** است، چون
 *  تاریخِ انقضای ثبت‌نشده یا اشتباه نباید فروشِ روزمره را ناگهان ببندد؛
 *  هشدارِ انقضا همیشه هست. همان `tohid-block-expired`ِ نسخهٔ وب.
 */
object ShopPolicy {
  private const val PREFS = "tohid-policy"
  private const val BLOCK_EXPIRED = "block_expired"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun blockExpired(context: Context): Boolean = prefs(context).getBoolean(BLOCK_EXPIRED, false)

  fun setBlockExpired(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(BLOCK_EXPIRED, on).apply()
  }

  /*
   *  PAY (v3 A4) — «پرداختِ ترکیبی و صندوقِ شیفت» (`PayFlow`). پیش‌فرض **خاموش**:
   *  فروش همان نقد/نسیه و صندوق همان `CashShift`ِ امروز. فقط روی همین دستگاه.
   */
  private const val PAY_V2 = "pay_v2"

  fun payV2(context: Context): Boolean = prefs(context).getBoolean(PAY_V2, false)

  fun setPayV2(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(PAY_V2, on).apply()
  }
}
