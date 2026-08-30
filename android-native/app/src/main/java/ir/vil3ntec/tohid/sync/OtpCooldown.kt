package ir.vil3ntec.tohid.sync

import android.content.Context

/**
 *  فاصلهٔ «ارسال دوبارهٔ کد» — که با بستنِ صفحه یا برنامه از بین نرود.
 *
 *  هر پیامک پول دارد. قبلاً شمارشِ معکوس فقط در حافظهٔ همان صفحه بود:
 *  کاربر برمی‌گشت، دوباره می‌آمد، شمارش از صفر شروع می‌شد و یک پیامکِ
 *  دیگر می‌رفت. با چرخاندنِ گوشی یا بستن و باز کردنِ برنامه هم همین.
 *
 *  حالا مهلت روی گوشی نوشته می‌شود، برای هر نشانی جدا. بستنِ برنامه هم
 *  پاکش نمی‌کند.
 *
 *  این جلوی کاربرِ بدخواه را نمی‌گیرد — او می‌تواند اطلاعاتِ برنامه را
 *  پاک کند. سدِ واقعی سمتِ سرور است (`OTP_RESEND_SECONDS` و سقفِ روزانه).
 *  این یکی جلوی کاربرِ عادی و عجول را می‌گیرد، که همان‌جایی است که پول
 *  هدر می‌رفت.
 */
class OtpCooldown(context: Context) {

  private val prefs = context.getSharedPreferences("tohid-otp", Context.MODE_PRIVATE)

  /** چند ثانیه تا اجازهٔ ارسالِ دوباره — صفر یعنی همین حالا می‌شود */
  fun secondsLeft(destination: String): Int {
    val until = prefs.getLong(key(destination), 0L)
    if (until <= 0L) return 0
    val left = until - System.currentTimeMillis()
    //  ساعتِ گوشی ممکن است عقب و جلو شود. سقف می‌گذاریم تا اگر کاربر
    //  ساعت را جلو برد، برنامه تا ابد قفل نماند.
    return when {
      left <= 0 -> 0
      left > MAX_MS -> 0
      else -> ((left + 999) / 1000).toInt()
    }
  }

  /** بعد از هر ارسالِ موفق، از روی همان چیزی که سرور گفت */
  fun start(destination: String, seconds: Int) {
    val safe = seconds.coerceIn(0, MAX_SECONDS)
    if (safe == 0) { clear(destination); return }
    prefs.edit()
      .putLong(key(destination), System.currentTimeMillis() + safe * 1000L)
      .apply()
  }

  fun clear(destination: String) {
    prefs.edit().remove(key(destination)).apply()
  }

  private fun key(destination: String) = "resend:" + destination.trim().lowercase()

  private companion object {
    const val MAX_SECONDS = 15 * 60
    const val MAX_MS = MAX_SECONDS * 1000L
  }
}
