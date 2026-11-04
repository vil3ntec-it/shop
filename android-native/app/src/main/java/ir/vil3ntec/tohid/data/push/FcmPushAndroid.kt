package ir.vil3ntec.tohid.data.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.R
import ir.vil3ntec.tohid.core.i18n.StrPush
import ir.vil3ntec.tohid.core.push.PushPolicy
import ir.vil3ntec.tohid.data.OrderNotify
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/*
 *  پروندهٔ سکو — فقط اندروید (Firebase). کامپیوتر (`desktop/build.gradle.kts` ← `replaced`) و آیفون
 *  (`ios/shared/ios-exclude.txt`) آن را نمی‌سازند؛ کدِ مشترک فقط [DevicePush] و [PushPlatform] را می‌شناسد.
 */

/**
 *  Firebase Cloud Messaging (تصمیمِ O-01، `docs/FIREBASE-SETUP-fa.md`).
 *
 *  ⛔ بی `google-services.json` هنگامِ ساخت (`BuildConfig.FCM_BUNDLED = false`) هیچ کدِ Firebase صدا زده نمی‌شود —
 *  CI و ساختِ محلی بی کلید همان برنامهٔ امروزند. با کلید هم تا پرچمِ دکان روشن نشود توکنی ساخته نمی‌شود
 *  (`firebase_messaging_auto_init_enabled = false` در مانیفست؛ [DevicePush] پیش از [token] پرچم را می‌پرسد).
 */
object FcmPushAndroid : PushPlatform {
  override val provider: String = "fcm"
  override val platform: String = "android"

  /** پیش از هر کار: سرویسِ پیام و صفحه هر دو همین را صدا می‌زنند (بی‌خطر، چند بار) */
  fun install() {
    if (DevicePush.platform == null) DevicePush.platform = this
  }

  override fun bundled(context: Context): Boolean =
    BuildConfig.FCM_BUNDLED && runCatching {
      FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
    }.getOrDefault(false)

  override suspend fun token(context: Context): String? {
    if (!bundled(context)) return null
    val fm = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return null
    fm.isAutoInitEnabled = true
    ensureChannels(context)
    //  بی Google Play Services تکلیف هرگز نمی‌رسد ⇒ سقفِ زمان، نه انتظارِ بی‌پایان
    return withTimeoutOrNull(30_000L) { fm.token.awaitOrNull() }
  }

  override suspend fun stop(context: Context) {
    if (!bundled(context)) return
    val fm = runCatching { FirebaseMessaging.getInstance() }.getOrNull() ?: return
    fm.isAutoInitEnabled = false
    withTimeoutOrNull(15_000L) { fm.deleteToken().awaitOrNull() }
  }

  private suspend fun <T> Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { c ->
    addOnCompleteListener { t -> if (c.isActive) c.resume(if (t.isSuccessful) t.result else null) }
  }

  /** کانال‌های پوشِ سرور (`message.channel`) — باید پیش از رسیدنِ نخستین پیام با برنامهٔ بسته باشند */
  fun ensureChannels(context: Context) {
    OrderNotify.ensureChannel(context)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(NotificationManager::class.java) ?: return
    val high = NotificationManager.IMPORTANCE_HIGH
    for ((id, name, importance) in listOf(
      Triple("events", StrPush.channelEvents, high),
      Triple("support", StrPush.channelSupport, high),
      Triple("notice", StrPush.channelNotice, NotificationManager.IMPORTANCE_DEFAULT),
      Triple("alerts", StrPush.channelAlerts, high),
    )) nm.createNotificationChannel(NotificationChannel(id, name, importance))
  }

  /**
   *  پیامی که با برنامهٔ **باز** رسید (با برنامهٔ بسته خودِ اندروید همان `notification` را نشان می‌دهد و لمسش
   *  extras ِ دادهٔ پیام را به [OrderNotify.fromIntent] می‌دهد). همان `tag` ِ سرور ⇒ اعلانِ پیشینِ همان سفارش
   *  جایگزین می‌شود؛ متن همان متنِ سرور است که دادهٔ مشتری ندارد.
   */
  fun show(context: Context, message: RemoteMessage) {
    val n = message.notification ?: return
    val title = n.title?.takeIf { it.isNotBlank() } ?: return
    if (!OrderNotify.allowed(context)) return
    ensureChannels(context)
    val data = message.data
    val open = PushPolicy.openOf(data)
    open?.notifId?.let { OrderNotify.markShown(context, it) }
    val tag = n.tag ?: open?.let { OrderNotify.tagOf(it.orderId) } ?: message.messageId ?: title
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
      if (open != null) {
        open.notifId?.let { putExtra(OrderNotify.EXTRA_NOTIF, it) }
        putExtra(OrderNotify.EXTRA_ORDER, open.orderId)
      }
    }
    val pending = launch?.let {
      PendingIntent.getActivity(context, tag.hashCode(), it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
    val note = NotificationCompat.Builder(context, PushPolicy.channelOf(n.channelId))
      .setSmallIcon(R.drawable.ic_launcher_monochrome)
      .setContentTitle(title)
      .setContentText(n.body.orEmpty())
      .setStyle(NotificationCompat.BigTextStyle().bigText(n.body.orEmpty()))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setAutoCancel(true)
      .apply { pending?.let { setContentIntent(it) } }
      .build()
    runCatching { NotificationManagerCompat.from(context).notify(tag, 0, note) }
  }
}

/** درِ Firebase در مانیفست — توکنِ تازه ⇒ سرور؛ پیامِ برنامهٔ باز ⇒ اعلان */
class TohidMessagingService : FirebaseMessagingService() {
  override fun onNewToken(token: String) {
    FcmPushAndroid.install()
    DevicePush.onNewToken(applicationContext)
  }

  override fun onMessageReceived(message: RemoteMessage) {
    FcmPushAndroid.install()
    FcmPushAndroid.show(applicationContext, message)
  }
}
