package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  نسخهٔ قبلیِ اندروید دفتر را در `localStorage`ِ یک WebView داشت. روی
 *  کامپیوتر نسخهٔ قبلی‌ای نبوده، پس چیزی برای آوردن نیست. (آوردنِ دفتر از
 *  گوشی یا سایت: «پشتیبان ⇒ بازیابی»، یا ورود به همان حساب.)
 */
object Migration {
  suspend fun readLegacyData(context: Context): String? = null
}
