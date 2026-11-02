package android.app

import android.content.Intent
import ir.vil3ntec.tohid.ios.IosNotifications

/*
 *  shimِ iOS ِ اعلانِ اندروید — همان نام‌های کامپیوتر، روی اعلانِ محلیِ iOS (UNUserNotificationCenter،
 *  ‎ir/…/ios/IosNotifications.kt‎). کانال در iOS نیست (فقط نامش نگه داشته می‌شود)؛ `notify` با همان شناسه
 *  اعلانِ پیشین را جایگزین می‌کند (مثلِ اندروید) و لمسِ اعلان همان `Intent` ِ `setContentIntent` را به
 *  ریشهٔ آیفون می‌دهد (`OrderNotify.fromIntent` ⇐ همان سفارش).
 */
class Notification internal constructor(val title: String, val text: String, internal val intent: Intent? = null) {
  /** همان کلیدهای اندروید (شنوندهٔ اعلان می‌خواند؛ ‎android/service/notification‎) */
  val extras: android.os.Bundle = android.os.Bundle().apply {
    putCharSequence(EXTRA_TITLE, title)
    putCharSequence(EXTRA_TEXT, text)
  }

  companion object {
    const val EXTRA_TITLE = "android.title"
    const val EXTRA_TEXT = "android.text"
    const val EXTRA_BIG_TEXT = "android.bigText"
  }
}

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
