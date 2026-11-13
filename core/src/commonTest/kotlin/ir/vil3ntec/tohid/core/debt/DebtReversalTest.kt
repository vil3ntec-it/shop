package ir.vil3ntec.tohid.core.debt

import ir.vil3ntec.tohid.core.debt.DebtReversal.Plan
import ir.vil3ntec.tohid.core.debt.DebtReversal.Refusal
import ir.vil3ntec.tohid.core.debt.DebtReversal.Row
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  S89 (تصمیمِ ۱۴۰۵/۰۸/۲۳، پرسشِ ۲۳): «حذف» ِ قرض یا رسیدِ ثبت‌شده فقط با `shop.doc_chain` سندِ معکوس است؛
 *  کلیدِ خاموش همان حذفِ امروز. جفتِ «اصل + معکوس» جمعِ حساب را عوض نمی‌کند و از نمای مؤثر بیرون می‌رود.
 */
class DebtReversalTest {

  private val give = Row("g1", "give", 50_000)
  private val pay = Row("r1", "receive", 20_000, cash = true)

  @Test
  fun keyOffKeepsTodaysDelete() {
    assertEquals(Plan.Delete, DebtReversal.plan(false, pay, listOf(give, pay)))
    //  ردیفِ ساختهٔ سیستم و حتی ردیفِ معکوس هم مثلِ امروز پاک می‌شوند
    val rev = Row("r1-rv", "give", 20_000, DebtReversal.KIND, "r1")
    assertEquals(Plan.Delete, DebtReversal.plan(false, rev, listOf(give, pay, rev)))
    assertEquals(Plan.Delete, DebtReversal.plan(false, Row("s", "give", 1, "sale_credit", "sale-1"), emptyList()))
  }

  @Test
  fun keyOnReversesWithOppositeTypeSameAmountAndStableId() {
    assertEquals(Plan.Reverse("r1-rv", "give", 20_000, "r1"), DebtReversal.plan(true, pay, listOf(give, pay)))
    assertEquals(Plan.Reverse("g1-rv", "receive", 50_000, "g1"), DebtReversal.plan(true, give, listOf(give, pay)))
  }

  @Test
  fun keyOnRefusesReversalSystemRowAndSecondReversal() {
    assertEquals(Plan.Refused(Refusal.NOT_FOUND), DebtReversal.plan(true, null, listOf(give)))
    val rev = Row("r1-rv", "give", 20_000, DebtReversal.KIND, "r1")
    //  معکوسِ معکوس رد (همان SEC-03)
    assertEquals(Plan.Refused(Refusal.IS_REVERSAL), DebtReversal.plan(true, rev, listOf(give, pay, rev)))
    //  اصلِ معکوس‌شده دوباره معکوس نمی‌شود
    assertEquals(Plan.Refused(Refusal.ALREADY_REVERSED), DebtReversal.plan(true, pay, listOf(give, pay, rev)))
    //  ردیفی که سندِ دیگری ساخته (نسیهٔ فاکتور، لغو، برگشت، امانی…) از همان سند اصلاح می‌شود
    val credit = Row("s1", "give", 9_000, "sale_credit", "sale-1")
    assertEquals(Plan.Refused(Refusal.SYSTEM_ROW), DebtReversal.plan(true, credit, listOf(credit)))
  }

  @Test
  fun pairNeutralIsLeftOutAndKeepsTheSum() {
    val rev = Row("r1-rv", "give", 20_000, DebtReversal.KIND, "r1")
    val rows = listOf(give, pay, rev)
    assertEquals(setOf("r1", "r1-rv"), DebtReversal.neutral(rows))
    fun sum(xs: List<Row>) = xs.sumOf { if (it.type == "give") it.amountMinor else -it.amountMinor }
    val kept = rows.filter { it.id !in DebtReversal.neutral(rows) }
    assertEquals(sum(rows), sum(kept))
    assertEquals(listOf(give), kept)
  }

  @Test
  fun badOrDanglingReversalIsAnOrdinaryRow() {
    //  مبلغ یا نوعِ ناهم‌خوان، اصلِ نبود، یا معکوسِ یک معکوس: جفت نیست ⇒ همان ردیفِ عادی (جمع همان جمعِ خام)
    val wrongAmount = Row("x1", "give", 19_999, DebtReversal.KIND, "r1")
    val sameType = Row("x2", "receive", 20_000, DebtReversal.KIND, "r1")
    val dangling = Row("x3", "give", 5_000, DebtReversal.KIND, "gone")
    assertTrue(DebtReversal.neutral(listOf(give, pay, wrongAmount, sameType, dangling)).isEmpty())
    val rev = Row("r1-rv", "give", 20_000, DebtReversal.KIND, "r1")
    val revOfRev = Row("y", "receive", 20_000, DebtReversal.KIND, "r1-rv")
    assertEquals(setOf("r1", "r1-rv"), DebtReversal.neutral(listOf(pay, rev, revOfRev)))
  }

  @Test
  fun twoDevicesReversingTheSameRowCountOnce() {
    //  شناسهٔ پایدار (`<اصل>-rv`) دو معکوسِ هم‌زمان را یک ردیف می‌کند؛ اگر باز هم دو تا آمد، فقط یکی جفت است
    val a = Row("r1-rv", "give", 20_000, DebtReversal.KIND, "r1")
    val b = Row("zz", "give", 20_000, DebtReversal.KIND, "r1")
    assertEquals(setOf("r1", "r1-rv"), DebtReversal.neutral(listOf(pay, b, a)))
    assertEquals(setOf("r1", "r1-rv"), DebtReversal.neutral(listOf(a, pay, b)))
  }

  @Test
  fun debtorWithEntriesIsNotDeletedWhenTheKeyIsOn() {
    assertTrue(DebtReversal.canDeleteDebtor(false, 5))
    assertTrue(DebtReversal.canDeleteDebtor(true, 0))
    assertEquals(false, DebtReversal.canDeleteDebtor(true, 1))
  }

  @Test
  fun onlyAReversedCashReceiptLeavesTheDrawer() {
    val revPay = Row("r1-rv", "give", 20_000, DebtReversal.KIND, "r1")
    val noCash = Row("r2", "receive", 7_000, cash = false)
    val revNoCash = Row("r2-rv", "give", 7_000, DebtReversal.KIND, "r2")
    val revGive = Row("g1-rv", "receive", 50_000, DebtReversal.KIND, "g1")
    val rows = listOf(give, pay, noCash, revPay, revNoCash, revGive)
    assertEquals(setOf("r1-rv"), DebtReversal.cashBack(rows))
    //  ردیفِ معکوس خودش هرگز رسیدِ نقد نیست
    assertTrue(DebtReversal.isReversal(revGive.kind))
  }
}
