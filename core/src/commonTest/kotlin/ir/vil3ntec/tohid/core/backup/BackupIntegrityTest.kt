package ir.vil3ntec.tohid.core.backup

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  OPS-02 — رکوردِ پایانیِ ZIP و مُهرِ بسته (`BackupIntegrity.kt`). سرتاسری با ZIP ِ واقعی: `BackupRestoreDrillTest`.
 */
class BackupIntegrityTest {

  /** رکوردِ END با [entries] قلم و توضیحِ [comment] بایتی */
  private fun end(entries: Int, comment: Int = 0): ByteArray {
    val b = ByteArray(22 + comment)
    fun le(at: Int, v: Int, n: Int) { for (k in 0 until n) b[at + k] = ((v shr (8 * k)) and 0xFF).toByte() }
    le(0, 0x06054b50, 4)
    le(8, entries, 2)
    le(10, entries, 2)
    le(20, comment, 2)
    for (k in 0 until comment) b[22 + k] = 'x'.code.toByte()
    return b
  }

  private val body = ByteArray(300) { (it * 31).toByte() }

  @Test
  fun completeOnlyWithTheEndRecordAndTheSameEntryCount() {
    assertTrue(ZipTail.complete(body + end(3), 3))
    assertTrue(ZipTail.complete(body + end(3, comment = 40), 3), "با توضیح")
    assertFalse(ZipTail.complete(body + end(4), 3), "قلمی خوانده نشد")
    assertFalse(ZipTail.complete(body, 3), "بی رکوردِ پایانی: بریده")
    assertFalse(ZipTail.complete(body + end(3).copyOf(21), 3), "رکوردِ پایانیِ نیمه")
    assertFalse(ZipTail.complete(body + end(3) + ByteArray(5), 3), "بایتِ اضافه پس از توضیح")
    assertFalse(ZipTail.complete(ByteArray(0), 0))
    //  ZIP64: شمارِ ‎0xFFFF‎
    assertTrue(ZipTail.complete(body + end(0xFFFF), 70_000))
    assertFalse(ZipTail.complete(body + end(0xFFFF), 12))
  }

  @Test
  fun tailKeepsOnlyTheLastBytesWhateverTheChunkSizes() {
    val all = ByteArray(10_000) { (it % 251).toByte() }
    for (chunk in listOf(1, 7, 500, 4096, 10_000)) {
      val t = ZipTail.Tail(cap = 1000)
      var at = 0
      while (at < all.size) {
        val n = minOf(chunk, all.size - at)
        t.add(all, at, n)
        at += n
      }
      assertContentEquals(all.copyOfRange(all.size - 1000, all.size), t.bytes(), "تکه‌های $chunk")
    }
    val short = ZipTail.Tail(cap = 1000).also { it.add(all, 0, 10) }
    assertContentEquals(all.copyOf(10), short.bytes())
  }

  @Test
  fun stampJudgesLikeLedgerOwner() {
    val a = BackupStamp.Stamp(1, "u-a", "s-a")
    assertEquals(BackupStamp.Verdict.OK, BackupStamp.judge(a, "u-a", "s-a"))
    assertEquals(BackupStamp.Verdict.OK, BackupStamp.judge(a, "u-staff", "s-a"), "کارمندِ همان دکان")
    assertEquals(BackupStamp.Verdict.OTHER_SHOP, BackupStamp.judge(a, "u-b", "s-b"))
    assertEquals(BackupStamp.Verdict.OTHER_SHOP, BackupStamp.judge(a, "u-a", "s-b"), "همان حساب، دکانِ دیگر")
    assertEquals(BackupStamp.Verdict.OTHER_SHOP, BackupStamp.judge(BackupStamp.Stamp(1, "u-a", ""), "u-b", ""), "بی دکان، حسابِ دیگر")
    assertEquals(BackupStamp.Verdict.OK, BackupStamp.judge(BackupStamp.Stamp(1, "u-a", ""), "u-a", "s-a"), "همان حساب پیش از دکان")
    assertEquals(BackupStamp.Verdict.OK, BackupStamp.judge(a, "", ""), "دفترِ بی‌صاحب")
    assertEquals(BackupStamp.Verdict.OK, BackupStamp.judge(BackupStamp.LEGACY, "u-b", "s-b"), "پشتیبانِ کهنهٔ بی مُهر")
    assertEquals(BackupStamp.Verdict.NEWER_FORMAT, BackupStamp.judge(BackupStamp.Stamp(BackupStamp.FORMAT_VERSION + 1, "u-a", "s-a"), "u-a", "s-a"))
  }
}
