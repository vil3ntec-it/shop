package ir.vil3ntec.tohid.hw

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import ir.vil3ntec.tohid.core.hw.LocalNetwork

/**
 *  HW-13 — اجازهٔ شبکهٔ محلیِ اندروید ۱۷ (`ACCESS_LOCAL_NETWORK`) روی همین دستگاه؛ فایلِ مرزیِ
 *  پلتفرم. قاعده در هسته (`core/…/hw/LocalNetwork.kt`)؛ کامپیوتر جایگزینِ خودش را دارد
 *  (`desktop/…/hw/LocalNetworkDesk.kt`: هرگز لازم نیست).
 *
 *  امروز هدفِ برنامه ۳۵ است ⇒ [needed] و [blocked] همیشه `false` و چاپگرِ شبکه همان امروز وصل
 *  می‌شود. با بالا رفتنِ هدف به ۳۷، همین‌جا بی تغییرِ دیگری فعال می‌شود (و `LocalNetworkManifestTest`
 *  اجازه را در مانیفست می‌خواهد).
 */
object LocalNetworkAccess {

  /** نامِ کاملِ اجازه در مانیفست و درخواستِ زمانِ اجرا (`LocalNetworkManifestTest`) */
  const val PERMISSION = "android.permission." + LocalNetwork.PERMISSION_NAME

  private fun target(context: Context): Int = context.applicationInfo.targetSdkVersion

  /** اجازهٔ زمانِ اجرا روی همین دستگاه لازم است؟ */
  fun needed(context: Context): Boolean = LocalNetwork.needsPermission(target(context), Build.VERSION.SDK_INT)

  /** لازم نیست، یا داده شده */
  fun granted(context: Context): Boolean =
    !needed(context) ||
      ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

  /** وصل شدن به [host] بی اجازه بسته است — صفِ چاپ همان دم می‌گوید، نه پس از مهلت */
  fun blocked(context: Context, host: String): Boolean =
    LocalNetwork.blocked(target(context), Build.VERSION.SDK_INT, granted(context), host)
}
