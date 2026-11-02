package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  OPS-05 — «چه چیزی تازه است» روی **همین دستگاه**: کلیدِ نمایش (`tohid-policy:whats_new`) و آخرین نسخهٔ پایهٔ
 *  دیده‌شده (`tohid-whats-new:seen`). **پیش‌فرض روشن** (قاعدهٔ صاحب‌کار ۱۴۰۵/۰۸/۱۱: آنچه تصمیم نمی‌خواهد روشن و
 *  پنهان؛ نسخهٔ ۳ کلیدی برایش نشان نمی‌دهد)؛ فقط کسی که در رابطِ قدیم خاموشش کرده بود خاموش می‌ماند ⇒ هیچ چیز
 *  خوانده یا نوشته نمی‌شود. منطق در `core/guide/WhatsNew`.
 */
object WhatsNewPref {

  private const val POLICY = "tohid-policy"
  private const val KEY_ON = "whats_new"
  private const val STATE = "tohid-whats-new"
  private const val KEY_SEEN = "seen"

  private fun prefs(context: Context, name: String) = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

  fun on(context: Context): Boolean = runCatching { prefs(context, POLICY).getBoolean(KEY_ON, DEFAULT_ON) }.getOrDefault(DEFAULT_ON)

  /** پیش‌فرضِ نمایش — روشن */
  const val DEFAULT_ON = true

  fun setOn(context: Context, on: Boolean) {
    prefs(context, POLICY).edit().putBoolean(KEY_ON, on).apply()
  }

  fun seen(context: Context): String? = runCatching { prefs(context, STATE).getString(KEY_SEEN, null) }.getOrNull()

  fun setSeen(context: Context, version: String?) {
    if (version == null) return
    prefs(context, STATE).edit().putString(KEY_SEEN, version).apply()
  }
}
