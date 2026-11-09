package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.pay.PayStatus.Change
import ir.vil3ntec.tohid.core.pay.PayStatus.Row
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PAY-07 — وضعیتِ ردیف‌های غیرنقدِ فاکتور: آغازِ «تأییدشده» ِ ثبتِ دستی، اصلاح، اجازه، دو دستگاه، پولِ دریافتی و بازپرداخت */
class PayStatusTest {
  /** ثبتِ دستیِ صندوقدار — همان ردیفی که `Pay.settle` امروز می‌نویسد */
  private val card = Row("afpay_card", "card", 150_000, "pending", "manual")
  private val cash = Row("cash", "cash", 50_000, "confirmed")
  private val gateway = Row("hesabpay", "wallet", 80_000, "confirmed", "gateway")
  /** ردیفی که واقعاً منتظرِ تأیید است (نه ثبتِ دستی) */
  private val awaiting = Row("hesabpay", "wallet", 20_000, "pending")
  private val rows = listOf(cash, card, gateway, awaiting)

  private fun input(
    to: String, index: Int = 1, sell: Boolean = true, manage: Boolean = false, existing: List<Change> = emptyList(),
    id: String = "c1", by: String? = "سارا", note: String? = null, at: Long = 1_000,
  ) = PayStatus.Input(id, "s1", rows, index, to, at, by, sell, manage, note, existing = existing)

  private fun ok(r: PayResult<Change>) = (r as PayResult.Ok).value
  private fun err(r: PayResult<Change>) = (r as PayResult.Err).code

  @Test
  fun manualEntryStartsConfirmedAndCountsAsReceived() {
    val st = PayStatus.apply("s1", rows, emptyList()).statuses
    assertEquals(listOf("confirmed", "confirmed", "confirmed", "pending"), st)
    assertEquals(mapOf("hesabpay" to 20_000L), PayStatus.unreceived(rows, st))
    assertEquals("confirmed", PayStatus.initial(card))
    assertEquals("pending", PayStatus.initial(awaiting))
  }

  @Test
  fun onlyOwnerOrManagerMovesAManualEntryOut() {
    for (to in listOf("needs_review", "failed", "cancelled")) {
      assertEquals("PERM_DENIED", err(PayStatus.check(input(to))), to)
      val c = ok(PayStatus.check(input(to, sell = false, manage = true)))
      assertEquals(Change("c1", "s1", 1, "afpay_card", "confirmed", to, 1_000, "سارا"), c)
    }
    val flagged = ok(PayStatus.check(input("needs_review", manage = true)))
    assertEquals("PERM_DENIED", err(PayStatus.check(input("confirmed", id = "c2", existing = listOf(flagged)))))
    assertEquals("confirmed", ok(PayStatus.check(input("confirmed", id = "c2", manage = true, existing = listOf(flagged)))).to)
  }

  @Test
  fun cashierConfirmsOrFlagsOnlyARowThatAwaitsConfirmation() {
    assertEquals("confirmed", ok(PayStatus.check(input("confirmed", index = 3))).to)
    assertEquals("needs_review", ok(PayStatus.check(input("needs_review", index = 3))).to)
    assertEquals("PERM_DENIED", err(PayStatus.check(input("failed", index = 3))))
    assertEquals("cancelled", ok(PayStatus.check(input("cancelled", index = 3, manage = true))).to)
  }

  @Test
  fun invalidTransitionsAndTargetsAreRejected() {
    //  تأییدشده دوباره تأیید نمی‌شود؛ ناموفق/لغو پایانی است؛ بازپرداخت فقط از راهِ برگشت
    assertEquals("BAD_TRANSITION", err(PayStatus.check(input("confirmed", manage = true))))
    assertEquals("BAD_TRANSITION", err(PayStatus.check(input("refunded", manage = true))))
    assertEquals("BAD_TRANSITION", err(PayStatus.check(input("pending", manage = true))))
    assertEquals("BAD_STATUS", err(PayStatus.check(input("paid", manage = true))))
    val failed = ok(PayStatus.check(input("failed", manage = true)))
    for (to in listOf("confirmed", "needs_review", "cancelled")) {
      assertEquals("BAD_TRANSITION", err(PayStatus.check(input(to, id = "c3", manage = true, existing = listOf(failed)))), to)
    }
  }

  @Test
  fun onlyManualNonCashRowsOfALiveShopSale() {
    assertEquals("NOT_MANUAL", err(PayStatus.check(input("failed", index = 0, manage = true))))
    assertEquals("NOT_MANUAL", err(PayStatus.check(input("failed", index = 2, manage = true))))
    assertEquals("BAD_INDEX", err(PayStatus.check(input("failed", index = 9, manage = true))))
    assertEquals("SALE_CANCELLED", err(PayStatus.check(input("failed", manage = true).copy(cancelled = true))))
    assertEquals("SALE_ONLINE", err(PayStatus.check(input("failed", manage = true).copy(online = true))))
    assertEquals(false, PayStatus.manual(Row("cod", "cash", 1_000, "pending", custody = "courier")))
    assertEquals("BY_REQUIRED", err(PayStatus.check(input("failed", manage = true, by = " "))))
    assertEquals("BAD_NOTE", err(PayStatus.check(input("failed", manage = true, note = "a\nb"))))
    assertEquals("BAD_NOTE", err(PayStatus.check(input("failed", manage = true, note = "x".repeat(121)))))
    assertEquals("INVALID_TIME", err(PayStatus.check(input("failed", manage = true, at = 0))))
  }

  @Test
  fun sameIdIsDuplicateOtherContentConflicts() {
    val c = ok(PayStatus.check(input("needs_review", manage = true)))
    assertTrue((PayStatus.check(input("needs_review", manage = true, existing = listOf(c))) as PayResult.Ok).duplicate)
    assertEquals("ID_CONFLICT", err(PayStatus.check(input("failed", manage = true, existing = listOf(c)))))
  }

  @Test
  fun twoOfflineDevicesEarliestWinsRegardlessOfArrival() {
    val a = Change("a", "s1", 1, "afpay_card", "confirmed", "needs_review", 10, "مدیر")
    val b = Change("b", "s1", 1, "afpay_card", "confirmed", "failed", 20, "صاحب")
    for (order in listOf(listOf(a, b), listOf(b, a))) {
      val r = PayStatus.apply("s1", rows, order)
      assertEquals("needs_review", r.statuses[1])
      assertEquals(listOf("a"), r.applied)
      assertEquals(listOf("b"), r.skipped)
    }
    //  مرزِ زمان (Z): سندِ پس از بستن دیده نمی‌شود
    assertEquals("confirmed", PayStatus.apply("s1", rows, listOf(a), until = 9).statuses[1])
    //  سندِ فاکتورِ دیگر، روشِ دیگر یا ردیفِ نه-دستی بی‌اثر
    val other = listOf(a.copy(id = "x", saleId = "s2"), a.copy(id = "y", method = "hawala"), a.copy(id = "z", index = 2))
    assertEquals(listOf("confirmed", "confirmed", "confirmed", "pending"), PayStatus.apply("s1", rows, other).statuses)
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
      mapOf("needs_review" to 150_000L, "confirmed" to 110_000L),
      PayStatus.totals(many, listOf("confirmed", "needs_review", "confirmed", "confirmed")),
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

  /** PAY-23 — بازپرداخت فقط از پولِ رسیده؛ همان قاعدهٔ X/Z ([PayStatus.unreceived])، نه «هر چه ثبت شد» */
  @Test
  fun refundSeesOnlyMoneyThatArrived() {
    val cod = Row("cod", "cash", 90_000, "pending", custody = "courier")
    val online = Row("online", "other", 90_000, "pending")
    val all = rows + listOf(cod, online)
    val st = PayStatus.apply("s1", all, emptyList()).statuses
    //  ثبتِ دستی و درگاه پس‌دادنی؛ «در انتظار» ِ واقعی و درگاهِ نپرداخته نه؛ نقدِ نزدِ پیک (امانت) پولِ رسیدهٔ دکان است
    assertEquals(listOf("confirmed", "confirmed", "confirmed", "pending", "confirmed", "pending"), PayStatus.forRefund(all, st))
    assertEquals(PayStatus.unreceived(all, st).keys, all.indices.filter { PayStatus.forRefund(all, st)[it] == "pending" }.map { all[it].method }.toSet())
    //  «پول رسید» ⇒ پس‌دادنی؛ ردیفی که «ناموفق» شد نه
    val moved = listOf(
      Change("c1", "s1", 3, "hesabpay", "pending", "confirmed", 1, "سارا"),
      Change("c2", "s1", 1, "afpay_card", "confirmed", "failed", 2, "مدیر"),
    )
    assertEquals(listOf("confirmed", "failed", "confirmed", "confirmed", "confirmed", "pending"), PayStatus.forRefund(all, PayStatus.apply("s1", all, moved).statuses))
    //  نیمه‌پس‌داده باقی‌اش را دارد؛ «نیازمند بررسی»، «لغو» و امانتِ ناموفق پس‌دادنی نیستند
    assertEquals(listOf("confirmed", "needs_review", "cancelled", "failed"),
      PayStatus.forRefund(listOf(card, card, card, cod), listOf("partially_refunded", "needs_review", "cancelled", "failed")))
    //  فاکتورِ سفارشِ اینترنتی: درگاه/سرور مرجع است (ORD-15) ⇒ «در انتظار» همان امروز گرفته‌شده؛ «ناموفق» نه
    assertEquals(listOf("confirmed", "failed"), PayStatus.forRefund(listOf(online, online), listOf("pending", "failed"), online = true))
  }

  /** PAY-23 — با ردیفِ نارسیده، جمعِ برگشت‌های فاکتور هرگز بیش از پولِ رسیده (ردیفِ نقد پس‌داده‌اش را نمی‌شمارد) */
  @Test
  fun refundRoomCapsTheInvoiceAtMoneyThatArrived() {
    val cashRow = PaidRow("cash", 40_000, 0, "confirmed")
    val wallet = PaidRow("hesabpay", 60_000, 10_000, "confirmed")
    //  همه رسیده ⇒ بی سقفِ تازه (همان سقفِ امروزِ فاکتور)
    assertNull(PayStatus.refundRoom(listOf(cashRow, wallet), 90_000))
    assertEquals(15_000L, PayStatus.refundRoom(listOf(cashRow, wallet.copy(status = "pending")), 25_000))
    assertEquals(0L, PayStatus.refundRoom(listOf(cashRow, wallet.copy(status = "failed")), 50_000))
    assertEquals(40_000L, PayStatus.refundRoom(listOf(cashRow, wallet.copy(status = "needs_review")), 0))
  }

  @Test
  fun summaryShowsTheWorstNonCashRow() {
    assertNull(PayStatus.summary(listOf(cash), listOf("confirmed")))
    assertEquals("needs_review", PayStatus.summary(rows, listOf("confirmed", "needs_review", "confirmed", "pending")))
    assertEquals("pending", PayStatus.summary(rows, listOf("confirmed", "confirmed", "confirmed", "pending")))
    assertEquals("confirmed", PayStatus.summary(rows.take(3), listOf("confirmed", "confirmed", "confirmed")))
  }

  @Test
  fun allowedListsWhatThisUserMaySet() {
    assertEquals(emptyList<String>(), PayStatus.allowed("confirmed", sell = true, manage = false))
    assertEquals(listOf("needs_review", "failed", "cancelled"), PayStatus.allowed("confirmed", sell = true, manage = true))
    assertEquals(listOf("confirmed", "needs_review"), PayStatus.allowed("pending", sell = true, manage = false))
    assertEquals(listOf("confirmed", "failed", "cancelled", "needs_review"), PayStatus.allowed("pending", sell = true, manage = true))
    assertEquals(emptyList<String>(), PayStatus.allowed("needs_review", sell = true, manage = false))
    assertEquals(listOf("confirmed", "failed", "cancelled"), PayStatus.allowed("needs_review", sell = false, manage = true))
    assertEquals(emptyList<String>(), PayStatus.allowed("failed", sell = true, manage = true))
  }
}
