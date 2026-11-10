package ir.vil3ntec.tohid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.calendar.ShopDay
import ir.vil3ntec.tohid.core.calendar.ShopWeek
import ir.vil3ntec.tohid.core.calendar.WeekRules

/**
 *  پایانِ روزِ کاریِ دکان (LOC-03) — انتخابِ دکان‌دار، کنارِ `ShopPolicy` در همان پرونده
 *  (`tohid-policy`). «مالِ دکان» ِ `ShopSettingsKeys`: با همگام‌سازیِ تنظیمِ دکان (پرچمِ سرورِ
 *  `shop.settings_sync`، پیش‌فرض خاموش) به دستگاه‌های دیگر می‌رود؛ مقدارِ رسیده با `load` ِ بعدی خوانده می‌شود.
 *
 *  پیش‌فرض نیمه‌شب (`ShopDay.OFF`) = رفتارِ امروز: تا دکان‌دار خودش در «تاریخ و رقم»
 *  عوض نکند هیچ تاریخی جابه‌جا نمی‌شود. روشن ⇒ `todayIso()` (تاریخِ ثبتِ فروش، برگشت،
 *  شیفت، مصارف…) و ساعتِ گزارش (`ReportBridge.deviceClock`) ساعتِ پیش از پایان را
 *  هنوز «دیروز» می‌شمارند. خودِ قاعده در `core/calendar/ShopDay` است.
 *
 *  مثلِ `LocaleChoice` حالتِ Compose است؛ `load` در `MainActivity` و `Main.kt`ِ کامپیوتر،
 *  کنارِ `LocaleChoice.load`، صدا زده می‌شود.
 *
 *  هفتهٔ دکان (LOC-03) هم همین‌جاست: آغازِ هفته و روزهای تعطیلِ هفتگی، کنارِ پایانِ روز در همان انبار و همان
 *  فهرستِ «مالِ دکان» ِ `ShopSettingsKeys`. تنظیم‌نشده ⇒ `WeekRules()` (شنبه، جمعه تعطیل) = رفتارِ پیشین. قاعده و
 *  سنجشِ مقدار در `core/calendar/ShopWeek`؛ هر صفحهٔ هفته [rules] را می‌گیرد.
 */
object ShopDayChoice {

  private const val PREFS = "tohid-policy"
  private const val DAY_END = "day_end_hour"
  private const val WEEK_START = "week_start"
  private const val CLOSED_DAYS = "closed_days"

  /** ساعتِ پایانِ روزِ کاری پس از نیمه‌شب — یکی از `ShopDay.CHOICES` */
  var hours by mutableStateOf(ShopDay.OFF)
    private set

  /** آغازِ هفته به شمارِ ISO — یکی از `ShopWeek.STARTS` */
  var weekStart by mutableStateOf(ShopWeek.DEFAULT_START)
    private set

  /** روزهای تعطیلِ هفتگی به شمارِ ISO؛ تهی = هر هفت روز باز */
  var closedDays by mutableStateOf(ShopWeek.DEFAULT_CLOSED)
    private set

  /** قاعدهٔ هفتهٔ همین دکان برای `BusinessDay` و هر صفحهٔ هفته */
  val rules: WeekRules get() = WeekRules(weekStart = weekStart, weekend = closedDays)

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun load(context: Context) {
    val p = runCatching { prefs(context) }.getOrNull() ?: return
    hours = ShopDay.clamp(runCatching { p.getInt(DAY_END, ShopDay.OFF) }.getOrDefault(ShopDay.OFF))
    weekStart = ShopWeek.clampStart(runCatching { p.getInt(WEEK_START, ShopWeek.DEFAULT_START) }.getOrDefault(ShopWeek.DEFAULT_START))
    closedDays = ShopWeek.parseClosed(runCatching { p.getString(CLOSED_DAYS, null) }.getOrNull())
  }

  fun set(context: Context, value: Int) {
    hours = ShopDay.clamp(value)
    prefs(context).edit().putInt(DAY_END, hours).apply()
  }

  fun setWeekStart(context: Context, value: Int) {
    weekStart = ShopWeek.clampStart(value)
    prefs(context).edit().putInt(WEEK_START, weekStart).apply()
  }

  /** روزهای تعطیل؛ آخرین روزِ کاری را `ShopWeek.toggle` نگه می‌دارد، این‌جا همان سنجشِ خواندن */
  fun setClosedDays(context: Context, days: Set<Int>) {
    val text = ShopWeek.encodeClosed(days)
    closedDays = ShopWeek.parseClosed(text)
    prefs(context).edit().putString(CLOSED_DAYS, text).apply()
  }
}
