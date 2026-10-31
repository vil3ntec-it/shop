package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  بازپرداختِ سفارشِ تحویل‌شده ⇒ برنامهٔ برگشت در دفترِ دکان ([OrderRefunds]).
 *  هر گام یک سندِ برگشت با شناسهٔ ثابت است؛ دو دستگاه همان گام‌ها را می‌سازند.
 */
class OrderRefundsTest {

  private val order = "01JORDER0000000000000000AB"
  private fun ev(key: String, minor: Long, at: Long, source: String = "ledger") = OrderRefunds.Event(key, minor, at, source)

  @Test
  fun fullRefundReversesWholeSaleOnce() {
    val events = listOf(ev(OrderRefunds.FULL, 90_000, 2_000, "status"))
    val first = OrderRefunds.plan(90_000, 0, emptySet(), events)
    assertEquals(listOf(OrderRefunds.Step(OrderRefunds.FULL, 90_000, 2_000, "status")), first)
    //  همان رویداد پس از نشستن (سند هست) ⇒ هیچ
    assertEquals(emptyList(), OrderRefunds.plan(90_000, 90_000, setOf(OrderRefunds.FULL), events))
  }

  @Test
  fun duplicateEventInOnePageAppliesOnce() {
    val e = ev("r1", 30_000, 1_000)
    val steps = OrderRefunds.plan(90_000, 0, emptySet(), listOf(e, e, e.copy(amountMinor = 99_999)))
    assertEquals(listOf(OrderRefunds.Step("r1", 30_000, 1_000, "ledger")), steps)
  }

  @Test
  fun twoDevicesBuildTheSameDocumentIds() {
    val events = listOf(ev("r2", 10_000, 3_000), ev("r1", 20_000, 1_000), ev(OrderRefunds.FULL, 60_000, 3_000, "status"))
    val a = OrderRefunds.plan(90_000, 0, emptySet(), events)
    val b = OrderRefunds.plan(90_000, 0, emptySet(), events.reversed())
    assertEquals(a, b, "ترتیبِ رسیدن مهم نیست")
    assertEquals(listOf("r1", "r2", OrderRefunds.FULL), a.map { it.key })
    val ids = a.map { OrderRefunds.docIdOf(order, it.key) }
    assertEquals(ids, b.map { OrderRefunds.docIdOf(order, it.key) })
    //  اجتماعِ ردیف‌های دو دستگاه با شناسهٔ یکسان ⇒ یک سند برای هر رویداد
    assertEquals(3, (ids + ids).toSet().size)
  }

  @Test
  fun partialRefundsReverseExactlyTheRefundedAmount() {
    val steps = OrderRefunds.plan(90_000, 0, emptySet(), listOf(ev("r1", 12_345, 1_000), ev("r2", 5, 2_000)))
    assertEquals(listOf(12_345L, 5L), steps.map { it.amountMinor })
    //  دومین بار فقط r2 تازه است
    assertEquals(listOf("r2"), OrderRefunds.plan(90_000, 12_345, setOf("r1"), listOf(ev("r1", 12_345, 1_000), ev("r2", 5, 2_000))).map { it.key })
  }

  @Test
  fun gatewayAndStatusFullShareOneKeyAndNeverExceedTheSale() {
    //  r1 پیش از بازپرداختِ درگاه ⇒ `full` فقط مانده؛ ردیفِ دیرتر ⇒ صفر (فقط سابقه)
    val steps = OrderRefunds.plan(
      90_000, 0, emptySet(),
      listOf(ev("r1", 30_000, 1_000), ev(OrderRefunds.FULL, 90_000, 2_000, "gateway"), ev("r2", 10_000, 3_000)),
    )
    assertEquals(listOf("r1" to 30_000L, OrderRefunds.FULL to 60_000L, "r2" to 0L), steps.map { it.key to it.amountMinor })
    assertEquals(90_000L, steps.sumOf { it.amountMinor })
  }

  @Test
  fun manualReturnsAlreadyInTheLedgerAreNotReversedTwice() {
    //  صندوق پیش‌تر ۲۰۰ افغانی مرجوع داده ⇒ «بازپرداختِ کامل» فقط ماندهٔ فروش
    val steps = OrderRefunds.plan(90_000, 20_000, emptySet(), listOf(ev(OrderRefunds.FULL, 90_000, 5_000, "status")))
    assertEquals(70_000L, steps.single().amountMinor)
    //  همه برگشته ⇒ گامِ صفر (فقط سابقه تا سرور فهرستش نکند)
    assertEquals(0L, OrderRefunds.plan(90_000, 95_000, emptySet(), listOf(ev(OrderRefunds.FULL, 90_000, 5_000))).single().amountMinor)
  }

  @Test
  fun refundDayIsTheEventTimeNotTheSaleTime() {
    val saleAt = 1_000L
    val refundAt = 9 * 86_400_000L
    val step = OrderRefunds.plan(50_000, 0, emptySet(), listOf(ev("r1", 50_000, refundAt))).single()
    assertEquals(refundAt, step.at)
    assertTrue(step.at > saleAt)
  }

  @Test
  fun badKeysAndNegativeAmountsAreIgnored() {
    val steps = OrderRefunds.plan(
      90_000, 0, emptySet(),
      listOf(ev("x1", 1_000, 1), ev("r0", 1_000, 1), ev("", 1_000, 1), ev("r3", -5_000, 2)),
    )
    assertEquals(listOf("r3" to 0L), steps.map { it.key to it.amountMinor })
  }

  @Test
  fun stockReturnsOnlyForConfirmedProductLines() {
    val lines = listOf(
      OrderRefunds.Line(1, "p-rice", 1.5),
      OrderRefunds.Line(2, "", 1.0),                     // قلمِ آزاد (کیسه) ⇒ بی موجودی
      OrderRefunds.Line(3, "p-oil", 2.0),
      OrderRefunds.Line(4, "p-tea", 1.0, restocked = true),
      OrderRefunds.Line(5, "p-salt", 0.0),               // پیش‌تر کامل مرجوع شده
    )
    assertEquals(emptyList(), OrderRefunds.restockLines(lines, emptySet()), "پیش‌فرض: هیچ کالایی برنمی‌گردد")
    assertEquals(listOf(1), OrderRefunds.restockLines(lines, setOf(1, 2, 4, 5)).map { it.lineNo })
    assertEquals(listOf(1, 3), OrderRefunds.restockLines(lines, setOf(1, 3)).map { it.lineNo })
  }

  @Test
  fun pendingStockQuestionUntilDecided() {
    assertEquals(listOf("r1", "r2", OrderRefunds.FULL), OrderRefunds.pendingKeys(listOf(OrderRefunds.FULL, "r2", "r1", "r1"), emptySet()))
    assertEquals(listOf("r2"), OrderRefunds.pendingKeys(listOf("r1", "r2"), setOf("r1")))
    assertEquals(emptyList(), OrderRefunds.pendingKeys(listOf("r1"), setOf("r1")))
  }

  @Test
  fun idsAreStableAndParseBack() {
    assertEquals("o-$order-rf-r1", OrderRefunds.docIdOf(order, OrderRefunds.ledgerKey(1)))
    assertEquals("o-$order-rf-full-a", OrderRefunds.markIdOf(order, OrderRefunds.FULL))
    assertEquals("o-$order-rs-r1", OrderRefunds.decisionIdOf(order, "r1"))
    assertEquals("o-$order-2-rs", OrderRefunds.restockIdOf(order, 2))
    assertEquals("o-$order-2-rsm", OrderRefunds.restockMoveIdOf(order, 2))
    assertEquals("o-$order-2-rsr", OrderRefunds.restockReturnIdOf(order, 2))
    assertEquals("r12", OrderRefunds.keyOfDoc(order, OrderRefunds.docIdOf(order, "r12")))
    assertEquals(OrderRefunds.FULL, OrderRefunds.keyOfDoc(order, OrderRefunds.docIdOf(order, OrderRefunds.FULL)))
    assertNull(OrderRefunds.keyOfDoc(order, "o-$order-rs-r1"))
    assertNull(OrderRefunds.keyOfDoc("other", OrderRefunds.docIdOf(order, "r1")))
    assertNull(OrderRefunds.keyOfDoc(order, null))
    assertFalse(OrderRefunds.isKey("r01"))
  }
}
