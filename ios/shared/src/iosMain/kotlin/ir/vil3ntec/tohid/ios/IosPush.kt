package ir.vil3ntec.tohid.ios

import android.content.IosContext
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.core.net.TokenStore
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSData
import platform.UIKit.UIApplication
import platform.UIKit.registerForRemoteNotifications

/*
 *  اعلانِ سرور (APNs) — سمتِ برنامه، گامِ ۴.
 *
 *  ۱) `appDidLaunch` ⇐ `registerForRemoteNotifications` (بی پرسش؛ اجازهٔ نمایش همان اجازهٔ اعلانِ محلیِ گامِ ۳ است).
 *  ۲) نمایندهٔ برنامهٔ SwiftUI (‎ios/iosApp/iosApp/iOSApp.swift‎) توکنِ دستگاه را به `apnsToken` می‌دهد.
 *  ۳) توکن (هگزِ کوچک) به همان درِ ثبتِ توکنِ سرور می‌رود: `POST /api/me/push` با `provider = "apns"`، `platform = "ios"`
 *     — پشتِ همان ورود و همان توکنِ دسترسی (`Backend.api`). فقط وقتی واردیم، و برای هر «توکن + حساب» یک بار در روز.
 *  سرور توکنِ apns را نگه می‌دارد و از درِ FCM نمی‌فرستد (`server/src/lib/push.js`). فرستادنِ واقعی به APNs کلیدِ p8
 *  و Team ID ِ صاحب‌کار را می‌خواهد (docs/IOS-PLAN-fa.md §۰.۶)؛ تا آن روز اعلانِ سفارش همان دیدبانِ محلی است.
 *  ⛔ بی قابلیتِ «Push Notifications» در امضای برنامه (حسابِ اپل) iOS توکن نمی‌دهد ⇐ `apnsFailed`، فقط ثبتِ دلیل.
 */
internal object IosPush {
  private const val PREFS = "tohid-push"
  private const val DAY_MS = 24 * 60 * 60_000L
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val prefs get() = IosContext.getSharedPreferences(PREFS, 0)

  fun register() = IosUi.onMain { UIApplication.sharedApplication.registerForRemoteNotifications() }

  fun token(hex: String) {
    prefs.edit().putString("apns", hex).remove("apns_error").apply()
    upload()
  }

  fun failed(message: String) {
    prefs.edit().putString("apns_error", message.take(200)).apply()
  }

  /** پس از ورود و هر بازگشت به برنامه (‎IosLifecycle‎) */
  fun upload() {
    val hex = prefs.getString("apns", null) ?: return
    if (!TokenStore(IosContext).signedIn) return
    val key = hex + "|" + LedgerOwner.currentUser(IosContext)
    val now = ir.vil3ntec.tohid.ios.jvm.System.currentTimeMillis()
    if (prefs.getString("sent", null) == key && now - prefs.getLong("sentAt", 0L) in 0 until DAY_MS) return
    scope.launch {
      runCatching {
        Backend.api(IosContext).post(ApiEndpoints.Push.ROOT, buildJsonObject {
          put("token", hex)
          put("provider", "apns")
          put("platform", "ios")
        })
      }.onSuccess { prefs.edit().putString("sent", key).putLong("sentAt", now).apply() }
    }
  }

  /** بایت‌های توکن ⇐ هگزِ کوچک (همان شکلی که APNs در نشانیِ `/3/device/<token>` می‌خواهد) */
  fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

/** `application(_:didRegisterForRemoteNotificationsWithDeviceToken:)` ِ SwiftUI */
fun apnsToken(token: NSData) {
  val len = token.length.toInt()
  val bytes = if (len == 0) ByteArray(0) else token.bytes!!.reinterpret<ByteVar>().readBytes(len)
  IosPush.token(IosPush.hex(bytes))
}

/** `application(_:didFailToRegisterForRemoteNotificationsWithError:)` ِ SwiftUI */
fun apnsFailed(message: String) = IosPush.failed(message)
