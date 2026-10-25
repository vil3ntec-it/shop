package ir.vil3ntec.tohid.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 *  پشتیبانِ کامل در پوشه‌ای که دیده می‌شود — کنارِ پشتیبانِ سرور.
 *
 *  `AutoBackup` هر ۱۲ ساعت دفتر را **داخلِ** برنامه کپی می‌کند (برای
 *  برگرداندنِ اشتباه) و ZIPِ کامل را به سرور می‌فرستد. این‌جا همان ZIPِ
 *  کامل (دفتر + همهٔ عکس‌ها، `BackupBundle`) در پوشه‌ای نوشته می‌شود که
 *  صاحبِ دکان خودش می‌بیند و می‌تواند به فلش یا هارد ببرد:
 *    • کامپیوتر: «اسناد ⇐ فروشگاه من ⇐ پشتیبان» یا هر پوشه‌ای که انتخاب شود
 *      (مثلاً پوشهٔ گوگل‌درایو یا دیسکِ دوم)
 *    • اندروید: ‎Android/data/…/files/Backups‎
 *  ۳۰ نسخهٔ آخر می‌ماند. هر بار که پشتیبانِ خودکار می‌رود و با دکمهٔ
 *  «پشتیبانِ کامل همین حالا» در تنظیمات.
 */
object BackupMirror {

  const val KEEP = 30
  private const val PREFIX = "tohid-"

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid", 0)

  fun folder(context: Context): File? {
    val custom = prefs(context).getString("backup_folder", null)?.takeIf { it.isNotBlank() }
    return (custom?.let { File(it) } ?: ir.vil3ntec.tohid.ui.Platform.defaultBackupDir(context))
  }

  fun setFolder(context: Context, path: String?) {
    prefs(context).edit().apply { if (path.isNullOrBlank()) remove("backup_folder") else putString("backup_folder", path) }.apply()
  }

  fun enabled(context: Context): Boolean = prefs(context).getBoolean("backup_mirror", true)
  fun setEnabled(context: Context, on: Boolean) { prefs(context).edit().putBoolean("backup_mirror", on).apply() }

  fun list(context: Context): List<File> =
    folder(context)?.listFiles()?.filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(".zip") }
      ?.sortedByDescending { it.lastModified() }.orEmpty()

  /** یک ZIPِ کامل در پوشه — فایلِ ساخته‌شده، یا خطا */
  fun write(context: Context): Result<File> = runCatching {
    val ledger = File(context.filesDir, "shop-data.json")
    require(ledger.exists() && ledger.length() > 0) { "دفتری برای پشتیبان نیست" }
    val dir = folder(context) ?: error("پوشهٔ پشتیبان در دسترس نیست")
    if (!dir.exists()) dir.mkdirs()
    require(dir.isDirectory && dir.canWrite()) { "در پوشهٔ «${dir.path}» نمی‌شود نوشت" }
    val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
    val target = File(dir, "$PREFIX$stamp.zip")
    val tmp = File(dir, "$PREFIX$stamp.zip.part")
    //  SEC-01: متنِ بازِ دفتر — پشتیبانِ پوشه با کلیدِ دستگاه رمز نمی‌شود (بند ۱۰٫۳)
    val ledgerJson = AtRest.readText(context, ledger)
    tmp.outputStream().use { out -> BackupBundle.write(context, out, ledgerJson).getOrThrow() }
    if (target.exists()) target.delete()
    require(tmp.renameTo(target)) { "نامِ فایلِ پشتیبان گذاشته نشد" }
    list(context).drop(KEEP).forEach { runCatching { it.delete() } }
    target
  }
}
