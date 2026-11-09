package ir.vil3ntec.tohid.ui

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

/*
 *  جایگزینِ iOS ِ `ui/Platform.kt` (همان API؛ کامپیوتر: ‎desktop/…/ui/PlatformDesk.kt‎).
 *  انتخابِ پوشه در آیفون نیست (مثلِ اندروید)؛ پوشهٔ پشتیبانِ دیدنی `Documents/Backups` است؛ «ذخیره به‌عنوان» همان `CreateDocument` است (‎ios/IosPickers.kt‎: Files) و اشتراک برگهٔ iOS.
 */
object Platform {
  const val DESKTOP: Boolean = false

  /**
   *  ⛔ DATA-SAFETY: پوشهٔ «پشتیبانِ دیدنی» (`BackupMirror`) — همتای `getExternalFilesDir("Backups")` ِ اندروید:
   *  `Documents/Backups` ِ همین برنامه (در برنامهٔ Files زیرِ «On My iPhone» با `UIFileSharingEnabled` ِ ‎ios/iosApp/…/Info.plist‎،
   *  و در پشتیبانِ iCloud ِ دستگاه). پیش‌تر `null` بود ⇒ `BackupMirror.write` همیشه شکست می‌خورد و «خروجِ اجباری»
   *  (`SafeSignOut.keepForAccount`: پشتیبانِ دیدنی **و** گاوصندوقِ رمزدار) روی آیفون هرگز انجام نمی‌شد.
   */
  fun defaultBackupDir(context: android.content.Context): java.io.File? =
    NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull()?.toString()
      ?.let { java.io.File(it, "Backups") }

  fun pickFolder(start: String?): String? = null

  fun openFolder(path: String): Boolean = false

  /**
   *  اثرِ انگشتِ دستگاه برای «شناسهٔ دستگاه» ِ کدِ آفلاین — iOS: `identifierForVendor` (تا وقتی
   *  برنامه پاک نشود ثابت است؛ مثلِ ANDROID_ID با نصبِ دوباره عوض می‌شود).
   */
  fun machineFingerprint(context: android.content.Context): String =
    "ios-" + (UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown")

  fun saveFile(suggestedName: String, bytes: ByteArray): String? = null

  /** OPS-02 — iOS پنجرهٔ «امن» در برابرِ عکسِ صفحه ندارد (برنامه نمی‌تواند آن را ببندد)؛ همان API بی‌اثر */
  @androidx.compose.runtime.Composable
  fun SecureWindow(on: Boolean) = Unit
}
