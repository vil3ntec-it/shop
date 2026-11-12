package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.net.ApiJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 *  قفلِ نرمِ پایانِ اشتراک — بندِ ۲۱٫۸.
 *
 *  ⛔ **هیچ داده‌ای پاک نمی‌شود.** دفتر سرِ جایش است، خروجی و چاپ و
 *     پشتیبانِ محلی کار می‌کنند، و با تمدیدِ اشتراک همان لحظه همه‌چیز
 *     باز می‌شود.
 *  ⛔ **همگام‌سازی هم متوقف نمی‌شود**: تغییرهایی که پیش از انقضا در صف
 *     مانده‌اند می‌روند. قفل جلوی **نوشتنِ تازه** را می‌گیرد، نه جلوی
 *     رسیدنِ کارِ دیروز به سرور.
 *  ⚠️ این قفل **رابط کاربری** است، نه قفلِ داده: کسی که به فایل‌های
 *     برنامه دست دارد دورش می‌زند. قفلِ واقعی روی سرور است. این را
 *     صریح می‌نویسیم تا کسی به آن تکیه نکند.
 *
 *  تصمیم از تپشِ **کش‌شده** گرفته می‌شود، نه از ساعتِ گوشی — وگرنه
 *  عقب بردنِ ساعت قفل را باز می‌کرد.
 */
object SoftLock {

  private val _locked = MutableStateFlow(false)
  val locked: StateFlow<Boolean> = _locked.asStateFlow()

  /** چرا بسته است — برای متنِ نوار (`ShellNotices`)؛ باز ⇒ null */
  enum class Reason {
    /** اشتراک تمام شده (تپشِ سرور، یا تاریخِ پایانِ کش‌شده با ساعتِ مطمئن گذشته) */
    ENDED,
    /** مجوزِ امضاشدهٔ این دستگاه تمام شده: بیش از عمرِ مجوز (۱۰ روز؛ با پرچم ۳۵) به اینترنت وصل نشده */
    RENEW,
  }
  private val _reason = MutableStateFlow<Reason?>(null)
  val reason: StateFlow<Reason?> = _reason.asStateFlow()

  /** آخرین باری که یک نوشتن پشتِ قفل ماند — تا صفحه پیام بدهد. */
  @Volatile var lastBlockedAt: Long = 0
    private set

  /**
   *  خواستِ صاحب‌کار ۱۴۰۵/۰۸/۲۲ («بی اینترنت تا ابد کار می‌کند»): پیش از این قفل **فقط** با تپشِ «تمام‌شده» ِ سرور بسته
   *  می‌شد ⇒ دستگاهی که آنلاین نمی‌شد هرگز قفل نمی‌شد، هرچند مجوزش پس از ۱۰ روز تمام شده بود. حالا:
   *    • مجوزِ فعال یا در مهلت (یا کدِ آفلاینِ همین کامپیوتر) ⇒ باز (همان `VipGate`)؛
   *    • تپشِ کش‌شده با ساعتِ مطمئن «تمام‌شده» ⇒ [Reason.ENDED]؛
   *    • مجوزی بوده و تمام شده (`EXPIRED`) ⇒ [Reason.RENEW]: یک بار وصل شدن مجوز را تازه می‌کند و همان دم باز می‌شود.
   *  ⛔ دستگاهی که هرگز مجوز نگرفته (`NONE`، بی حساب) همان امروز: فقط تپشِ سرور.
   */
  fun decide(license: ir.vil3ntec.tohid.sync.License.State?, cacheEnded: Boolean): Reason? = when {
    license == ir.vil3ntec.tohid.sync.License.State.ACTIVE || license == ir.vil3ntec.tohid.sync.License.State.GRACE -> null
    cacheEnded -> Reason.ENDED
    license == ir.vil3ntec.tohid.sync.License.State.EXPIRED -> Reason.RENEW
    else -> null
  }

  fun refresh(context: Context): Boolean {
    val ended = runCatching { HeartbeatCache(context).softLocked() }.getOrDefault(false)
    val st = runCatching {
      ir.vil3ntec.tohid.sync.LicenseGuard.status(context, ir.vil3ntec.tohid.sync.SyncStore(context)).state
    }.getOrNull()
    val r = decide(st, ended)
    _reason.value = r
    _locked.value = r != null
    return r != null
  }

  /** هر نوشتن تازه می‌سنجد: تمدید یا تپشِ تازه همان دم دیده شود (`CrmSweepDiskTest`) */
  fun isLocked(context: Context): Boolean = refresh(context)

  internal fun noteBlocked() { lastBlockedAt = System.currentTimeMillis() }

  /** روزهای مانده — برای بنرِ زردِ هفت روز پیش از پایان. */
  fun warnDays(context: Context): Int? {
    val sub = runCatching { HeartbeatCache(context).read() }.getOrNull()
      ?.get("subscription") as? kotlinx.serialization.json.JsonObject ?: return null
    if (ApiJson.bool(sub, "permanent")) return null
    if (!ApiJson.bool(sub, "active")) return null
    val days = ApiJson.int(sub, "daysLeft")
    return if (days in 1..7) days else null
  }
}
