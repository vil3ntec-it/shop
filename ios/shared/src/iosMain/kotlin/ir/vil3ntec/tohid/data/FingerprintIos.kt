package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.fragment.app.FragmentActivity
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/*
 *  جایگزینِ iOS ِ `data/Fingerprint.kt` (BiometricPrompt ِ اندروید؛ کامپیوتر: ‎FingerprintDesk‎) — همان API،
 *  روی LocalAuthentication: Face ID یا Touch ID، هر کدام که گوشی دارد و ثبت شده.
 *
 *  همان قراردادِ اندروید: فقط زیست‌سنجی (نه رمزِ گوشی — رمزِ برنامه همان صفحه‌کلیدِ قفل است)؛ لغو یا
 *  «با رمز وارد می‌شوم» ⇐ `onFail(null)`؛ نشدنِ دستگاه ⇐ `onFail(پیام)`؛ چهرهٔ نادرست را خودِ iOS دوباره می‌پرسد.
 *  `activity` همان `IosActivity` است (‎ios/IosActivity.kt‎) که `LocalContext` ِ ریشهٔ آیفون است، پس
 *  `LockGate` و `AppLock` ِ مشترک دست‌نخورده دکمه را نشان می‌دهند و خودشان یک بار می‌پرسند.
 *  کلیدِ `NSFaceIDUsageDescription` در ‎ios/iosApp/iosApp/Info.plist‎ است (بی آن iOS برنامه را می‌بندد).
 */
object Fingerprint {
  /** آیا این گوشی Face ID / Touch ID ِ ثبت‌شده دارد */
  fun available(context: Context): Boolean =
    runCatching { LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null) }.getOrDefault(false)

  fun ask(activity: FragmentActivity, onOk: () -> Unit, onFail: (String?) -> Unit = {}) {
    val la = LAContext()
    la.localizedFallbackTitle = "با رمز وارد می‌شوم"
    runCatching {
      la.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, localizedReason = "باز کردن فروشگاه من") { ok, error ->
        //  پاسخِ LocalAuthentication روی صفِ دیگری است؛ صفحه فقط روی رشتهٔ اصلی
        dispatch_async(dispatch_get_main_queue()) {
          when {
            ok -> onOk()
            error == null || error.code in CANCELLED -> onFail(null)
            else -> onFail(error.localizedDescription)
          }
        }
      }
    }.onFailure { onFail(it.message) }
  }

  private val CANCELLED = setOf(LAErrorUserCancel, LAErrorAppCancel, LAErrorSystemCancel, LAErrorUserFallback)
}
