package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** ORD-14: دکمه‌ها و پیش‌نمایشِ «ویرایشِ سفارش» همان قاعده‌های `lib/order-amend.js` */
class OrderAmendTest {

  private val st = listOf("new", "accepted")
  //  همان سفارشِ `order-amend.test.js`: برنج ۲ × ۱۲۰، روغن ۱ × ۳۳۳٫۳۳، نمک ۱ × ۲۰
  private val lines = listOf(
    OrderAmend.Line(1, 2000, 12000, 24000),
    OrderAmend.Line(2, 1000, 33333, 33300),
    OrderAmend.Line(3, 1000, 2000, 2000),
  )
  private val total = 59300L

  @Test
  fun buttonByFlagStatusPaymentAndPermission() {
    assertTrue(OrderAmend.can("new", "unpaid", st, paidAllowed = false, manage = true))
    assertFalse(OrderAmend.can("picking", "unpaid", st, false, true), "جمع‌آوری شروع شده")
    assertFalse(OrderAmend.can("new", "unpaid", null, false, true), "پرچمِ خاموش")
    assertFalse(OrderAmend.can("new", "unpaid", st, false, manage = false))
    assertFalse(OrderAmend.can("new", "paid", st, paidAllowed = false, manage = true), "پرداخت‌شده بی amend_paid")
    assertTrue(OrderAmend.can("new", "paid", st, paidAllowed = true, manage = true))
    for (p in listOf("pending", "partially_refunded", "refunded")) assertFalse(OrderAmend.can("new", p, st, true, true), p)
    assertTrue(OrderAmend.needsConsent("phone"))
    assertFalse(OrderAmend.needsConsent("storefront"))
  }

  @Test
  fun planMatchesServerRounding() {
    val p = OrderAmend.plan(lines, mapOf(1 to 3000L, 2 to 1000L, 3 to 0L), 0, 0, total)
    assertEquals(OrderAmend.Problem.NONE, p.problem)
    assertEquals(listOf(OrderAmend.Change(1, 3000), OrderAmend.Change(3, 0)), p.changes, "قلمِ دست‌نخورده نمی‌رود")
    assertEquals(36000 + 33300L, p.subtotalMinor)
    assertEquals(69300L, p.totalMinor)
    assertEquals(10000L, p.differenceMinor)
    //  گردکردن: ۰٫۵ × ۳۳۳٫۳۳ = ۱۶۶٫۶۶۵ ⇒ ۱۶۷
    assertEquals(24000 + 16700 + 2000 + 4000L - 1000, OrderAmend.plan(lines, mapOf(2 to 500L), 1000, 4000, total).totalMinor)
  }

  @Test
  fun planProblems() {
    assertEquals(OrderAmend.Problem.NO_CHANGE, OrderAmend.plan(lines, mapOf(1 to 2000L), 0, 0, total).problem)
    assertEquals(OrderAmend.Problem.BAD_QTY, OrderAmend.plan(lines, mapOf(1 to null), 0, 0, total).problem)
    assertEquals(OrderAmend.Problem.BAD_QTY, OrderAmend.plan(lines, mapOf(1 to OrderAmend.MAX_QTY_MILLI + 1), 0, 0, total).problem)
    assertEquals(OrderAmend.Problem.ALL_REMOVED, OrderAmend.plan(lines, mapOf(1 to 0L, 2 to 0L, 3 to 0L), 0, 0, total).problem)
    assertEquals(OrderAmend.Problem.DISCOUNT, OrderAmend.plan(lines, mapOf(2 to 0L), 50000, 0, total).problem)
  }

  @Test
  fun reasonConsentSettleWithdraw() {
    assertTrue(OrderAmend.valid("", "", "phone", staffOrder = true))
    assertFalse(OrderAmend.valid("", "", "", staffOrder = true), "سفارشِ کارمند بی رضایت نه")
    assertTrue(OrderAmend.valid("out_of_stock", "", "", staffOrder = false))
    assertFalse(OrderAmend.valid("other", " ", "phone", staffOrder = true))
    assertFalse(OrderAmend.valid("lunch", "", "phone", staffOrder = true))
    assertTrue(OrderAmend.canSettle("applied", "refund", 2000, null, refund = true))
    assertFalse(OrderAmend.canSettle("applied", "refund", 2000, 1L, refund = true), "یک بار")
    assertFalse(OrderAmend.canSettle("applied", "none", 0, null, refund = true))
    assertFalse(OrderAmend.canSettle("pending", "charge", 100, null, refund = true))
    assertFalse(OrderAmend.canSettle("applied", "charge", 100, null, refund = false))
    assertTrue(OrderAmend.canWithdraw("pending", manage = true))
    assertFalse(OrderAmend.canWithdraw("applied", manage = true))
  }
}
