package ir.vil3ntec.tohid.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  بارکدخوانِ سخت‌افزاری — بی گوشی و بی بارکدخوان.
 *
 *  کلِ تصمیمِ این لایه زمان‌محور است: «آیا این را دستگاه زده یا آدم؟»
 *  و زمان دقیقاً همان چیزی است که اینجا می‌شود ساختگی داد. پس همان
 *  منطقی که روی پیشخانِ دکان اجرا می‌شود، همین‌جا سنجیده می‌شود.
 */
class HidScanTest {

  private fun scan(text: String, gapMs: Long, start: Long = 1_000): Pair<HidScan, String?> {
    val hid = HidScan()
    var t = start
    for (ch in text) {
      hid.feed(ch, t)
      t += gapMs
    }
    return hid to hid.submit(t)
  }

  @Test
  fun `بارکدخوان تند می‌فرستد و کد کامل بیرون می‌آید`() {
    val (_, code) = scan("6260100120014", gapMs = 8)
    assertEquals("6260100120014", code)
  }

  @Test
  fun `تایپِ آدم بارکد حساب نمی‌شود`() {
    //  صد و پنجاه میلی‌ثانیه به ازای هر نویسه — تندنویسِ خوب
    val (_, code) = scan("6260100120014", gapMs = 150)
    assertNull("تایپِ آدم نباید اسکن شمرده شود", code)
  }

  @Test
  fun `کدِ خیلی کوتاه رد می‌شود`() {
    val (_, code) = scan("12", gapMs = 8)
    assertNull(code)
  }

  @Test
  fun `Enterِ دیرهنگام، اسکن را باطل می‌کند`() {
    val hid = HidScan()
    var t = 1_000L
    for (ch in "6260100120014") { hid.feed(ch, t); t += 8 }
    //  رقم‌ها تند آمدند ولی Enter نیم ثانیه بعد — یعنی آدم خودش زده
    assertNull(hid.submit(t + 500))
  }

  @Test
  fun `دو اسکنِ پشتِ سرِ هم قاطی نمی‌شوند`() {
    val hid = HidScan()
    var t = 1_000L
    for (ch in "1111111111") { hid.feed(ch, t); t += 8 }
    assertEquals("1111111111", hid.submit(t))

    //  اسکنِ دوم، یک ثانیه بعد
    t += 1_000
    for (ch in "2222222222") { hid.feed(ch, t); t += 8 }
    assertEquals("2222222222", hid.submit(t))
  }

  @Test
  fun `نویسه‌های جامانده از قبل، اسکنِ تازه را خراب نمی‌کنند`() {
    val hid = HidScan()
    var t = 1_000L
    //  نیمه‌کاره: کاربر چیزی زده و رها کرده
    for (ch in "999") { hid.feed(ch, t); t += 8 }

    //  دو ثانیه بعد، اسکنِ واقعی
    t += 2_000
    for (ch in "6260100120014") { hid.feed(ch, t); t += 8 }
    assertEquals(
      "نیمهٔ قبلی باید دور ریخته شده باشد",
      "6260100120014", hid.submit(t)
    )
  }

  @Test
  fun `busy فقط وسطِ اسکن درست است`() {
    val hid = HidScan()
    assertFalse(hid.busy)
    hid.feed('6', 1_000)
    assertTrue("وسطِ اسکن", hid.busy)
    hid.submit(1_008)
    assertFalse("بعد از Enter دیگر نه", hid.busy)
  }

  @Test
  fun `reset هرچه نیمه‌کاره مانده را می‌برد`() {
    val hid = HidScan()
    hid.feed('6', 1_000)
    hid.feed('2', 1_008)
    hid.reset()
    assertFalse(hid.busy)
    //  و بافر واقعاً خالی است، نه اینکه فقط پرچم عوض شده باشد
    var t = 2_000L
    for (ch in "6260100120014") { hid.feed(ch, t); t += 8 }
    assertEquals("6260100120014", hid.submit(t))
  }

  @Test
  fun `حرف و رقم هر دو پذیرفته‌اند — کدهای داخلیِ دکان عددی نیستند`() {
    val (_, code) = scan("ABC-9911", gapMs = 8)
    assertEquals("ABC-9911", code)
  }
}
