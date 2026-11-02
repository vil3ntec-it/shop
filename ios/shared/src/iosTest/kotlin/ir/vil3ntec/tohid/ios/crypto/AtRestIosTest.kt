package ir.vil3ntec.tohid.ios.crypto

import ir.vil3ntec.tohid.core.atrest.AtRestEnvelope
import ir.vil3ntec.tohid.core.atrest.AtRestException
import java.security.SecureRandom
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 *  پاکتِ دفترِ رمزدار (`core/atrest/AtRestEnvelope` ِ همان کدِ اندروید، روی shimهای iOS) در برابرِ پاکتی که
 *  `javax.crypto` ِ JDK با همان کلید/شناسه/nonce ساخته است: آیفون پاکتِ اندروید/کامپیوتر را باز می‌کند و
 *  با همان nonce **همان بایت‌ها** را می‌سازد.
 */
class AtRestIosTest {
  private val key = SecretKeySpec(ByteArray(32) { (0x30 + it).toByte() }, "AES")
  private val plain = unhex("7b2270726f6475637473223a5b7b226964223a227031222c226e616d65223a22d8b4db8cd8b1227d5d7d")
  private val jdkSealed = unhex(
    "8954454e430d0a1a01026b3155565758595a5b5c5d5e5f60b8b728bc06394e4f1814a3cb2fec43de711378ba621e0809e88eec35bc5fcdc64b9c604215c2f31e3a9f803f33718eff377ab9861d188aec8855",
  )

  /** همان nonce ِ ثابتِ بردار (۰x55…۰x60) */
  private class FixedRandom : SecureRandom() {
    override fun nextBytes(bytes: ByteArray) { for (i in bytes.indices) bytes[i] = (0x55 + i).toByte() }
  }

  @Test
  fun opensJdkEnvelope() {
    assertContentEquals(plain, AtRestEnvelope.open(jdkSealed) { kid -> if (kid == "k1") key else null })
    assertEquals("k1", AtRestEnvelope.keyIdOf(jdkSealed))
  }

  @Test
  fun sealsSameBytesAsJdk() {
    assertContentEquals(jdkSealed, AtRestEnvelope.seal(plain, "k1", key, FixedRandom()))
  }

  @Test
  fun tamperedEnvelopeFails() {
    val bad = jdkSealed.copyOf().also { it[it.size - 3] = (it[it.size - 3].toInt() xor 0x10).toByte() }
    val e = assertFailsWith<AtRestException> { AtRestEnvelope.open(bad) { key } }
    assertEquals(AtRestException.Kind.AUTH_FAILED, e.kind)
  }
}
