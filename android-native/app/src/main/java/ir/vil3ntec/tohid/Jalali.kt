package ir.vil3ntec.tohid

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.calendar.ShopDay
import ir.vil3ntec.tohid.core.calendar.SolarHijri
import java.util.Calendar

/**
 *  تبدیلِ تاریخ میلادی به خورشیدی.
 *
 *  نسخهٔ وب تاریخ‌ها را با `toLocaleDateString('fa-IR')` نشان می‌داد، یعنی
 *  تقویمِ خورشیدی. تاریخ در فایلِ داده میلادی ذخیره می‌شود (`2026-08-28`)
 *  ولی چیزی که فروشنده می‌بیند باید خورشیدی باشد — وگرنه فاکتورِ امروز
 *  تاریخِ ناآشنا دارد.
 *
 *  حالا فقط پوسته است: حساب در `core/calendar` (`SolarHijri`، `CivilDays`)
 *  است — همان الگوریتمِ jalaali با جدولِ «شکست»‌ها که پیش‌تر همین‌جا دوباره
 *  نوشته شده بود. `CoreVectorsTest` برابریِ هر روزِ ۱۳۰۰ تا ۱۵۰۰ را پیش از
 *  این جابه‌جایی نشان داده بود و `JalaliTest` هنوز ۱۷۰ سال روزِ پیاپی را
 *  می‌سنجد. نام‌ها و امضاها ماندند تا ده‌ها صدازننده دست نخورند.
 */
object Jalali {

  data class Date(val year: Int, val month: Int, val day: Int)

  /** میلادی ⇒ خورشیدی. مثلِ قبل، تاریخِ میلادیِ نادرست (۳۰ فوریه) جلو می‌غلتد؛ بیرون از دامنه ⇒ استثنا */
  fun of(gy: Int, gm: Int, gd: Int): Date {
    val j = SolarHijri.ofJdn(CivilDays.jdnOf(gy, gm, gd))
    require(j != null) { "سالِ خورشیدیِ خارج از دامنه: $gy-$gm-$gd" }
    return Date(j.year, j.month, j.day)
  }

  /** آیا این سه عدد اصلاً یک روزِ خورشیدیِ درست‌اند */
  fun isValid(jy: Int, jm: Int, jd: Int): Boolean = jd >= 1 && jd <= SolarHijri.monthLength(jy, jm)

  /**
   *  تاریخِ خورشیدی به همان `YYYY-MM-DD`ِ میلادی که در فایل ذخیره می‌شود.
   *  اگر تاریخ درست نباشد، `null`. رقم همیشه ASCII است (LOC-05).
   */
  fun toIso(jy: Int, jm: Int, jd: Int): String? = SolarHijri.fromSolar(jy, jm, jd)

  /**
   *  `YYYY-MM-DD` همیشه با رقمِ لاتین (LOC-05: ذخیرهٔ ASCII).
   *
   *  ⛔ `"%04d".format(…)` بی Locale رقم را به زبانِ دستگاه می‌نویسد: روی
   *  گوشی یا ویندوزی که زبانش دری/پشتو/فارسی است، `۲۰۲۶-۰۳-۲۱` در فایلِ
   *  داده می‌نشست — وب و سرور آن را تاریخ نمی‌شناسند و مقایسهٔ رشته‌ایِ
   *  بازهٔ گزارش هم به‌هم می‌ریخت.
   */
  private fun isoText(y: Int, m: Int, d: Int): String =
    String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", y, m, d)

  /**
   *  آنچه کاربر نوشته، به تاریخِ فایل.
   *
   *  `1405/06/06`، `1405-6-6`، `۱۴۰۵/۰۶/۰۶` — همه یکی‌اند. اگر عدد چهار
   *  رقمیِ اول بزرگ‌تر از ۱۷۰۰ باشد، خودِ کاربر میلادی نوشته و همان
   *  برگردانده می‌شود.
   */
  fun parseTyped(text: String): String? {
    val parts = ir.vil3ntec.tohid.latinDigits(text.trim())
      .split('/', '-', '.', ' ')
      .filter { it.isNotBlank() }
    if (parts.size != 3) return null
    val a = parts[0].toIntOrNull() ?: return null
    val b = parts[1].toIntOrNull() ?: return null
    val c = parts[2].toIntOrNull() ?: return null
    //  کسی که میلادی نوشته، میلادی می‌خواهد
    if (a >= 1700) {
      if (b !in 1..12 || c !in 1..31) return null
      return isoText(a, b, c)
    }
    return toIso(a, b, c)
  }

  /** از تاریخِ ذخیره‌شده در فایلِ داده: `YYYY-MM-DD` */
  fun ofIso(iso: String): Date? {
    val parts = iso.split('-')
    if (parts.size != 3) return null
    val y = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    val d = parts[2].toIntOrNull() ?: return null
    return runCatching { of(y, m, d) }.getOrNull()
  }
}

/**
 * تاریخِ خورشیدی، با رقمِ لاتین: `1405/06/06`. اگر تاریخ خوانده نشد،
 * خودِ رشته برگردانده می‌شود تا چیزی گم نشود.
 */
fun formatDate(iso: String): String {
  val j = Jalali.ofIso(iso) ?: return iso
  val mm = j.month.toString().padStart(2, '0')
  val dd = j.day.toString().padStart(2, '0')
  return "${j.year}/$mm/$dd"
}

/**
 *  رشتهٔ `YYYY-MM-DD` به ساعتِ میلی‌ثانیه‌ای — نیمروزِ همان روز.
 *
 *  نیمروز، نه نیمه‌شب: تراکنش‌ها فقط روز دارند و اگر ساعتِ صفر گرفته
 *  شوند، اختلافِ منطقهٔ زمانی می‌تواند روز را یکی عقب ببرد و «۱ روز
 *  پیش» به «امروز» تبدیل شود.
 *
 *  خواندنی نبود؟ صفر برمی‌گردد — صدازننده خودش تصمیم می‌گیرد.
 */
fun isoMillis(iso: String): Long {
  val parts = iso.trim().split('-')
  if (parts.size < 3) return 0L
  val y = parts[0].toIntOrNull() ?: return 0L
  val m = parts[1].toIntOrNull() ?: return 0L
  val d = parts[2].take(2).toIntOrNull() ?: return 0L
  return runCatching {
    java.util.Calendar.getInstance().apply {
      clear()
      set(y, m - 1, d, 12, 0, 0)
    }.timeInMillis
  }.getOrDefault(0L)
}

/**
 *  ساعتِ خام به تاریخِ خورشیدی.
 *
 *  چیزهایی که از سرور می‌آیند — پایانِ اشتراک، روزِ ساختنِ حساب — ساعتِ
 *  میلی‌ثانیه‌ای‌اند، نه رشتهٔ `YYYY-MM-DD`. تبدیل یک جا انجام می‌شود تا
 *  هر صفحه از نو تقویم نسازد.
 */
fun formatMillis(at: Long): String {
  if (at <= 0) return "—"
  val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
  val y = c.get(java.util.Calendar.YEAR)
  val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
  val d = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  return formatDate("$y-$m-$d")
}

/**
 *  تاریخِ امروزِ دستگاه به شکلِ `YYYY-MM-DD` — همان `todayISO` نسخهٔ وب.
 *
 *  LOC-03: با «پایانِ روزِ کاری» (`ShopDayChoice`، پیش‌فرض نیمه‌شب) ساعتِ پیش از
 *  پایان هنوز دیروز است — تاریخِ ثبتِ فروش، برگشت و شیفت از همین‌جا می‌آید.
 *  نیمه‌شب ⇒ همان تاریخِ تقویمیِ دستگاه، بی هیچ جابه‌جایی.
 */
fun todayIso(): String = todayIsoAt(Calendar.getInstance(), ShopDayChoice.hours)

/** همان `todayIso` برای یک لحظهٔ معین و یک پایانِ روزِ کاری (آزمون‌پذیر) */
internal fun todayIsoAt(now: Calendar, dayEndHours: Int): String {
  val c = now.clone() as Calendar
  val shift = ShopDay.dayShift(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), dayEndHours)
  if (shift != 0) c.add(Calendar.DAY_OF_MONTH, shift)
  val y = c.get(Calendar.YEAR)
  val m = (c.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
  val d = c.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  return "$y-$m-$d"
}

/**
 *  نامِ ماه‌های خورشیدی — برای برچسبِ ماه، نه برای تاریخِ کامل.
 *
 *  دری یا پشتو، به انتخابِ دکان (`LocaleChoice.months`، LOC-01)؛ پیش‌فرض دری،
 *  همان فهرستِ امروز. خواندنش در صفحه، حالتِ Compose را می‌خواند، پس با
 *  عوض‌شدنِ کلید برچسب‌ها هم بی بازکردنِ دوباره عوض می‌شوند.
 */
val JALALI_MONTHS: List<String>
  get() = ir.vil3ntec.tohid.core.i18n.I18n.solarMonths(LocaleChoice.lang, LocaleChoice.months)

/**
 *  تاریخ برای **نمایش**، به زبان و تقویمِ انتخاب‌شده: فارسی و پشتو (تقویمِ
 *  خودکار) دقیقاً همان `formatDate`؛ انگلیسی `2026-10-20`. رقمِ ASCII است و
 *  رقمِ فارسی را فونت می‌کشد. ⛔ برای CSV، یادداشتِ ذخیره‌شده و کادرِ ویرایش
 *  همان `formatDate` بماند — داده نباید با زبانِ صفحه عوض شود.
 */
fun displayDate(iso: String): String {
  val lang = LocaleChoice.lang
  val calendar = LocaleChoice.calendar
  return if (ir.vil3ntec.tohid.core.i18n.I18n.gregorian(lang, calendar))
    ir.vil3ntec.tohid.core.i18n.I18n.dateNumeric(iso, lang, calendar)
  else formatDate(iso)
}

/**
 *  برچسبِ یک ماه: از `2026-09` به «سنبله ۱۴۰۵».
 *
 *  ماهِ ذخیره‌شده در فایلِ داده میلادی است (همان `date.take(7)`) و یک
 *  ماهِ میلادی روی دو ماهِ خورشیدی می‌افتد. پانزدهمِ ماه گرفته می‌شود تا
 *  برچسب همان ماهی باشد که بیشترِ روزهایش در آن است — همان قاعده‌ای که
 *  صفحهٔ مصارف از اول داشت.
 */
fun formatMonth(month: String): String {
  val j = Jalali.ofIso("$month-15") ?: return month
  val name = JALALI_MONTHS.getOrNull(j.month - 1) ?: return month
  return "$name ${j.year}"
}
