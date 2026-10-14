package ir.vil3ntec.tohid.ui.screens

/**
 *  F0-24 — شمارهٔ واتس‌اپِ فروشِ اشتراک از سرور می‌آید، نه از کد.
 *
 *  تا 2.14.2 شماره در `Vip.kt` ثابت نوشته شده بود (`93792236008`)، پس عوض
 *  کردنِ `whatsapp_number` در پنلِ مدیر (`app_config`، یکی از
 *  `PUBLIC_CONFIG_KEYS`) روی هیچ گوشی‌ای اثر نداشت. حالا صفحهٔ اشتراک همان
 *  `whatsapp.number`ِ پاسخِ `/me/plans` را به کار می‌برد.
 *
 *  ⚠️ شمارهٔ همیشگی فقط **پشتوانه** است: آفلاین، سرورِ قدیمی، یا پاسخِ خالی.
 *  این پرونده هیچ وابستگیِ اندرویدی ندارد تا در آزمونِ JVM و برنامهٔ
 *  کامپیوتر هم همان باشد.
 */
internal object SalesContact {
  /** شمارهٔ فروشِ پیش از F0-24 — فقط وقتی سرور چیزی نگفته باشد. */
  const val FALLBACK_NUMBER = "0792236008"

  /** شماره‌ای که روی صفحه نوشته می‌شود: همان که مدیر نوشته، یا پشتوانه. */
  fun display(server: String?): String {
    val s = server?.trim().orEmpty()
    return if (s.any { it in '0'..'9' }) s else FALLBACK_NUMBER
  }

  /**
   *  رقم‌های `https://wa.me/<رقم‌ها>` — همان قاعدهٔ سرور (`app.js`، `/plans`):
   *  فقط رقم، و صفرِ اولِ شمارهٔ داخلی ⇒ کدِ کشورِ ۹۳. «۰۰» ی بین‌المللی
   *  هم برداشته می‌شود.
   */
  fun waDigits(server: String?): String {
    val digits = display(server).filter { it in '0'..'9' }.removePrefix("00")
    return if (digits.startsWith("0")) "93" + digits.substring(1) else digits
  }
}
