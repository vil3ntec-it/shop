package ir.vil3ntec.tohid.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  خواندنِ شماره و مقایسه‌اش — همان دو چیزی که اگر بلغزند، به‌روزرسانی
 *  یا هیچ‌وقت چیزی پیدا نمی‌کند یا نسخهٔ عقب‌تر را پیشنهاد می‌دهد.
 */
class UpdaterTest {

  @Test
  fun `شماره از نام فایل خوانده می‌شود`() {
    assertEquals("3.2.45", Updater.versionOf("Tohid-Native-3.2.45.apk", "tohid-native"))
  }

  @Test
  fun `نام بی‌شماره می‌رود سراغ برچسب`() {
    // «Tohid-Native.apk» نامِ ثابتِ لینکِ دانلود است و شماره ندارد
    assertEquals("1.4.0", Updater.versionOf("Tohid-Native.apk", "v1.4.0"))
  }

  @Test
  fun `برچسبِ بی‌شماره خودش برمی‌گردد`() {
    assertEquals("tohid-native", Updater.versionOf("Tohid-Native.apk", "tohid-native"))
  }

  @Test
  fun `مقایسه عددی است نه الفبایی`() {
    assertTrue(Updater.isNewer("3.2.120", "3.2.99"))
    assertFalse(Updater.isNewer("3.2.99", "3.2.120"))
    assertFalse(Updater.isNewer("3.2.10", "3.2.10"))
    assertTrue(Updater.isNewer("3.3.0", "3.2.999"))
  }
}
