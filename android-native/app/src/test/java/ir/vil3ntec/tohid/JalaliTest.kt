package ir.vil3ntec.tohid

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  تاریخِ خورشیدی.
 *
 *  اگر این تبدیل یک روز بلغزد، تاریخِ روی فاکتور و گزارشِ «فروش امروز»
 *  اشتباه می‌شود — چیزی که به‌سختی به چشم می‌آید و به‌سختی جبران می‌شود.
 *  پس هم چند تاریخِ شناخته‌شده سنجیده می‌شود، هم پیوستگیِ همهٔ روزها.
 */
class JalaliTest {

  private fun j(y: Int, m: Int, d: Int) = Jalali.of(y, m, d).let { "${it.year}/${it.month}/${it.day}" }

  @Test
  fun `تاریخ های شناخته شده درست تبدیل می شوند`() {
    assertEquals("1357/11/22", j(1979, 2, 11))    // ۲۲ بهمن ۵۷
    assertEquals("1378/10/11", j(2000, 1, 1))
    assertEquals("1403/1/1", j(2024, 3, 20))      // نوروز ۱۴۰۳
    assertEquals("1403/12/30", j(2025, 3, 20))    // ۱۴۰۳ کبیسه است
    assertEquals("1404/12/29", j(2026, 3, 20))    // ۱۴۰۴ کبیسه نیست
    assertEquals("1405/1/1", j(2026, 3, 21))      // نوروز ۱۴۰۵
    assertEquals("1405/6/6", j(2026, 8, 28))
  }

  @Test
  fun `هیچ روزی جا نمی افتد و هیچ روزی دوبار نمی آید`() {
    val calendar = java.util.GregorianCalendar(1930, 0, 1)
    val end = java.util.GregorianCalendar(2100, 0, 1)
    var previous: Jalali.Date? = null

    while (calendar.before(end)) {
      val current = Jalali.of(
        calendar.get(java.util.Calendar.YEAR),
        calendar.get(java.util.Calendar.MONTH) + 1,
        calendar.get(java.util.Calendar.DAY_OF_MONTH),
      )
      previous?.let { p ->
        val stepped = (current.year == p.year && current.month == p.month && current.day == p.day + 1) ||
          (current.year == p.year && current.month == p.month + 1 && current.day == 1) ||
          (current.year == p.year + 1 && current.month == 1 && current.day == 1)
        if (!stepped) {
          throw AssertionError("پرشِ تاریخ: $p → $current در ${calendar.time}")
        }
      }
      previous = current
      calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
    }
  }

  @Test
  fun `تاریخ برای نمایش با رقم لاتین و دو رقمی نوشته می شود`() {
    assertEquals("1405/06/06", formatDate("2026-08-28"))
    assertEquals("1405/01/01", formatDate("2026-03-21"))
    // تاریخِ ناخوانا گم نمی‌شود — خودش برگردانده می‌شود
    assertEquals("چیزی-نیست", formatDate("چیزی-نیست"))
  }

  @Test
  fun `تاریخ امروز شکل درستی دارد`() {
    val today = todayIso()
    assert(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(today)) { "شکلِ تاریخ درست نیست: $today" }
  }

  /* ═══════════════ راهِ برگشت: خورشیدی → میلادی ═══════════════ */

  /*
   *  کادرِ تاریخ راهنمای `۱۴۰۵/۰۶/۰۶` نشان می‌داد ولی میلادی می‌خواست و
   *  اسلش را دور می‌ریخت — کسی که همان را می‌زد، رشتهٔ بی‌معنیِ
   *  `14050606` ذخیره می‌کرد و آن ردیف بی‌صدا از هر گزارشِ بازه‌ای بیرون
   *  می‌افتاد. حالا کادر خورشیدی می‌گیرد؛ این آزمون‌ها همان تبدیل را
   *  می‌سنجند.
   */

  @Test
  fun `رفت و برگشت، هر روزِ ده سال را سرِ جایش برمی‌گرداند`() {
    var iso = "2020-01-01"
    var checked = 0
    repeat(3650) {
      val j = Jalali.ofIso(iso)!!
      val back = Jalali.toIso(j.year, j.month, j.day)
      org.junit.Assert.assertEquals("روزِ $iso برنگشت", iso, back)
      checked++
      iso = nextDay(iso)
    }
    org.junit.Assert.assertEquals(3650, checked)
  }

  @Test
  fun `تاریخِ تایپ‌شده با اسلش و خط‌تیره و رقمِ فارسی، یکی خوانده می‌شود`() {
    val expected = Jalali.toIso(1405, 6, 6)
    org.junit.Assert.assertEquals(expected, Jalali.parseTyped("1405/06/06"))
    org.junit.Assert.assertEquals(expected, Jalali.parseTyped("1405-6-6"))
    org.junit.Assert.assertEquals(expected, Jalali.parseTyped("۱۴۰۵/۰۶/۰۶"))
    org.junit.Assert.assertEquals(expected, Jalali.parseTyped("  1405 / 6 / 6  "))
  }

  @Test
  fun `کسی که میلادی نوشته، میلادی می‌گیرد`() {
    org.junit.Assert.assertEquals("2026-09-01", Jalali.parseTyped("2026-09-01"))
  }

  @Test
  fun `تاریخِ بی‌معنی خوانده نمی‌شود`() {
    org.junit.Assert.assertNull(Jalali.parseTyped("14050606"))
    org.junit.Assert.assertNull(Jalali.parseTyped("1405/13/01"))
    org.junit.Assert.assertNull(Jalali.parseTyped("1405/07/31"))
    org.junit.Assert.assertNull(Jalali.parseTyped(""))
    org.junit.Assert.assertNull(Jalali.parseTyped("سلام"))
  }

  @Test
  fun `شش ماهِ اول سی‌ویک روز دارند و شش ماهِ دوم سی`() {
    (1..6).forEach { org.junit.Assert.assertTrue(Jalali.isValid(1405, it, 31)) }
    (7..11).forEach {
      org.junit.Assert.assertTrue(Jalali.isValid(1405, it, 30))
      org.junit.Assert.assertFalse(Jalali.isValid(1405, it, 31))
    }
  }

  @Test
  fun `برچسبِ ماه از ماهِ میلادی در می‌آید`() {
    //  پانزدهمِ ماه گرفته می‌شود، پس برچسب همان ماهی است که بیشترِ
    //  روزهایش در آن است
    val label = ir.vil3ntec.tohid.formatMonth("2026-09")
    org.junit.Assert.assertTrue("برچسبِ نامنتظر: $label", label.contains("۱۴۰۵") || label.contains("1405"))
  }

  /** روزِ بعدِ یک تاریخِ میلادی */
  private fun nextDay(iso: String): String {
    val parts = iso.split('-').map { it.toInt() }
    val c = java.util.Calendar.getInstance().apply {
      clear()
      set(parts[0], parts[1] - 1, parts[2])
      add(java.util.Calendar.DAY_OF_MONTH, 1)
    }
    return "%04d-%02d-%02d".format(
      c.get(java.util.Calendar.YEAR),
      c.get(java.util.Calendar.MONTH) + 1,
      c.get(java.util.Calendar.DAY_OF_MONTH),
    )
  }
}
