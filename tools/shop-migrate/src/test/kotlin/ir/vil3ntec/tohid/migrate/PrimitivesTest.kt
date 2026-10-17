package ir.vil3ntec.tohid.migrate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrimitivesTest {

  @Test fun roundingIsHalfTowardPositiveInfinityOnTheDecimalValue() {
    assertEquals(29L, Scale.toMinor(0.285))          // ۰٫۲۸۵ × ۱۰۰ در Double = ۲۸٫۴۹۹۹…
    assertEquals(-28L, Scale.toMinor(-0.285))        // همان Math.round: نیمه به سمتِ +∞
    assertEquals(1001L, Scale.toMilli(1.0005))
    assertEquals(-1000L, Scale.toMilli(-1.0005))
    assertEquals(123456789012L, Scale.toMinor(1234567890.12))
    assertEquals(0L, Scale.toMinor(Double.NaN))
    assertTrue(Scale.hasExtraDecimals(10.004, 2))
    assertFalse(Scale.hasExtraDecimals(10.5, 2))
    assertEquals("-1.005", Scale.format(-1005, 1000))
    assertEquals("12.30000", Scale.format(1_230_000, 100_000))
  }

  @Test fun idsAreUlidsAndDeterministic() {
    val ulid = "01HV2NFXH1MNG1FRAF3KMB2TC3"
    assertEquals(ulid, Ids.derive("product", ulid, 1, 0))
    val a = Ids.derive("product", "k3j9x2", 1, 1_712_700_000_000)
    assertTrue(Ids.isUlid(a), a)
    assertEquals(a, Ids.derive("product", "k3j9x2", 1, 1_712_700_000_000))
    assertNotEquals(a, Ids.derive("product", "k3j9x2", 2, 1_712_700_000_000))
    assertNotEquals(a, Ids.derive("customer", "k3j9x2", 1, 1_712_700_000_000))
    assertTrue(a.startsWith(Ids.encode(1_712_700_000_000, ByteArray(10)).take(10)))
  }

  @Test fun canonicalJsonFollowsJcs() {
    assertEquals(
      """{"a":[1,"x",null,true],"b":-5,"c\"":"\n\u0001é😀"}""",
      HashChain.canonicalJson(mapOf("c\"" to "\n\u0001é😀", "b" to -5L, "a" to listOf(1, "x", null, true))),
    )
    // ترتیبِ کلید بر حسبِ واحدِ UTF-16
    assertEquals("""{"B":1,"a":2,"é":3}""", HashChain.canonicalJson(mapOf("é" to 3L, "a" to 2L, "B" to 1L)))
    // بردارِ ثابت، جدا با Node و sha256sum ساخته شده: پیاده‌سازیِ JS باید همین را بدهد
    //   printf '%s' "$(printf '0%.0s' {1..64}){\"amountMinor\":100,\"id\":\"x\"}" | sha256sum
    assertEquals(
      "ded58c5254639cebdb753dc1d555defbd212641098aa3a323c9668b0a0ad174f",
      HashChain.hash(HashChain.GENESIS, mapOf("id" to "x", "amountMinor" to 100L)),
    )
  }

  @Test fun chainDetectsAnyChange() {
    var prev = HashChain.GENESIS
    val links = (1..5).map { i ->
      val doc = mapOf("id" to "d$i", "amountMinor" to i * 100L)
      val h = HashChain.hash(prev, doc)
      HashChain.Link("d$i", i.toLong(), prev, h, doc).also { prev = h }
    }
    assertNull(HashChain.firstBroken(links))
    val edited = links.toMutableList().apply { this[2] = this[2].copy(doc = mapOf("id" to "d3", "amountMinor" to 301L)) }
    assertEquals("d3", HashChain.firstBroken(edited)?.id)
    assertEquals("d4", HashChain.firstBroken(links.filterNot { it.id == "d3" })?.id)
  }

  @Test fun unitFactorBecomesAnExactFraction() {
    assertEquals(1L to 1000L, Mapper.fraction(0.001))
    assertEquals(12L to 1L, Mapper.fraction(12.0))
    assertEquals(5L to 2L, Mapper.fraction(2.5))
    assertNull(Mapper.fraction(1.0 / 3))
    assertNull(Mapper.fraction(-1.0))
  }
}
