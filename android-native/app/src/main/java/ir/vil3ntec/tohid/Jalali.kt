package ir.vil3ntec.tohid

import ir.vil3ntec.tohid.util.PERSIAN_MONTHS
import ir.vil3ntec.tohid.util.faDigits

/**
 *  تبدیلِ تاریخ میلادی به خورشیدی.
 *
 *  نسخهٔ وب تاریخ‌ها را با `toLocaleDateString('fa-IR')` نشان می‌داد، یعنی
 *  تقویمِ خورشیدی. تاریخ در فایلِ داده میلادی ذخیره می‌شود (`2026-08-28`)
 *  ولی چیزی که فروشنده می‌بیند باید خورشیدی باشد — وگرنه فاکتورِ امروز
 *  تاریخِ ناآشنا دارد.
 *
 *  الگوریتم همان الگوریتمِ شناخته‌شدهٔ jalaali است (بر پایهٔ تقویمِ رسمی با
 *  جدولِ «شکست»‌ها، نه تقریبِ ۳۳ ساله). درستی‌اش با ۱۷۰ سال روزِ پیاپی
 *  سنجیده شده: هیچ روزی جا نمی‌افتد و هیچ روزی دوبار نمی‌آید.
 *
 *  در کاتلین `/` روی عددِ صحیح به سمتِ صفر بریده می‌شود و `%` هم علامتِ
 *  مقسوم را می‌گیرد — دقیقاً همان `div` و `mod` الگوریتمِ اصلی.
 */
object Jalali {

  private val BREAKS = intArrayOf(
    -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
    1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178,
  )

  data class Date(val year: Int, val month: Int, val day: Int)

  private data class Cal(val leap: Int, val gy: Int, val march: Int)

  private fun cal(jy: Int): Cal {
    val gy = jy + 621
    var leapJ = -14
    var jp = BREAKS[0]
    var jump = 0

    require(jy >= jp && jy < BREAKS[BREAKS.size - 1]) { "سالِ خورشیدیِ خارج از دامنه: $jy" }

    for (i in 1 until BREAKS.size) {
      val jm = BREAKS[i]
      jump = jm - jp
      if (jy < jm) break
      leapJ += (jump / 33) * 8 + (jump % 33) / 4
      jp = jm
    }
    var n = jy - jp

    leapJ += (n / 33) * 8 + ((n % 33) + 3) / 4
    if (jump % 33 == 4 && jump - n == 4) leapJ += 1

    val leapG = gy / 4 - ((gy / 100 + 1) * 3) / 4 - 150
    val march = 20 + leapJ - leapG

    if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33
    var leap = (((n + 1) % 33) - 1) % 4
    if (leap == -1) leap = 4

    return Cal(leap, gy, march)
  }

  /** شمارهٔ روزِ مطلق برای یک تاریخِ میلادی */
  private fun g2d(gy: Int, gm: Int, gd: Int): Int {
    var d = ((gy + (gm - 8) / 6 + 100100) * 1461) / 4 +
      (153 * ((gm + 9) % 12) + 2) / 5 + gd - 34840408
    d -= ((gy + 100100 + (gm - 8) / 6) / 100 * 3) / 4 - 752
    return d
  }

  private fun d2gYear(jdn: Int): Int {
    var j = 4 * jdn + 139361631
    j += (((4 * jdn + 183187720) / 146097) * 3) / 4 * 4 - 3908
    val i = ((j % 1461) / 4) * 5 + 308
    val gm = ((i / 153) % 12) + 1
    return j / 1461 - 100100 + (8 - gm) / 6
  }

  fun of(gy: Int, gm: Int, gd: Int): Date {
    val jdn = g2d(gy, gm, gd)
    val gYear = d2gYear(jdn)
    var jy = gYear - 621
    val c = cal(jy)
    var k = jdn - g2d(gYear, 3, c.march)

    if (k >= 0) {
      if (k <= 185) return Date(jy, 1 + k / 31, k % 31 + 1)
      k -= 186
    } else {
      jy -= 1
      k += 179
      if (c.leap == 1) k += 1
    }
    return Date(jy, 7 + k / 30, k % 30 + 1)
  }

  /* ─────────── راهِ برگشت: خورشیدی → میلادی ─────────── */

  /**
   *  شمارهٔ روزِ مطلق برای یک تاریخِ خورشیدی — عکسِ `of`.
   *
   *  تا امروز فقط راهِ رفت بود (میلادی → خورشیدی)، چون تاریخ همیشه از
   *  فایل می‌آمد و فقط نشان داده می‌شد. حالا کاربر هم تاریخ **می‌نویسد**،
   *  و آنچه می‌نویسد خورشیدی است.
   */
  private fun j2d(jy: Int, jm: Int, jd: Int): Int {
    val c = cal(jy)
    return g2d(c.gy, 3, c.march) + (jm - 1) * 31 - (jm / 7) * (jm - 7) + jd - 1
  }

  private fun d2g(jdn: Int): Triple<Int, Int, Int> {
    var j = 4 * jdn + 139361631
    j += (((4 * jdn + 183187720) / 146097) * 3) / 4 * 4 - 3908
    val i = ((j % 1461) / 4) * 5 + 308
    val gd = ((i % 153) / 5) + 1
    val gm = ((i / 153) % 12) + 1
    val gy = j / 1461 - 100100 + (8 - gm) / 6
    return Triple(gy, gm, gd)
  }

  /** آیا این سه عدد اصلاً یک روزِ خورشیدیِ درست‌اند */
  fun isValid(jy: Int, jm: Int, jd: Int): Boolean {
    if (jm !in 1..12 || jd < 1) return false
    val max = when {
      jm <= 6 -> 31
      jm <= 11 -> 30
      else -> if (runCatching { cal(jy).leap == 0 }.getOrDefault(false)) 30 else 29
    }
    return jd <= max
  }

  /**
   *  تاریخِ خورشیدی به همان `YYYY-MM-DD`ِ میلادی که در فایل ذخیره می‌شود.
   *  اگر تاریخ درست نباشد، `null`.
   */
  fun toIso(jy: Int, jm: Int, jd: Int): String? = runCatching {
    if (!isValid(jy, jm, jd)) return null
    val (gy, gm, gd) = d2g(j2d(jy, jm, jd))
    "%04d-%02d-%02d".format(gy, gm, gd)
  }.getOrNull()

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
      return "%04d-%02d-%02d".format(a, b, c)
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
  //  «۱۴ سنبله ۱۴۰۵»، نه «1405/06/14». عددِ ماه را باید در ذهن به نام
  //  ترجمه کرد؛ نام را نه. منطقِ تبدیل همان است، فقط شکلِ نمایش عوض شد.
  val name = JALALI_MONTHS.getOrNull(j.month - 1) ?: return iso
  return "${j.day} $name ${j.year}".faDigits()
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

/** تاریخِ امروزِ دستگاه به شکلِ `YYYY-MM-DD` — همان `todayISO` نسخهٔ وب */
fun todayIso(): String {
  val c = java.util.Calendar.getInstance()
  val y = c.get(java.util.Calendar.YEAR)
  val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
  val d = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
  return "$y-$m-$d"
}

/** نامِ ماه‌های خورشیدی — برای برچسبِ ماه، نه برای تاریخِ کامل */
val JALALI_MONTHS = PERSIAN_MONTHS

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
  return "$name ${j.year}".faDigits()
}
