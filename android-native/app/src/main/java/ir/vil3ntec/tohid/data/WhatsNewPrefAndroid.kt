package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  OPS-05 — «چه چیزی تازه است» روی **همین دستگاه**: کلیدِ نمایش (`tohid-policy:whats_new`، **پیش‌فرض
 *  خاموش**) و آخرین نسخهٔ پایهٔ دیده‌شده (`tohid-whats-new:seen`). خاموش ⇒ هیچ چیز خوانده یا نوشته
 *  نمی‌شود و برنامه همان است که بود. منطق در `core/guide/WhatsNew`.
 */
object WhatsNewPref {

  private const val POLICY = "tohid-policy"
  private const val KEY_ON = "whats_new"
  private const val STATE = "tohid-whats-new"
  private const val KEY_SEEN = "seen"

  private fun prefs(context: Context, name: String) = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context, POLICY).getBoolean(KEY_ON, false) }.getOrDefault(false)

  fun setOn(context: Context, on: Boolean) {
    prefs(context, POLICY).edit().putBoolean(KEY_ON, on).apply()
  }

  fun seen(context: Context): String? = runCatching { prefs(context, STATE).getString(KEY_SEEN, null) }.getOrNull()

  fun setSeen(context: Context, version: String?) {
    if (version == null) return
    prefs(context, STATE).edit().putString(KEY_SEEN, version).apply()
  }
}
