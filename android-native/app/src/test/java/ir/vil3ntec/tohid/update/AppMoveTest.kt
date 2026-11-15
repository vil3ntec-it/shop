package ir.vil3ntec.tohid.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  «برنامه با نامِ تازه آمده» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۴): نصبیِ شناسهٔ دیگر هرگز «به‌روزرسانی» نیست، و
 *  پاک کردنِ برنامهٔ قدیمی فقط وقتی پیشنهاد می‌شود که هیچ تغییرِ همگام‌نشده‌ای نمانده باشد.
 */
class AppMoveTest {

  private val old = AppMove.LEGACY_ID
  private val new = AppMove.CURRENT_ID

  @Test
  fun `دو شناسهٔ تصمیم`() {
    assertEquals("ir.vil3ntec.tohid", old)
    assertEquals("com.vill3n.dailyshop", new)
  }

  @Test
  fun `app-id txt — فقط خطِ اول و فقط نامِ بستهٔ درست`() {
    assertEquals(new, AppMove.parseId("com.vill3n.dailyshop\n"))
    assertEquals("$new.preview", AppMove.parseId("  com.vill3n.dailyshop.preview  \r\nچیزِ دیگر"))
    assertEquals("", AppMove.parseId(null))
    assertEquals("", AppMove.parseId(""))
    assertEquals("", AppMove.parseId("dailyshop"))
    assertEquals("", AppMove.parseId("<html>404</html>"))
    assertEquals("", AppMove.parseId("com.vill3n/../x"))
    assertEquals("", AppMove.parseId("a." + "b".repeat(200)))
  }

  @Test
  fun `برنامهٔ قدیمی انتشارِ شناسهٔ تازه را برنامهٔ دیگر می‌بیند`() {
    assertEquals(new, AppMove.movedTo(new, old))
    assertEquals("$new.preview", AppMove.movedTo("$new.preview", "$old.preview"))
  }

  @Test
  fun `همان شناسه، شناسهٔ نامعلوم یا ناجور ⇒ به‌روزرسانیِ عادی`() {
    assertEquals("", AppMove.movedTo(new, new))
    assertEquals("", AppMove.movedTo(old, old))
    //  انتشارِ پیش از این تصمیم `app-id.txt` نداشت
    assertEquals("", AppMove.movedTo("", old))
    assertEquals("", AppMove.movedTo("not a package", old))
    assertEquals("", AppMove.movedTo(new, ""))
  }

  @Test
  fun `برنامهٔ دیگر بی دکمهٔ برگه گرفته نمی‌شود`() {
    assertTrue(AppMove.mayGet("", ""))
    assertFalse(AppMove.mayGet(new, ""))
    assertTrue(AppMove.mayGet(new, new))
    //  تأییدِ یک شناسه برای شناسهٔ دیگر حساب نیست
    assertFalse(AppMove.mayGet("$new.preview", new))
  }

  @Test
  fun `فایلِ بستهٔ دیگر بی برگه به نصب‌کننده نمی‌رود — حتی وقتی انتشار شناسه نداشت`() {
    //  همین برنامه ⇒ نصب
    assertNull(AppMove.installGate(null, "", ""))
    assertNull(AppMove.installGate(null, new, new))
    //  انتشار چیزی نگفته بود ولی فایل برنامهٔ دیگری است ⇒ اول برگه
    assertEquals(new, AppMove.installGate(new, "", ""))
    //  گفته بود ولی دکمهٔ برگه زده نشده ⇒ اول برگه
    assertEquals(new, AppMove.installGate(new, new, ""))
    //  برگه برای همان شناسه تأیید شد ⇒ نصب
    assertNull(AppMove.installGate(new, new, new))
    //  فایل بستهٔ سومی است ⇒ دوباره برگه برای همان بسته
    assertEquals("x.y.z", AppMove.installGate("x.y.z", new, new))
  }

  @Test
  fun `پاک کردنِ برنامهٔ قدیمی فقط وقتی همه‌چیز رسیده`() {
    assertEquals(AppMove.Advice.SAFE, AppMove.advice(signedIn = true, pending = 0, everSynced = true))
    assertEquals(AppMove.Advice.PENDING, AppMove.advice(signedIn = true, pending = 3, everSynced = true))
    //  صفِ خالی ولی دفتر هرگز کامل نرفته
    assertEquals(AppMove.Advice.PENDING, AppMove.advice(signedIn = true, pending = 0, everSynced = false))
    //  بی حساب دفتر فقط روی همین گوشی است
    assertEquals(AppMove.Advice.NOT_SIGNED_IN, AppMove.advice(signedIn = false, pending = 0, everSynced = true))
    assertEquals(AppMove.Advice.NOT_SIGNED_IN, AppMove.advice(signedIn = false, pending = 5, everSynced = false))
  }

  @Test
  fun `برگه برای هر شناسهٔ تازه کلیدِ جدا دارد`() {
    assertEquals("app_move_seen_com.vill3n.dailyshop", AppMove.seenKey(new))
    assertFalse(AppMove.seenKey(new) == AppMove.seenKey("$new.preview"))
  }
}
