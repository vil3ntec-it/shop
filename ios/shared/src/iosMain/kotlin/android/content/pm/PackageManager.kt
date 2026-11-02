package android.content.pm

/** shimِ iOS ِ ثابت‌های `PackageManager` که کدِ مشترک می‌خواند */
object PackageManager {
  /** برنامهٔ دیگری برای «قصد» ِ اندروید در iOS نیست ⇐ فهرستِ خالی (مثلاً تشخیصِ گفتار ⇐ نشانِ میکروفون پنهان) */
  /** «باز کردنِ همین برنامه» (لمسِ اعلان) — در iOS خودِ برنامه همیشه همین است */
  fun getLaunchIntentForPackage(packageName: String): android.content.Intent? = android.content.Intent(android.content.Intent.ACTION_MAIN)

  /** نصبِ APK ِ بیرونی در iOS نیست (قاعدهٔ ۲.۵.۲ ِ اپل) */
  fun canRequestPackageInstalls(): Boolean = false

  fun queryIntentActivities(intent: android.content.Intent, flags: Int): List<Any> = emptyList()

  const val PERMISSION_GRANTED = 0
  const val PERMISSION_DENIED = -1
  const val MATCH_DEFAULT_ONLY = 0x00010000
  const val FEATURE_NFC_HOST_CARD_EMULATION = "android.hardware.nfc.hce"
  const val FEATURE_CAMERA_ANY = "android.hardware.camera.any"
}
