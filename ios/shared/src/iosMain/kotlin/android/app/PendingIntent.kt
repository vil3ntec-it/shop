package android.app

import android.content.Context
import android.content.Intent

/**
 *  shimِ iOS ِ `PendingIntent` — فقط همان `Intent` را نگه می‌دارد: لمسِ اعلانِ iOS آن را به ریشهٔ آیفون می‌دهد
 *  (‎ios/IosNotifications.kt‎). اجازهٔ USB (`getBroadcast`) در iOS نیست (‎UsbManager‎ همیشه null است).
 */
class PendingIntent private constructor(internal val intent: Intent?) {
  companion object {
    const val FLAG_UPDATE_CURRENT = 0x08000000
    const val FLAG_IMMUTABLE = 0x04000000
    const val FLAG_MUTABLE = 0x02000000
    const val FLAG_ONE_SHOT = 0x40000000

    fun getBroadcast(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent(null)
    fun getActivity(context: Context, requestCode: Int, intent: Intent, flags: Int): PendingIntent = PendingIntent(intent)
  }
}
