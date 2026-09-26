package android.app

import android.content.Context
import android.content.Intent

open class Activity {
  val window: android.view.Window? get() = null
  companion object { const val RESULT_OK = -1; const val RESULT_CANCELED = 0 }
}

class ActivityManager { val isLowRamDevice: Boolean = false }

class PendingIntent private constructor(val intent: Intent?) {
  companion object {
    const val FLAG_IMMUTABLE = 0x04000000
    const val FLAG_MUTABLE = 0x02000000
    const val FLAG_UPDATE_CURRENT = 0x08000000
    fun getActivity(context: Context, code: Int, intent: Intent?, flags: Int) = PendingIntent(intent)
    fun getBroadcast(context: Context, code: Int, intent: Intent?, flags: Int) = PendingIntent(intent)
  }
}

class NotificationChannel(val id: String, val name: CharSequence, val importance: Int) {
  var description: String? = null
  fun enableVibration(on: Boolean) {}
  fun setShowBadge(on: Boolean) {}
}

/**
 *  اعلان‌ها روی کامپیوتر به سینیِ سیستم می‌روند (`DesktopNotify`)؛ این
 *  فقط کانال‌ها را نگه می‌دارد تا کدِ اندروید دست‌نخورده بماند.
 */
class NotificationManager {
  private val channels = linkedMapOf<String, NotificationChannel>()
  fun createNotificationChannel(channel: NotificationChannel) { channels[channel.id] = channel }
  fun getNotificationChannel(id: String): NotificationChannel? = channels[id]
  fun areNotificationsEnabled(): Boolean = true
  fun notify(id: Int, notification: Notification) = DesktopNotify.show(notification.title, notification.text)
  fun cancel(id: Int) {}

  companion object {
    const val IMPORTANCE_DEFAULT = 3
    const val IMPORTANCE_HIGH = 4
    const val IMPORTANCE_LOW = 2
  }
}

class Notification(val title: String, val text: String)

/** جایی که اعلان واقعاً نشان داده می‌شود — `Main.kt` پرش می‌کند. */
object DesktopNotify {
  @Volatile var sink: ((String, String) -> Unit)? = null
  fun show(title: String, text: String) { runCatching { sink?.invoke(title, text) } }
}
