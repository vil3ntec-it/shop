package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  PRD-05/PRD-23 — کلیدِ «برچسب و بارکدِ داخلی» روی **همین دستگاه** (پروندهٔ `tohid-policy`، کنارِ `ShopPolicy`).
 *  پیش‌فرض **خاموش**: صفحهٔ «انبار» همان است که بود، بیت‌به‌بیت. روشن ⇒ دکمهٔ «چاپِ برچسب و بارکدِ داخلی».
 *  خودِ کلید همگام نمی‌شود؛ بارکدی که با آن ساخته شود دادهٔ کالاست و مثلِ هر ویرایشی همگام می‌شود.
 */
object LabelToolsPref {

  private const val PREFS = "tohid-policy"
  private const val KEY = "label_tools"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context).getBoolean(KEY, false) }.getOrDefault(false)

  fun set(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY, on).apply()
  }
}
