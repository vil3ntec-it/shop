package ir.vil3ntec.tohid.sync.v1

import android.content.Context

/**
 *  حالِ همگام‌سازیِ **این حساب** روی این دستگاه.
 *
 *  ⛔ کلیدها شناسهٔ حساب را در خود دارند. بی این، سایهٔ حسابِ قبلی روی
 *  یک گوشیِ مشترک همهٔ ردیف‌هایش را «تغییرِ تازه» می‌دید و داخلِ دکانِ
 *  نفرِ بعدی می‌فرستاد — همان چاله‌ای که `ShopStore` برای خودِ دفتر
 *  بسته است.
 */
class SyncStateV1(context: Context, private val accountKey: String) : V1Restore.State {

  private val prefs = context.applicationContext
    .getSharedPreferences("tohid-sync-v1", Context.MODE_PRIVATE)

  companion object {
    private const val ACCOUNT = "account_id"

    /**
     *  شناسهٔ حسابی که همین حالا وارد است.
     *
     *  ⛔ همهٔ کلیدهای Sync (cursor، سایه، صف) این را در خود دارند. بی
     *  آن، دو حساب روی یک گوشی یک سایه می‌گرفتند و ردیف‌های نفرِ اول
     *  «تغییرِ تازه»ی نفرِ دوم شمرده می‌شدند — یعنی دفترِ یکی داخلِ
     *  دکانِ دیگری.
     */
    fun currentAccount(context: Context): String =
      context.applicationContext.getSharedPreferences("tohid-sync-v1", Context.MODE_PRIVATE)
        .getString(ACCOUNT, "").orEmpty()

    fun setCurrentAccount(context: Context, userId: String) {
      context.applicationContext.getSharedPreferences("tohid-sync-v1", Context.MODE_PRIVATE)
        .edit().putString(ACCOUNT, userId).apply()
    }
  }

  private fun k(name: String) = "$accountKey.$name"

  override var cursor: Long
    get() = prefs.getLong(k("cursor"), 0)
    set(v) = prefs.edit().putLong(k("cursor"), v).apply()

  /** سایهٔ آخرین حالتِ همگام‌شده، به شکلِ متنِ JSON. خالی یعنی «هنوز هیچ». */
  var shadow: String
    get() = prefs.getString(k("shadow"), "").orEmpty()
    set(v) = prefs.edit().putString(k("shadow"), v).apply()

  /**
   *  سایه و cursor با هم و **همزمان** (`commit`، نه `apply`) — F0-05.
   *
   *  `apply()` روی دیسک دیرتر می‌نشیند؛ اگر برنامه پیش از آن بسته شود،
   *  سایه عقب می‌ماند در حالی که opها در صف‌اند. این‌جا یک نوشتنِ هم‌زمان
   *  است و ژورنالِ `SyncCommit` فقط پس از آن پاک می‌شود.
   *
   *  @return نوشته شد یا نه
   */
  fun commitShadow(shadow: String, cursor: Long?): Boolean {
    val e = prefs.edit().putString(k("shadow"), shadow)
    if (cursor != null) e.putLong(k("cursor"), cursor)
    return e.commit()
  }

  var lastOkAt: Long
    get() = prefs.getLong(k("lastOkAt"), 0)
    set(v) = prefs.edit().putLong(k("lastOkAt"), v).apply()

  var lastPushAt: Long
    get() = prefs.getLong(k("lastPushAt"), 0)
    set(v) = prefs.edit().putLong(k("lastPushAt"), v).apply()

  var lastPullAt: Long
    get() = prefs.getLong(k("lastPullAt"), 0)
    set(v) = prefs.edit().putLong(k("lastPullAt"), v).apply()

  var lastError: String
    get() = prefs.getString(k("lastError"), "").orEmpty()
    set(v) = prefs.edit().putString(k("lastError"), v).apply()

  /** سرور از برنامه عقب‌تر است (۴۲۶): opها نگه داشته می‌شوند. */
  var holding: Boolean
    get() = prefs.getBoolean(k("holding"), false)
    set(v) = prefs.edit().putBoolean(k("holding"), v).apply()

  var upgradeAvailable: Boolean
    get() = prefs.getBoolean(k("upgrade"), false)
    set(v) = prefs.edit().putBoolean(k("upgrade"), v).apply()

  var serverSchema: Int
    get() = prefs.getInt(k("serverSchema"), 0)
    set(v) = prefs.edit().putInt(k("serverSchema"), v).apply()

  var snapshotAt: Long
    get() = prefs.getLong(k("snapshotAt"), 0)
    set(v) = prefs.edit().putLong(k("snapshotAt"), v).apply()

  /** «دورانِ بازیابیِ» سروری که این حساب آخرین بار دید — §۹.۵، `core/sync/RestoreReplay.kt` */
  override var epoch: String
    get() = prefs.getString(k("epoch"), "").orEmpty()
    set(v) = prefs.edit().putString(k("epoch"), v).apply()

  /** سرور برگشته و دفتر هنوز از Snapshot از نو ساخته نشده — تا آن وقت Pull نه (`V1Restore`) */
  override val restorePending: Boolean
    get() = prefs.getBoolean(k("restorePending"), false)

  override fun adoptEpoch(epoch: String) { this.epoch = epoch }

  /** دوران و «در انتظار» با هم و همزمان (`commit`، نه `apply`) — شرح سرِ `V1Restore` */
  override fun commitRestore(epoch: String, pending: Boolean): Boolean =
    prefs.edit().putString(k("epoch"), epoch).putBoolean(k("restorePending"), pending).commit()

  /** دفترچه همهٔ پنجرهٔ پس از پایه را نداشت — هشدار تا `RESTORE_NOTICE_MS` (`SyncV1Engine.notice`) */
  var restoreNoticeAt: Long
    get() = prefs.getLong(k("restoreNoticeAt"), 0)
    set(v) = prefs.edit().putLong(k("restoreNoticeAt"), v).apply()

  override fun noteGap(at: Long) { restoreNoticeAt = at }

  /** آخرین pull دوران آورد (پرچمِ `SYNC_EPOCH_ENABLED` ِ سرور روشن) — `V1Restore.asks` */
  override var epochLive: Boolean
    get() = prefs.getBoolean(k("epochLive"), false)
    set(v) = prefs.edit().putBoolean(k("epochLive"), v).apply()

  /** زمانِ «هم‌ترازیِ یک‌باره» با سرور — شرح سرِ `SyncV1Engine.prepare` */
  var reconciledAt: Long
    get() = prefs.getLong(k("reconciledAt"), 0)
    set(v) = prefs.edit().putLong(k("reconciledAt"), v).apply()

  /** گزارشِ خطا به سرور — با اجازهٔ کاربر (بندِ ۲۰.۸). پیش‌فرض روشن. */
  var errorReports: Boolean
    get() = prefs.getBoolean("errorReports", true)
    set(v) = prefs.edit().putBoolean("errorReports", v).apply()

  /**
   *  A00: بزرگ‌ترین `op_id` ِ صف در نخستین شروع پس از به‌روزرسانی از ساختِ پیش از A00
   *  (`SyncCommit.start`)؛ "" یعنی هیچ. با `commit()` ِ هم‌زمان، چون باید پیش از مهرِ سایه
   *  روی دیسک باشد.
   */
  var legacyOpsUpTo: String
    get() = prefs.getString(k("legacyOpsUpTo"), "") ?: ""
    set(v) { prefs.edit().putString(k("legacyOpsUpTo"), v).commit() }

  fun reset() {
    prefs.edit()
      .remove(k("cursor")).remove(k("shadow")).remove(k("lastOkAt"))
      .remove(k("lastPushAt")).remove(k("lastPullAt")).remove(k("lastError"))
      .remove(k("holding")).remove(k("upgrade")).remove(k("serverSchema"))
      .remove(k("snapshotAt")).remove(k("legacyOpsUpTo"))
      .apply()
  }
}

/** چراغِ نوارِ بالا — همان چهار حال، همان تصمیمِ نسخهٔ وب. */
enum class SyncDot { GREEN, YELLOW, GREY, RED;

  val label: String get() = when (this) {
    GREEN -> "همگام با سرور"
    YELLOW -> "در صف ارسال"
    GREY -> "آفلاین"
    RED -> "خطای همگام‌سازی"
  }

  companion object {
    fun of(error: Boolean, online: Boolean, signedIn: Boolean, configured: Boolean, queued: Int, busy: Boolean): SyncDot =
      when {
        error -> RED
        !online || !signedIn || !configured -> GREY
        queued > 0 || busy -> YELLOW
        else -> GREEN
      }
  }
}

/** آن‌چه نوارِ بالا و صفحهٔ تنظیمات نشان می‌دهند. */
data class SyncStatus(
  val dot: SyncDot,
  val queued: Int,
  val dropped: Int,
  val cursor: Long,
  val lastOkAt: Long,
  val lastError: String,
  val holding: Boolean,
  val upgradeAvailable: Boolean,
  val schemaVersion: Int,
  val serverSchema: Int,
  val live: Boolean,
)
