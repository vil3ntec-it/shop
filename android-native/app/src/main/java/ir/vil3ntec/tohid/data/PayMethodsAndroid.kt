package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  PAY-06 — «روش‌های پرداخت قابل تعریف»: روش‌هایی که دکان **نمی‌پذیرد** و از پنلِ پرداختِ صندوق برداشته می‌شوند
 *  (`tohid-policy:pay_methods_off`، تنظیمِ **دکان** — `ShopSettingsKeys`، همهٔ صندوق‌ها یک فهرست). پیش‌فرض خالی ⇒
 *  بیت‌به‌بیت همان پنلِ امروز. فقط روش‌های [PayFlow.toggleableMethods] (نقد همیشه هست؛ کارتِ هدیه/اعتبار تنظیمِ خودشان را
 *  دارند)؛ هر مقدارِ دیگرِ روی دیسک با [PayFlow.offMethods] نادیده. فاکتورِ پیشین با همان روش دست نمی‌خورد (داده است).
 */
object PayMethodsPref {

  private const val KEY = "pay_methods_off"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(ShopSettingsKeys.POLICY, Context.MODE_PRIVATE)

  fun off(context: Context): Set<String> =
    runCatching { PayFlow.offMethods(prefs(context).getStringSet(KEY, null)) }.getOrDefault(emptySet())

  /** روشِ [id] در پنل باشد ([on]) یا نه */
  fun set(context: Context, id: String, on: Boolean) {
    val now = off(context)
    save(context, PayFlow.offMethods(if (on) now - id else now + id))
  }

  fun save(context: Context, ids: Set<String>) {
    prefs(context).edit().putStringSet(KEY, HashSet(ids)).apply()
  }
}
