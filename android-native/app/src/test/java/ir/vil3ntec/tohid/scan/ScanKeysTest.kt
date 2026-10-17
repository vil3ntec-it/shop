package ir.vil3ntec.tohid.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 *  F0-06 — بارکدخوان وقتی چیدمانِ ویندوز/اندروید فارسی یا پشتو است.
 *
 *  بارکدخوان «کلید» می‌فرستد، نه «نویسه»: کلیدِ ۶ِ ردیفِ بالا. سیستم با
 *  چیدمانِ فارسی/پشتو از آن «۶» می‌سازد و از کلیدِ A «ش». پیش از این
 *  پنجرهٔ کامپیوتر فقط نویسهٔ ASCII ۳۳ تا ۱۲۶ را برمی‌داشت، پس اسکن
 *  بی‌صدا گم می‌شد. حالا وقتی نویسه ASCII نیست، خودِ کلیدِ فیزیکی خوانده
 *  می‌شود.
 *
 *  هر سطر: (رقمِ کلید، حرفِ کلید، Shift، نویسه‌ای که چیدمان ساخت).
 */
class ScanKeysTest {

  private fun ch(c: Char) = c.code

  @Test
  fun `ریشهٔ خطا — رقمِ فارسی در بازهٔ قبلی نبود`() {
    assertFalse(ch('۶') in 33..126)
    assertFalse(ch('ش') in 33..126)
  }

  @Test
  fun `چیدمانِ فارسی — ردیفِ رقم`() {
    for (d in 0..9) {
      val fa = '۰' + d
      assertEquals('0' + d, ScanKeys.charOf(digit = d, letter = -1, shift = false, codePoint = fa.code))
    }
  }

  @Test
  fun `چیدمانِ پشتو — همان رقم‌های ۰ تا ۹ و حرف‌های دیگر`() {
    //  پشتو هم رقمِ U+06F0..U+06F9 می‌فرستد
    assertEquals('4', ScanKeys.charOf(digit = 4, letter = -1, shift = false, codePoint = ch('۴')))
    //  کلیدِ Q در پشتو «ض»، کلیدِ C در پشتو «ز»
    assertEquals('Q', ScanKeys.charOf(digit = -1, letter = 'Q' - 'A', shift = true, codePoint = ch('ض')))
    assertEquals('c', ScanKeys.charOf(digit = -1, letter = 'C' - 'A', shift = false, codePoint = ch('ز')))
  }

  @Test
  fun `چیدمانِ فارسی — حرفِ بارکدِ Code128`() {
    //  کلیدِ A در فارسی «ش» است
    assertEquals('A', ScanKeys.charOf(digit = -1, letter = 0, shift = true, codePoint = ch('ش')))
    assertEquals('a', ScanKeys.charOf(digit = -1, letter = 0, shift = false, codePoint = ch('ش')))
  }

  @Test
  fun `رقمِ عربی بی کلیدِ شناخته — از خودِ نویسه`() {
    assertEquals('4', ScanKeys.charOf(digit = -1, letter = -1, shift = false, codePoint = ch('٤')))
    assertEquals('7', ScanKeys.charOf(digit = -1, letter = -1, shift = false, codePoint = ch('۷')))
  }

  @Test
  fun `چیدمانِ انگلیسی مثلِ قبل`() {
    assertEquals('1', ScanKeys.charOf(digit = 1, letter = -1, shift = false, codePoint = ch('1')))
    assertEquals('A', ScanKeys.charOf(digit = -1, letter = 0, shift = true, codePoint = ch('A')))
    //  CapsLock روشن: همان نویسه‌ای که سیستم ساخت، مثلِ پیش
    assertEquals('A', ScanKeys.charOf(digit = -1, letter = 0, shift = false, codePoint = ch('A')))
    assertEquals('-', ScanKeys.charOf(digit = -1, letter = -1, shift = false, codePoint = ch('-')))
    assertEquals('!', ScanKeys.charOf(digit = 1, letter = -1, shift = true, codePoint = ch('!')))
  }

  @Test
  fun `کلیدِ کنترلی و فاصله`() {
    assertNull(ScanKeys.charOf(digit = -1, letter = -1, shift = false, codePoint = 0))
    assertNull(ScanKeys.charOf(digit = -1, letter = -1, shift = false, codePoint = 32))
    assertEquals(' ', ScanKeys.charOf(digit = -1, letter = -1, shift = false, codePoint = 32, allowSpace = true))
    //  Shift+رقم در فارسی «٬» و «٫» … می‌سازد — بارکد نیست
    assertNull(ScanKeys.charOf(digit = 2, letter = -1, shift = true, codePoint = ch('٬')))
  }

  @Test
  fun `یک اسکنِ کامل با چیدمانِ فارسی به همان بارکد می‌رسد`() {
    val hid = HidScan()
    var t = 1_000L
    for (d in "6260100120014") {
      val n = d - '0'
      val c = ScanKeys.charOf(digit = n, letter = -1, shift = false, codePoint = ('۰' + n).code)
      hid.feed(c!!, t)
      t += 8
    }
    assertEquals("6260100120014", hid.submit(t))
  }
}
