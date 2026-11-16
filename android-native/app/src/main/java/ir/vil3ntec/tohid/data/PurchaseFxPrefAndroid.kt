package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  PUR-11 (D-15 ب) — کلیدِ «خرید به ارزِ دیگر»: تنظیمِ **دکان** (`tohid-policy:purchase_fx`، به همهٔ دستگاه‌ها می‌رود —
 *  `ShopSettingsKeys`)، پیش‌فرض **خاموش** (تصمیمِ ایمنیِ هماهنگ‌کننده، ۱۴۰۵/۰۸/۲۶). نسخهٔ کهنه ردیفِ ارزی را افغانی می‌خواند و
 *  پرداختِ ارزی را از دخلِ افغانی کم می‌کند؛ پس فقط صاحب یا مدیر (بخشِ تنظیمات `Need.MANAGE`) و پس از به‌روز کردنِ همهٔ
 *  دستگاه‌های دکان روشنش می‌کند.
 *
 *  ⛔ خاموش ⇒ فاکتورِ تازه همان افغانیِ پیشین (کادرِ ارز نیست — `SupplierFx.offered`). فاکتور، حساب و پرداختِ ارزیِ پیشین همچنان
 *  دیده و پرداخت می‌شود: بدهی هرگز پنهان نمی‌شود.
 */
object PurchaseFxPref {

  private const val KEY = "purchase_fx"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
