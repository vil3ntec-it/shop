package ir.vil3ntec.tohid.ios

import android.content.Intent
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 *  اعلانِ محلیِ آیفون (UNUserNotificationCenter) پشتِ همان `NotificationManagerCompat` ِ اندروید که کدِ مشترک
 *  صدا می‌زند: سفارشِ تازه (`OrderNotify`)، دیدبان (`Watchman`)، یادآورِ روزانه (`Reminders`).
 *
 *  - اجازه: اندرویدِ ۱۳+ آن را هنگامِ باز شدنِ `AppRoot` می‌پرسد؛ آیفون همان لحظه (`askOnce`، از ریشهٔ آیفون)، یک بار.
 *    `enabled` حالِ واقعیِ تنظیمِ iOS است (با هر بازگشت به برنامه تازه می‌شود)؛ «نه» ⇐ همان مسیرِ «اعلان خاموش» ِ اندروید.
 *  - شناسه: `tohid-<id>` — `notify` ِ دوباره با همان شناسه جایگزین می‌کند، `cancel` برمی‌دارد (مثلِ اندروید).
 *  - لمس: `Intent` ِ `setContentIntent` (action و extras) در userInfo می‌رود و با لمس دوباره ساخته می‌شود ⇐
 *    `IosLinks.openNotice` (همان `openNotice` ِ `MainActivity`). برنامهٔ باز هم بنر می‌بیند (مثلِ heads-up ِ اندروید).
 */
object IosNotifications {
  /** اجازهٔ اعلان (آخرین حالِ خوانده‌شده از iOS) */
  var enabled: Boolean = false
    private set

  private val center: UNUserNotificationCenter get() = UNUserNotificationCenter.currentNotificationCenter()
  private val delegate = Delegate()
  private const val ACTION = "tohid.action"

  /** پیش از پایانِ راه‌اندازیِ برنامه (‎MainViewController.kt‎: `appDidLaunch`) — تا لمسِ اعلانی که برنامه را باز کرد گم نشود */
  fun install() {
    center.delegate = delegate
    refresh()
  }

  fun refresh() {
    center.getNotificationSettingsWithCompletionHandler { s ->
      enabled = granted(s?.authorizationStatus)
    }
  }

  /** یک بار: اگر کاربر هنوز پاسخ نداده، پنجرهٔ اجازهٔ iOS */
  fun askOnce() = request {}

  /** `RequestPermission(POST_NOTIFICATIONS)`: پاسخ‌داده ⇐ همان پاسخ؛ وگرنه پنجرهٔ اجازهٔ iOS */
  fun request(done: (Boolean) -> Unit) {
    center.getNotificationSettingsWithCompletionHandler { s ->
      if (s?.authorizationStatus != UNAuthorizationStatusNotDetermined) {
        enabled = granted(s?.authorizationStatus)
        done(enabled)
        return@getNotificationSettingsWithCompletionHandler
      }
      center.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { ok, _ ->
        enabled = ok
        done(ok)
      }
    }
  }

  fun post(id: Int, title: String, text: String, intent: Intent?) {
    val content = UNMutableNotificationContent()
    content.setTitle(title)
    content.setBody(text)
    content.setSound(UNNotificationSound.defaultSound())
    if (intent != null) content.setUserInfo(userInfoOf(intent))
    val request = UNNotificationRequest.requestWithIdentifier(key(id), content, trigger = null)
    center.addNotificationRequest(request, withCompletionHandler = null)
  }

  fun cancel(id: Int) {
    center.removePendingNotificationRequestsWithIdentifiers(listOf(key(id)))
    center.removeDeliveredNotificationsWithIdentifiers(listOf(key(id)))
  }

  private fun key(id: Int) = "tohid-$id"

  /** فقط مقدارهای سادهٔ extras (رشته، عدد، درست/نادرست) — همان‌ها که `OrderNotify` می‌گذارد */
  internal fun userInfoOf(intent: Intent): Map<Any?, Any?> {
    val out = LinkedHashMap<Any?, Any?>()
    intent.action?.let { out[ACTION] = it }
    intent.extras.forEach { (k, v) -> if (v is String || v is Int || v is Long || v is Boolean) out[k] = v }
    return out
  }

  internal fun intentOf(info: Map<Any?, *>): Intent {
    val intent = Intent(info[ACTION] as? String)
    info.forEach { (k, v) ->
      val key = k as? String ?: return@forEach
      if (key == ACTION) return@forEach
      when (v) {
        is String -> intent.putExtra(key, v)
        is Boolean -> intent.putExtra(key, v)
        is Long -> intent.putExtra(key, v)
        is Int -> intent.putExtra(key, v)
        is Number -> intent.putExtra(key, v.toLong())
      }
    }
    return intent
  }

  private fun granted(status: Long?): Boolean =
    status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional || status == UNAuthorizationStatusEphemeral

  private class Delegate : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
      center: UNUserNotificationCenter,
      willPresentNotification: UNNotification,
      withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
      withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(
      center: UNUserNotificationCenter,
      didReceiveNotificationResponse: UNNotificationResponse,
      withCompletionHandler: () -> Unit,
    ) {
      val intent = intentOf(didReceiveNotificationResponse.notification.request.content.userInfo)
      dispatch_async(dispatch_get_main_queue()) { IosLinks.openNotice(intent) }
      withCompletionHandler()
    }
  }
}
