package ir.vil3ntec.tohid.sync

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import ir.vil3ntec.tohid.BuildConfig
import java.security.MessageDigest

/**
 *  آیا این همان برنامه‌ای است که ما ساختیم؟
 *
 *  قفلِ اشتراک تا وقتی معنی دارد که کسی نتواند فایلِ برنامه را باز کند،
 *  خودِ قفل را بردارد و دوباره ببندد. جلوی این کار را نمی‌شود کاملاً
 *  گرفت — هر برنامه‌ای که روی گوشیِ کاربر اجرا می‌شود، در نهایت دستِ
 *  اوست — ولی می‌شود گران و بی‌فایده‌اش کرد:
 *
 *    • اندروید هر فایلِ نصبی را با کلیدِ سازنده‌اش می‌شناسد. کسی که فایل
 *      را عوض کند، مجبور است با کلیدِ **خودش** امضا کند، چون کلیدِ ما را
 *      ندارد. پس امضا عوض می‌شود.
 *    • اثرِ انگشتِ کلیدِ درست هنگامِ ساخت داخلِ برنامه نوشته شده. برنامه
 *      هنگامِ اجرا امضای خودش را با همان می‌سنجد.
 *
 *  نسخهٔ دست‌کاری‌شده اجرا می‌شود — جلویش را نمی‌گیریم، چون بستنِ برنامه
 *  روی کاربرِ بی‌گناهی که گوشی‌اش عجیب است، بدتر از خودِ مسئله است — ولی
 *  اشتراک به آن داده نمی‌شود.
 *
 *  و مهم‌تر از همهٔ این‌ها: تصمیمِ نهایی مالِ سرور است. مجوز را سرور امضا
 *  می‌کند و بدونِ کلیدِ خصوصیِ سرور، هیچ برنامه‌ای — دست‌کاری‌شده یا نه —
 *  نمی‌تواند مجوزِ معتبر بسازد. این بررسی فقط یک لایهٔ دیگر است.
 */
object Integrity {

  /** اثرِ انگشتِ گواهیِ امضای همین فایلِ نصبی */
  fun fingerprint(context: Context): String = runCatching {
    val pm = context.packageManager
    val name = context.packageName
    val certificates: Array<android.content.pm.Signature> =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = info.signingInfo ?: return ""
        if (signing.hasMultipleSigners()) signing.apkContentsSigners
        else signing.signingCertificateHistory
      } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures ?: return ""
      }

    val first = certificates.firstOrNull() ?: return ""
    MessageDigest.getInstance("SHA-256")
      .digest(first.toByteArray())
      .joinToString("") { "%02x".format(it) }
  }.getOrDefault("")

  /**
   *  آیا امضا همان است.
   *
   *  اگر اثرِ انگشتِ مرجع خالی باشد (نسخهٔ آزمایشی، یا ساختی که کلید
   *  نداشته) بررسی خاموش است و جواب «بله» است — وگرنه برنامه در دستِ
   *  خودِ سازنده هم کار نمی‌کرد.
   */
  fun isGenuine(context: Context): Boolean {
    val expected = BuildConfig.SIGNING_SHA256
    if (expected.isBlank()) return true
    return fingerprint(context).equals(expected, ignoreCase = true)
  }
}
