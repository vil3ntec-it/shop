package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  POS-23 / STF-08 — «فروشندهٔ هر ردیف» (پایهٔ پورسانت): تنظیمِ **دکان** (`tohid-policy:line_seller`، به همهٔ دستگاه‌ها
 *  می‌رود — `ShopSettingsKeys`)، پیش‌فرض **خاموش**. چون مبلغِ پورسانتِ کارمند را جابه‌جا می‌کند، فقط با روشن کردنِ صاحب یا
 *  مدیر (`settings.write`) کار می‌کند.
 *
 *  ⛔ خاموش ⇒ بیت‌به‌بیت همان امروز: صندوق دکمه و کلیدِ تازه‌ای ندارد (Shift+F2 همان «مشتری» است)، ثبت هرگز
 *  `SaleItem.seller` نمی‌نویسد (`Checkout.lineSellers = false`) و «فروشِ هر کاربر»، پورسانت و جدولِ «کاربر» ِ گزارش‌ها
 *  فروشندهٔ ردیف را نمی‌بینند، حتی اگر دفتر آن را از پیش داشته باشد.
 *  مجوزِ نامِ دیگری: صاحب و مدیر ([othersAllowed])؛ نامِ خودِ کاربرِ صندوق همیشه (`core/staff/LineSeller.mayAssign`).
 */
object LineSellerPref {

  private const val KEY = "line_seller"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  /** نامِ کارمندِ دیگری را روی ردیف می‌گذارد؟ صاحب و مدیر — همان دو نقشی که «پورسانتِ این ماه» را می‌بینند */
  fun othersAllowed(context: Context): Boolean = !ShopRole.isStaff(context)
}
