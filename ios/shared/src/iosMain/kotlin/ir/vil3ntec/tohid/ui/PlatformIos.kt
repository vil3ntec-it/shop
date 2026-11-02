package ir.vil3ntec.tohid.ui

import platform.UIKit.UIDevice

/*
 *  جایگزینِ iOS ِ `ui/Platform.kt` (همان API؛ کامپیوتر: ‎desktop/…/ui/PlatformDesk.kt‎).
 *  پوشه در آیفون نیست (مثلِ اندروید)؛ «ذخیره به‌عنوان» همان `CreateDocument` است (‎ios/IosPickers.kt‎: Files) و اشتراک برگهٔ iOS.
 */
object Platform {
  const val DESKTOP: Boolean = false

  fun defaultBackupDir(context: android.content.Context): java.io.File? = null

  fun pickFolder(start: String?): String? = null

  fun openFolder(path: String): Boolean = false

  /**
   *  اثرِ انگشتِ دستگاه برای «شناسهٔ دستگاه» ِ کدِ آفلاین — iOS: `identifierForVendor` (تا وقتی
   *  برنامه پاک نشود ثابت است؛ مثلِ ANDROID_ID با نصبِ دوباره عوض می‌شود).
   */
  fun machineFingerprint(context: android.content.Context): String =
    "ios-" + (UIDevice.currentDevice.identifierForVendor?.UUIDString ?: "unknown")

  fun saveFile(suggestedName: String, bytes: ByteArray): String? = null
}
