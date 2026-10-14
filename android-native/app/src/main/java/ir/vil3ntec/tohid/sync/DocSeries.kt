package ir.vil3ntec.tohid.sync

import android.content.Context
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 *  «سری سند»ِ این دستگاه — F0-03 (فاز ۰؛ طرحِ کامل در ۳.۵ِ مشخصات).
 *
 *  ── چه اشکالی را می‌بندد ───────────────────────────────────────────
 *  در Sync v1 `nextInvoiceNo` هماهنگ نمی‌شود (و نباید بشود: عددِ دو
 *  دستگاه هر لحظه فرق دارد). گوشی و کامپیوترِ یک دکان هر دو از ۱
 *  می‌شمردند و دو «فاکتور #۵» می‌ساختند. حالا هر دستگاه هنگامِ پیوستن
 *  یک سریِ کوتاه از سرور می‌گیرد (A1، B1، …) و شمارهٔ نمایشی
 *  `{سری}-{شماره}` می‌شود: `A1-5` و `B1-5`.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────
 *  - سری برای هر حساب روی این دستگاه یک بار گرفته و نگه داشته می‌شود؛
 *    سرور برای همان دستگاه همیشه همان را برمی‌گرداند.
 *  - تا سری نرسیده (نخستین فروشِ آفلاین)، فاکتور بی سری است — همان رفتارِ
 *    پیشین. هیچ شمارهٔ گذشته‌ای عوض نمی‌شود.
 *  - شکستِ گرفتن (نت، سرورِ قدیمی بی این مسیر) بی‌صداست؛ دورِ بعد دوباره.
 */
object DocSeries {

  private const val PREFS = "tohid-doc-series"
  private val FORMAT = Regex("^[A-Z][1-9][0-9]{0,3}$")

  fun valid(series: String): Boolean = FORMAT.matches(series)

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** سریِ حسابِ واردشده روی این دستگاه؛ خالی یعنی هنوز نرسیده. */
  fun current(context: Context): String {
    val account = SyncStateV1.currentAccount(context)
    if (account.isBlank()) return ""
    return prefs(context).getString(account, "").orEmpty().takeIf { valid(it) }.orEmpty()
  }

  /** اگر نیست، از سرور می‌گیرد (`POST /shop/series`). */
  suspend fun ensure(context: Context): String {
    val have = current(context)
    if (have.isNotEmpty()) return have
    val account = SyncStateV1.currentAccount(context)
    if (account.isBlank()) return ""
    val res = Backend.api(context).post(
      ApiEndpoints.Shop.SERIES,
      buildJsonObject { put("deviceId", JsonPrimitive(SyncStore(context).deviceUid)) },
    )
    val got = (res["series"] as? JsonPrimitive)?.content.orEmpty()
    if (!valid(got)) return ""
    prefs(context).edit().putString(account, got).apply()
    return got
  }
}
