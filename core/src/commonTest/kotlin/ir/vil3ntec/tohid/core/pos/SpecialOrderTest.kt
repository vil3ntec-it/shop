package ir.vil3ntec.tohid.core.pos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** POS-26 — قاعده‌های سفارشِ ویژه (`SpecialOrder`) روی هر سکو؛ بردارِ کامل در `jvmTest/…/SpecialOrderVectorsTest` */
class SpecialOrderTest {

  @Test
  fun itemAndReasonAreOneCleanLine() {
    assertEquals("یخچال سفید ۱۲ فوت", SpecialOrder.item("  یخچال\nسفید\t ۱۲ فوت "))
    assertNull(SpecialOrder.item(" \n "))
    assertNull(SpecialOrder.item(null))
    assertEquals(SpecialOrder.ITEM_MAX, SpecialOrder.item("x".repeat(500))!!.length)
    //  نیم‌فاصله فاصله نیست: «می‌خواهد» دست نمی‌خورد
    assertEquals("مشتری نمی‌خواهد", SpecialOrder.reason(" مشتری  نمی‌خواهد "))
  }

  @Test
  fun statusCodesRoundTrip() {
    for (s in SpecialOrder.Status.entries) assertEquals(s, SpecialOrder.Status.of(s.code))
    assertNull(SpecialOrder.Status.of("OPEN"))
    assertTrue(SpecialOrder.Status.DELIVERED.final)
    assertTrue(SpecialOrder.Status.CANCELLED.final)
    assertFalse(SpecialOrder.Status.READY.final)
  }

  @Test
  fun finalStatusesNeverMove() {
    for (from in listOf(SpecialOrder.Status.DELIVERED, SpecialOrder.Status.CANCELLED)) {
      for (to in SpecialOrder.Status.entries) assertFalse(SpecialOrder.canMove(from, to), "$from ⇒ $to")
    }
    //  هیچ وضعیتی به «باز» برنمی‌گردد
    for (from in SpecialOrder.Status.entries) assertFalse(SpecialOrder.canMove(from, SpecialOrder.Status.OPEN))
  }

  @Test
  fun settleAlwaysAddsUpToTheSale() {
    for (total in listOf(0L, 100L, 25_000_00L, 9_999_900L)) {
      for (held in listOf(0L, 100L, 5_000_00L, 30_000_00L)) {
        val s = SpecialOrder.settle(total, held)
        assertEquals(total, s.applyMinor + s.payNowMinor)
        assertEquals(held, s.applyMinor + s.leftMinor)
        assertTrue(s.applyMinor >= 0 && s.payNowMinor >= 0 && s.leftMinor >= 0)
      }
    }
  }

  @Test
  fun foldIsOrderIndependent() {
    val a = SpecialOrder.Event("a", 5, "ready")
    val b = SpecialOrder.Event("b", 9, "delivered")
    val c = SpecialOrder.Event("c", 9, "cancelled")
    assertEquals(SpecialOrder.fold(listOf(a, b, c)), SpecialOrder.fold(listOf(c, b, a)))
    assertEquals(SpecialOrder.Status.DELIVERED, SpecialOrder.fold(listOf(c, a, b)).status)
  }
}
