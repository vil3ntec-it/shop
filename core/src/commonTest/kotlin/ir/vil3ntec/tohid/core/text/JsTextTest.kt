package ir.vil3ntec.tohid.core.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `JsText` = همان `trim()` و `Number()` ِ JS؛ هر عدد این‌جا خروجیِ خودِ Node است (فازِ تفاضلی). */
class JsTextTest {

  @Test fun trimLikeJs() {
    assertEquals("احمد", JsText.trim("\uFEFFاحمد"))
    assertEquals("\u001Fکریم", JsText.trim("\u001Fکریم"))
    assertEquals("x", JsText.trim("\u00A0x\u3000"))
    assertEquals("\u200Bx", JsText.trim("\u200Bx"))
    assertTrue(JsText.isBlank("\uFEFF\u00A0 \t"))
    assertTrue(JsText.isBlank(null))
    assertFalse(JsText.isBlank("\u001F"))
  }

  /** خروجیِ `toLowerCase()` ِ Node 22 */
  @Test fun lowercaseLikeJs() {
    val cases = listOf(
      "ΑΣ" to "ας", "ΑΣ " to "ας ", "ΑΣع" to "αςع", "ΑΣa" to "ασa", "ΑΣ1" to "ας1", "ΑΣ." to "ας.", "Σ" to "σ",
      "ΑΣ\u0301ع" to "ας\u0301ع", "aΣb" to "aσb", "ΑΣΑ" to "ασα", "ΑΣ\u200c" to "ας\u200c", "ΑΣ\u2019a" to "ασ\u2019a",
      "ΑΣ'a" to "ασ'a", "ΑΣ\u0640ع" to "ας\u0640ع", "ΑΣعددgram" to "αςعددgram", "İ" to "i\u0307", "DRINKS" to "drinks", "" to "",
    )
    for ((s, want) in cases) assertEquals(want, JsText.lowercase(s), s)
  }

  @Test fun numberLikeJs() {
    val cases = listOf(
      "0x1F" to 31.0, " 5" to 5.0, "\u00A05" to 5.0, " 7 " to 7.0, "" to 0.0, "  " to 0.0, "0b101" to 5.0, "0o17" to 15.0,
      "+5" to 5.0, ".5" to 0.5, "5." to 5.0, "1e3" to 1000.0, "Infinity" to Double.POSITIVE_INFINITY,
      "-Infinity" to Double.NEGATIVE_INFINITY, "1e400" to Double.POSITIVE_INFINITY,
    )
    for ((s, want) in cases) assertEquals(want, JsText.toNumber(s), s)
    for (s in listOf("5d", "1_000", "-0x10", "0x", "infinity", "abc", "۵", "0x۵", "1f")) assertTrue(JsText.toNumber(s).isNaN(), s)
  }
}
