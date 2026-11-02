package ir.vil3ntec.tohid.core.promo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRM-13 — قاعده‌های امتیازِ وفاداری (`Loyalty`) روی هر سکو؛ همه عددِ صحیح */
class LoyaltyTest {

  private val rule = Loyalty.Rule(everyAfn = 100, pointAfn = 1, maxSharePct = 20, silverAfn = 50_000, goldAfn = 200_000)
  private val bronze = rule.tiers[0]
  private val silver = rule.tiers[1]
  private val gold = rule.tiers[2]

  @Test
  fun earnIsOnePointPerEveryAfnRoundedDown() {
    assertEquals(12, Loyalty.earn(rule, bronze, 1_299_00))
    assertEquals(0, Loyalty.earn(rule, bronze, 99_99))
    assertEquals(0, Loyalty.earn(rule, bronze, 0))
    assertEquals(0, Loyalty.earn(rule, bronze, -500_00))
  }

  @Test
  fun tierFollowsTwelveMonthSpendAndMultipliesEarn() {
    assertEquals(Loyalty.BRONZE, Loyalty.tier(rule, 49_999).key)
    assertEquals(Loyalty.SILVER, Loyalty.tier(rule, 50_000).key)
    assertEquals(Loyalty.GOLD, Loyalty.tier(rule, 250_000).key)
    //  ۱٬۰۰۰ افغانی: ۱۰ ⇒ ×۱٫۲۵ = ۱۲٫۵ ⇒ ۱۲؛ ×۱٫۵ = ۱۵
    assertEquals(10, Loyalty.earn(rule, bronze, 1_000_00))
    assertEquals(12, Loyalty.earn(rule, silver, 1_000_00))
    assertEquals(15, Loyalty.earn(rule, gold, 1_000_00))
  }

  @Test
  fun spendIsCappedByBalanceAndByShareOfInvoice() {
    //  فاکتورِ ۱٬۰۰۰ ⇒ سقفِ سهم ۲۰۰ امتیاز؛ ماندهٔ ۵۰ ⇒ ۵۰
    assertEquals(50, Loyalty.cap(rule, 50, 1_000_00))
    assertEquals(200, Loyalty.cap(rule, 5_000, 1_000_00))
    assertEquals(0, Loyalty.cap(rule, 0, 1_000_00))
    assertEquals(0, Loyalty.cap(rule, -3, 1_000_00))
    assertEquals(Loyalty.Spend.Ok(200, 200_00), Loyalty.spend(rule, 5_000, 1_000_00, 200))
    assertEquals(Loyalty.Spend.Bad(Loyalty.Code.OVER_SHARE), Loyalty.spend(rule, 5_000, 1_000_00, 201))
    assertEquals(Loyalty.Spend.Bad(Loyalty.Code.OVER_BALANCE), Loyalty.spend(rule, 50, 1_000_00, 51))
    assertEquals(Loyalty.Spend.Bad(Loyalty.Code.BAD_POINTS), Loyalty.spend(rule, 50, 1_000_00, 0))
    //  ارزشِ هر امتیاز ۵ افغانی ⇒ سقفِ سهم ۴۰ امتیاز
    val five = rule.copy(pointAfn = 5)
    assertEquals(40, Loyalty.cap(five, 5_000, 1_000_00))
    assertEquals(Loyalty.Spend.Ok(40, 200_00), Loyalty.spend(five, 5_000, 1_000_00, 40))
  }

  @Test
  fun badRuleEarnsAndSpendsNothing() {
    val bad = rule.copy(everyAfn = 0)
    assertEquals(0, Loyalty.earn(bad, bronze, 1_000_00))
    assertEquals(0, Loyalty.cap(bad, 100, 1_000_00))
    assertEquals(Loyalty.Spend.Bad(Loyalty.Code.BAD_RULE), Loyalty.spend(bad, 100, 1_000_00, 1))
    assertTrue(!rule.copy(goldAfn = 50_000).valid)
  }

  @Test
  fun balanceIsTheSignedSumOfTheLedger() {
    val ev = listOf(
      Loyalty.Event("s1-lp-e", Loyalty.EARN, "c1", 30, "s1"),
      Loyalty.Event("s2-lp-s", Loyalty.SPEND, "c1", 10, "s2"),
      Loyalty.Event("r1-lp-e", Loyalty.EARN_BACK, "c1", 5, "s1"),
      Loyalty.Event("r2-lp-s", Loyalty.SPEND_BACK, "c1", 4, "s2"),
      Loyalty.Event("s3-lp-e", Loyalty.EARN, "c2", 99, "s3"),
    )
    assertEquals(19, Loyalty.balance(ev, "c1"))
    assertEquals(99, Loyalty.balance(ev, "c2"))
    assertEquals(30, Loyalty.total(ev, "s1", Loyalty.EARN))
  }

  @Test
  fun splitIsProportionalExactAndNeverAboveALine() {
    assertEquals(listOf(50L, 30L, 20L), Loyalty.split(listOf(500, 300, 200), 100))
    //  ۱۰ میانِ سه ردیفِ برابر ⇒ ۴، ۳، ۳ (باقی‌مانده به ردیفِ جلوتر)
    assertEquals(listOf(4L, 3L, 3L), Loyalty.split(listOf(7, 7, 7), 10))
    assertEquals(listOf(0L, 0L), Loyalty.split(listOf(7, 7), 0))
    assertEquals(listOf(7L, 7L), Loyalty.split(listOf(7, 7), 14))
    assertNull(Loyalty.split(listOf(7, 7), 15))
    assertNull(Loyalty.split(listOf(-1, 7), 1))
    val got = Loyalty.split(listOf(1, 999, 0), 333)!!
    assertEquals(333, got.sum())
    assertTrue(got.zip(listOf(1L, 999L, 0L)).all { (s, l) -> s <= l })
  }

  @Test
  fun partialReturnsAddUpToTheFullReversalAndNoMore() {
    //  فاکتورِ ۹۰۰ با ۱۰ امتیازِ کسب و ۷ امتیازِ مصرف؛ سه برگشتِ ۳۰۰ تایی
    var earnedBack = 0L
    var spentBack = 0L
    var returned = 0L
    repeat(3) {
      returned += 300_00
      val r = Loyalty.reverse(10, earnedBack, 7, spentBack, returned, 900_00, balance = 1_000)
      earnedBack += r.earnBack
      spentBack += r.spendBack
      assertEquals(0, r.shortfall)
    }
    assertEquals(10, earnedBack)
    assertEquals(7, spentBack)
    //  یک‌سوم ⇒ رو به پایین: ۳ از ۱۰ و ۲ از ۷
    assertEquals(Loyalty.Reversal(2, 3, 0), Loyalty.reverse(10, 0, 7, 0, 300_00, 900_00, 1_000))
    //  بیش از فاکتور ⇒ همان کلّ
    assertEquals(Loyalty.Reversal(7, 10, 0), Loyalty.reverse(10, 0, 7, 0, 5_000_00, 900_00, 1_000))
    //  تکرارِ همان برگشت چیزی پس نمی‌گیرد
    assertEquals(Loyalty.Reversal(0, 0, 0), Loyalty.reverse(10, 10, 7, 7, 900_00, 900_00, 1_000))
  }

  @Test
  fun reversalNeverDrivesTheBalanceBelowZero() {
    //  ۳۰ کسب، ۲۵ جای دیگر خرج شد ⇒ مانده ۵؛ برگشتِ کامل فقط ۵ کم می‌کند و ۲۵ کسری
    val r = Loyalty.reverse(30, 0, 0, 0, 100_00, 100_00, balance = 5)
    assertEquals(Loyalty.Reversal(0, 5, 25), r)
    //  مصرفِ همان فاکتور اول برمی‌گردد و جا باز می‌کند
    assertEquals(Loyalty.Reversal(8, 13, 17), Loyalty.reverse(30, 0, 8, 0, 100_00, 100_00, balance = 5))
    assertEquals(Loyalty.Reversal(0, 0, 4), Loyalty.reverse(4, 0, 0, 0, 1, 1, balance = -2))
  }

  @Test
  fun voidReversesWhateverIsLeft() {
    //  نیمی پیش‌تر با برگشت رفت ⇒ لغو باقی را
    assertEquals(Loyalty.Reversal(3, 5, 0), Loyalty.reverseAll(10, 5, 7, 4, balance = 100))
    assertEquals(Loyalty.Reversal(0, 0, 0), Loyalty.reverseAll(10, 10, 7, 7, balance = 100))
  }

  @Test
  fun mulDivIsExactWithoutOverflow() {
    assertEquals(6, Loyalty.mulDiv(4, 3, 2))
    val big = 4_000_000_000_000L
    assertEquals(big / 3 * 2 + (big % 3 * 2) / 3, Loyalty.mulDiv(big, 2, 3))
    //  حاصل‌ضرب بیرون از Long، حاصل در آن
    assertEquals(3_000_000_000L, Loyalty.mulDiv(3_000_000_000L, 9_000_000_000_000L, 9_000_000_000_000L))
    assertEquals(1_499_999_999L, Loyalty.mulDiv(3_000_000_000L, 4_499_999_999_999L, 9_000_000_000_000L))
    assertEquals(5, Loyalty.share(10, 450_000_000_000_000L, 900_000_000_000_000L))
  }
}
