package ir.vil3ntec.tohid.sync

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.data.DeviceLocation
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  رساندنِ لوکیشن به سرور.
 *
 *  ── قرارِ صاحب مخزن ────────────────────────────────────────────────
 *  «بدون اینکه برنامه برود ثبت‌نام کند هم لوکیشن باید روشن باشد و
 *  لوکیشنِ طرف ثبت بشود و بیاید به سرور اون لوکیشن اش.»
 *
 *  پس این کار به حساب بند نیست. سرور هم مسیرِ `/location` را بی‌توکن
 *  قبول می‌کند و ردیف را به شناسهٔ دستگاه می‌بندد؛ روزی که همان دستگاه
 *  حساب ساخت، ردیف‌های قبلی‌اش هم به آن حساب می‌چسبند.
 *
 *  ── چه چیزی جلوی زیاده‌روی را می‌گیرد ──────────────────────────────
 *  هر بار باز کردنِ برنامه یک ردیفِ تازه لازم ندارد. دکان جابه‌جا
 *  نمی‌شود، و ردیفِ تکراری فقط جای دیتابیس را می‌گیرد. پس در هر شش
 *  ساعت یک بار، و آن هم فقط وقتی نشانیِ سروری تنظیم شده باشد.
 */
object LocationPing {

  private const val PREFS = "tohid-location-ping"
  private const val KEY_SENT = "sentAt"
  private const val KEY_ASKED = "askedAt"

  private const val EVERY_MS = 6 * 60 * 60 * 1000L

  private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /**
   *  آیا وقتش هست که اجازهٔ لوکیشن پرسیده شود.
   *
   *  یک بار می‌پرسیم و بس. کاربری که «نه» گفته، با پرسشِ دوباره در هر
   *  بار باز کردنِ برنامه فقط عصبانی می‌شود — و اندروید هم بعد از دو بار
   *  ردّ، خودش دیگر پنجره را نشان نمی‌دهد.
   */
  fun shouldAsk(context: Context): Boolean {
    if (prefs(context).getLong(KEY_ASKED, 0) > 0) return false
    prefs(context).edit().putLong(KEY_ASKED, System.currentTimeMillis()).apply()
    return true
  }

  /**
   *  گرفتن و فرستادن.
   *
   *  هیچ خطایی به بیرون نمی‌رود: لوکیشن یک خبر است، نه یک شرط. نت نبود،
   *  اجازه نبود، سرور نبود — برنامه بی‌کم‌وکاست کار می‌کند.
   */
  suspend fun send(context: Context, force: Boolean = false) {
    runCatching {
      if (!AppConfig.isConfigured(context)) return
      if (!DeviceLocation.granted(context)) return
      val last = prefs(context).getLong(KEY_SENT, 0)
      if (!force && System.currentTimeMillis() - last < EVERY_MS) return

      val fix = DeviceLocation.current(context, force = force) ?: return
      val state = SyncStore(context)
      Backend.auth(context)
        .sendLocation(fix, state.deviceUid, android.os.Build.MODEL ?: "گوشی")
        .onSuccess {
          prefs(context).edit().putLong(KEY_SENT, System.currentTimeMillis()).apply()
        }
    }
  }
}
