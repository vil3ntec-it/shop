package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سنجشِ قالبِ کدِ شاگرد.
 *
 *  ── آزمونی که جای آزمونِ قبلی را گرفت ───────────────────────────────
 *  آزمونِ قبلی می‌سنجید که کدِ ساختهٔ **خودِ برنامه** با الگوی خودِ
 *  برنامه بخواند — و همیشه هم می‌خواند. همان حلقهٔ بسته باعث شد کسی
 *  نفهمد الگو با کدی که **سرور** می‌سازد نمی‌خواند: سرور سه دستهٔ
 *  چهارتایی می‌دهد و الگو پنج‌تایی می‌خواست، پس برنامه کدِ درست را هم
 *  پیش از فرستادن رد می‌کرد.
 *
 *  حالا آزمون از بیرون می‌سنجد: هر دو قالبی که در عمل وجود دارند —
 *  ۴-۴-۴ (سرور) و ۵-۵-۵ (نسخهٔ وب) — باید پذیرفته شوند.
 *  ──────────────────────────────────────────────────────────────────
 */
class StaffCodeTest {

  @Test
  fun `کد چهارتاییِ سرور پذیرفته می‌شود`() {
    assertTrue(StaffCode.looksValid("SHG-8F29-KD72-PL5T"))
  }

  @Test
  fun `کد پنج‌تاییِ نسخه وب پذیرفته می‌شود`() {
    assertTrue(StaffCode.looksValid("SHG-GNFJX-T8FTA-X6SA3"))
  }

  @Test
  fun `حرف کوچک و فاصله مانع نمی‌شود`() {
    assertTrue(StaffCode.looksValid(" shg-8f29-kd72-pl5t "))
    assertEquals("SHG-8F29-KD72-PL5T", StaffCode.clean(" shg-8f29 -kd72-pl5t "))
  }

  @Test
  fun `زیرخط به خط تیره تبدیل می‌شود`() {
    //  کدی که با پیام‌رسان می‌آید گاهی خط تیره‌اش زیرخط شده
    assertTrue(StaffCode.looksValid("SHG_8F29_KD72_PL5T"))
  }

  @Test
  fun `چیزهای بی‌ربط رد می‌شوند`() {
    listOf(
      "",
      "SHG",
      "SHG-8F29",
      "SHG-8F29-KD72",
      "SHG-8F29-KD72-PL5T-EXTRA",
      "TSH-8F29-KD72-PL5T",
      "SHG-8F2-KD72-PL5T",
      "SHG-8F291-KD721-PL5T1-",
    ).forEach { assertFalse(it, StaffCode.looksValid(it)) }
  }

  @Test
  fun `نمونه راهنما خودش قالب درستی دارد`() {
    //  متنِ راهنما به کاربر نشان داده می‌شود؛ اگر خودش قالبِ درست نباشد،
    //  کاربر را به کدِ غلط راهنمایی می‌کند
    assertTrue(StaffCode.HINT, StaffCode.looksValid(StaffCode.HINT.replace("X", "A")))
  }
}
