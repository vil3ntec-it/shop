package ir.vil3ntec.tohid.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سنجشِ سدِ بارکدِ دروغین.
 *
 *  دو چیز باید هم‌زمان درست باشد و این دو با هم در کشمکش‌اند:
 *  کالای درست باید **همان فریمِ اول** پذیرفته شود (فروشنده منتظر
 *  نماند)، و خوانشِ تصادفی نباید هیچ‌وقت رد شود. آزمون‌ها دقیقاً همین
 *  مرز را می‌سنجند.
 */
class BarcodeGuardTest {

  @Test
  fun `بارکدهای واقعیِ با رقمِ کنترلیِ درست، همان فریمِ اول`() {
    val guard = BarcodeGuard()
    //  EAN-13 کوکاکولا، EAN-8، UPC-A، و همان کدی که در گزارش آمده بود
    listOf("5449000000996", "96385074", "036000291452", "007067303822").forEach {
      assertTrue("باید همان بار اول پذیرفته شود: $it", guard.trust(it, now = 1_000))
    }
  }

  @Test
  fun `رقمِ کنترلیِ غلط، همان بار اول پذیرفته نمی‌شود`() {
    val guard = BarcodeGuard()
    //  رقمِ آخرِ همان EAN-13 دستکاری شده
    assertFalse(guard.trust("5449000000997", now = 1_000))
  }

  @Test
  fun `کدی که رقمِ کنترلی ندارد، با سه بارِ یکسان پذیرفته می‌شود`() {
    val guard = BarcodeGuard()
    assertFalse(guard.trust("ABC123XY", now = 1_000))
    assertFalse(guard.trust("ABC123XY", now = 1_030))
    assertTrue(guard.trust("ABC123XY", now = 1_060))
  }

  @Test
  fun `خوانشِ تصادفی که هر بار عوض می‌شود، هیچ‌وقت رد نمی‌شود`() {
    val guard = BarcodeGuard()
    //  همان چیزی که دوربینِ تار می‌دهد: هر فریم یک عددِ کمی متفاوت
    val junk = listOf("11223", "112233", "1122334", "11223", "9988776")
    var at = 1_000L
    junk.forEach {
      assertFalse("این نباید پذیرفته شود: $it", guard.trust(it, now = at))
      at += 30
    }
  }

  @Test
  fun `تکرارِ دیرآمده شمارش را از نو شروع می‌کند`() {
    val guard = BarcodeGuard()
    assertFalse(guard.trust("ABC123XY", now = 1_000))
    assertFalse(guard.trust("ABC123XY", now = 1_500))
    //  بیش از پنجرهٔ ۹۰۰ میلی‌ثانیه از اولی گذشته، پس این «دومی» است نه سومی
    assertFalse(guard.trust("ABC123XY", now = 2_600))
  }

  @Test
  fun `رقمِ کنترلیِ نخوان با تکرار می‌گذرد — کدِ داخلیِ دکان نباید بشکند`() {
    val guard = BarcodeGuard()
    val internal = "5449000000997"
    assertFalse(guard.trust(internal, now = 1_000))
    assertFalse(guard.trust(internal, now = 1_030))
    assertTrue(guard.trust(internal, now = 1_060))
  }

  @Test
  fun `قاعدهٔ GTIN روی هر چهار طول`() {
    assertTrue(Gtin.valid("96385074"))          // GTIN-8
    assertTrue(Gtin.valid("036000291452"))      // GTIN-12
    assertTrue(Gtin.valid("5449000000996"))     // GTIN-13
    assertTrue(Gtin.valid("10614141000415"))    // GTIN-14
    assertFalse(Gtin.verifiable("1234567890"))  // طولِ بی‌رقمِ کنترلی
    assertFalse(Gtin.verifiable("54490000009A6"))
  }
}
