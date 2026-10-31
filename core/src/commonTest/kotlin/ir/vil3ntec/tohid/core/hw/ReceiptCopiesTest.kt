package ir.vil3ntec.tohid.core.hw

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  POS-35: شمارِ نسخهٔ رسیدِ خودکار — تنظیمِ هر دستگاه، پیش‌فرض یک (همان رفتارِ امروز).
 *  هر نسخه کارِ جدای صف است؛ نسخهٔ تکراریِ همان فروش در صف نمی‌نشیند.
 */
class ReceiptCopiesTest {

  private fun receipt(id: String, sale: String, copy: Int = 1) =
    PrintQueue.Job(id, PrintQueue.Kind.RECEIPT, "bt:AA", "فاکتور #1", 0L, saleId = sale, copy = copy)

  @Test
  fun defaultIsOneCopyLikeToday() {
    assertEquals(1, ReceiptCopies.DEFAULT)
    assertEquals(1, ReceiptCopies.parse(null))
    assertEquals(1, ReceiptCopies.parse(""))
    assertEquals(1, ReceiptCopies.parse("abc"))
    assertEquals(listOf(1), ReceiptCopies.plan(ReceiptCopies.DEFAULT))
    //  تک‌نسخه هیچ نشانی ندارد: همان کاغذِ امروز
    assertNull(ReceiptCopies.mark(1, 1))
    assertEquals("— چاپِ دوباره —", ReceiptCopies.retryMark(1, 1, "— چاپِ دوباره —"))
  }

  @Test
  fun copiesAreClampedToAllowedRange() {
    assertEquals(1, ReceiptCopies.parse("0"))
    assertEquals(1, ReceiptCopies.parse("-4"))
    assertEquals(2, ReceiptCopies.parse(" 2 "))
    assertEquals(3, ReceiptCopies.parse("3"))
    assertEquals(3, ReceiptCopies.parse("99"))
    assertEquals(listOf(1, 2, 3), ReceiptCopies.plan(7))
    assertEquals(listOf(1), ReceiptCopies.plan(0))
  }

  @Test
  fun secondCopyIsMarkedOriginalIsNot() {
    assertNull(ReceiptCopies.mark(1, 2))
    assertEquals("— نسخهٔ ۲ از ۲ —", ReceiptCopies.mark(2, 2))
    assertEquals("— نسخهٔ ۳ از ۳ —", ReceiptCopies.mark(3, 3))
    //  شمارِ کل هم به بازه می‌رود: «نسخهٔ ۲ از ۹» هرگز روی کاغذ نمی‌آید
    assertEquals("— نسخهٔ ۲ از ۳ —", ReceiptCopies.mark(2, 9))
    assertFailsWith<IllegalArgumentException> { ReceiptCopies.mark(0, 2) }
    assertFailsWith<IllegalArgumentException> { ReceiptCopies.mark(3, 2) }
  }

  @Test
  fun retryOfSecondCopyCarriesBothMarks() {
    assertEquals("— نسخهٔ ۲ از ۲ · چاپِ دوباره —", ReceiptCopies.retryMark(2, 2, "— چاپِ دوباره —"))
    assertEquals("نسخهٔ ۳ از ۳", ReceiptCopies.label(3, 3))
    assertNull(ReceiptCopies.label(1, 3))
    assertEquals("— چاپِ دوباره —", ReceiptCopies.retryMark(1, 2, "— چاپِ دوباره —"))
  }

  @Test
  fun eachCopyIsItsOwnQueueJobInOrder() {
    val q = PrintQueue()
    assertTrue(q.enqueue(receipt("a1", "s1", copy = 1)))
    assertTrue(q.enqueue(receipt("a2", "s1", copy = 2)))
    assertEquals(listOf("a1", "a2"), q.all().map { it.id })
    //  نسخهٔ دوم پشتِ اصل منتظر است و شکستِ اصل آن را جلو نمی‌اندازد
    assertEquals("a1", q.next(0L)?.id)
    q.failed("a1", "خاموش", 0L)
    assertNull(q.next(1L))
    q.succeeded("a1")
    assertEquals("a2", q.next(0L)?.id)
  }

  @Test
  fun sameCopyOfSameSaleIsNotQueuedTwice() {
    val q = PrintQueue()
    assertTrue(q.enqueue(receipt("a1", "s1", copy = 1)))
    assertTrue(q.enqueue(receipt("a2", "s1", copy = 2)))
    assertFalse(q.enqueue(receipt("b2", "s1", copy = 2)))
    assertFalse(q.enqueue(receipt("b1", "s1", copy = 1)))
    //  فروشِ دیگر جداست
    assertTrue(q.enqueue(receipt("c1", "s2", copy = 1)))
    assertEquals(listOf("a1", "a2", "c1"), q.all().map { it.id })
  }

  @Test
  fun singleCopyJobKeepsTodaysDefaults() {
    val j = PrintQueue.Job("x", PrintQueue.Kind.RECEIPT, "bt:AA", "t", 0L, saleId = "s")
    assertEquals(1, j.copy)
  }
}
