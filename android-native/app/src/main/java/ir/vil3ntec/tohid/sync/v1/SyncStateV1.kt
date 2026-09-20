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
class SyncStateV1(context: Context, private val accountKey: String) {

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

  var cursor: Long
    get() = prefs.getLong(k("cursor"), 0)
    set(v) = prefs.edit().putLong(k("cursor"), v).apply()

  /** سایهٔ آخرین حالتِ همگام‌شده، به شکلِ متنِ JSON. خالی یعنی «هنوز هیچ». */
  var shadow: String
    get() = prefs.getString(k("shadow"), "").orEmpty()
    set(v) = prefs.edit().putString(k("shadow"), v).apply()

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

  /** گزارشِ خطا به سرور — با اجازهٔ کاربر (بندِ ۲۰.۸). پیش‌فرض روشن. */
  var errorReports: Boolean
    get() = prefs.getBoolean("errorReports", true)
    set(v) = prefs.edit().putBoolean("errorReports", v).apply()

  fun reset() {
    prefs.edit()
      .remove(k("cursor")).remove(k("shadow")).remove(k("lastOkAt"))
      .remove(k("lastPushAt")).remove(k("lastPullAt")).remove(k("lastError"))
      .remove(k("holding")).remove(k("upgrade")).remove(k("serverSchema"))
      .remove(k("snapshotAt"))
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
