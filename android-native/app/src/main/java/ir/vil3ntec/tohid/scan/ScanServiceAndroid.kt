package ir.vil3ntec.tohid.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.hw.DeviceRoster
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.hw.DeviceLinks
import ir.vil3ntec.tohid.hw.DeviceSignals

/**
 *  اسکن وقتی برنامه بسته است (اندروید) — «سرویسِ پیش‌رو» (foreground service، نوعِ `connectedDevice`) که بارکدخوان‌های
 *  SPP ِ «دستگاه‌ها» را وصل نگه می‌دارد (`DeviceLinks`) و هر اسکن را در صفِ پایدار می‌گذارد (`ScanInbox`).
 *
 *  - کلیدِ جدا ندارد (تنظیماتِ کم): با نخستین بارکدخوانِ «وصلِ خودکار» خودش روشن و با برداشتنِ آخرینش خاموش می‌شود.
 *  - اندروید برای چنین کاری اعلانِ همیشگی می‌خواهد؛ همان اعلان یک خطِ ساده می‌گوید چه می‌کند (`StrDevices.noteLine`)
 *    و با لمس برنامه را باز می‌کند. کانال کم‌صدا است (بی صدا و بی نشان).
 *  - پس از روشن شدنِ گوشی یا به‌روزرسانیِ برنامه دوباره بالا می‌آید (`ScanBootReceiver`).
 *  - بارکدخوانِ حالتِ صفحه‌کلید (HID) را هیچ برنامه‌ای در پس‌زمینه نمی‌گیرد؛ «دستگاه‌ها» راهنمای حالتِ SPP را نشان می‌دهد.
 *  کامپیوتر جایگزینِ خودش را دارد (‎`desktop/…/scan/ScanServiceDesk.kt`‎: همان کارگرها در سینی).
 */
class ScanCaptureService : Service() {

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    //  اندروید پنج ثانیه پس از `startForegroundService` اعلان می‌خواهد — پیش از هر سنجشی
    try {
      ServiceCompat.startForeground(this, NOTE_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
    } catch (e: Exception) {
      stopSelf()
      return START_NOT_STICKY
    }
    if (!ScanBackground.wanted(this)) {
      ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
      stopSelf()
      return START_NOT_STICKY
    }
    DeviceLinks.syncWorkers(this)
    return START_STICKY
  }

  override fun onDestroy() {
    DeviceLinks.stopWorkers()
    super.onDestroy()
  }

  private fun notification(): android.app.Notification {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      getSystemService(NotificationManager::class.java)?.createNotificationChannel(
        NotificationChannel(CHANNEL, StrDevices.noteChannel, NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) },
      )
    }
    val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
      PendingIntent.getActivity(this, NOTE_ID, it.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }, PendingIntent.FLAG_IMMUTABLE)
    }
    return NotificationCompat.Builder(this, CHANNEL)
      .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
      .setContentTitle(StrDevices.noteTitle)
      .setContentText(StrDevices.noteLine)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .apply { open?.let { setContentIntent(it) } }
      .build()
  }

  companion object {
    const val CHANNEL = "scan_background"
    const val NOTE_ID = 4711
  }
}

/** روشن/خاموش کردنِ سرویس بر پایهٔ فهرستِ «دستگاه‌ها» — برنامه، صفحهٔ «دستگاه‌ها» و روشن شدنِ گوشی صدایش می‌زنند */
object ScanBackground {
  /** دست‌کم یک بارکدخوانِ «وصلِ خودکار» و اجازهٔ «دستگاه‌های نزدیک» */
  fun wanted(context: Context): Boolean =
    DeviceRoster.backgroundScanners(DeviceLinks.load(context)).isNotEmpty() && DeviceSignals.granted(context)

  fun sync(context: Context) {
    val app = context.applicationContext ?: context
    val intent = Intent(app, ScanCaptureService::class.java)
    if (wanted(app)) runCatching { ContextCompat.startForegroundService(app, intent) }
    else runCatching { app.stopService(intent) }
  }
}

/** پس از روشن شدنِ گوشی یا به‌روزرسانیِ برنامه: اگر بارکدخوانی ثبت است، سرویس دوباره بالا می‌آید */
class ScanBootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) ScanBackground.sync(context)
  }
}
