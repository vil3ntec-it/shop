package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.pay.PayStatus.Change
import ir.vil3ntec.tohid.core.pay.PayStatus.Row
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PAY-07 — وضعیتِ ردیف‌های غیرنقدِ فاکتور: گذار، اجازه، دو دستگاه، پولِ دریافتی و بازپرداخت */
class PayStatusTest {
  private val card = Row("afpay_card", "card", 150_000, "pending", "manual")
  private val cash = Row("cash", "cash", 50_000, "confirmed")
  private val gateway = Row("hesabpay", "wallet", 80_000, "confirmed", "gateway")
  private val rows = listOf(cash, card, gateway)

  private fun input(
    to: String, index: Int = 1, sell: Boolean = true, manage: Boolean = false, existing: List<Change> = emptyList(),
    id: String = "c1", by: String? = "سارا", note: String? = null, at: Long = 1_000,
  ) = PayStatus.Input(id, "s1", rows, index, to, at, by, sell, manage, note, existing = existing)

  private fun ok(r: PayResult<Change>) = (r as PayResult.Ok).value
  private fun err(r: PayResult<Change>) = (r as PayResult.Err).code

  @Test
  fun cashierConfirmsOrFlagsPendingOnly() {
    val c = ok(PayStatus.check(input("confirmed")))
    assertEquals(Change("c1", "s1", 1, "afpay_card", "pending", "confirmed", 1_000, "سارا"), c)
    assertEquals("needs_review", ok(PayStatus.check(input("needs_review"))).to)
    assertEquals("PERM_DENIED", err(PayStatus.check(input("failed"))))
    assertEquals("PERM_DENIED", err(PayStatus.check(input("cancelled"))))
    //  حلِ «نیازمند بررسی» فقط صاحب/مدیر
    val flagged = ok(PayStatus.check(input("needs_review")))
    assertEquals("PERM_DENIED", err(PayStatus.check(input("confirmed", id = "c2", existing = listOf(flagged)))))
    assertEquals("confirmed", ok(PayStatus.check(input("confirmed", id = "c2", manage = true, existing = listOf(flagged)))).to)
  }

  @Test
  fun invalidTransitionsAndTargetsAreRejected() {
    val done = ok(PayStatus.check(input("confirmed")))
    //  پایانی برنمی‌گردد؛ بازپرداخت فقط از راهِ برگشت
    assertEquals("BAD_TRANSITION", err(PayStatus.check(input("failed", id = "c2", manage = true, existing = listOf(done)))))
    assertEquals("BAD_TRANSITION", err(PayStatus.check(input("refunded", manage = true))))
    assertEquals("BAD_TRANSITION", err(PayStatus.check(input("pending", manage = true))))
    assertEquals("BAD_STATUS", err(PayStatus.check(input("paid", manage = true))))
    val failed = ok(PayStatus.check(input("failed", manage = true)))
    assertEquals("BAD_TRANSITION", err(PayStatus.check(input("confirmed", id = "c3", manage = true, existing = listOf(failed)))))
  }

  @Test
  fun onlyManualNonCashRowsOfALiveShopSale() {
    assertEquals("NOT_MANUAL", err(PayStatus.check(input("confirmed", index = 0, manage = true))))
    assertEquals("NOT_MANUAL", err(PayStatus.check(input("confirmed", index = 2, manage = true))))
    assertEquals("BAD_INDEX", err(PayStatus.check(input("confirmed", index = 9))))
    assertEquals("SALE_CANCELLED", err(PayStatus.check(input("confirmed").copy(cancelled = true))))
    assertEquals("SALE_ONLINE", err(PayStatus.check(input("confirmed").copy(online = true))))
    val courier = Row("cod", "cash", 1_000, "pending", custody = "courier")
    assertEquals(false, PayStatus.manual(courier))
    assertEquals("BY_REQUIRED", err(PayStatus.check(input("confirmed", by = " "))))
    assertEquals("BAD_NOTE", err(PayStatus.check(input("confirmed", note = "a\nb"))))
    assertEquals("BAD_NOTE", err(PayStatus.check(input("confirmed", note = "x".repeat(121)))))
    assertEquals("INVALID_TIME", err(PayStatus.check(input("confirmed", at = 0))))
  }

  @Test
  fun sameIdIsDuplicateOtherContentConflicts() {
    val c = ok(PayStatus.check(input("confirmed")))
    val again = PayStatus.check(input("confirmed", existing = listOf(c)))
    assertTrue((again as PayResult.Ok).duplicate)
    assertEquals("ID_CONFLICT", err(PayStatus.check(input("needs_review", existing = listOf(c)))))
  }

  @Test
  fun twoOfflineDevicesEarliestWinsRegardlessOfArrival() {
    val a = Change("a", "s1", 1, "afpay_card", "pending", "confirmed", 10, "سارا")
    val b = Change("b", "s1", 1, "afpay_card", "pending", "needs_review", 20, "علی")
    for (order in listOf(listOf(a, b), listOf(b, a))) {
      val r = PayStatus.apply("s1", rows, order)
      assertEquals(listOf("confirmed", "confirmed", "confirmed"), r.statuses)
      assertEquals(listOf("a"), r.applied)
      assertEquals(listOf("b"), r.skipped)
    }
    //  مرزِ زمان (Z): سندِ پس از بستن دیده نمی‌شود
    assertEquals("pending", PayStatus.apply("s1", rows, listOf(a), until = 9).statuses[1])
    //  سندِ فاکتورِ دیگر، روشِ دیگر یا ردیفِ نه-دستی بی‌اثر
    val other = listOf(a.copy(id = "x", saleId = "s2"), a.copy(id = "y", method = "hawala"), a.copy(id = "z", index = 2))
    assertEquals(rows.map { it.status }, PayStatus.apply("s1", rows, other).statuses)
  }

  @Test
  fun pendingFailedCancelledAndReviewAreNeverReceivedMoney() {
    val many = listOf(cash, card, card.copy(method = "hawala", kind = "transfer", amount = 30_000), gateway)
    for (st in listOf("pending", "failed", "cancelled", "needs_review")) {
      val statuses = listOf("confirmed", st, "confirmed", "confirmed")
      assertEquals(mapOf("afpay_card" to 150_000L), PayStatus.unreceived(many, statuses), st)
    }
    assertEquals(emptyMap<String, Long>(), PayStatus.unreceived(many, listOf("confirmed", "confirmed", "partially_refunded", "refunded")))
    assertEquals(
      mapOf("pending" to 150_000L, "confirmed" to 110_000L),
      PayStatus.totals(many, listOf("confirmed", "pending", "confirmed", "confirmed")),
    )
  }

  @Test
  fun refundsFromTheReturnPathMarkFullOrPartial() {
    val two = listOf(card, card.copy(amount = 50_000))
    val st = listOf("confirmed", "confirmed")
    assertEquals(listOf("partially_refunded", "confirmed"), PayStatus.withRefunds(two, st, mapOf("afpay_card" to 100_000)))
    assertEquals(listOf("refunded", "partially_refunded"), PayStatus.withRefunds(two, st, mapOf("afpay_card" to 160_000)))
    //  ردیفِ در انتظار یا ناموفق «بازپرداخت‌شده» نمی‌شود
    assertEquals(listOf("pending", "failed"), PayStatus.withRefunds(two, listOf("pending", "failed"), mapOf("afpay_card" to 200_000)))
  }

  @Test
  fun summaryShowsTheWorstNonCashRow() {
    assertNull(PayStatus.summary(listOf(cash), listOf("confirmed")))
    assertEquals("needs_review", PayStatus.summary(rows, listOf("confirmed", "needs_review", "confirmed")))
    assertEquals("pending", PayStatus.summary(rows, listOf("confirmed", "pending", "confirmed")))
    assertEquals("confirmed", PayStatus.summary(rows, listOf("confirmed", "confirmed", "confirmed")))
  }

  @Test
  fun allowedListsWhatThisUserMaySet() {
    assertEquals(listOf("confirmed", "needs_review"), PayStatus.allowed("pending", sell = true, manage = false))
    assertEquals(listOf("confirmed", "failed", "cancelled", "needs_review"), PayStatus.allowed("pending", sell = true, manage = true))
    assertEquals(emptyList<String>(), PayStatus.allowed("needs_review", sell = true, manage = false))
    assertEquals(listOf("confirmed", "failed", "cancelled"), PayStatus.allowed("needs_review", sell = false, manage = true))
    assertEquals(emptyList<String>(), PayStatus.allowed("confirmed", sell = true, manage = true))
  }
}
