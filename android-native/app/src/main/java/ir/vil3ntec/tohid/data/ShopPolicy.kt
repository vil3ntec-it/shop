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
  private const val CRM_REPORTS = "crm_reports"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun blockExpired(context: Context): Boolean = prefs(context).getBoolean(BLOCK_EXPIRED, false)

  fun setBlockExpired(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(BLOCK_EXPIRED, on).apply()
  }

  /**
   *  گزارش‌های مشتری در «گزارش‌ها»ی کامپیوتر: سنِ بدهی، تحلیلِ خرید (RFM)، مشتریانِ
   *  تکراری و یادآوریِ امروز (CRM-04/06/10/14). پیش‌فرض **خاموش**: تا دکان‌دار خودش
   *  روشن نکند، صفحهٔ گزارش‌ها همان است که بود. فقط خواندنی‌اند و چیزی در دفتر نمی‌نویسند.
   */
  fun crmReports(context: Context): Boolean = prefs(context).getBoolean(CRM_REPORTS, false)

  fun setCrmReports(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(CRM_REPORTS, on).apply()
  }
}
