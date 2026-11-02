package androidx.core.app

import android.app.Notification
import android.app.PendingIntent
import android.content.Context

/** shimِ iOS ِ `NotificationCompat`/`NotificationManagerCompat` — نگاه کنید به ‎android/app/Notification.kt‎ (گامِ ۳) */
class NotificationCompat {
  class Builder(context: Context, channel: String) {
    private var title = ""
    private var text = ""
    fun setSmallIcon(id: Int) = this
    fun setContentTitle(t: CharSequence?) = apply { title = t?.toString().orEmpty() }
    fun setContentText(t: CharSequence?) = apply { text = t?.toString().orEmpty() }
    fun setStyle(s: Style?) = apply { (s as? BigTextStyle)?.big?.let { text = it } }
    fun setAutoCancel(b: Boolean) = this
    fun setPriority(p: Int) = this
    fun setCategory(c: String?) = this
    fun setOnlyAlertOnce(b: Boolean) = this
    fun setContentIntent(p: PendingIntent?) = this
    fun setColor(c: Int) = this
    fun setWhen(w: Long) = this
    fun setShowWhen(b: Boolean) = this
    fun setGroup(g: String?) = this
    fun build() = Notification(title, text)
  }

  abstract class Style

  class BigTextStyle : Style() {
    internal var big: String? = null
    fun bigText(t: CharSequence?) = apply { big = t?.toString() }
  }

  companion object {
    const val PRIORITY_DEFAULT = 0
    const val PRIORITY_HIGH = 1
    const val PRIORITY_LOW = -1
    const val CATEGORY_REMINDER = "reminder"
    const val CATEGORY_STATUS = "status"
    const val CATEGORY_MESSAGE = "msg"
  }
}

class NotificationManagerCompat private constructor() {
  fun notify(id: Int, n: Notification) {}
  fun areNotificationsEnabled() = false
  fun cancel(id: Int) {}

  companion object {
    fun from(context: Context) = NotificationManagerCompat()
  }
}
