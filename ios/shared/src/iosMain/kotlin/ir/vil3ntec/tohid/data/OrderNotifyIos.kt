package ir.vil3ntec.tohid.data

import android.content.Context
import android.content.Intent

/*
 *  جایگزینِ iOS ِ `data/OrderNotify.kt` (اعلانِ «سفارشِ تازه»). اعلانِ محلیِ iOS (UNUserNotificationCenter و
 *  اجازه‌اش) گامِ ۳ است؛ تا آن روز هیچ اعلانی ساخته نمی‌شود و فهرستِ سفارش‌ها همان‌جا در صفحه است.
 */
object OrderNotify {
  fun fromIntent(context: Context, intent: Intent?, now: Long = ir.vil3ntec.tohid.ios.jvm.System.currentTimeMillis()): String? = null

  suspend fun tick(context: Context) {}

  suspend fun flush(context: Context) {}
}
