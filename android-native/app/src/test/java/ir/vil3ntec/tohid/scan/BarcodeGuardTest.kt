package ir.vil3ntec.tohid.scan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سنجشِ سدِ بارکدِ دروغین.
 *
 *  دو چیز باید هم‌زمان درست باشد و این دو با هم در کشمکش‌اند:
 *  کالای دکان باید **همان فریمِ اول** پذیرفته شود (فروشنده منتظر
 *  نماند)، و خوانشِ نصفه یا تصادفی نباید هیچ‌وقت رد شود. آزمون‌ها
 *  دقیقاً همین مرز را می‌سنجند.
 */
class BarcodeGuardTest {

  /** فهرستِ کالاهای یک دکانِ فرضی */
  private fun shop(vararg codes: String) = BarcodeGuard(known = { it in codes.toSet() })

  @Test
  fun `کالای خودِ دکان با رقمِ کنترلیِ درست، همان فریمِ اول`() {
    val guard = shop("5449000000996", "96385074", "036000291452", "007067303822")
    listOf("5449000000996", "96385074", "036000291452", "007067303822").forEach {
      assertTrue("باید همان بار اول پذیرفته شود: $it", guard.trust(it, now = 1_000))
    }
  }

  @Test
  fun `کدِ ناشناسِ درست، با دو بار پذیرفته می‌شود`() {
    //  کالایی که هنوز ثبت نشده: عجله‌ای نیست، پس یک فریم مدرکِ بیشتر
    //  می‌خواهیم — بارکدِ غلطی که به اسمِ کالای تازه ثبت شود، می‌ماند
    val guard = BarcodeGuard()
    assertFalse(guard.trust("5449000000996", now = 1_000))
    assertTrue(guard.trust("5449000000996", now = 1_030))
  }

  @Test
  fun `ITF با طولِ فرد هرگز پذیرفته نمی‌شود — همان کدِ گزارش‌شده`() {
    val guard = BarcodeGuard()
    //  عکسی که کاربر فرستاد: یازده رقم. ITF دوتادوتا رمز می‌کند، پس
    //  طولِ فرد یعنی خوانشِ نصفه‌مانده، نه یک کد.
    var at = 1_000L
    repeat(20) {
      assertFalse("خوانشِ نصفه نباید بگذرد", guard.trust("49495030626", CodeKind.ITF, at))
      at += 30
    }
  }

  @Test
  fun `ITF ناشناس پنج بارِ یکسان می‌خواهد`() {
    val guard = BarcodeGuard()
    val code = "1234567890"  // ده رقم، پس ساختارش سالم است
    var at = 1_000L
    repeat(4) {
      assertFalse(guard.trust(code, CodeKind.ITF, at))
      at += 30
    }
    assertTrue(guard.trust(code, CodeKind.ITF, at))
  }

  @Test
  fun `ITF-14 کالای دکان همان فریمِ اول — رقمِ کنترلی دارد`() {
    val guard = shop("10614141000415")
    assertTrue(guard.trust("10614141000415", CodeKind.ITF, now = 1_000))
  }

  @Test
  fun `EAN با رقمِ کنترلیِ غلط، هرچقدر هم تکرار شود رد است`() {
    val guard = BarcodeGuard()
    //  رقمِ آخرِ همان EAN-13 دستکاری شده. اینجا قالب را دوربین گفته،
    //  پس «کدِ داخلیِ دکان» نیست — خوانشِ خراب است.
    var at = 1_000L
    repeat(10) {
      assertFalse(guard.trust("5449000000997", CodeKind.CHECKED, at))
      at += 30
    }
  }

  @Test
  fun `کدِ داخلیِ دکان با رقمِ کنترلیِ نخوان نمی‌شکند`() {
    //  دکان‌هایی برچسبِ خودشان را چاپ می‌کنند و رقمِ کنترلی‌اش درست
    //  نیست. تا وقتی در فهرست باشد، دو بار بس است.
    val guard = shop("5449000000997")
    assertFalse(guard.trust("5449000000997", CodeKind.OTHER, now = 1_000))
    assertTrue(guard.trust("5449000000997", CodeKind.OTHER, now = 1_030))
  }

  @Test
  fun `کدِ ناشناسِ بی‌رقمِ کنترلی، چهار بار`() {
    val guard = BarcodeGuard()
    var at = 1_000L
    repeat(3) {
      assertFalse(guard.trust("ABC123XY", CodeKind.OTHER, at))
      at += 30
    }
    assertTrue(guard.trust("ABC123XY", CodeKind.OTHER, at))
  }

  @Test
  fun `خوانشِ تصادفی که هر بار عوض می‌شود، هیچ‌وقت رد نمی‌شود`() {
    val guard = BarcodeGuard()
    //  همان چیزی که دوربینِ تار می‌دهد: هر فریم یک عددِ کمی متفاوت
    val junk = listOf("11223", "112233", "1122334", "11223", "9988776")
    var at = 1_000L
    junk.forEach {
      assertFalse("این نباید پذیرفته شود: $it", guard.trust(it, CodeKind.OTHER, at))
      at += 30
    }
  }

  @Test
  fun `کدِ درست پشتِ نصفه‌ها گم نمی‌شود`() {
    /*
     *  اشکالِ نسخهٔ قبل: شمارش فقط برای **یک** کد نگه داشته می‌شد، پس
     *  رسیدنِ کدِ دیگر آن را صفر می‌کرد. دوربینِ نیمه‌تار یکی‌درمیان کدِ
     *  درست و کدِ نصفه می‌دهد — و آن‌طور کدِ درست هیچ‌وقت به حدِ نصاب
     *  نمی‌رسید و اسکن «گیر» می‌کرد.
     */
    val guard = BarcodeGuard()
    val real = "4901234567894"      // EAN-13 با رقمِ کنترلیِ درست
    assertFalse(guard.trust(real, CodeKind.CHECKED, 1_000))
    assertFalse(guard.trust("777888", CodeKind.OTHER, 1_030))
    assertTrue(guard.trust(real, CodeKind.CHECKED, 1_060))
  }

  @Test
  fun `تکرارِ دیرآمده شمارش را از نو شروع می‌کند`() {
    val guard = BarcodeGuard()
    assertFalse(guard.trust("ABC123XY", CodeKind.OTHER, 1_000))
    assertFalse(guard.trust("ABC123XY", CodeKind.OTHER, 1_500))
    //  بیش از پنجرهٔ ۱۲۰۰ میلی‌ثانیه از اولی گذشته، پس شمارش از نو
    assertFalse(guard.trust("ABC123XY", CodeKind.OTHER, 2_800))
    assertFalse(guard.trust("ABC123XY", CodeKind.OTHER, 2_830))
  }

  @Test
  fun `کدِ خیلی کوتاه اصلاً بارکد نیست`() {
    val guard = BarcodeGuard()
    var at = 1_000L
    repeat(10) {
      assertFalse(guard.trust("123", CodeKind.OTHER, at))
      at += 30
    }
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
