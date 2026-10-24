package ir.vil3ntec.tohid.data

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.ParcelFileDescriptor
import ir.vil3ntec.tohid.core.atrest.AtRestScope
import java.io.File

/**
 *  پشتیبانِ خودکار و انتقالِ اندروید — SEC-01، بازبینیِ #331.
 *
 *  ── خاموش (پیش‌فرض) ────────────────────────────────────────────────
 *  همان `super.onFullBackup`: قاعده‌های `backup_rules.xml` و `backup_rules_legacy.xml`
 *  بی هیچ تغییری (دفتر، تنظیمات و حالِ همگام‌سازی می‌روند؛ توکن نه).
 *
 *  ── رمز در کار است ──────────────────────────────────────────────────
 *  کلیدِ Keystore با پشتیبان نمی‌رود. اگر دفترِ رمزدار و سایهٔ پُر و صف و cursor با هم
 *  می‌رفتند، گوشیِ تازه دفتری داشت که باز نمی‌شد کنارِ سایه‌ای که «همه‌چیز همگام است»
 *  می‌گفت. پس هیچ‌کدام نمی‌روند (`AtRestScope.skipInDeviceBackup`): نه فایل‌های رمزپذیر و
 *  کلیددان، نه صف و ژورنال و `tohid-sync-v1.xml`. گوشیِ تازه مثلِ نصبِ تازه دفتر را از
 *  سرور می‌گیرد؛ تنظیم‌ها و عکس‌ها مثلِ همیشه می‌روند.
 *
 *  ⚠️ این عامل در «حالتِ محدود» اجرا می‌شود (بی `Application`ِ برنامه) — فقط فایل و prefs.
 */
class TohidBackupAgent : BackupAgent() {

  override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput?, newState: ParcelFileDescriptor?) = Unit

  override fun onRestore(data: BackupDataInput?, appVersionCode: Int, newState: ParcelFileDescriptor?) = Unit

  override fun onFullBackup(data: FullBackupDataOutput) {
    if (!runCatching { AtRest.activeForBackup(this) }.getOrDefault(true)) {
      super.onFullBackup(data)
      return
    }
    //  همان دو دامنهٔ XML (`file`، `sharedpref`)، منهای حالِ رمز و همگام‌سازی
    val files = filesDir
    files.walkTopDown()
      .onEnter { dir -> dir == files || !AtRestScope.skipInDeviceBackup(dir.relativeTo(files).invariantSeparatorsPath) }
      .filter { it.isFile && !AtRestScope.skipInDeviceBackup(it.relativeTo(files).invariantSeparatorsPath) }
      .forEach { runCatching { fullBackupFile(it, data) } }
    val prefs = File(applicationInfo.dataDir, "shared_prefs")
    prefs.listFiles().orEmpty()
      .filter { it.isFile && it.name !in EXCLUDED_PREFS && it.name !in AtRestScope.SYNC_PREFS }
      .forEach { runCatching { fullBackupFile(it, data) } }
  }

  private companion object {
    /** همان استثناهای XML: توکنِ امروز و جای قدیمی‌اش، و PINهای شاگرد (STF-02/04) */
    val EXCLUDED_PREFS = setOf("tohid-sync.xml", "tohid-session.xml", "tohid-session-secure.xml", "tohid-staff-pins.xml")
  }
}
