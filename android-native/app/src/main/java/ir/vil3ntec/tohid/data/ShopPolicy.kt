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

  /*
   *  PAY (v3 A4) — «پرداختِ ترکیبی و صندوقِ شیفت» (`PayFlow`). پیش‌فرض **خاموش**:
   *  فروش همان نقد/نسیه و صندوق همان `CashShift`ِ امروز. فقط روی همین دستگاه.
   */
  private const val PAY_V2 = "pay_v2"

  fun payV2(context: Context): Boolean = prefs(context).getBoolean(PAY_V2, false)

  fun setPayV2(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(PAY_V2, on).apply()
  }

  /**
   *  گزارش‌های مشتری در «گزارش‌ها»ی کامپیوتر: سنِ بدهی، تحلیلِ خرید (RFM)، مشتریانِ
   *  تکراری و یادآوریِ امروز (CRM-04/06/10/14). پیش‌فرض **خاموش**: تا دکان‌دار خودش
   *  روشن نکند، صفحهٔ گزارش‌ها همان است که بود. خودِ گزارش‌ها فقط خواندنی‌اند.
   *  روشن ⇒ پس از هر همگام‌سازی و در شروع، ردیف‌های دیررسیدهٔ مشتریِ ادغام‌شده هم به ماندگار
   *  می‌روند (`ShopStore.sweepMerged`، CRM-10). تا ادغامی نباشد، این هیچ چیزی نمی‌نویسد.
   */
  fun crmReports(context: Context): Boolean = prefs(context).getBoolean(CRM_REPORTS, false)

  fun setCrmReports(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(CRM_REPORTS, on).apply()
  }

  /**
   *  «تحلیلِ موجودی» در گزارشِ کالاها: ارزش به بها و به قیمتِ فروش، سودِ بالقوه (INV-16) و تحلیلِ
   *  ABC (INV-15) — `ReportEngine.stockInsight` روی `core` (`StockAnalysis`). پیش‌فرض **خاموش**: تا
   *  دکان‌دار خودش روشن نکند، گزارش‌ها همان است که بود. فقط خواندنی؛ هیچ داده‌ای نمی‌نویسد. فقط همین دستگاه.
   */
  private const val STOCK_INSIGHT = "stock_insight"

  fun stockInsight(context: Context): Boolean = prefs(context).getBoolean(STOCK_INSIGHT, false)

  fun setStockInsight(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(STOCK_INSIGHT, on).apply()
  }

  /**
   *  «نقطهٔ سفارش و بیشینه» (INV-12): دو کادرِ تازه در فرمِ کالا، ویرایشِ گروهیِ «حدِ موجودی» در فهرستِ
   *  کالاهای کامپیوتر و فهرستِ «نیاز به سفارش» با پیشنهادِ «تا بیشینه» در گزارش‌ها. پیش‌فرض **خاموش**:
   *  فرم و فهرست همان امروزند. روشن ⇒ فقط دو فیلدِ افزودنیِ کالا (`reorderPoint`، `maxStock`) نوشته
   *  می‌شوند و همگام می‌شوند؛ دستگاهی که خاموش است آن‌ها را نمی‌بیند ولی پاکشان هم نمی‌کند. فقط همین دستگاه.
   */
  private const val STOCK_LEVELS = "stock_levels"

  fun stockLevels(context: Context): Boolean = prefs(context).getBoolean(STOCK_LEVELS, false)

  fun setStockLevels(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(STOCK_LEVELS, on).apply()
  }

  /**
   *  CRM-03 / S07 — سقفِ اعتبار در صندوق: فروشِ نسیه‌ای که ماندهٔ مشتری را از سقفش می‌گذراند بی تأییدِ
   *  مدیر/صاحب ثبت نمی‌شود (`SalesEngine.Checkout.creditGate`، `CreditGate`). پیش‌فرض **خاموش**: صندوق
   *  همان است که بود؛ سقف فقط در دفترِ قرض سنجیده می‌شود. فقط روی همین دستگاه.
   */
  private const val CREDIT_GATE = "credit_gate"

  fun creditGate(context: Context): Boolean = prefs(context).getBoolean(CREDIT_GATE, false)

  fun setCreditGate(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(CREDIT_GATE, on).apply()
  }
}
