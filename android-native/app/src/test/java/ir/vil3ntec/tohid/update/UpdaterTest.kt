package ir.vil3ntec.tohid.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  به‌روزرسانی از داخلِ برنامه.
 *
 *  این آزمون‌ها بعد از یک باگِ واقعی نوشته شدند: شمارهٔ نسخه از نامِ
 *  برچسبِ انتشار خوانده می‌شد، و برچسبِ این مخزن غلتان است — «tohid-native»،
 *  بی هیچ عددی. پس `isNewer` هر بار «نه» می‌گفت و برنامه با اطمینان
 *  می‌گفت «نسخهٔ شما تازه‌ترین است»، در حالی که نسخهٔ تازه همان‌جا بود.
 *
 *  خرابی‌ای که خودش را به‌شکلِ «همه‌چیز مرتب است» نشان می‌دهد، از خرابیِ
 *  پر سر و صدا بدتر است. برای همین اینجا آزمون دارد.
 */
class UpdaterTest {

  /* --------------------------- شمارهٔ نسخه --------------------------- */

  @Test
  fun `شماره از نام فایل خوانده می شود، نه از برچسب غلتان`() {
    assertEquals("3.2.0", Updater.versionOf("Tohid-Native-3.2.0.apk", "tohid-native"))
    assertEquals("1.0.0", Updater.versionOf("Tohid-1.0.0.apk", "android-preview"))
    assertEquals("10.20.30", Updater.versionOf("Tohid-Native-10.20.30.apk", "tohid-native"))
  }

  @Test
  fun `برچسبِ نسخه دار هم پذیرفته می شود`() {
    // انتشارِ رسمی، بدونِ شماره در نامِ فایل
    assertEquals("1.3.0", Updater.versionOf("app-release.apk", "v1.3.0"))
    assertEquals("2.0", Updater.versionOf("app-release.apk", "2.0"))
  }

  @Test
  fun `وقتی هیچ شماره ای نیست، چیزی برنمی گردد`() {
    // بهتر است رد شود تا اینکه رشتهٔ بی‌معنی وارد مقایسه شود
    assertNull(Updater.versionOf("app-release.apk", "tohid-native"))
    assertNull(Updater.versionOf("build.apk", "latest"))
  }

  /* --------------------------- مقایسهٔ نسخه --------------------------- */

  @Test
  fun `نسخه ی بالاتر تازه تر شناخته می شود`() {
    assertTrue(Updater.isNewer("3.2.0", "3.1.0"))
    assertTrue(Updater.isNewer("1.3.0", "1.2.9"))     // عددی، نه الفبایی
    assertTrue(Updater.isNewer("2.0.0", "1.99.99"))
    assertTrue(Updater.isNewer("3.2.1", "3.2.0"))
  }

  @Test
  fun `نسخه ی یکسان یا پایین تر تازه نیست`() {
    assertFalse(Updater.isNewer("3.2.0", "3.2.0"))
    assertFalse(Updater.isNewer("3.1.0", "3.2.0"))
    assertFalse(Updater.isNewer("1.0.0", "3.0.0"))
  }

  @Test
  fun `شماره ی کوتاه تر با صفر پر می شود`() {
    assertTrue(Updater.isNewer("3.2", "3.1.9"))
    assertFalse(Updater.isNewer("3.2", "3.2.0"))
    assertTrue(Updater.isNewer("3.2.1", "3.2"))
  }

  /* ------------------- همان چیزی که در عمل اتفاق افتاد ------------------- */

  @Test
  fun `باگِ واقعی — برچسبِ غلتان دیگر جلوی به روزرسانی را نمی گیرد`() {
    // قبلاً: version از tag_name می‌آمد → "tohid-native" → همیشه false
    assertFalse("این همان رفتارِ خراب بود", Updater.isNewer("tohid-native", "3.1.0"))

    // حالا: version از نامِ فایل می‌آید
    val version = Updater.versionOf("Tohid-Native-3.2.0.apk", "tohid-native")!!
    assertTrue("نسخهٔ تازه باید پیدا شود", Updater.isNewer(version, "3.1.0"))
    assertFalse("نسخهٔ همان، تازه نیست", Updater.isNewer(version, "3.2.0"))
  }
}
