package ir.vil3ntec.tohid.core.atrest

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 *  سنجشِ رمزِ دفتر (بازبینیِ #331، RULES F0-26) — پیش و پس از بافرِ یک‌جا.
 *
 *  «پیش» همان الگوریتمِ نسخهٔ پیشینِ `AtRestEnvelope.seal` است (`header + doFinal(plain)`):
 *  یک کپیِ کامل در `doFinal` و کپیِ کاملِ دوم در `+`. «پس» خودِ `seal` امروز است. عددها چاپ
 *  می‌شوند (زمان روی ماشینِ CI مرجع نیست)؛ فقط **حافظهٔ گرفته‌شده** سنجه است: «پس» باید
 *  کمتر از ۷۵٪ِ «پیش» بگیرد (≈ ۱ برابرِ دفتر به‌جای ≈ ۲ برابر).
 */
class AtRestBenchTest {

  private val random = SecureRandom()
  private val key: SecretKey = SecretKeySpec(ByteArray(32).also { random.nextBytes(it) }, "AES")
  private val kid = "kbench0001"

  /** نسخهٔ پیشینِ `seal` — فقط برای سنجش */
  private fun legacySeal(plain: ByteArray): ByteArray {
    val kidBytes = kid.toByteArray(Charsets.US_ASCII)
    val nonce = ByteArray(12).also { random.nextBytes(it) }
    val header = AtRestEnvelope.MAGIC + byteArrayOf(AtRestEnvelope.VERSION.toByte(), kidBytes.size.toByte()) + kidBytes + nonce
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
    cipher.updateAAD(header)
    return header + cipher.doFinal(plain)
  }

  private fun newSeal(plain: ByteArray): ByteArray = AtRestEnvelope.seal(plain, kid, key, random)

  /**
   *  حافظهٔ گرفته‌شدهٔ همین رشته (HotSpot) — با بازتاب، چون `java.lang.management` در
   *  classpathِ آزمونِ اندروید (android.jar) نیست. نبود ⇒ `-1` و سنجهٔ حافظه کنار می‌رود.
   */
  private fun allocated(): Long = runCatching {
    val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
    val m = Class.forName("com.sun.management.ThreadMXBean").getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    m.invoke(bean, Thread.currentThread().id) as Long
  }.getOrDefault(-1L)

  private class Run(val ms: Double, val allocated: Long)

  private fun measure(times: Int, block: () -> Unit): Run {
    repeat(2) { block() } // گرم کردن
    val a0 = allocated()
    val t0 = System.nanoTime()
    repeat(times) { block() }
    val ms = (System.nanoTime() - t0) / 1e6 / times
    val a1 = allocated()
    return Run(ms, if (a0 < 0 || a1 < 0) -1 else (a1 - a0) / times)
  }

  @Test fun `سنجش — رمزِ دفترِ ۲ و ۸ مگابایتی، پیش و پس`() {
    for ((mb, times) in listOf(2 to 8, 8 to 3)) {
      val plain = ByteArray(mb shl 20) { (it % 97 + 32).toByte() }
      //  هر دو قالب یکی‌اند: «پس» همان پاکتِ «پیش» را باز می‌کند و برعکس
      val opener: (String) -> SecretKey? = { if (it == kid) key else null }
      assertArrayEquals(plain, AtRestEnvelope.open(legacySeal(plain), opener))
      assertArrayEquals(plain, AtRestEnvelope.open(newSeal(plain), opener))

      val before = measure(times) { legacySeal(plain) }
      val after = measure(times) { newSeal(plain) }
      val open = measure(times) { AtRestEnvelope.open(newSeal(plain), opener) }
      println(
        "atrest-bench ${mb}MB: seal before %.1f ms / %.2f×ledger alloc; after %.1f ms / %.2f×ledger alloc; seal+open after %.1f ms"
          .format(before.ms, before.allocated / plain.size.toDouble(), after.ms, after.allocated / plain.size.toDouble(), open.ms),
      )
      if (before.allocated > 0 && after.allocated > 0) {
        assertTrue("بافرِ یک‌جا کمتر می‌گیرد (${after.allocated} در برابرِ ${before.allocated})", after.allocated < before.allocated * 3 / 4)
      }
    }
  }
}
