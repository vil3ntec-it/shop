package ir.vil3ntec.tohid.scan

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.ScanConfig
import ir.vil3ntec.tohid.print.HwTools

/**
 *  HW-05 — تنظیمِ بارکدخوانِ صفحه‌کلیدیِ همین دستگاه: آستانهٔ فاصلهٔ نویسه‌ها، کمینهٔ طول، پیشوند و پسوند.
 *  همگام نمی‌شود (هر دستگاه بارکدخوانِ خودش را دارد). فقط وقتی «ابزارهای تازهٔ دستگاه» (`HwTools`) روشن
 *  است به کار می‌رود؛ خاموش ⇒ همان ۵۰ میلی‌ثانیه و کمینهٔ ۴ِ همیشگی، بی پیشوند و پسوند.
 */
object ScanPrefs {
  private const val PREFS = "tohid-ui"
  private const val GAP = "scan_gap_ms"
  private const val MIN = "scan_min_len"
  private const val PREFIX = "scan_prefix"
  private const val SUFFIX = "scan_suffix"

  /** تنظیمِ ذخیره‌شده (چه پرچم روشن باشد چه نه) — برای صفحهٔ دستگاه‌ها */
  val saved = mutableStateOf(ScanConfig())
  private var loaded = false

  fun load(context: Context) {
    if (loaded) return
    loaded = true
    HwTools.load(context)
    saved.value = runCatching {
      val p = context.getSharedPreferences(PREFS, 0)
      ScanConfig(
        maxGapMs = p.getLong(GAP, ScanConfig.DEFAULT_GAP_MS),
        minLength = p.getInt(MIN, ScanConfig.DEFAULT_MIN_LENGTH),
        prefix = p.getString(PREFIX, "").orEmpty(),
        suffix = p.getString(SUFFIX, "").orEmpty(),
      ).normalized()
    }.getOrDefault(ScanConfig())
  }

  fun save(context: Context, c: ScanConfig) {
    val n = c.normalized()
    saved.value = n
    runCatching {
      context.getSharedPreferences(PREFS, 0).edit()
        .putLong(GAP, n.maxGapMs).putInt(MIN, n.minLength).putString(PREFIX, n.prefix).putString(SUFFIX, n.suffix).apply()
    }
  }

  /** آنچه بارکدخوان‌ها و نگهبانِ کادرِ عدد به کار می‌برند */
  fun current(): ScanConfig = if (HwTools.on.value) saved.value else ScanConfig()
}
