package ir.vil3ntec.tohid.sync

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.data.DeviceLocation
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  رساندنِ لوکیشن به سرور — فقط با رضایتِ صریح.
 *
 *  ── قرارِ تازه (F0-07، تا تصمیمِ D-04) ──────────────────────────────
 *  پیش از این، اگر اجازهٔ اندروید بود، هر شش ساعت موقعیت می‌رفت، حتی
 *  بی حساب؛ در حالی که `privacy.html` می‌گفت «فقط یک بار و اختیاری
 *  هنگام ثبت‌نام». اجازهٔ اندروید (که صفحهٔ ثبت‌نام هم می‌خواهد) رضایت
 *  به فرستادنِ دوره‌ای نیست.
 *
 *  پس حالا پیش‌فرض «بی رضایت» است: نه چیزی فرستاده می‌شود و نه اجازهٔ
 *  اندروید سرِ باز شدنِ برنامه پرسیده می‌شود. مسیرِ فرستادن برای کسی که
 *  صریحاً `setConsent(true)` داده دست‌نخورده می‌ماند — همان قاعدهٔ شش
 *  ساعت و همان مسیرِ بی‌توکنِ `/location` سرور. لوکیشنِ یک‌بارهٔ صفحهٔ
 *  ثبت‌نام (`Welcome`) جدا است و این‌جا عوض نشده.
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
  /** زمانِ رضایتِ صریح؛ صفر یعنی رضایتی نیست */
  private const val KEY_CONSENT = "consentAt"

  const val EVERY_MS = 6 * 60 * 60 * 1000L

  /** تا تصمیمِ D-04: کسی که چیزی نگفته، رضایت نداده است */
  const val DEFAULT_CONSENT = false

  private fun prefs(context: Context) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** آیا کاربر صریحاً به فرستادنِ موقعیت رضایت داده است */
  fun consented(context: Context): Boolean =
    prefs(context).getLong(KEY_CONSENT, 0) > 0 || DEFAULT_CONSENT

  /** ثبت یا پس گرفتنِ رضایتِ صریح — جای صدا زدنش با تصمیمِ D-04 روشن می‌شود */
  fun setConsent(context: Context, yes: Boolean) {
    val edit = prefs(context).edit()
    if (yes) edit.putLong(KEY_CONSENT, System.currentTimeMillis()) else edit.remove(KEY_CONSENT)
    edit.apply()
  }

  /** دروازهٔ پرسیدنِ اجازهٔ اندروید؛ جدا تا بی‌دستگاه آزموده شود */
  fun mayAsk(consented: Boolean, askedBefore: Boolean): Boolean =
    consented && !askedBefore

  /** دروازهٔ فرستادن؛ جدا تا بی‌دستگاه آزموده شود */
  fun maySend(
    consented: Boolean,
    configured: Boolean,
    granted: Boolean,
    lastSentAt: Long,
    now: Long,
    force: Boolean,
  ): Boolean =
    consented && configured && granted && (force || now - lastSentAt >= EVERY_MS)

  /**
   *  آیا وقتش هست که اجازهٔ لوکیشن پرسیده شود.
   *
   *  یک بار می‌پرسیم و بس. کاربری که «نه» گفته، با پرسشِ دوباره در هر
   *  بار باز کردنِ برنامه فقط عصبانی می‌شود — و اندروید هم بعد از دو بار
   *  ردّ، خودش دیگر پنجره را نشان نمی‌دهد.
   */
  fun shouldAsk(context: Context): Boolean {
    //  بی رضایت پرسیده نمی‌شود — و «یک بار» هم مصرف نمی‌شود
    if (!mayAsk(consented(context), prefs(context).getLong(KEY_ASKED, 0) > 0)) return false
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
      val allowed = maySend(
        consented = consented(context),
        configured = AppConfig.isConfigured(context),
        granted = DeviceLocation.granted(context),
        lastSentAt = prefs(context).getLong(KEY_SENT, 0),
        now = System.currentTimeMillis(),
        force = force,
      )
      if (!allowed) return

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
