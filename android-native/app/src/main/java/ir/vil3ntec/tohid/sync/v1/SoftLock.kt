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

  /** آخرین باری که یک نوشتن پشتِ قفل ماند — تا صفحه پیام بدهد. */
  @Volatile var lastBlockedAt: Long = 0
    private set

  fun refresh(context: Context): Boolean {
    val on = runCatching { HeartbeatCache(context).softLocked() }.getOrDefault(false)
    _locked.value = on
    return on
  }

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
