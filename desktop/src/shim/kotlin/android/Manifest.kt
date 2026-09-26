package android

/** نام‌های اجازه — روی کامپیوتر همه از پیش داده شده‌اند. */
object Manifest {
  object permission {
    const val CAMERA = "android.permission.CAMERA"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val ACCESS_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION"
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val RECORD_AUDIO = "android.permission.RECORD_AUDIO"
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
  }
}

/** `android.R` — فقط آن‌چه برنامه صدا می‌زند. */
object R {
  object drawable { const val ic_dialog_info = 0 }
}
