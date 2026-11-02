package android.app

/*
 *  shimِ iOS ِ اعلانِ اندروید — همان نام‌های کامپیوتر. اعلانِ محلیِ iOS (UNUserNotificationCenter و پرسیدنِ
 *  اجازه) گامِ ۳ است؛ تا آن روز `areNotificationsEnabled` نه می‌گوید و `notify` هیچ نمی‌کند — کدِ مشترک
 *  همان مسیرِ «اعلان خاموش است» ِ اندروید را می‌رود و هشدارها در خودِ برنامه دیده می‌شوند.
 */
class Notification internal constructor(val title: String, val text: String)

class NotificationChannel(val id: String, val name: CharSequence, val importance: Int) {
  var description: String? = null
  fun enableVibration(on: Boolean) {}
  fun setShowBadge(on: Boolean) {}
}

class NotificationManager internal constructor() {
  private val channels = linkedMapOf<String, NotificationChannel>()
  fun createNotificationChannel(channel: NotificationChannel) { channels[channel.id] = channel }
  fun getNotificationChannel(id: String): NotificationChannel? = channels[id]
  fun areNotificationsEnabled(): Boolean = false
  fun notify(id: Int, notification: Notification) {}
  fun cancel(id: Int) {}

  companion object {
    const val IMPORTANCE_DEFAULT = 3
    const val IMPORTANCE_HIGH = 4
    const val IMPORTANCE_LOW = 2
    internal val ios = NotificationManager()
  }
}
