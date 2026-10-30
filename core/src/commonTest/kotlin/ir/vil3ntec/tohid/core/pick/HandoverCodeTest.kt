package ir.vil3ntec.tohid.core.pick

import ir.vil3ntec.tohid.core.qr.QrCode
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PCK-11: کدِ تحویل به شکلِ عدد یا QR — `HandoverCode` */
class HandoverCodeTest {

  @Test
  fun payloadIsPrefixOrderNoAndCode() {
    assertEquals("VLNH-1234-567890", HandoverCode.payload(1234, "567890"))
    assertEquals("VLNH-1-000001", HandoverCode.payload(1, "000001"))
  }

  @Test
  fun payloadRejectsWhatServerNeverMakes() {
    assertFailsWith<IllegalArgumentException> { HandoverCode.payload(0, "123456") }
    assertFailsWith<IllegalArgumentException> { HandoverCode.payload(HandoverCode.MAX_ORDER_NO + 1, "123456") }
    for (bad in listOf("12345", "1234567", "12a456", "۱۲۳۴۵۶", "")) {
      assertFailsWith<IllegalArgumentException>("کدِ «$bad»") { HandoverCode.payload(5, bad) }
    }
  }

  @Test
  fun payloadRoundTripsThroughParse() {
    for ((no, code) in listOf(1L to "000000", 42L to "123456", 999_999_999_999L to "999999")) {
      assertEquals(HandoverCode.Scan(code, no), HandoverCode.parse(HandoverCode.payload(no, code)))
    }
  }

  @Test
  fun hidScannerOnPersianLayoutStillReadsDigits() {
    //  بارکدخوانِ صفحه‌کلیدی با چیدمانِ فارسی: حرف‌ها عوض می‌شوند، رقم‌ها فارسی می‌آیند
    assertEquals(HandoverCode.Scan("567890", 1234), HandoverCode.parse("رمدا-۱۲۳۴-۵۶۷۸۹۰"))
    assertEquals(HandoverCode.Scan("567890", 1234), HandoverCode.parse("VLNH-١٢٣٤-٥٦٧٨٩٠"))
    //  پسوندِ Enter/Tab ِ بارکدخوان
    assertEquals(HandoverCode.Scan("567890", 1234), HandoverCode.parse("VLNH-1234-567890\n"))
  }

  @Test
  fun typedSixDigitsIsCodeWithoutOrder() {
    assertEquals(HandoverCode.Scan("123456", null), HandoverCode.parse("123456"))
    assertEquals(HandoverCode.Scan("123456", null), HandoverCode.parse(" ۱۲۳ ۴۵۶ "))
    assertEquals(HandoverCode.Scan("012345", null), HandoverCode.parse("012345"))
  }

  @Test
  fun notACode() {
    for (bad in listOf("", "12345", "1234567", "abc", "VLNH-1234-56789", "VLNH-0012-567890x1")) {
      val s = HandoverCode.parse(bad)
      assertNull(s.orderNo, "«$bad» شمارهٔ سفارش ندارد")
    }
    assertNull(HandoverCode.parse("12345").code)
    assertNull(HandoverCode.parse("1234567").code)
    //  شمارهٔ سفارش با صفرِ آغازین یا بیش از ۱۲ رقم QRِ ما نیست
    assertNull(HandoverCode.parse("VLNH-0123-567890").orderNo)
    assertNull(HandoverCode.parse("VLNH-1234567890123-567890").orderNo)
  }

  @Test
  fun typedKeepsOldDigitFilterAndAcceptsScan() {
    assertEquals("12", HandoverCode.typed("12"))
    assertEquals("123456", HandoverCode.typed("1234567"))
    assertEquals("123", HandoverCode.typed("۱۲۳"))
    assertEquals("12", HandoverCode.typed("1a2"))
    assertEquals("567890", HandoverCode.typed("VLNH-1234-567890"))
    assertEquals("567890", HandoverCode.typed("رمدا-۱۲۳۴-۵۶۷۸۹۰"))
    assertEquals("", HandoverCode.typed("abc"))
  }

  @Test
  fun inputIsBounded() {
    val long = "1".repeat(10_000) + "-123456"
    //  فقط ۶۴ نویسهٔ نخست خوانده می‌شود
    assertNull(HandoverCode.parse(long).orderNo)
    assertEquals(6, HandoverCode.typed(long).length)
  }

  @Test
  fun otherOrderOnlyForScannedPayload() {
    assertEquals(77L, HandoverCode.otherOrder("VLNH-77-123456", 78))
    assertNull(HandoverCode.otherOrder("VLNH-78-123456", 78))
    assertNull(HandoverCode.otherOrder("123456", 78))
  }

  @Test
  fun matrixIsQrOfPayload() {
    val m = assertNotNull(HandoverCode.matrix(1234, "567890"))
    assertTrue(m.size in 21..25, "نسخهٔ ۱ یا ۲ — کوچک و خوانا روی کاغذ و صفحه")
    val want = QrCode.encode("VLNH-1234-567890")
    assertEquals(want.size, m.size)
    for (y in want.indices) assertContentEquals(want[y], m[y])
    assertNull(HandoverCode.matrix(1234, "12345"))
  }

  @Test
  fun spacedForReadingAloud() {
    assertEquals("123 456", HandoverCode.spaced("123456"))
    assertEquals("12345", HandoverCode.spaced("12345"))
  }
}
