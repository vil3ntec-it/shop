package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.pay.FxRate

/**
 *  PAY-10 — «پرداخت با ارزِ نقد» (دالر، کلدار، ریال): تنظیمِ **دکان** (`tohid-policy:fx_tender`، به همهٔ دستگاه‌ها می‌رود —
 *  `ShopSettingsKeys`)، پیش‌فرض **خاموش** (D-15: فقط وقتی دکانی خواست). جدولِ نرخِ خودِ دکان هم تنظیمِ دکان است
 *  (`tohid-policy:fx_rates`، JSON ِ `FxTillFlow.encode`) و فقط صاحب/مدیر آن را می‌نویسد (بخشِ تنظیمات `Need.MANAGE`؛
 *  همگام‌سازیِ تنظیماتِ کارمند هرگز نمی‌فرستد).
 *
 *  ⛔ خاموش ⇒ بیت‌به‌بیت همان امروز: صندوق جدولی نمی‌گیرد ([tillRates] خالی)، کادرِ ارز نیست، `SettleInput.fx` خالی و هیچ
 *  ردیفِ `fx` یا خروجِ «پولِ پسِ ارز» ساخته نمی‌شود. فاکتوری که پیش‌تر با ارز ثبت شد همان می‌ماند (داده است).
 */
object FxTillPref {

  private const val KEY = "fx_tender"
  private const val RATES_KEY = "fx_rates"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  /** جدولِ نرخِ دکان (سنجیده)؛ خراب یا نبوده ⇒ خالی */
  fun rates(context: Context): List<FxRate> =
    runCatching { FxTillFlow.decode(prefs(context).getString(RATES_KEY, null)) }.getOrDefault(emptyList())

  fun saveRates(context: Context, list: List<FxRate>) {
    prefs(context).edit().putString(RATES_KEY, FxTillFlow.encode(list)).apply()
  }

  /** صندوق: جدولِ نرخ فقط با تنظیمِ روشن؛ خاموش ⇒ خالی (بی کادرِ ارز) */
  fun tillRates(context: Context): List<FxRate> = if (on(context)) rates(context) else emptyList()
}
