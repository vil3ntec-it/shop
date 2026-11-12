package ir.vil3ntec.tohid.data

import android.content.Context

/**
 *  OPS-02 — تنظیم و حالِ پشتیبانِ **مالِ همین دستگاه**: پروندهٔ `tohid-backup-device` که هرگز با پشتیبانِ اندروید یا انتقالِ
 *  گوشی‌به‌گوشی نمی‌رود (`backup_rules.xml`، `backup_rules_legacy.xml`، `TohidBackupAgent.EXCLUDED_PREFS`؛ `DeviceOnlyPrefsTest`).
 *
 *   • `backup_seal` («پشتیبانِ دستی و پوشه هم رمزدار»، `BackupSeal`): کلیدِ عبارت مالِ همین دستگاه است؛ اگر پرچم با پشتیبانِ
 *     اندروید به گوشیِ تازه می‌رفت، آن‌جا روشن بی کلید بود و هر پشتیبانِ پوشه بی‌صدا شکست می‌خورد.
 *   • شکستِ پشتیبانِ پوشه (خودکارِ شبانه یا «همین حالا»): پیامِ روشن و زمان، تا صفحهٔ «داده‌ها» نشانش دهد؛ پشتیبانِ موفق پاکش
 *     می‌کند (همان الگوی `SnapshotSeal.lastError`، ولی ماندگار: کارِ شبانه در فرآیندِ دیگری است).
 */
object BackupDevice {

  const val PREFS = "tohid-backup-device"

  private const val MIRROR_ERROR = "mirror_error"
  private const val MIRROR_ERROR_AT = "mirror_error_at"

  internal fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** شکستِ پشتیبانِ پوشه ⇒ پیامِ کاربر و زمان؛ موفق (`null`) ⇒ پاک */
  fun noteMirror(context: Context, failure: Throwable?) {
    runCatching {
      prefs(context).edit().apply {
        if (failure == null) remove(MIRROR_ERROR).remove(MIRROR_ERROR_AT)
        else putString(MIRROR_ERROR, failure.message ?: failure.toString()).putLong(MIRROR_ERROR_AT, System.currentTimeMillis())
      }.commit()
    }
  }

  /** آخرین شکستِ پشتیبانِ پوشه که هنوز با پشتیبانِ موفق پاک نشده: پیام و زمان */
  fun mirrorError(context: Context): Pair<String, Long>? = runCatching {
    val p = prefs(context)
    p.getString(MIRROR_ERROR, null)?.takeIf { it.isNotBlank() }?.let { it to p.getLong(MIRROR_ERROR_AT, 0L) }
  }.getOrNull()
}
