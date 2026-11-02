package ir.vil3ntec.tohid.core.pay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 *  PAY-24 گام ۲ — `StoredPay`: جدولِ روش‌ها پشتِ تنظیم، ردیفِ کارت/اعتبار «تأییدشده با دفتر»، خرج فقط تا مانده و
 *  تلاشِ دوباره بی خرجِ دوم، بازپرداخت به همان روش و هر کارت تا خرجش. روی JVM و شبیه‌سازِ iOS.
 */
class StoredPayTest {
  private val t0 = 1_760_000_000_000L
  private val code = GiftCard.make { 7 }
  private val code2 = GiftCard.make { 11 }
  private fun issue(c: String, amount: Long, id: String = "i-$c") = GiftCard.Event(id, GiftCard.ISSUE, c, amount, t0)

  private fun settle(tenders: List<Tender>, on: Boolean = true, total: Long = 100_000, customer: String? = null): PayResult<Settlement> =
    Pay.settle(SettleInput(total = total, tenders = tenders, customerId = customer, methods = StoredPay.methods(on)))

  @Test
  fun offKeepsTheDefaultTableAndRejectsTheMethods() {
    assertSame(Pay.DEFAULT_METHODS, StoredPay.methods(false))
    val r = settle(listOf(Tender("gift_card", 100_000, ref = code)), on = false)
    assertEquals("METHOD_DISABLED", (r as PayResult.Err).code)
    val on = StoredPay.methods(true)
    assertTrue(on.first { it.id == "gift_card" }.enabled && on.first { it.id == "store_credit" }.enabled)
    assertEquals(Pay.DEFAULT_METHODS.filter { it.kind !in StoredPay.KINDS }, on.filter { it.kind !in StoredPay.KINDS })
  }

  @Test
  fun confirmMarksOnlyStoredRowsAndLeavesOthersAsTheyWere() {
    val s = (settle(listOf(Tender("gift_card", 40_000, ref = code), Tender("hesabpay", 60_000, ref = "HP1"))) as PayResult.Ok).value
    val c = StoredPay.confirm(s)
    assertEquals(listOf("confirmed" to StoredPay.LEDGER, "pending" to "manual"), c.payments.map { it.status to it.verification })
    val plain = (settle(listOf(Tender("cash", 100_000)), on = false) as PayResult.Ok).value
    assertSame(plain, StoredPay.confirm(plain))
  }

  @Test
  fun redeemSpendsOnlyUpToTheBalanceAcrossRowsOfTheSameCard() {
    val ledger = listOf(issue(code, 50_000))
    val two = (settle(listOf(Tender("gift_card", 30_000, ref = code), Tender("gift_card", 30_000, ref = code), Tender("cash", 40_000)))
      as PayResult.Ok).value
    assertEquals(GiftCard.Code.INSUFFICIENT_BALANCE, (StoredPay.redeem(ledger, two, "s1", t0 + 1, "a") as GiftCard.Result.Err).code)
    val fits = (settle(listOf(Tender("gift_card", 20_000, ref = code), Tender("gift_card", 30_000, ref = code), Tender("cash", 50_000)))
      as PayResult.Ok).value
    val ev = (StoredPay.redeem(ledger, fits, "s1", t0 + 1, "a") as GiftCard.Result.Ok).value
    assertEquals(listOf("s1-g0", "s1-g1"), ev.map { it.id })
    assertEquals(0L, GiftCard.view(ledger + ev).card(code)!!.balanceMinor)
  }

  @Test
  fun aRetryWithTheSameSaleIdIsTheSameEventNeverASecondSpend() {
    val ledger = listOf(issue(code, 50_000))
    val s = (settle(listOf(Tender("gift_card", 50_000, ref = code), Tender("cash", 50_000))) as PayResult.Ok).value
    val first = (StoredPay.redeem(ledger, s, "s1", t0 + 1, "a") as GiftCard.Result.Ok).value
    val again = StoredPay.redeem(ledger + first, s, "s1", t0 + 9, "a")
    assertEquals(emptyList(), (again as GiftCard.Result.Ok).value)
    assertEquals(0L, GiftCard.view(ledger + first).card(code)!!.balanceMinor)
    //  همان شناسه با مبلغِ دیگر ⇒ ID_CONFLICT، هیچ رویدادی
    val other = (settle(listOf(Tender("gift_card", 10_000, ref = code), Tender("cash", 90_000))) as PayResult.Ok).value
    assertEquals(GiftCard.Code.ID_CONFLICT, (StoredPay.redeem(ledger + first, other, "s1", t0 + 9, "a") as GiftCard.Result.Err).code)
  }

  @Test
  fun badOrUnknownCardIsRejected() {
    val s = (settle(listOf(Tender("gift_card", 10_000, ref = "NOPE"), Tender("cash", 90_000))) as PayResult.Ok).value
    assertNull(StoredPay.gifts(s))
    assertEquals(GiftCard.Code.BAD_CODE, (StoredPay.redeem(emptyList(), s, "s1", t0, null) as GiftCard.Result.Err).code)
    val unknown = (settle(listOf(Tender("gift_card", 10_000, ref = code2), Tender("cash", 90_000))) as PayResult.Ok).value
    assertEquals(GiftCard.Code.UNKNOWN_CARD, (StoredPay.redeem(listOf(issue(code, 50_000)), unknown, "s1", t0, null) as GiftCard.Result.Err).code)
  }

  @Test
  fun storeCreditIsCappedAtTheCustomersCredit() {
    val s = (settle(listOf(Tender("store_credit", 30_000), Tender("cash", 70_000)), customer = "d1") as PayResult.Ok).value
    assertEquals(30_000L, StoredPay.creditMinor(s))
    assertNull(StoredPay.creditProblem(s, true, -30_000))
    assertEquals(StoredPay.INSUFFICIENT_CREDIT, StoredPay.creditProblem(s, true, -29_999))
    assertEquals(StoredPay.INSUFFICIENT_CREDIT, StoredPay.creditProblem(s, true, 5_000))
    assertEquals(StoredPay.NO_CUSTOMER, StoredPay.creditProblem(s, false, -90_000))
    val cash = (settle(listOf(Tender("cash", 100_000))) as PayResult.Ok).value
    assertNull(StoredPay.creditProblem(cash, false, 0))
  }

  @Test
  fun refundSplitGoesLastToFirstAndNeverBeyondWhatEachCardPaid() {
    val paid = listOf(StoredPay.Spend(code, 30_000), StoredPay.Spend(code2, 20_000))
    assertEquals(listOf(StoredPay.Spend(code2, 20_000), StoredPay.Spend(code, 5_000)), StoredPay.refundSplit(paid, emptyMap(), 25_000))
    //  کارتِ دوم پیش‌تر ۲۰٬۰۰۰ گرفت ⇒ فقط کارتِ اول، و تا ۳۰٬۰۰۰
    assertEquals(listOf(StoredPay.Spend(code, 30_000)), StoredPay.refundSplit(paid, mapOf(code2 to 20_000L), 30_000))
    assertNull(StoredPay.refundSplit(paid, mapOf(code2 to 20_000L), 30_001))
  }

  private fun rows() = listOf(
    PaidRow("cash", 50_000, 0, "confirmed"),
    PaidRow("gift_card", 30_000, 0, "confirmed"),
    PaidRow("store_credit", 20_000, 0, "confirmed"),
  )
  private val table = (Pay.table(null) as PayResult.Ok).value

  private fun refund(mode: String, amount: Long, before: List<PaidRow> = rows()): Pair<Refund, StoredPay.Routed> {
    val r = (Pay.refund(RefundInput(before, amount, mode, "owner", customerId = "d1")) as PayResult.Ok).value
    return r to StoredPay.route(r, before, mode, table)
  }

  @Test
  fun refundOfCardAndCreditRowsAlwaysGoesBackToTheSameMethod() {
    for (mode in listOf("original", "cash", "store_credit")) {
      val (r, x) = refund(mode, 100_000)
      assertEquals(30_000L, x.giftMinor, mode)
      assertEquals(20_000L, x.creditMinor, mode)
      //  بقیه همان ۵۰٬۰۰۰ ِ نقد: در «اعتبار» اعتبارِ جایگزین، وگرنه نقد
      val rest = x.payouts.associate { it.method to it.amount }
      assertEquals(if (mode == "store_credit") mapOf("store_credit" to 50_000L) else mapOf("cash" to 50_000L), rest, mode)
      assertEquals(r.payouts.sumOf { it.amount }, x.payouts.sumOf { it.amount } + x.giftMinor + x.creditMinor, mode)
    }
    //  برگشتِ جزئی از آخر به اول: اول اعتبار، بعد کارت
    val (_, part) = refund("cash", 25_000)
    assertEquals(20_000L to 5_000L, part.creditMinor to part.giftMinor)
    assertTrue(part.payouts.isEmpty())
    //  کارتی که پیش‌تر تا ته پس گرفت دیگر سهمی نمی‌گیرد
    val used = listOf(PaidRow("cash", 50_000, 0, "confirmed"), PaidRow("gift_card", 30_000, 30_000, "refunded"))
    val (_, after) = refund("original", 50_000, used)
    assertEquals(0L, after.giftMinor)
  }

  @Test
  fun routeLeavesARefundWithoutStoredRowsUntouched() {
    val before = listOf(PaidRow("cash", 50_000, 0, "confirmed"), PaidRow("hesabpay", 50_000, 0, "confirmed"))
    for (mode in listOf("original", "cash", "store_credit")) {
      val r = (Pay.refund(RefundInput(before, 70_000, mode, "owner", customerId = "d1")) as PayResult.Ok).value
      val x = StoredPay.route(r, before, mode, table)
      assertSame(r.payouts, x.payouts)
      assertEquals(0L to 0L, x.giftMinor to x.creditMinor)
    }
    assertIs<GiftCard.Result.Ok<*>>(
      StoredPay.redeem(emptyList(), (settle(listOf(Tender("cash", 100_000)), on = false) as PayResult.Ok).value, "s1", t0, null),
    )
  }
}
