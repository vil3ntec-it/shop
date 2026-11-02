package android.app

import android.content.Intent
import ir.vil3ntec.tohid.ios.IosNotifications

/*
 *  shimِ iOS ِ اعلانِ اندروید — همان نام‌های کامپیوتر، روی اعلانِ محلیِ iOS (UNUserNotificationCenter،
 *  ‎ir/…/ios/IosNotifications.kt‎). کانال در iOS نیست (فقط نامش نگه داشته می‌شود)؛ `notify` با همان شناسه
 *  اعلانِ پیشین را جایگزین می‌کند (مثلِ اندروید) و لمسِ اعلان همان `Intent` ِ `setContentIntent` را به
 *  ریشهٔ آیفون می‌دهد (`OrderNotify.fromIntent` ⇐ همان سفارش).
 */
class Notification internal constructor(val title: String, val text: String, internal val intent: Intent? = null)

class NotificationChannel(val id: String, val name: CharSequence, val importance: Int) {
  var description: String? = null
  fun enableVibration(on: Boolean) {}
  fun setShowBadge(on: Boolean) {}
}

class NotificationManager internal constructor() {
  private val channels = linkedMapOf<String, NotificationChannel>()
  fun createNotificationChannel(channel: NotificationChannel) { channels[channel.id] = channel }
  fun getNotificationChannel(id: String): NotificationChannel? = channels[id]
  fun areNotificationsEnabled(): Boolean = IosNotifications.enabled
  fun notify(id: Int, notification: Notification) = IosNotifications.post(id, notification.title, notification.text, notification.intent)
  fun cancel(id: Int) = IosNotifications.cancel(id)

  companion object {
    const val IMPORTANCE_DEFAULT = 3
    const val IMPORTANCE_HIGH = 4
    const val IMPORTANCE_LOW = 2
    internal val ios = NotificationManager()
  }
}
