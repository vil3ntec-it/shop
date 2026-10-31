package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.pos.LinePricing

/**
 *  تنظیمِ «قیمت و تخفیفِ ردیف» روی همین دستگاه (POS-08، POS-10، POS-20، POS-21) — فایلِ مرزیِ
 *  پلتفرم (`SharedPreferences`)؛ منطق در `LinePricing` (core) و `LinePolicy` است.
 *
 *  ⛔ پیش‌فرض **خاموش**، کنارِ `ShopPolicy` در همان فایلِ `tohid-policy`؛ همگام نمی‌شود.
 *  نوشتنش با کسی است که «تنظیمات» را باز می‌کند (`settings.write`).
 */
object LinePolicyPrefs {
  private const val PREFS = "tohid-policy"
  private const val ON = "line_pricing"
  private const val DIRECTION = "line_price_direction"
  /** سقفِ قیمتِ واحدِ قلمِ آزاد به Minor؛ نبود ⇒ بی سقف، ۰ ⇒ بسته */
  private const val FREE_CAP = "free_line_cap_minor"

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = prefs(context).getBoolean(ON, false)

  fun setOn(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(ON, on).apply()
  }

  fun direction(context: Context): LinePricing.Direction = LinePricing.Direction.of(prefs(context).getString(DIRECTION, null))

  fun setDirection(context: Context, d: LinePricing.Direction) {
    prefs(context).edit().putString(DIRECTION, d.code).apply()
  }

  fun freeLineCapMinor(context: Context): Long? {
    val p = prefs(context)
    if (!p.contains(FREE_CAP)) return null
    return p.getLong(FREE_CAP, 0L).coerceAtLeast(0L)
  }

  /** `null` ⇒ بی سقف */
  fun setFreeLineCapMinor(context: Context, cap: Long?) {
    val e = prefs(context).edit()
    if (cap == null) e.remove(FREE_CAP) else e.putLong(FREE_CAP, cap.coerceAtLeast(0L))
    e.apply()
  }

  /** سیاستِ کاربرِ همین لحظه برای صفحه‌های صندوق */
  fun of(context: Context): LinePricing.Policy = LinePolicy.policy(
    on = on(context),
    role = ShopRole.current(context),
    direction = direction(context),
    freeLineCapMinor = freeLineCapMinor(context),
    mayDiscount = ShopRole.mayDiscount(context),
  )
}
