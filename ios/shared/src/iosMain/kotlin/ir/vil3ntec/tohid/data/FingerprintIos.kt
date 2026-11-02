package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.fragment.app.FragmentActivity

/*
 *  جایگزینِ iOS ِ `data/Fingerprint.kt` (BiometricPrompt ِ اندروید؛ کامپیوتر: ‎FingerprintDesk‎ همین را دارد).
 *  Face ID / Touch ID (LocalAuthentication) گامِ ۳ است؛ تا آن روز «نیست» و قفل فقط با رمزِ برنامه باز می‌شود.
 */
object Fingerprint {
  fun available(context: Context): Boolean = false

  fun ask(activity: FragmentActivity, onOk: () -> Unit, onFail: (String?) -> Unit = {}) {
    onFail(null)
  }
}
