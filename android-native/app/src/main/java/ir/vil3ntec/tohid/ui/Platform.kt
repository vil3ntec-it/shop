package ir.vil3ntec.tohid.ui

/**
 *  کدام برنامه است؟ — گوشی/تبلتِ اندروید.
 *
 *  برنامهٔ کامپیوتر همین فایل را با `ui/PlatformDesk.kt` جایگزین می‌کند
 *  (فهرستِ `replaced` در ‎desktop/build.gradle.kts‎). یک ثابت است، نه
 *  سنجشِ اندازهٔ صفحه: تبلتِ افقی هم پهن است ولی برنامهٔ لمسی است و
 *  باید دکمه‌های درشت و برگهٔ پایین داشته باشد؛ کامپیوتر ماوس و
 *  صفحه‌کلید دارد و اندازه‌ها و پنجره‌های خودش را.
 */
object Platform {
  const val DESKTOP: Boolean = false

  /** پوشهٔ پیش‌فرضِ پشتیبانِ کامل — اندروید: ‎Android/data/…/files/Backups‎ (با مدیرِ فایل دیده می‌شود) */
  fun defaultBackupDir(context: android.content.Context): java.io.File? = context.getExternalFilesDir("Backups")

  /** انتخابِ پوشه — فقط کامپیوتر */
  fun pickFolder(start: String?): String? = null

  /** باز کردنِ پوشه در مدیرِ فایلِ سیستم — فقط کامپیوتر */
  fun openFolder(path: String): Boolean = false

  /**
   *  اثرِ انگشتِ همین دستگاه برای «شناسهٔ کامپیوتر»ِ کدِ آفلاین — اندروید:
   *  ‎ANDROID_ID‎ (با نصبِ دوباره عوض نمی‌شود).
   */
  @android.annotation.SuppressLint("HardwareIds")
  fun machineFingerprint(context: android.content.Context): String =
    "android-" + (android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
      ?: ir.vil3ntec.tohid.sync.SyncStore(context).deviceUid)

  /** ذخیرهٔ فایل با پنجرهٔ «ذخیره به‌عنوان» — فقط کامپیوتر؛ مسیرِ ذخیره‌شده یا null */
  fun saveFile(suggestedName: String, bytes: ByteArray): String? = null
}
