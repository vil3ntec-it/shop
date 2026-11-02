package androidx.core.app

import android.app.Notification
import android.app.Service

/** shimِ iOS — سرویسِ پیش‌رو در iOS نیست (‎android/app/Service.kt‎)؛ هیچ اعلانِ همیشگی‌ای ساخته نمی‌شود */
object ServiceCompat {
  const val STOP_FOREGROUND_REMOVE = 1
  const val STOP_FOREGROUND_DETACH = 2

  fun startForeground(service: Service, id: Int, notification: Notification, foregroundServiceType: Int) {}

  fun stopForeground(service: Service, flags: Int) {}
}
