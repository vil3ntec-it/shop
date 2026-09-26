package ir.vil3ntec.tohid.data

import android.content.Context
import androidx.fragment.app.FragmentActivity

/**
 *  اثرِ انگشت روی کامپیوتر در دسترسِ JVM نیست (Windows Hello و Touch ID
 *  راهِ جاوایی ندارند). `available` همیشه `false` است و قفلِ برنامه با
 *  همان رمزِ چهاررقمی باز می‌شود — دقیقاً همان رفتارِ گوشیِ بی‌حسگر.
 */
object Fingerprint {
  fun available(context: Context): Boolean = false

  fun ask(activity: FragmentActivity, onOk: () -> Unit, onFail: (String?) -> Unit = {}) {
    onFail(null)
  }
}
