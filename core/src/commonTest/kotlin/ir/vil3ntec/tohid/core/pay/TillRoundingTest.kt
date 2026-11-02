package ir.vil3ntec.tohid.core.pay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  PAY-09 — گردکردنِ نقدی در صندوق: بردارهای خاموش، پلهٔ ۵ و ۱۰، نقدِ کامل و ترکیبی، برگشتِ فاکتورِ گردشده
 *  و نقدِ موردِ انتظارِ شیفت. همه به پول (۱ افغانی = ۱۰۰).
 */
class TillRoundingTest {

  private fun <T> ok(r: PayResult<T>): T = (r as PayResult.Ok).value

  private fun settle(total: Long, step: Long, vararg tenders: Tender, customer: String? = null): Settlement =
    ok(Pay.settle(SettleInput(total = total, tenders = tenders.toList(), cashRounding = step, customerId = customer)))

  /** (جمعِ فاکتور، پله) ⇒ ردیفِ گرد */
  private val vectors = listOf(
    Triple(12_300L, 0L, 0L), Triple(12_800L, 0L, 0L),
    Triple(12_300L, 500L, 200L), Triple(12_200L, 500L, -200L), Triple(12_250L, 500L, 250L),
    Triple(12_800L, 500L, 200L), Triple(12_500L, 500L, 0L), Triple(12_700L, 500L, -200L),
    Triple(12_300L, 1_000L, -300L), Triple(12_500L, 1_000L, 500L), Triple(12_600L, 1_000L, 400L),
    Triple(12_000L, 1_000L, 0L), Triple(700L, 500L, -200L), Triple(1_200L, 1_000L, -200L),
    //  کوچک‌تر از یک پله، و فاکتورِ صفر ⇒ بی گرد
    Triple(200L, 500L, 0L), Triple(400L, 500L, 0L), Triple(900L, 1_000L, 0L), Triple(0L, 1_000L, 0L),
  )

  @Test
  fun cashOnlyVectorsMatchTheCoreSettle() {
    for ((total, step, want) in vectors) {
      assertEquals(want, TillRounding.cash(total, step), "cash($total, $step)")
      //  همان عددِ `Pay.settle` وقتی نقدِ دقیقِ گردشده داده شود
      if (total > 0) {
        val s = settle(total, TillRounding.stepFor(total, step), Tender("cash", total + want))
        assertEquals(want, s.rounding, "settle($total, $step)")
        assertEquals(total + want, s.due)
        assertEquals(0L, s.change)
        assertEquals("paid", s.status)
      }
    }
  }

  @Test
  fun offOrUnknownStepChangesNothing() {
    assertEquals(0L, TillRounding.step(null))
    assertEquals(0L, TillRounding.step(250L))
    assertEquals(0L, TillRounding.step(-500L))
    assertEquals(500L, TillRounding.step(500L))
    assertEquals(1_000L, TillRounding.step(1_000L))
    assertEquals(0L, TillRounding.cash(12_300L, 250L))
    val off = settle(12_300L, 0L, Tender("cash", 20_000L))
    assertEquals(0L, off.rounding)
    assertEquals(12_300L, off.due)
    assertEquals(7_700L, off.change)
  }

  @Test
  fun mixedRoundsOnlyTheCashPartAndOnlyWhenCashClosesTheInvoice() {
    //  ۱۲۳ = کیف پول ۱۰۰ + نقد؛ سهمِ نقد ۲۳ ⇒ ۲۵
    val mixed = settle(12_300L, 500L, Tender("hesabpay", 10_000L, ref = "R1"), Tender("cash", 3_000L))
    assertEquals(200L, mixed.rounding)
    assertEquals(12_500L, mixed.due)
    assertEquals(500L, mixed.change)
    assertEquals(2_500L, mixed.payments.single { it.kind == Pay.CASH }.amount)
    //  سهمِ نقد ۲۲ ⇒ ۲۰ (گردِ منفی)
    val down = settle(12_200L, 500L, Tender("hesabpay", 10_000L, ref = "R1"), Tender("cash", 2_000L))
    assertEquals(-200L, down.rounding)
    assertEquals(2_000L, down.payments.single { it.kind == Pay.CASH }.amount)
    //  غیرنقدِ تنها، و ماندهٔ به حساب ⇒ بی گرد
    assertEquals(0L, settle(12_300L, 500L, Tender("hesabpay", 12_300L, ref = "R1")).rounding)
    val credit = settle(12_300L, 500L, Tender("cash", 5_000L), customer = "c1")
    assertEquals(0L, credit.rounding)
    assertEquals(7_300L, credit.toAccount)
  }

  @Test
  fun refundOfARoundedSaleNeverPaysOutMoreThanWasPaid() {
    //  گردِ منفی: کالا ۱۲۷ + ۱ = ۱۲۸، پرداختی ۱۲۵
    val first = TillRounding.refund(listOf(12_700L), paidMinor = 12_500L, refundedBeforeMinor = 0L, completes = false)
    assertEquals(listOf(12_500L), first)
    assertEquals(listOf(0L), TillRounding.refund(listOf(100L), 12_500L, 12_500L, completes = true))
    //  یک‌جا: از ردیفِ آخر کم می‌شود
    assertEquals(listOf(12_500L, 0L), TillRounding.refund(listOf(12_700L, 100L), 12_500L, 0L, completes = true))
    //  گردِ مثبت: ۴۱ × ۳ = ۱۲۳، پرداختی ۱۲۵ ⇒ ۴۱ + ۴۱ + (۴۱ + ۲)
    val paid = 12_500L
    var before = 0L
    val got = ArrayList<Long>()
    for (k in 1..3) {
      val r = TillRounding.refund(listOf(4_100L), paid, before, completes = k == 3).single()
      got += r
      before += r
    }
    assertEquals(listOf(4_100L, 4_100L, 4_300L), got)
    assertEquals(paid, got.sum())
    //  جزئیِ گردِ مثبت همان کالاست؛ بی برگشتِ کامل گرد پس داده نمی‌شود
    assertEquals(listOf(4_100L, 4_100L), TillRounding.refund(listOf(4_100L, 4_100L), paid, 0L, completes = false))
  }

  @Test
  fun anyOrderOfPartialReturnsStaysWithinWhatWasPaid() {
    //  سه قلم، هر برگشت یک قلم؛ همهٔ ترتیب‌ها و هر دو علامتِ گرد
    for ((items, paid) in listOf(listOf(4_300L, 4_300L, 4_300L) to 13_000L, listOf(12_700L, 100L, 0L) to 12_500L, listOf(4_100L, 4_100L, 4_100L) to 12_500L)) {
      for (order in listOf(listOf(0, 1, 2), listOf(2, 1, 0), listOf(1, 0, 2))) {
        var before = 0L
        order.forEachIndexed { n, i ->
          val r = TillRounding.refund(listOf(items[i]), paid, before, completes = n == order.lastIndex).single()
          assertTrue(r >= 0L)
          before += r
          assertTrue(before <= paid, "$items $order: $before > $paid")
        }
        assertEquals(paid, before, "$items $order")
      }
    }
  }

  @Test
  fun shiftExpectedCashCountsTheRoundingLine() {
    var s = ok(ShiftCore.open(OpenShiftInput(id = "open-1", at = 1_000, float = 100_000)))
    //  نقدِ کامل ۱۲۳ ⇒ ۱۲۵ (مشتری ۲۰۰ داد، ۷۵ پس)
    val a = settle(12_300L, 500L, Tender("cash", 20_000L))
    assertEquals(7_500L, a.change)
    //  ترکیبی ۱۲۲: کیف پول ۱۰۰ + نقد ۲۰
    val b = settle(12_200L, 500L, Tender("hesabpay", 10_000L, ref = "R1"), Tender("cash", 2_000L))
    for ((id, st) in listOf("sale-a" to a, "sale-b" to b)) {
      s = ok(ShiftCore.record(s, ShiftEventInput(id = id, type = "sale", at = 2_000, rows = st.payments.map { ShiftRow(it.method, it.amount) })))
    }
    //  برگشتِ کاملِ فاکتورِ نخست: همان ۱۲۵ ِ پرداختی، نه ۱۲۳ و نه بیشتر
    val back = TillRounding.refund(listOf(12_300L), paidMinor = a.due, refundedBeforeMinor = 0L, completes = true).single()
    assertEquals(12_500L, back)
    s = ok(ShiftCore.record(s, ShiftEventInput(id = "ref-a", type = "refund", at = 3_000, rows = listOf(ShiftRow("cash", back)))))
    val x = ok(ShiftCore.xReport(s, 4_000))
    assertEquals(100_000L + 12_500L + 2_000L - 12_500L, x.expected!!.getValue("cash"))
    assertEquals(12_500L + 2_000L, x.methods.getValue("cash").sales)
  }
}
