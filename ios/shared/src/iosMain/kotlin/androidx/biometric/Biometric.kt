package androidx.biometric

import android.content.Context
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorAppCancel
import platform.LocalAuthentication.LAErrorSystemCancel
import platform.LocalAuthentication.LAErrorUserCancel
import platform.LocalAuthentication.LAErrorUserFallback
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/*
 *  shim ِ iOS ِ `androidx.biometric` (گامِ ۶) — Face ID / Touch ID با LocalAuthentication، تا خودِ `data/Fingerprint.kt` ِ
 *  اندروید روی آیفون کامپایل و اجرا شود (جایگزینِ `FingerprintIos` حذف شد؛ رفتار همان):
 *  - «دلیل» ِ پنجرهٔ iOS = `title` ِ اندروید («باز کردن فروشگاه من»)؛ `subtitle` («انگشتتان را…») به iOS نمی‌رود چون روی
 *    Face ID نادرست است. دکمهٔ دوم = `negativeButtonText` («با رمز وارد می‌شوم»).
 *  - لغوِ کاربر/برنامه/سیستم ⇐ `ERROR_USER_CANCELED` و «با رمز» ⇐ `ERROR_NEGATIVE_BUTTON`: کدِ اندروید هر دو را بی پیام
 *    می‌گذارد. خطای دیگر ⇐ پیامِ خودِ iOS. پاسخ روی همان `Executor` ِ صدازننده (رشتهٔ اصلی).
 */
class BiometricManager private constructor() {
  object Authenticators {
    const val BIOMETRIC_STRONG = 0x000F
    const val BIOMETRIC_WEAK = 0x00FF
    const val DEVICE_CREDENTIAL = 0x8000
  }

  fun canAuthenticate(authenticators: Int): Int =
    if (LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, error = null)) BIOMETRIC_SUCCESS
    else BIOMETRIC_ERROR_NONE_ENROLLED

  companion object {
    const val BIOMETRIC_SUCCESS = 0
    const val BIOMETRIC_ERROR_HW_UNAVAILABLE = 1
    const val BIOMETRIC_ERROR_NONE_ENROLLED = 11
    const val BIOMETRIC_ERROR_NO_HARDWARE = 12

    fun from(context: Context): BiometricManager = BiometricManager()
  }
}

class BiometricPrompt(
  @Suppress("UNUSED_PARAMETER") activity: FragmentActivity,
  private val executor: Executor,
  private val callback: AuthenticationCallback,
) {
  abstract class AuthenticationCallback {
    open fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
    open fun onAuthenticationSucceeded(result: AuthenticationResult) {}
    open fun onAuthenticationFailed() {}
  }

  class AuthenticationResult internal constructor()

  class PromptInfo internal constructor(val title: String, val subtitle: String?, val negativeButtonText: String) {
    class Builder {
      private var title = ""
      private var subtitle: String? = null
      private var negative = ""
      fun setTitle(title: CharSequence) = apply { this.title = title.toString() }
      fun setSubtitle(subtitle: CharSequence?) = apply { this.subtitle = subtitle?.toString() }
      fun setNegativeButtonText(text: CharSequence) = apply { negative = text.toString() }
      fun setAllowedAuthenticators(authenticators: Int) = this
      fun build(): PromptInfo = PromptInfo(title, subtitle, negative)
    }
  }

  fun authenticate(info: PromptInfo) {
    val la = LAContext()
    la.localizedFallbackTitle = info.negativeButtonText
    la.evaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, localizedReason = info.title) { ok, error ->
      executor.execute {
        when {
          ok -> callback.onAuthenticationSucceeded(AuthenticationResult())
          error == null || error.code in CANCELLED -> callback.onAuthenticationError(ERROR_USER_CANCELED, "")
          error.code == LAErrorUserFallback -> callback.onAuthenticationError(ERROR_NEGATIVE_BUTTON, info.negativeButtonText)
          else -> callback.onAuthenticationError(ERROR_VENDOR, error.localizedDescription)
        }
      }
    }
  }

  companion object {
    const val ERROR_CANCELED = 5
    const val ERROR_LOCKOUT = 7
    const val ERROR_VENDOR = 8
    const val ERROR_USER_CANCELED = 10
    const val ERROR_NEGATIVE_BUTTON = 13

    private val CANCELLED = setOf(LAErrorUserCancel, LAErrorAppCancel, LAErrorSystemCancel)
  }
}
