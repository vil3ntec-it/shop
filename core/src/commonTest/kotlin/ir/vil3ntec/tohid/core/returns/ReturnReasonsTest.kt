package ir.vil3ntec.tohid.core.returns

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** RET-08 — فهرستِ قابلِ تنظیمِ دلیل‌های برگشت و سنجشِ دلیلِ انتخاب‌شده */
class ReturnReasonsTest {

  /** فهرستِ خالی یا ناخوانا همان فهرستِ پیش‌فرض است — دلیل همیشه اجباری می‌ماند */
  @Test
  fun emptyListFallsBackToDefault() {
    assertEquals(ReturnReasons.DEFAULT, ReturnReasons.normalize(null))
    assertEquals(ReturnReasons.DEFAULT, ReturnReasons.normalize(emptyList()))
    assertEquals(ReturnReasons.DEFAULT, ReturnReasons.normalize(listOf("", "   ", "\n\t")))
    assertEquals(ReturnReasons.DEFAULT, ReturnReasons.parse(""))
  }

  /** پاک‌سازی — فاصلهٔ دو سر و فاصله‌های پیاپی، بی تکرار با ي و ی، بلند کنار، حداکثر دوازده */
  @Test
  fun normalizeCleansDedupesAndCaps() {
    assertEquals(listOf("شکسته در راه"), ReturnReasons.normalize(listOf("  شکسته   در\tراه ")))
    //  تکراری با کلیدِ مقایسه: نخستین می‌ماند و متنش دست نمی‌خورد
    assertEquals(listOf("خراب يا معيوب", "دیگر"), ReturnReasons.normalize(listOf("خراب يا معيوب", "خراب یا معیوب", "دیگر", "دي‌گر")))
    val forty = "ا".repeat(ReturnReasons.MAX_LENGTH)
    assertEquals(listOf(forty), ReturnReasons.normalize(listOf(forty, forty + "ب")))
    val many = (1..20).map { "دلیل $it" }
    assertEquals(many.take(ReturnReasons.MAX_COUNT), ReturnReasons.normalize(many))
  }

  /** کادرِ تنظیمات — هر خط یک دلیل، و برگشت به همان متن */
  @Test
  fun parseAndFormatSettingsText() {
    assertEquals(listOf("الف", "ب"), ReturnReasons.parse("الف\r\nب\n\n الف \n"))
    assertEquals("الف\nب", ReturnReasons.format(listOf("الف", " ب ", "")))
  }

  /** انتخاب — فقط ردیفِ فهرست، با همان متنِ فهرست برای ثبت */
  @Test
  fun pickAcceptsOnlyListedReasons() {
    val list = ReturnReasons.DEFAULT
    assertEquals("خراب یا معیوب", ReturnReasons.pick("خراب یا معیوب", list))
    //  عربی/فارسی، نیم‌فاصله و اعراب یک کلیدند؛ ثبت با متنِ خودِ فهرست
    assertEquals("خراب یا معیوب", ReturnReasons.pick("خراب يا معيوب", list))
    assertEquals("پشیمانیِ مشتری", ReturnReasons.pick("پشیمانی مشتری", list))
    assertEquals("دیگر", ReturnReasons.pick("  دیگر ", list))
    //  بیرون از فهرست، خالی یا null ⇒ رد
    assertNull(ReturnReasons.pick("دلم خواست", list))
    assertNull(ReturnReasons.pick("", list))
    assertNull(ReturnReasons.pick("   ", list))
    assertNull(ReturnReasons.pick(null, list))
    //  فهرستِ دکان: دلیلِ پیش‌فرضی که صاحبِ دکان برداشته دیگر پذیرفته نیست
    val shop = ReturnReasons.parse("رنگِ نامناسب\nخراب")
    assertEquals("رنگِ نامناسب", ReturnReasons.pick("رنگ نامناسب", shop))
    assertNull(ReturnReasons.pick("خراب یا معیوب", shop))
  }
}
