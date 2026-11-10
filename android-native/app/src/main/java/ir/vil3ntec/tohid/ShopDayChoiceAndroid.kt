package ir.vil3ntec.tohid

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.calendar.Holidays
import ir.vil3ntec.tohid.core.calendar.ShopDay
import ir.vil3ntec.tohid.core.calendar.ShopWeek
import ir.vil3ntec.tohid.core.calendar.WeekRules
import ir.vil3ntec.tohid.core.net.ApiEndpoints
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.delay

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
 *
 *  تعطیلاتِ رسمی (LOC-04) هم در [rules] اند: جدولِ سرور (`GET /api/calendar/holidays`، بی‌توکن و بی شناسهٔ دستگاه)
 *  روزی یک بار گرفته می‌شود ([keepHolidaysFresh] در پوستهٔ برنامه و `Main.kt` ِ کامپیوتر) و در پروندهٔ جدای
 *  `tohid-holidays` می‌ماند — نسخهٔ محلیِ دادهٔ سرور، نه تنظیمِ دکان (DATA-SAFETY §۶)؛ بی‌شبکه همان نسخهٔ ذخیره‌شده.
 *  هنوز نگرفته یا پاسخِ خراب ⇒ هیچ تعطیلِ رسمی = همان رفتارِ پیشین. خواندن و سنجش در `core/calendar/Holidays`.
 */
object ShopDayChoice {

  private const val PREFS = "tohid-policy"
  private const val DAY_END = "day_end_hour"
  private const val WEEK_START = "week_start"
  private const val CLOSED_DAYS = "closed_days"
  private const val HOLIDAY_PREFS = "tohid-holidays"
  private const val HOLIDAY_TABLE = "table"
  private const val HOLIDAY_AT = "fetched_at"

  /** ساعتِ پایانِ روزِ کاری پس از نیمه‌شب — یکی از `ShopDay.CHOICES` */
  var hours by mutableStateOf(ShopDay.OFF)
    private set

  /** آغازِ هفته به شمارِ ISO — یکی از `ShopWeek.STARTS` */
  var weekStart by mutableStateOf(ShopWeek.DEFAULT_START)
    private set

  /** روزهای تعطیلِ هفتگی به شمارِ ISO؛ تهی = هر هفت روز باز */
  var closedDays by mutableStateOf(ShopWeek.DEFAULT_CLOSED)
    private set

  /** روزهای تعطیلِ رسمیِ جدولِ سرور (LOC-04)؛ تهی تا نخستین گرفتن */
  var holidays by mutableStateOf(emptySet<String>())
    private set

  /** قاعدهٔ هفتهٔ همین دکان برای `BusinessDay` و هر صفحهٔ هفته */
  val rules: WeekRules get() = WeekRules(weekStart = weekStart, weekend = closedDays, holidays = holidays)

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  private fun holidayPrefs(context: Context) =
    context.applicationContext.getSharedPreferences(HOLIDAY_PREFS, Context.MODE_PRIVATE)

  fun load(context: Context) {
    holidays = runCatching { Holidays.parse(holidayPrefs(context).getString(HOLIDAY_TABLE, null)).dates }.getOrDefault(emptySet())
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

  /**
   *  LOC-04 — جدولِ تعطیلاتِ سرور، فقط اگر روزی گذشته (`Holidays.due`؛ ساعتِ عقب‌رفته هم). پاسخِ درست ⇒ همان دم در
   *  [rules] و در `tohid-holidays`؛ بی‌شبکه، خطا یا پاسخِ بدشکل ⇒ نسخهٔ ذخیره‌شده دست نمی‌خورد و نوبتِ بعد دوباره.
   *  هرگز پرتاب نمی‌کند. ⇒ آیا جدولِ تازه نشست.
   */
  suspend fun refreshHolidays(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
    val p = runCatching { holidayPrefs(context) }.getOrNull() ?: return false
    if (!Holidays.due(runCatching { p.getLong(HOLIDAY_AT, 0L) }.getOrDefault(0L), nowMs)) return false
    val body = runCatching { Backend.api(context).getPublic(ApiEndpoints.Calendar.HOLIDAYS, anonymous = true) }.getOrNull()
      ?: return false
    val table = Holidays.of(body) ?: return false
    p.edit().putString(HOLIDAY_TABLE, Holidays.encode(table)).putLong(HOLIDAY_AT, nowMs).apply()
    holidays = table.dates
    return true
  }

  /** تا برنامه باز است: هر ساعت می‌سنجد و روزی یک بار می‌گیرد (`LaunchedEffect` ِ پوسته؛ با بستنِ صفحه می‌ایستد) */
  suspend fun keepHolidaysFresh(context: Context) {
    while (true) {
      refreshHolidays(context)
      delay(Holidays.CHECK_MS)
    }
  }
}
