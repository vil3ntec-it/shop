package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.promo.Loyalty

/**
 *  PRM-13 — «امتیازِ وفاداری» در صندوقِ **همین دستگاه** (پروندهٔ `tohid-policy`، کلیدِ `loyalty`، کنارِ `SpecialOrderPref`).
 *  پیش‌فرض **خاموش**: نه نواری، نه کسبی، نه مصرفی — صندوق، فاکتور، برگشت، خروجی و همگام‌سازی همان امروز
 *  (`LoyaltyOffTest`). برگشتِ امتیاز با برگشت/لغو و نشانِ رسید به این تنظیم بسته نیست (داده است؛ `LoyaltyPoints`).
 *  قاعده ([rule]) را فقط صاحب یا مدیر عوض می‌کند (`LoyaltySettingsWired`)؛ انقضا ([setExpiry]) هم (`LoyaltyShopSettings`).
 *  [on]/[rule] همان تنظیمِ **همین دستگاه** است؛ صندوق و دفتر [effective] را می‌خوانند: قاعدهٔ یکسانِ دکان (سرور، پشتِ پرچمِ
 *  خاموشِ `shop.loyalty_rule`، `LoyaltyShop`) اگر هست، وگرنه همین (`LoyaltyRules.resolve`).
 */
object LoyaltyPref {

  private const val KEY = "loyalty"
  private const val EVERY = "loyalty_every_afn"
  private const val POINT = "loyalty_point_afn"
  private const val SHARE = "loyalty_max_share"
  private const val SILVER = "loyalty_silver_afn"
  private const val GOLD = "loyalty_gold_afn"
  private const val EXPIRY = "loyalty_expiry_months"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }

  /** قاعدهٔ ذخیره‌شده؛ نبود یا خرابی ⇒ پیش‌فرضِ هسته */
  fun rule(context: Context): Loyalty.Rule = runCatching {
    val p = prefs(context)
    val d = Loyalty.Rule()
    Loyalty.Rule(
      everyAfn = p.getLong(EVERY, d.everyAfn), pointAfn = p.getLong(POINT, d.pointAfn), maxSharePct = p.getInt(SHARE, d.maxSharePct),
      silverAfn = p.getLong(SILVER, d.silverAfn), goldAfn = p.getLong(GOLD, d.goldAfn), expiryMonths = p.getInt(EXPIRY, d.expiryMonths),
    ).takeIf { it.valid } ?: d
  }.getOrDefault(Loyalty.Rule())

  /** فقط قاعدهٔ درست نوشته می‌شود؛ نادرست ⇒ false و هیچ. انقضا جدا ([setExpiry]) تا فرمِ بی انقضا آن را صفر نکند */
  fun setRule(context: Context, rule: Loyalty.Rule): Boolean {
    if (!rule.valid) return false
    prefs(context).edit()
      .putLong(EVERY, rule.everyAfn).putLong(POINT, rule.pointAfn).putInt(SHARE, rule.maxSharePct)
      .putLong(SILVER, rule.silverAfn).putLong(GOLD, rule.goldAfn).apply()
    return true
  }

  /** انقضای امتیاز (ماه، ۰ = هرگز) برای قاعدهٔ همین دستگاه؛ بیرون از بازه ⇒ false و هیچ */
  fun setExpiry(context: Context, months: Int): Boolean {
    if (months !in 0..Loyalty.MAX_EXPIRY_MONTHS) return false
    prefs(context).edit().putInt(EXPIRY, months).apply()
    return true
  }

  /** قاعدهٔ در کار برای صندوق، دفتر و پروندهٔ مشتری: قاعدهٔ یکسانِ دکان اگر هست، وگرنه همین دستگاه */
  fun effective(context: Context): LoyaltyRules.Config =
    runCatching { LoyaltyRules.resolve(on(context), rule(context), LoyaltyShop.cached(context)) }.getOrDefault(LoyaltyRules.OFF)
}
