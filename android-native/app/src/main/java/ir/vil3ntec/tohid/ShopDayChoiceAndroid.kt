package ir.vil3ntec.tohid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.calendar.ShopDay

/**
 *  پایانِ روزِ کاریِ دکان (LOC-03) — انتخابِ دکان‌دار روی همین دستگاه، کنارِ
 *  `ShopPolicy` در همان پرونده (`tohid-policy`). همگام نمی‌شود.
 *
 *  پیش‌فرض نیمه‌شب (`ShopDay.OFF`) = رفتارِ امروز: تا دکان‌دار خودش در «تاریخ و رقم»
 *  عوض نکند هیچ تاریخی جابه‌جا نمی‌شود. روشن ⇒ `todayIso()` (تاریخِ ثبتِ فروش، برگشت،
 *  شیفت، مصارف…) و ساعتِ گزارش (`ReportBridge.deviceClock`) ساعتِ پیش از پایان را
 *  هنوز «دیروز» می‌شمارند. خودِ قاعده در `core/calendar/ShopDay` است.
 *
 *  مثلِ `LocaleChoice` حالتِ Compose است؛ `load` در `MainActivity` و `Main.kt`ِ کامپیوتر،
 *  کنارِ `LocaleChoice.load`، صدا زده می‌شود.
 */
object ShopDayChoice {

  private const val PREFS = "tohid-policy"
  private const val DAY_END = "day_end_hour"

  /** ساعتِ پایانِ روزِ کاری پس از نیمه‌شب — یکی از `ShopDay.CHOICES` */
  var hours by mutableStateOf(ShopDay.OFF)
    private set

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context) {
    val p = runCatching { prefs(context) }.getOrNull() ?: return
    hours = ShopDay.clamp(runCatching { p.getInt(DAY_END, ShopDay.OFF) }.getOrDefault(ShopDay.OFF))
  }

  fun set(context: Context, value: Int) {
    hours = ShopDay.clamp(value)
    prefs(context).edit().putInt(DAY_END, hours).apply()
  }
}
