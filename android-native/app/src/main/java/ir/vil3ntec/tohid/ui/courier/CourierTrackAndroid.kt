package ir.vil3ntec.tohid.ui.courier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.i18n.StrCourierApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 *  DLV-16 (اندروید) — «سرویسِ پیش‌زمینه از نوعِ `location` فقط در حینِ مأموریت» (§۳.۱۱). همان الگوی سرویسِ اسکن
 *  (`scan/ScanServiceAndroid.kt`): اعلانِ همیشگیِ کم‌صدا که می‌گوید چه می‌کند و با لمس برنامه را باز می‌کند.
 *
 *  - کلیدِ جدا ندارد: صفحهٔ پیک (`CourierLiveTrack`) با نخستین مأموریتِ «در راه» که سرور برایش موقعیت خواست روشنش
 *    می‌کند؛ با «رسیدم»، پایان، پس‌گرفتن، «دیگر نه» ِ سرور یا برداشتنِ اجازه خودش بسته می‌شود (`CourierTrack.loop`).
 *  - پس از بسته شدنِ برنامه هم تا همان لحظه می‌ماند (کارِ سرویسِ پیش‌رو)، ولی پس از کشته شدنِ فرایند خودبه‌خود بالا
 *    نمی‌آید (`START_NOT_STICKY`) و با روشن شدنِ گوشی هم نه — پیک برنامه را باز می‌کند و صفحه دوباره روشنش می‌کند.
 *  - فقط موقعیتِ «هنگامِ استفاده»: سرویس از صفحهٔ بازِ برنامه شروع می‌شود؛ اجازهٔ مکانِ پس‌زمینه
 *    (`ACCESS_BACKGROUND_LOCATION`) خواسته نمی‌شود.
 *  کامپیوتر جایگزینِ خودش را دارد (‎`desktop/…/ui/courier/CourierTrackDesk.kt`‎: هیچ؛ GPS ندارد).
 */
class CourierTrackService : Service() {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private var job: Job? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    //  اندروید پنج ثانیه پس از `startForegroundService` اعلان می‌خواهد — پیش از هر سنجشی
    try {
      ServiceCompat.startForeground(this, NOTE_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
    } catch (e: Exception) {
      //  بی اجازهٔ موقعیت یا شروع از پس‌زمینه (اندروید ۱۲+/۱۴+) ⇒ بی سرویس؛ کارِ پیک بسته نمی‌شود
      stopSelf()
      return START_NOT_STICKY
    }
    if (!CourierTrack.wanted(this)) {
      finish()
      return START_NOT_STICKY
    }
    if (job?.isActive != true) {
      job = scope.launch {
        try { CourierTrack.loop(this@CourierTrackService) } catch (e: Exception) { /* حلقه شکست ⇒ بسته؛ صفحه دوباره روشن می‌کند */ }
        finish()
      }
    }
    return START_NOT_STICKY
  }

  private fun finish() {
    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun notification(): android.app.Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      getSystemService(NotificationManager::class.java)?.createNotificationChannel(
        NotificationChannel(CHANNEL, StrCourierApp.trackChannel, NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
      )
    }
    val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
      PendingIntent.getActivity(this, NOTE_ID, it.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }, PendingIntent.FLAG_IMMUTABLE)
    }
    return NotificationCompat.Builder(this, CHANNEL)
      .setSmallIcon(android.R.drawable.ic_menu_mylocation)
      .setContentTitle(StrCourierApp.trackTitle)
      .setContentText(StrCourierApp.trackLine)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .apply { open?.let { setContentIntent(it) } }
      .build()
  }

  companion object {
    const val CHANNEL = "courier_location"
    const val NOTE_ID = 4712
  }
}

/** روشن/خاموش کردنِ سرویس — فقط صفحهٔ پیک صدایش می‌زند (`CourierLiveTrack`) */
object CourierTrackPlatform {
  fun sync(context: Context, wanted: Boolean) {
    val app = context.applicationContext ?: context
    val intent = Intent(app, CourierTrackService::class.java)
    if (wanted) runCatching { ContextCompat.startForegroundService(app, intent) }
    else runCatching { app.stopService(intent) }
  }
}
