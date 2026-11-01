package ir.vil3ntec.tohid.core.audit

import java.security.MessageDigest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  SHA-256 ِ خالصِ commonMain همان `MessageDigest("SHA-256")` ِ JDK است — روی هر طولِ ۰ تا ۳۰۰
 *  بایت (همهٔ مرزهای بلوکِ ۶۴ و طولِ ۵۶) و چند ورودیِ تصادفیِ بزرگ با دانهٔ ثابت.
 */
class Sha256ParityTest {

  private fun jdk(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b)

  @Test
  fun everyLengthUpTo300MatchesJdk() {
    val rnd = Random(20261030)
    for (n in 0..300) {
      val b = ByteArray(n).also { rnd.nextBytes(it) }
      assertEquals(Sha256.hex(jdk(b)), Sha256.hex(Sha256.digest(b)), "طولِ $n")
    }
  }

  @Test
  fun largeRandomInputsMatchJdk() {
    val rnd = Random(1405)
    repeat(20) {
      val b = ByteArray(rnd.nextInt(1, 200_000)).also { rnd.nextBytes(it) }
      assertEquals(Sha256.hex(jdk(b)), Sha256.hex(Sha256.digest(b)), "طولِ ${b.size}")
    }
  }

  @Test
  fun textHashUsesUtf8LikeJdk() {
    for (t in listOf("", "abc", "دکان توحید", "😀 ۱۲۳", "{\"a\":\" \"}")) {
      assertEquals(Sha256.hex(jdk(t.toByteArray(Charsets.UTF_8))), Sha256.hexOf(t), t)
    }
  }
}
