package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  سنجشِ شناختنِ کالا از نیمهٔ بارکد.
 *
 *  خطِ سرخِ این آزمون‌ها یکتا بودن است: نیمه‌ای که به دو کالا بخورد
 *  نباید هیچ‌کدام را به سبد بیندازد. در دکان، کالای غلط در سبد یعنی
 *  پولِ غلط از مشتری و موجودیِ غلط در انبار.
 */
class BarcodeMatchTest {

  private val index = mapOf(
    "5449000000996" to "cola",
    "4001686301227" to "milk",
    "036000291452" to "soap",
    "SHOP-77" to "rice",
  )

  @Test
  fun `بارکدِ کامل، همان کالا`() {
    assertEquals(BarcodeMatch.Hit.Product("cola", true), BarcodeMatch.find("5449000000996", index))
  }

  @Test
  fun `نیمهٔ یکتا کالا را پیدا می‌کند`() {
    //  نورِ لامپ افتاده سرِ ابتدای بارکد و فقط دنباله‌اش خوانده شده
    assertEquals(BarcodeMatch.Hit.Product("cola", false), BarcodeMatch.find("9000000996", index))
    //  و برعکس: فقط ابتدایش
    assertEquals(BarcodeMatch.Hit.Product("milk", false), BarcodeMatch.find("4001686", index))
  }

  @Test
  fun `خوانشی که چیزِ اضافه چسبانده هم پیدا می‌شود`() {
    assertEquals(BarcodeMatch.Hit.Product("soap", false), BarcodeMatch.find("0360002914520", index))
  }

  @Test
  fun `نیمهٔ کوتاه پذیرفته نمی‌شود`() {
    assertEquals(BarcodeMatch.Hit.None, BarcodeMatch.find("9996", index))
  }

  @Test
  fun `نیمه‌ای که به دو کالا بخورد، هیچ‌کدام`() {
    val two = mapOf("1234567890123" to "a", "9991234567890" to "b")
    assertEquals(BarcodeMatch.Hit.Several(2), BarcodeMatch.find("123456789", two))
  }

  @Test
  fun `کدِ بی‌ربط، هیچ`() {
    assertEquals(BarcodeMatch.Hit.None, BarcodeMatch.find("87654321", index))
  }

  @Test
  fun `کدِ خالی، هیچ`() {
    assertEquals(BarcodeMatch.Hit.None, BarcodeMatch.find("   ", index))
  }
}
