package ir.vil3ntec.tohid.core.pay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 *  PRM-14 / PAY-24 — شارژِ دوباره، باطل و گزارشِ بدهیِ کارت‌ها روی همان دفترِ فقط‌افزودنیِ `GiftCard`. گزارش با جمعِ
 *  مستقلِ رویدادهای خامِ دفتر سنجیده می‌شود، نه با خودش. روی JVM و شبیه‌سازِ iOS.
 */
class GiftCardOpsTest {
  private val c1 = GiftCard.make { 3 }
  private val c2 = GiftCard.make { 4 }
  private val c3 = GiftCard.make { 6 }

  private fun ev(id: String, kind: String, code: String, amount: Long, at: Long, ref: String? = null, reason: String? = null) =
    GiftCard.Event(id, kind, code, amount, at, refId = ref, reason = reason)

  private fun ok(events: List<GiftCard.Event>, input: GiftCard.Input): GiftCard.Result.Ok<GiftCard.Event> =
    assertIs<GiftCard.Result.Ok<GiftCard.Event>>(GiftCard.build(events, input))

  @Test
  fun rechargeAddsToBalanceAndIsIdempotentByKey() {
    val base = listOf(ev("i1", GiftCard.ISSUE, c1, 50_000, 10), ev("s1", GiftCard.REDEEM, c1, 20_000, 20, ref = "sale1"))
    val first = ok(base, GiftCard.Input("r1", GiftCard.RECHARGE, GiftCard.display(c1), 15_000, 30, by = "رضا"))
    assertFalse(first.duplicate)
    assertEquals(c1, first.value.code)
    val after = base + first.value
    val card = GiftCard.view(after).card(c1)!!
    assertEquals(45_000L, card.balanceMinor)
    assertEquals(15_000L, card.rechargedMinor)
    assertEquals(GiftCard.RECHARGE, card.history.first().event.kind)
    //  همان کلید و همان محتوا ⇒ همان رویداد، بی شارژِ دوم؛ محتوای دیگر ⇒ ناسازگار
    val again = ok(after, GiftCard.Input("r1", GiftCard.RECHARGE, c1, 15_000, 99, by = "رضا"))
    assertTrue(again.duplicate)
    assertEquals(first.value, again.value)
    assertEquals(GiftCard.Result.Err(GiftCard.Code.ID_CONFLICT), GiftCard.build(after, GiftCard.Input("r1", GiftCard.RECHARGE, c1, 16_000, 99, by = "رضا")))
    //  شناسهٔ تکراری در دفتر (دو دستگاه یک ردیف را دو بار فرستادند) فقط یک بار شمرده می‌شود
    assertEquals(45_000L, GiftCard.view(after + first.value).card(c1)!!.balanceMinor)
  }

  @Test
  fun rechargeRejectsUnknownVoidAndBadAmounts() {
    val base = listOf(ev("i1", GiftCard.ISSUE, c1, 50_000, 10), ev("v1", GiftCard.VOID, c1, 0, 20, reason = "گم شد"))
    assertEquals(GiftCard.Result.Err(GiftCard.Code.CARD_VOID), GiftCard.build(base, GiftCard.Input("r1", GiftCard.RECHARGE, c1, 100, 30)))
    assertEquals(GiftCard.Result.Err(GiftCard.Code.UNKNOWN_CARD), GiftCard.build(base, GiftCard.Input("r2", GiftCard.RECHARGE, c2, 100, 30)))
    val live = listOf(ev("i2", GiftCard.ISSUE, c2, 50_000, 10))
    for (bad in listOf(0L, -5L, GiftCard.MAX_AMOUNT + 1)) {
      assertEquals(GiftCard.Result.Err(GiftCard.Code.INVALID_AMOUNT), GiftCard.build(live, GiftCard.Input("r3", GiftCard.RECHARGE, c2, bad, 30)), "$bad")
    }
    //  شارژ دلیل نمی‌گیرد؛ کدِ غلط رد
    assertEquals(GiftCard.Code.BAD_FIELD, GiftCard.check(ev("r4", GiftCard.RECHARGE, c2, 100, 30, reason = "چرا")))
    val wrong = c2.dropLast(1) + GiftCard.ALPHABET.first { it != c2.last() }
    assertEquals(GiftCard.Result.Err(GiftCard.Code.BAD_CODE), GiftCard.build(live, GiftCard.Input("r5", GiftCard.RECHARGE, wrong, 100, 30)))
    //  شارژی که پس از باطل از دستگاهِ دیگر رسید ⇒ «نیازمندِ بررسی»، کارت همچنان باطل و خرج‌نشدنی
    val late = GiftCard.view(base + ev("r6", GiftCard.RECHARGE, c1, 1_000, 25)).card(c1)!!
    assertTrue(late.void && late.review)
    assertEquals(0L, late.spendableMinor)
  }

  @Test
  fun voidNeedsAReasonAndForfeitsTheBalance() {
    val base = listOf(ev("i1", GiftCard.ISSUE, c1, 50_000, 10), ev("s1", GiftCard.REDEEM, c1, 10_000, 20, ref = "sale1"))
    for (r in listOf(null, "", "  ", "ab")) {
      assertEquals(GiftCard.Result.Err(GiftCard.Code.REASON_REQUIRED), GiftCard.build(base, GiftCard.Input("v1", GiftCard.VOID, c1, 0, 30, reason = r)), "$r")
    }
    //  باطل مبلغ ندارد: ماندهٔ کارت دست نمی‌خورد ولی از بدهی کنار می‌رود
    assertEquals(GiftCard.Result.Err(GiftCard.Code.INVALID_AMOUNT), GiftCard.build(base, GiftCard.Input("v1", GiftCard.VOID, c1, 40_000, 30, reason = "گم شد")))
    val v = ok(base, GiftCard.Input("v1", GiftCard.VOID, c1, 0, 30, reason = "  گم شد  ")).value
    assertEquals("گم شد", v.reason)
    val view = GiftCard.view(base + v)
    assertEquals(40_000L, view.card(c1)!!.balanceMinor)
    assertEquals(0L, view.liabilityMinor)
    assertEquals(40_000L, GiftCard.liability(view).voidedMinor)
    //  باطلِ دوباره و خرج پس از باطل رد
    assertEquals(GiftCard.Result.Err(GiftCard.Code.CARD_VOID), GiftCard.build(base + v, GiftCard.Input("v2", GiftCard.VOID, c1, 0, 40, reason = "دوباره")))
    assertEquals(GiftCard.Result.Err(GiftCard.Code.CARD_VOID), GiftCard.build(base + v, GiftCard.Input("s2", GiftCard.REDEEM, c1, 100, 40, refId = "sale2")))
  }

  @Test
  fun liabilityReconcilesWithTheRawLedger() {
    val events = listOf(
      ev("i1", GiftCard.ISSUE, c1, 50_000, 10),
      ev("i2", GiftCard.ISSUE, c2, 30_000, 11),
      ev("i3", GiftCard.ISSUE, c3, 20_000, 12),
      ev("s1", GiftCard.REDEEM, c1, 20_000, 20, ref = "sale1"),
      ev("f1", GiftCard.REFUND, c1, 5_000, 21, ref = "ret1"),
      ev("r1", GiftCard.RECHARGE, c2, 10_000, 22),
      ev("v3", GiftCard.VOID, c3, 0, 23, reason = "گم شد"),
      //  دوبار خرج از دو دستگاهِ بی‌خبر: ماندهٔ منفی، نه صفرِ ساختگی
      ev("s2", GiftCard.REDEEM, c2, 30_000, 24, ref = "sale2"),
      ev("s3", GiftCard.REDEEM, c2, 30_000, 25, ref = "sale3"),
      //  ردیف‌های نادرست در هیچ جمعی نیستند
      ev("s1", GiftCard.REDEEM, c1, 9_999, 26, ref = "dup"),
      ev("x1", GiftCard.RECHARGE, GiftCard.make { 8 }, 7_000, 27),
      ev("x2", GiftCard.RECHARGE, c1, 0, 28),
    )
    val view = GiftCard.view(events)
    val report = GiftCard.liability(view)
    //  جمعِ مستقل از ردیف‌های خامِ درست: شکلِ درست، هر شناسه یک بار (نخستین)، فقط کارتِ صادرشده
    val seen = HashSet<String>()
    val issued = events.filter { it.kind == GiftCard.ISSUE }.map { it.code }.toSet()
    val good = events.filter { GiftCard.check(it) == null && seen.add(it.id) && it.code in issued }
    fun total(kind: String) = good.filter { it.kind == kind }.sumOf { it.amountMinor }
    assertEquals(total(GiftCard.ISSUE), report.issuedMinor)
    assertEquals(total(GiftCard.RECHARGE), report.rechargedMinor)
    assertEquals(total(GiftCard.REDEEM), report.redeemedMinor)
    assertEquals(total(GiftCard.REFUND), report.refundedMinor)
    assertEquals(listOf(100_000L, 10_000L, 80_000L, 5_000L), listOf(report.issuedMinor, report.rechargedMinor, report.redeemedMinor, report.refundedMinor))
    //  مانده‌ها از ردیف‌ها، کارت به کارت
    fun balance(code: String) = good.filter { it.code == code }.sumOf {
      when (it.kind) { GiftCard.ISSUE, GiftCard.RECHARGE, GiftCard.REFUND -> it.amountMinor; GiftCard.REDEEM -> -it.amountMinor; else -> 0L }
    }
    assertEquals(35_000L, balance(c1))
    assertEquals(-20_000L, balance(c2))
    assertEquals(balance(c3), report.voidedMinor)
    assertEquals(35_000L, report.outstandingMinor)
    assertEquals(20_000L, report.overdrawnMinor)
    assertEquals(view.liabilityMinor, report.outstandingMinor)
    //  صدور + شارژ − خرج + برگشت − باطل = جمعِ ماندهٔ کارت‌های نه‌باطل
    assertEquals(balance(c1) + balance(c2), report.netMinor)
    assertEquals(report.issuedMinor + report.rechargedMinor - report.redeemedMinor + report.refundedMinor - report.voidedMinor, report.netMinor)
    assertTrue(report.reconciles)
    assertEquals(listOf(c1, c2), report.open.map { it.code })
    assertEquals(2 to 1, report.activeCount to report.voidCount)
    assertEquals(listOf(c2), report.review)
    assertEquals(listOf("s1", "x1", "x2"), view.invalid.sorted())
  }

  @Test
  fun emptyLedgerHasNoLiability() {
    val r = GiftCard.liability(GiftCard.view(emptyList()))
    assertEquals(0L, r.outstandingMinor + r.overdrawnMinor + r.issuedMinor + r.voidedMinor)
    assertTrue(r.reconciles)
    assertTrue(r.open.isEmpty())
  }
}
