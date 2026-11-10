package ir.vil3ntec.tohid.sync.v1

import android.content.Context

import ir.vil3ntec.tohid.core.i18n.StrSync
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.sync.SyncOutage
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

  /**
   *  سایه و cursor — پرچمِ `shop.sync_shadow_file` خاموش (پیش‌فرض): همان کلیدهای prefs ِ همیشه، بیت‌به‌بیت؛ روشن:
   *  یک فایل و یک نوشتن (`ShadowStore`، PERF-LEDGER). همهٔ نمونه‌های همین کلید یک حافظه را می‌بینند.
   */
  private val store = ShadowStore.of(context, accountKey)

  override var cursor: Long
    get() = store.cursor()
    set(v) = store.setCursor(v)

  /** فقط نوار و صفحه: هرگز فایلِ سایه را نمی‌خواند (رشتهٔ صفحه) — منطق همیشه [cursor] */
  val cursorSeen: Long get() = store.cursorSeen()

  /** ردی از همگام‌سازی (cursor، سایه، هم‌ترازی) بی خواندنِ فایلِ سایه — `SyncV1Keys.hasState` */
  fun hasSyncState(): Boolean = store.present() || reconciledAt > 0 || snapshotAt > 0

  /** سایهٔ آخرین حالتِ همگام‌شده، به شکلِ متنِ JSON. خالی یعنی «هنوز هیچ». */
  var shadow: String
    get() = store.shadow()
    set(v) = store.setShadow(v)

  /**
   *  سایه و cursor با هم و **همزمان** (`commit`، نه `apply`) — F0-05.
   *
   *  `apply()` روی دیسک دیرتر می‌نشیند؛ اگر برنامه پیش از آن بسته شود،
   *  سایه عقب می‌ماند در حالی که opها در صف‌اند. این‌جا یک نوشتنِ هم‌زمان
   *  است و ژورنالِ `SyncCommit` فقط پس از آن پاک می‌شود.
   *
   *  @return نوشته شد یا نه
   */
  fun commitShadow(shadow: String, cursor: Long?): Boolean = store.commit(shadow, cursor)

  var lastOkAt: Long
    get() = prefs.getLong(k("lastOkAt"), 0)
    set(v) = prefs.edit().putLong(k("lastOkAt"), v).apply()

  var lastPushAt: Long
    get() = prefs.getLong(k("lastPushAt"), 0)
    set(v) = prefs.edit().putLong(k("lastPushAt"), v).apply()

  var lastPullAt: Long
    get() = prefs.getLong(k("lastPullAt"), 0)
    set(v) = prefs.edit().putLong(k("lastPullAt"), v).apply()

  /**
   *  «یادداشتِ» `since` ِ push (#481): cursor ِ لحظه‌ای که صفِ خالی نخستین op را گرفت، و تا خالی شدنِ
   *  صف فقط پایین می‌آید — شرح سرِ `core/…/core/sync/PushSince.kt`. `-1` یعنی هیچ (⇒ همان cursor).
   *  گم شدنِ این نوشتن (بسته شدن پیش از `apply`) فقط محتاط‌ترش می‌کند یا cursor را می‌فرستد؛ پس از
   *  باز شدن هم push پیش از pull است، پس cursor همان لحظهٔ ثبت است.
   */
  var pushSince: Long
    get() = prefs.getLong(k("pushSince"), -1L)
    set(v) = prefs.edit().putLong(k("pushSince"), v).apply()

  var lastError: String
    get() = prefs.getString(k("lastError"), "").orEmpty()
    set(v) = prefs.edit().putString(k("lastError"), v).apply()

  /** `device_id` ِ همین حساب روی همین دستگاه برای سرور، یک بار انتخاب و نگه داشته (`V1ReplicaAndroid.kt`) */
  var replica: String
    get() = prefs.getString(k("replica"), "").orEmpty()
    set(v) = prefs.edit().putString(k("replica"), v).apply()

  /** DATA-06: شناسهٔ دستگاهی که این حال به آن بسته است (`core/sync/RestoredDevice.kt`)؛ خالی ⇒ هنوز نه */
  val device: String
    get() = prefs.getString(k("device"), "").orEmpty()

  /** DATA-06: حالِ دستگاهِ دیگر بود ⇒ تا «بازسازی از سرور» نه Pull، نه هم‌ترازی (`V1Replica.fence`) */
  var restoredRebuild: Boolean
    get() = prefs.getBoolean(k("restoredRebuild"), false)
    set(v) { prefs.edit().putBoolean(k("restoredRebuild"), v).commit() }

  /**
   *  بستن به [device] — با حصار ([fence]) replica ِ تازه و «در انتظارِ بازسازی» هم، هر سه در **یک** `commit()`: بسته شدنِ
   *  برنامه پیش از آن ⇒ هیچ چیزی عوض نشده و دورِ بعد همان تصمیم؛ پس از آن ⇒ تمام.
   */
  fun commitBinding(device: String, fence: Boolean, replica: String): Boolean {
    val e = prefs.edit().putString(k("device"), device)
    if (fence) {
      e.putBoolean(k("restoredRebuild"), true)
      if (replica.isBlank()) e.remove(k("replica")) else e.putString(k("replica"), replica)
    }
    return e.commit()
  }

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
  /**
   *  دفترِ بایگانی‌شدهٔ ساخت‌های پیش از «یک درِ ورود» برای این حساب باز شد و سایه/صفِ این کلید شاید مالِ
   *  دفترِ دیگری است ⇒ تا `SyncV1Engine.recoverArchived` نشده، نه تفاضل، نه push، نه pull (شرح همان‌جا).
   */
  var recoverArchive: Boolean
    //  سایهٔ فایل که به نسخهٔ کهنه‌تر برگشت همین نشان را می‌گذارد — پیش از سنجیدنش (`ShadowStore.settleOffMain`)
    get() { store.settleOffMain(); return prefs.getBoolean(k("recoverArchive"), false) }
    set(v) { prefs.edit().putBoolean(k("recoverArchive"), v).commit() }

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
    store.reset()
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
    GREEN -> StrSync.dotGreen
    YELLOW -> StrSync.dotYellow
    GREY -> StrSync.dotGrey
    RED -> StrSync.dotRed
  }

  companion object {
    /** SYN-12: `alarm = false` (حکمِ `SyncOutage`) ⇒ شکست سرخ نمی‌کند ولی هرگز سبز هم نیست — `SyncOutage.dot` ِ core */
    fun of(error: Boolean, online: Boolean, signedIn: Boolean, configured: Boolean, queued: Int, busy: Boolean, alarm: Boolean = true): SyncDot =
      when (SyncOutage.dot(error, alarm, online, signedIn, configured, queued, busy)) {
        SyncOutage.Dot.RED -> RED
        SyncOutage.Dot.GREY -> GREY
        SyncOutage.Dot.YELLOW -> YELLOW
        SyncOutage.Dot.GREEN -> GREEN
      }
  }
}

/**
 *  SYN-12: کدِ شکست برای `SyncOutage` — بی پاسخ (نت، دامنه، مهلت) ⇒ `NO_RESPONSE`؛ پاسخِ HTTP ⇒ همان کد؛
 *  بقیه (دیسک، ژورنال، پیکربندی، نشستِ بی کد) ⇒ `LOCAL` (کدِ متنیِ نشست جدا سنجیده می‌شود).
 */
internal object SyncFailure {
  fun status(error: Throwable): Int = when (error) {
    is ApiFailure.Offline, is ApiFailure.Unreachable, is ApiFailure.Timeout -> SyncOutage.NO_RESPONSE
    is ApiFailure -> if (error.status > 0) error.status else SyncOutage.LOCAL
    else -> SyncOutage.LOCAL
  }

  fun code(error: Throwable): String = (error as? ApiFailure)?.code.orEmpty()

  /** شکستِ تازه ⇒ `lastError` ِ حال + رشتهٔ تازه یا ادامهٔ همان (`SyncOutage.next`)؛ صف و فاصلهٔ تلاش دست نمی‌خورند */
  fun note(state: SyncStateV1, prev: SyncOutage.Streak?, error: Throwable, message: String, now: Long): SyncOutage.Streak {
    val before = state.lastError
    state.lastError = message
    return SyncOutage.next(prev, before, status(error), code(error), message, now)
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
  /** SYN-12: چرا نرسید — شبکهٔ دستگاه، سرور، قطعیِ سراسری یا نشست (`SyncOutage.of`) */
  val outage: SyncOutage.Kind = SyncOutage.Kind.OK,
)
