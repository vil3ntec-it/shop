package ir.vil3ntec.tohid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  F0-06 — رقمِ فارسی در کادرِ عدد.
 *
 *  پیش از این، «دریافتیِ همین حالا» و «تخفیف (٪)» در صندوقِ کامپیوتر و
 *  پنجرهٔ تسویهٔ گوشی نویسه را با `isDigit()` نگه می‌داشتند — که «۵» را
 *  هم رقم می‌داند — و بعد `toDoubleOrNull() ?: 0.0` آن را **صفر**
 *  می‌خواند. فروشنده «۵۰۰» می‌دید و صفر ثبت می‌شد.
 */
class NumberInputTest {

  @Test
  fun `ریشهٔ خطا — مسیرِ قبلی رقمِ فارسی را صفر می‌خواند`() {
    //  همان دو خطِ قبلیِ DeskSale/SaleDialogs، بی هیچ تغییر
    val kept = "۵۰۰".filter { c -> c.isDigit() || c == '.' }
    assertEquals("کادر «۵۰۰» را نگه می‌داشت", "۵۰۰", kept)
    assertEquals("ولی صفر حساب می‌شد", 0.0, kept.toDoubleOrNull() ?: 0.0, 0.0)
  }

  @Test
  fun `پالایشِ کادر رقمِ فارسی و عربی را لاتین می‌کند`() {
    assertEquals("500", numericInput("۵۰۰"))
    assertEquals("500", numericInput("٥٠٠"))
    assertEquals("1.5", numericInput("۱٫۵"))
    assertEquals("1250.75", numericInput("۱۲۵۰.۷۵"))
    //  حرف و نشانهٔ دیگر بیرون می‌ماند
    assertEquals("12", numericInput("1a2؋ "))
  }

  @Test
  fun `عددِ نوشته‌شده درست خوانده می‌شود`() {
    assertEquals(500.0, parseNumber("۵۰۰")!!, 0.0)
    assertEquals(500.0, parseNumber("٥٠٠")!!, 0.0)
    assertEquals(1.5, parseNumber("۱٫۵")!!, 0.0)
    assertEquals(500.0, parseNumber(numericInput("۵۰۰"))!!, 0.0)
    assertEquals(1.5, parseNumber(numericInput("۱٫۵"))!!, 0.0)
    assertEquals(42.0, parseNumber(" 42 ")!!, 0.0)
  }

  @Test
  fun `عددِ نامعتبر هرگز صفر نمی‌شود`() {
    assertNull(parseNumber("1.2.3"))
    assertNull(parseNumber("."))
    assertNull(parseNumber("۵۰۰ تومان"))
    assertNull(parseNumber("NaN"))
    assertNull(parseNumber("Infinity"))
    assertNull("خالی هم عدد نیست؛ صدازننده خودش تصمیم می‌گیرد", parseNumber(""))
    assertTrue(numberInvalid("1.2.3"))
    assertTrue(numberInvalid("."))
    assertFalse("خالی خطا نیست", numberInvalid(""))
    assertFalse(numberInvalid("۵۰۰"))
    assertFalse(numberInvalid("۱٫۵"))
  }

  @Test
  fun `normalizeDigits همان latinDigits است`() {
    assertEquals("0123456789", normalizeDigits("۰۱۲۳۴۵۶۷۸۹"))
    assertEquals("0123456789", normalizeDigits("٠١٢٣٤٥٦٧٨٩"))
    assertEquals("0799123456", normalizeDigits("۰۷۹۹۱۲۳۴۵۶"))
    assertEquals("ABC-12", normalizeDigits("ABC-۱۲"))
    //  یک نویسه به یک نویسه — جای حرف‌ها عوض نمی‌شود
    val s = "۳*برنج ۵ کیلویی"
    assertEquals(s.length, normalizeDigits(s).length)
  }
}
