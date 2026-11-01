package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Action
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Code
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Line
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Order
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Outcome
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Receipt
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.ReceiptLine
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.ReceiveResult
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.State
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Status
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 *  قاعده‌های اصلیِ سفارشِ خرید روی JVM و Kotlin/Native (شبیه‌سازِ iOS). بردارهای کامل (همان فایلِ
 *  سرور) در `jvmTest/.../PurchaseOrderVectorsTest` خوانده می‌شوند؛ این‌جا سناریوها با عددِ دستی.
 */
class PurchaseOrderCoreTest {

  private val s19 = Order(
    supplierId = "sup-1", state = State.DRAFT,
    lines = listOf(Line("l1", "pA", 10_000, 10_000), Line("l2", "pB", 10_000, 20_000), Line("l3", "pC", 10_000, 5_000)),
  )

  private fun rl(id: String, rec: Long, dmg: Long, price: Long) = ReceiptLine(id, rec, dmg, price)

  private fun ok(r: ReceiveResult): ReceiveResult.Ok = assertIs<ReceiveResult.Ok>(r, r.toString())

  private fun failed(r: ReceiveResult): String = assertIs<ReceiveResult.Failed>(r, r.toString()).failure.code

  /** S19: سه قلم، هر کدام ۱۰؛ A ۱۰، B ۷، C ۱۰ که ۲ خراب ⇒ موجودی +۱۰/+۷/+۸، باقی‌ماندهٔ B و C باز */
  @Test fun s19PartialReceiptWithDamagedKeepsRemainderOpen() {
    val sent = s19.copy(state = (PurchaseOrderCore.send(s19, emptyList()) as Outcome.Changed).state)
    assertEquals(Status.SENT, PurchaseOrderCore.status(sent, emptyList()))

    val r1 = Receipt("r1", listOf(rl("l1", 10_000, 0, 10_000), rl("l2", 7_000, 0, 20_000), rl("l3", 10_000, 2_000, 5_000)))
    val got = ok(PurchaseOrderCore.receive(sent, emptyList(), r1))
    assertEquals(listOf(10_000L, 7_000L, 8_000L), got.lines.map { it.acceptedMilli }, "موجودی‌ها: A +۱۰، B +۷، C +۸")
    assertEquals(2_000L, got.lines[2].damagedMilli, "اختلافِ ۲ عددِ C ثبت شده")
    assertEquals(3_000L, got.lines[1].shortMilli)
    assertEquals(280_000L, got.totalMinor, "۱۰۰۰ + ۱۴۰۰ + ۴۰۰ افغانی")
    assertEquals(Status.PARTIAL, got.status)

    val p = PurchaseOrderCore.progress(sent, listOf(got.receipt))
    assertEquals(listOf(0L, 3_000L, 2_000L), p.map { it.remainingMilli }, "خراب از باقی‌مانده کم نمی‌کند")

    val r2 = Receipt("r2", listOf(rl("l2", 3_000, 0, 20_000), rl("l3", 2_000, 0, 5_000)))
    val done = ok(PurchaseOrderCore.receive(sent, listOf(got.receipt), r2))
    assertEquals(Status.COMPLETE, done.status)
    assertTrue(PurchaseOrderCore.allowed(Status.COMPLETE).isEmpty())
  }

  @Test fun sameKeyIsOneReceiptEvenAfterCompletion() {
    val sent = s19.copy(state = State.SENT)
    val all = Receipt("k", s19.lines.map { rl(it.id, it.qtyMilli, 0, it.priceMinor) })
    val first = ok(PurchaseOrderCore.receive(sent, emptyList(), all))
    assertEquals(Status.COMPLETE, first.status)
    //  تلاشِ دوباره (شبکه قطع شد پس از ثبت) ⇒ همان رسید، نه «کامل است»
    val again = PurchaseOrderCore.receive(sent, listOf(first.receipt), all.copy(lines = all.lines.reversed() + rl("l1", 0, 0, 0)))
    assertEquals(first.receipt, assertIs<ReceiveResult.Duplicate>(again).receipt)
    val other = all.copy(lines = all.lines.map { it.copy(receivedMilli = 1) })
    assertEquals(Code.ID_CONFLICT, failed(PurchaseOrderCore.receive(sent, listOf(first.receipt), other)))
  }

  @Test fun transitions() {
    assertEquals(setOf(Action.EDIT, Action.SEND, Action.CANCEL), PurchaseOrderCore.allowed(Status.DRAFT))
    assertEquals(Code.NOT_SENT, failed(PurchaseOrderCore.receive(s19, emptyList(), Receipt("r", listOf(rl("l1", 1, 0, 1))))))
    assertEquals(Outcome.Changed(State.CANCELLED), PurchaseOrderCore.cancel(s19, emptyList(), null))

    val sent = s19.copy(state = State.SENT)
    assertEquals(Outcome.Unchanged, PurchaseOrderCore.send(sent, emptyList()))
    assertEquals(Code.REASON_REQUIRED, (PurchaseOrderCore.cancel(sent, emptyList(), " \t\n") as Outcome.Failed).failure.code)
    assertEquals(Outcome.Changed(State.CANCELLED), PurchaseOrderCore.cancel(sent, emptyList(), "دیر شد"))
    assertEquals(Code.NOT_DRAFT, (PurchaseOrderCore.edit(sent, emptyList(), s19) as Outcome.Failed).failure.code)

    val cancelled = s19.copy(state = State.CANCELLED)
    assertEquals(Outcome.Unchanged, PurchaseOrderCore.cancel(cancelled, emptyList(), null))
    assertEquals(Code.CANCELLED, (PurchaseOrderCore.send(cancelled, emptyList()) as Outcome.Failed).failure.code)
  }

  @Test fun overReceiptNeedsExplicitConsent() {
    val sent = Order("s", State.SENT, listOf(Line("l1", "p", 10_000, 1_000)))
    val r = Receipt("r", listOf(rl("l1", 12_000, 0, 1_000)))
    val f = assertIs<ReceiveResult.Failed>(PurchaseOrderCore.receive(sent, emptyList(), r)).failure
    assertEquals(Code.OVER_ORDERED, f.code)
    assertEquals("l1", f.lineId)
    val got = ok(PurchaseOrderCore.receive(sent, emptyList(), r, allowOver = true))
    assertEquals(2_000L, got.lines.single().overMilli)
    assertEquals(Status.COMPLETE, got.status)
  }

  @Test fun allDamagedStaysSent() {
    val sent = Order("s", State.SENT, listOf(Line("l1", "p", 10_000, 1_000)))
    val got = ok(PurchaseOrderCore.receive(sent, emptyList(), Receipt("r", listOf(rl("l1", 3_000, 3_000, 1_000)))))
    assertEquals(0L, got.totalMinor)
    assertEquals(Status.SENT, got.status)
  }

  @Test fun checkOrderLimits() {
    fun many(n: Int) = Order("s", State.DRAFT, (0 until n).map { Line("l$it", "p$it", 1_000, 1) })
    assertNull(PurchaseOrderCore.checkOrder(many(PurchaseOrderCore.MAX_LINES)))
    assertEquals(Code.TOO_MANY_LINES, PurchaseOrderCore.checkOrder(many(PurchaseOrderCore.MAX_LINES + 1))?.code)
    assertEquals(Code.DUPLICATE_PRODUCT, PurchaseOrderCore.checkOrder(Order("s", State.DRAFT, listOf(Line("a", "p", 1, 1), Line("b", "p", 1, 1))))?.code)
    assertEquals(Code.LINE_ID_REQUIRED, PurchaseOrderCore.checkOrder(Order("s", State.DRAFT, listOf(Line("ردیف", "p", 1, 1))))?.code)
    val m = PurchaseOrderCore.MAX_SAFE
    assertEquals(Code.OVERFLOW, PurchaseOrderCore.checkOrder(Order("s", State.DRAFT, listOf(Line("a", "p", m, m))))?.code)
  }

  @Test fun mulDivRoundIsExactWithoutBigInteger() {
    val m = PurchaseOrderCore.MAX_SAFE
    assertEquals(8_250L, PurchaseOrderCore.lineTotalMinor(2_500, 3_300), "۲٫۵ × ۳۳ = ۸۲٫۵۰")
    assertEquals(1L, PurchaseOrderCore.mulDivRound(1, 1, 2))
    assertEquals(0L, PurchaseOrderCore.mulDivRound(-1, 1, 2), "نیمه به +∞")
    assertEquals(-2L, PurchaseOrderCore.mulDivRound(-5, 5, 10))
    assertEquals(3_860_228_252_031_853L, PurchaseOrderCore.mulDivRound(m, 3, 7))
    assertEquals(-3_860_228_252_031_853L, PurchaseOrderCore.mulDivRound(-m, 3, 7))
    assertEquals(m, PurchaseOrderCore.mulDivRound(m, 1_000, 1_000))
    assertFailsWith<ArithmeticException> { PurchaseOrderCore.mulDivRound(m, 2, 1) }
    assertFailsWith<ArithmeticException> { PurchaseOrderCore.mulDivRound(m, m, 1_000) }
    assertFailsWith<IllegalArgumentException> { PurchaseOrderCore.mulDivRound(1, 1, 0) }
    //  برابر با فرمولِ مستقیم وقتی ضرب در Long جا می‌شود
    for (a in listOf(-7_777L, -1L, 0L, 1L, 999L, 123_456L)) for (b in listOf(-3_301L, 0L, 1L, 4_999L)) for (c in listOf(1L, 2L, 3L, 7L, 1_000L, 10_000L)) {
      assertEquals((2 * a * b + c).floorDiv(2 * c), PurchaseOrderCore.mulDivRound(a, b, c), "$a×$b/$c")
    }
  }

  @Test fun matchFlagsPriceAndQuantity() {
    val sent = s19.copy(state = State.SENT)
    val r1 = Receipt("r1", listOf(rl("l1", 10_000, 0, 10_000), rl("l2", 7_000, 0, 20_000), rl("l3", 10_000, 2_000, 5_000)))
    val bills = listOf(
      PurchaseOrderCore.Bill("l1", "pA", 10_000, 100_000),
      PurchaseOrderCore.Bill("l2", "pB", 7_000, 147_000),
      PurchaseOrderCore.Bill(null, "pC", 10_000, 50_000),
    )
    val strict = PurchaseOrderCore.match(sent, listOf(r1), bills)
    assertEquals(false, strict.matched)
    assertEquals(listOf(PurchaseOrderCore.Issue.PRICE_OVER_ORDER), strict.lines[1].issues)
    assertEquals(listOf(PurchaseOrderCore.Issue.BILLED_OVER_RECEIVED), strict.lines[2].issues, "۲ خراب هم فاکتور شده")
    val loose = PurchaseOrderCore.match(sent, listOf(r1), bills, PurchaseOrderCore.Tolerance(qtyMilli = 2_000, priceBp = 500))
    assertTrue(loose.matched, loose.toString())
  }
}
