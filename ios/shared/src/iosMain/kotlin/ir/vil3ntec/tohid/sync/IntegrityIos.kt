package ir.vil3ntec.tohid.sync

import android.content.Context

/*
 *  جایگزینِ iOS ِ `sync/Integrity.kt` (اثرِ امضای APK ِ اندروید؛ کامپیوتر: ‎IntegrityDesk‎ همین را دارد).
 *  برنامهٔ iOS را خودِ سیستم با امضای اپل وارسی می‌کند و برنامهٔ دست‌خورده اجرا نمی‌شود؛ App Attest
 *  (گواهیِ «همین برنامه» برای سرور) کارِ گامِ ۳ و نیازمندِ حسابِ اپل است (BLOCKED-EXTERNAL).
 */
object Integrity {
  fun fingerprint(context: Context): String = ""
  fun isGenuine(context: Context): Boolean = true
}
