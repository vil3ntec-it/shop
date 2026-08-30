package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 *  باز کردنِ قفل با اثر انگشت.
 *
 *  رمز همیشه سرِ جایش می‌ماند و اثر انگشت فقط یک میان‌بُر است — نه
 *  جایگزین. دلیلش ساده است: حسگر خراب می‌شود، انگشت خیس است، گوشی عوض
 *  می‌شود. اگر تنها راه باشد، کاربر بیرونِ دفترِ خودش می‌ماند.
 *
 *  `BIOMETRIC_WEAK` هم پذیرفته می‌شود، نه فقط `STRONG`: روی خیلی از
 *  گوشی‌های ارزان، تشخیصِ چهره یا حسگرِ پشتِ گوشی «ضعیف» شمرده می‌شود و
 *  با `STRONG` تنها، آن دستگاه‌ها اصلاً دکمه را نمی‌دیدند.
 */
object Fingerprint {

  private const val KINDS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
      BiometricManager.Authenticators.BIOMETRIC_WEAK

  /** آیا این گوشی اثر انگشتِ ثبت‌شده دارد */
  fun available(context: Context): Boolean = runCatching {
    BiometricManager.from(context).canAuthenticate(KINDS) == BiometricManager.BIOMETRIC_SUCCESS
  }.getOrDefault(false)

  /**
   *  پرسیدن.
   *
   *  `onFail` فقط وقتی صدا زده می‌شود که کاربر خودش انصراف بدهد یا
   *  دستگاه نتواند — تشخیصِ نادرستِ یک انگشت، خودِ پنجره دوباره می‌پرسد.
   */
  fun ask(
    activity: FragmentActivity,
    onOk: () -> Unit,
    onFail: (String?) -> Unit = {},
  ) {
    val prompt = BiometricPrompt(
      activity,
      ContextCompat.getMainExecutor(activity),
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          onOk()
        }

        override fun onAuthenticationError(code: Int, message: CharSequence) {
          // «کاربر زد روی انصراف» خطا نیست؛ فقط برمی‌گردیم به رمز
          if (code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
            code == BiometricPrompt.ERROR_USER_CANCELED
          ) {
            onFail(null)
          } else {
            onFail(message.toString())
          }
        }
      },
    )

    val info = BiometricPrompt.PromptInfo.Builder()
      .setTitle("باز کردن توحید")
      .setSubtitle("انگشتتان را روی حسگر بگذارید")
      .setNegativeButtonText("با رمز وارد می‌شوم")
      .setAllowedAuthenticators(KINDS)
      .build()

    runCatching { prompt.authenticate(info) }
      .onFailure { onFail(it.message) }
  }
}
