package ir.vil3ntec.tohid.ios

import android.Manifest
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType

/*
 *  اجازه‌های اندروید ⇐ اجازه‌های iOS، پشتِ همان `ContextCompat.checkSelfPermission` و قراردادهای
 *  `RequestPermission`/`RequestMultiplePermissions` که کدِ مشترک صدا می‌زند:
 *    دوربین ⇐ AVCaptureDevice؛ موقعیت (تقریبی/دقیق) ⇐ CoreLocation «هنگامِ استفاده» (‎IosLocation‎)؛
 *    اعلان ⇐ UNUserNotificationCenter (‎IosNotifications‎)؛ گالری ⇐ همیشه بله (PHPicker اجازه نمی‌خواهد).
 *  بقیه (میکروفون/گفتار، بلوتوث، وای‌فای ِ نزدیک) در آیفونِ این برنامه نیستند ⇐ «نه»، همان مسیرِ «اجازه نیست» ِ اندروید.
 *  پاسخ همیشه روی رشتهٔ اصلی است (مثلِ launcher ِ اندروید).
 */
internal object IosPermissions {
  private enum class Group { CAMERA, LOCATION, NOTIFY, MEDIA, NONE }

  private fun groupOf(permission: String): Group = when (permission) {
    Manifest.permission.CAMERA -> Group.CAMERA
    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION -> Group.LOCATION
    Manifest.permission.POST_NOTIFICATIONS -> Group.NOTIFY
    Manifest.permission.READ_MEDIA_IMAGES -> Group.MEDIA
    else -> Group.NONE
  }

  fun granted(permission: String): Boolean = when (groupOf(permission)) {
    Group.CAMERA -> AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) == AVAuthorizationStatusAuthorized
    Group.LOCATION -> IosLocation.granted()
    Group.NOTIFY -> IosNotifications.enabled
    Group.MEDIA -> true
    Group.NONE -> false
  }

  fun request(permission: String, done: (Boolean) -> Unit) {
    val reply: (Boolean) -> Unit = { ok -> IosUi.onMain { done(ok) } }
    when (groupOf(permission)) {
      Group.CAMERA -> AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { ok -> reply(ok) }
      Group.LOCATION -> IosLocation.request(reply)
      Group.NOTIFY -> IosNotifications.request(reply)
      Group.MEDIA -> reply(true)
      Group.NONE -> reply(false)
    }
  }

  /** چند اجازه: هر گروهِ iOS یک بار پرسیده می‌شود (تقریبی و دقیق ⇐ یک پنجرهٔ موقعیت) */
  fun requestAll(permissions: Array<String>, done: (Map<String, Boolean>) -> Unit) {
    val groups = permissions.map(::groupOf).distinct()
    val answers = HashMap<Group, Boolean>()
    fun next(i: Int) {
      if (i == groups.size) {
        done(permissions.associateWith { answers[groupOf(it)] == true })
        return
      }
      val g = groups[i]
      val sample = permissions.first { groupOf(it) == g }
      request(sample) { ok ->
        answers[g] = ok
        next(i + 1)
      }
    }
    next(0)
  }
}
