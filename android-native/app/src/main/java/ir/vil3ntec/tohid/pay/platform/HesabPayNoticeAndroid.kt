package ir.vil3ntec.tohid.pay.platform

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ir.vil3ntec.tohid.pay.HesabPayHints

/**
 *  اعلانِ برنامهٔ حساب‌پی ⇐ پیشنهادِ «پول رسید» در صندوق (حالتِ سادهٔ «حساب‌پیِ دکان»؛ فقط اندروید).
 *
 *  - فقط با اجازهٔ «دسترسی به اعلان» که صاحب خودش در تنظیماتِ اندروید می‌دهد ([HesabPayNotice.openAccess]) و
 *    کلیدِ روشنِ همین دستگاه ([HesabPayNotice.hintOn])؛ وگرنه هیچ اعلانی خوانده نمی‌شود.
 *  - فقط اعلانِ بستهٔ برنامهٔ حساب‌پی ([HesabPayNotice.PACKAGES])؛ متن فقط در حافظه و فقط مبلغش به کار می‌رود.
 *  - ⛔ فقط پیشنهاد؛ هرگز فاکتور را نمی‌بندد. ⛔ پیامک نه (READ_SMS نه — سیاستِ Play).
 *  کامپیوتر: `desktop/…/pay/platform/HesabPayNoticeDesk.kt` (ندارد).
 */
class HesabPayNoticeService : NotificationListenerService() {
  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    val n = sbn ?: return
    if (n.packageName !in HesabPayNotice.PACKAGES) return
    if (!HesabPayNotice.hintOn(applicationContext)) return
    val extras = n.notification?.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
    val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
    HesabPayHints.offer("$title $body", System.currentTimeMillis())
  }
}

object HesabPayNotice {
  const val SUPPORTED = true
  private const val PREFS = "tohid-hesabpay"
  private const val KEY = "notice_hint"

  /** پیشنهاد از اعلان روشن است؟ تنظیمِ همین دستگاه، پیش‌فرض خاموش (دستهٔ B) */
  fun hintOn(context: Context): Boolean =
    runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false) }.getOrDefault(false)

  fun setHintOn(context: Context, on: Boolean) {
    runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply() }
    if (!on) HesabPayHints.consume()
  }

  /** TODO(hesabpay-sandbox): نامِ بستهٔ برنامهٔ حساب‌پی روی Play تأیید نشده */
  val PACKAGES: Set<String> = setOf("com.hesab.hesabpay", "com.hesabpay.app", "af.hesab.pay")

  /** اجازهٔ «دسترسی به اعلان» برای همین برنامه داده شده؟ */
  fun granted(context: Context): Boolean = runCatching {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val me = ComponentName(context, HesabPayNoticeService::class.java)
    flat.split(':').any { ComponentName.unflattenFromString(it) == me }
  }.getOrDefault(false)

  /** صفحهٔ «دسترسی به اعلان» ِ اندروید — صاحب خودش روشن می‌کند */
  fun openAccess(context: Context) {
    runCatching {
      context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
  }
}
