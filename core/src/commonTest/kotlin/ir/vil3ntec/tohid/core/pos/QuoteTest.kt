package ir.vil3ntec.tohid.core.pos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** POS-25 — قاعده‌های پیش‌فاکتور (`Quote`): اعتبار، وضعیت، شماره، ساختن و تبدیل به سبد */
class QuoteTest {

  @Test
  fun validityIsInclusiveAndBounded() {
    assertEquals("2026-11-08", Quote.validUntil("2026-11-01", 7))
    assertEquals("2027-01-30", Quote.validUntil("2026-11-01", Quote.MAX_DAYS))
    assertEquals("2026-03-01", Quote.validUntil("2026-02-28", 1))
    assertNull(Quote.validUntil("2026-11-01", 0))
    assertNull(Quote.validUntil("2026-11-01", Quote.MAX_DAYS + 1))
    assertNull(Quote.validUntil("2026-13-01", 7))
    assertEquals(0, Quote.daysLeft("2026-11-08", "2026-11-08"))
    assertEquals(-1, Quote.daysLeft("2026-11-08", "2026-11-09"))
  }

  @Test
  fun stateOrder() {
    assertEquals(Quote.State.VALID, Quote.state("2026-11-08", "2026-11-08", inCart = false, sold = false))
    assertEquals(Quote.State.EXPIRED, Quote.state("2026-11-08", "2026-11-09", inCart = true, sold = false))
    assertEquals(Quote.State.SOLD, Quote.state("2026-11-08", "2026-11-20", inCart = true, sold = true))
    assertEquals(Quote.State.IN_CART, Quote.state("2026-11-08", "2026-11-02", inCart = true, sold = false))
    //  تاریخِ خراب ⇒ گذشته (قیمتی که روزش معلوم نیست قول داده نمی‌شود)
    assertEquals(Quote.State.EXPIRED, Quote.state("bad", "2026-11-02", inCart = false, sold = false))
  }

  @Test
  fun createAndConvertProblems() {
    assertEquals(1, Quote.nextNumber(emptyList()))
    assertEquals(8, Quote.nextNumber(listOf(3, 7, 1)))
    assertEquals("EMPTY", Quote.createProblem(0, 0, "2026-11-01", 7))
    assertEquals("TOO_MANY", Quote.createProblem(2, Quote.MAX_OPEN, "2026-11-01", 7))
    assertEquals("BAD_DAYS", Quote.createProblem(2, 0, "2026-11-01", 0))
    assertNull(Quote.createProblem(2, 0, "2026-11-01", 7))
    assertEquals("SOLD", Quote.convertProblem(Quote.State.SOLD, 2, true))
    assertEquals("EXPIRED", Quote.convertProblem(Quote.State.EXPIRED, 2, true))
    assertEquals("EMPTY", Quote.convertProblem(Quote.State.VALID, 0, true))
    assertEquals("CART_BUSY", Quote.convertProblem(Quote.State.VALID, 2, false))
    assertNull(Quote.convertProblem(Quote.State.IN_CART, 2, true))
    assertEquals("آقای کریمی — تحویل فردا", Quote.note("  آقای کریمی\n— تحویل   فردا "))
    assertNull(Quote.note("   "))
    assertEquals(Quote.NOTE_MAX, Quote.note("ا".repeat(500))!!.length)
  }
}
