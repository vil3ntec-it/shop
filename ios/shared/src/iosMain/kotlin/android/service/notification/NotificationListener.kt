package android.service.notification

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder

/*
 *  shimِ iOS ِ «شنوندهٔ اعلان» ِ اندروید (پیشنهادِ «پول رسید» از اعلانِ برنامهٔ حساب‌پی، دستهٔ یازدهم). iOS به هیچ
 *  برنامه‌ای اعلانِ برنامهٔ دیگر را نمی‌دهد: اجازه‌اش هرگز داده نمی‌شود (`Settings.Secure` ⇐ خالی) و
 *  `onNotificationPosted` صدا زده نمی‌شود — همان «اجازه نیست» ِ اندروید.
 */
abstract class NotificationListenerService : Service() {
  override fun onBind(intent: Intent?): IBinder? = null
  open fun onNotificationPosted(sbn: StatusBarNotification?) {}
  open fun onNotificationRemoved(sbn: StatusBarNotification?) {}
  open fun onListenerConnected() {}
}

class StatusBarNotification internal constructor(val packageName: String, val notification: Notification?, val postTime: Long)
