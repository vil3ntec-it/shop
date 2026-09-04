package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  تمیز کردنِ کدِ پیوستن — و اینکه دیگر قالبی سنجیده نمی‌شود.
 *
 *  ── تاریخچهٔ کوتاهِ یک اشکال ────────────────────────────────────────
 *  اول آزمون می‌سنجید که کدِ ساختهٔ **خودِ برنامه** با الگوی خودِ برنامه
 *  بخواند — حلقه‌ای بسته که هیچ‌وقت چیزی نمی‌گرفت. بعد الگو را با قالبِ
 *  سرور جور کردیم (۴-۴-۴ و ۵-۵-۵).
 *
 *  و باز هم اشتباه بود: سرورِ صاحب مخزن کدِ `F6D75A07` می‌ساخت — نه
 *  `SHG` دارد نه دسته‌بندی. برنامه آن کدِ **زنده** را پیش از رسیدن به
 *  سرور رد می‌کرد و می‌گفت «این کد درست نیست».
 *
 *  درسش: قالبِ کد مالِ سرور است و از سروری به سروری فرق می‌کند. هر
 *  الگویی که گوشی بگذارد، روزی جلوی کدِ درست را می‌گیرد. پس اینجا فقط
 *  تمیز کردن مانده — و آزمون هم همان را می‌سنجد.
 *  ──────────────────────────────────────────────────────────────────
 */
class StaffCodeTest {

  @Test
  fun `کدِ کوتاهِ سرور هم پذیرفته می‌شود`() {
    //  همان کدی که در پنلِ صاحب مخزن ساخته شده بود
    assertTrue(StaffCode.usable("F6D75A07"))
    assertEquals("F6D75A07", StaffCode.clean("f6d75a07"))
  }

  @Test
  fun `کدِ دسته‌بندی‌شده هم پذیرفته می‌شود`() {
    assertTrue(StaffCode.usable("SHG-8F29-KD72-PL5T"))
    assertTrue(StaffCode.usable("SHG-GNFJX-T8FTA-X6SA3"))
  }

  @Test
  fun `حرف کوچک و فاصله مانع نمی‌شود`() {
    assertEquals("SHG-8F29-KD72-PL5T", StaffCode.clean(" shg-8f29 -kd72-pl5t "))
    assertTrue(StaffCode.usable(" shg-8f29-kd72-pl5t "))
  }

  @Test
  fun `زیرخط به خط تیره تبدیل می‌شود`() {
    //  کدی که با پیام‌رسان می‌آید گاهی خط تیره‌اش زیرخط شده
    assertEquals("SHG-8F29-KD72-PL5T", StaffCode.clean("SHG_8F29_KD72_PL5T"))
  }

  @Test
  fun `نویسه‌های نامرئیِ کپی‌شده برداشته می‌شوند`() {
    //  کدی که از واتساپ کپی می‌شود گاهی نیم‌فاصله و نشانهٔ جهت دارد
    assertEquals("F6D75A07", StaffCode.clean("‏F6D7‌5A07 "))
  }

  @Test
  fun `رقمِ فارسی به لاتین برمی‌گردد`() {
    //  کسی که با صفحه‌کلیدِ فارسی «۷» می‌زند، همان ۷ را می‌خواهد
    assertEquals("A7B2", StaffCode.clean("a۷b۲"))
  }

  @Test
  fun `فقط خالی و درازِ بی‌معنی رد می‌شود`() {
    assertFalse("خالی", StaffCode.usable(""))
    assertFalse("خیلی کوتاه", StaffCode.usable("AB"))
    assertFalse("یک متنِ کامل چسبانده شده", StaffCode.usable("A".repeat(60)))
  }
}
