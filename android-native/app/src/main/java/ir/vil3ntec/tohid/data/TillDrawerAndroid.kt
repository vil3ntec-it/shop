package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  PAY-17 — «چند کشو و شیفتِ شناور»: تنظیمِ **همین دستگاه** (پیش‌فرض خاموش؛ ردهٔ B چون پولِ دخل را جدا می‌شمارد) و
 *  شیفتِ برگزیدهٔ همین دستگاه، در `tohid-policy`. همگام نمی‌شود؛ قاعده‌ها `core/pay/Drawers` و `TillDrawer`.
 *  خاموش ⇒ `TillDrawer.picked = null` ⇒ صندوق همان کشوی اصلیِ امروز و هیچ ردیفی مهر نمی‌گیرد.
 */
object MultiDrawerPref {

  private const val KEY = "multi_drawer"
  private const val PICKED = "multi_drawer_shift"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-policy", Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  /** خاموش کردن شیفتِ برگزیده را هم رها می‌کند ⇒ این دستگاه دوباره روی کشوی اصلی */
  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply { if (!on) remove(PICKED) }.apply()
    load(context)
  }

  /** «ادامه روی این دستگاه» / «کشوی اصلی» (`null`) — فقط با تنظیمِ روشن */
  fun pick(context: Context, shiftId: String?) {
    prefs(context).edit().apply { if (shiftId == null) remove(PICKED) else putString(PICKED, shiftId) }.apply()
    load(context)
  }

  /** پیش از نخستین کشیدنِ صفحه (`AppStart.loadSettings`) و پس از هر تغییر */
  fun load(context: Context) {
    val p = runCatching { prefs(context) }.getOrNull()
    TillDrawer.picked = if (p != null && on(context)) runCatching { p.getString(PICKED, null) }.getOrNull() else null
  }
}
