package ir.vil3ntec.tohid.core.risk

import ir.vil3ntec.tohid.core.risk.CashierAlerts.Alert
import ir.vil3ntec.tohid.core.risk.CashierAlerts.Counts
import ir.vil3ntec.tohid.core.risk.CashierAlerts.Kind
import ir.vil3ntec.tohid.core.risk.CashierAlerts.Policy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  RSK-10 — هشدارِ رفتارِ غیرعادیِ صندوقدار. هر عدد با دست حساب شده است (همان ردیف‌های
 *  `core/test-vectors/cashier-alerts-v1.json`؛ برابریِ کاملِ بردارها با JS در `CashierAlertsVectorsTest` ِ برنامه).
 */
class CashierAlertsTest {
  private fun r(key: String, invoices: Long, voids: Long = 0, discounts: Long = 0, noSales: Long = 0) =
    Counts(key, invoices, voids, discounts, noSales)

  @Test
  fun manyVoidsAgainstPeersIsAnAlert() {
    //  ۵ لغو در ۴۰ فاکتور، در برابرِ ۳ لغو در ۲۶۰ فاکتورِ بقیه: ۵×۲۶۰×۱۰۰ = ۱۳۰٬۰۰۰ ≥ ۲۰۰×۳×۴۰ = ۲۴٬۰۰۰
    val got = CashierAlerts.alerts(listOf(r("علی", 40, voids = 5), r("سارا", 160, voids = 2), r("رضا", 100, voids = 1)))
    assertEquals(listOf(Alert("علی", Kind.VOID, 5, 40, 3, 260, 1083)), got)
  }

  @Test
  fun belowMinimumOrBelowTwiceIsQuiet() {
    assertEquals(emptyList(), CashierAlerts.alerts(listOf(r("علی", 5, 2, 2, 2), r("سارا", 500))))
    assertEquals(emptyList(), CashierAlerts.alerts(listOf(r("علی", 100, voids = 4), r("سارا", 100, voids = 3))))
    //  مرز: درست دو برابر هشدار است
    assertEquals(listOf(Alert("علی", Kind.VOID, 6, 100, 3, 100, 200)), CashierAlerts.alerts(listOf(r("علی", 100, voids = 6), r("سارا", 100, voids = 3))))
  }

  @Test
  fun peersWhoNeverDidItCountAsOne() {
    //  بقیه هیچ‌بار ولی فقط ۱۰ فاکتور: ۳×۱۰×۱۰۰ = ۳٬۰۰۰ < ۲۰۰×۱×۱۰۰ ⇒ هشدار نیست
    assertEquals(emptyList(), CashierAlerts.alerts(listOf(r("علی", 100, discounts = 3), r("سارا", 10))))
    assertEquals(listOf(Alert("علی", Kind.DISCOUNT, 3, 100, 0, 200, 600)), CashierAlerts.alerts(listOf(r("علی", 100, discounts = 3), r("سارا", 200))))
  }

  @Test
  fun drawerOpensBySomeoneWhoSellsNothingUseDenominatorOne() {
    val got = CashierAlerts.alerts(listOf(r("مدیر", 0, noSales = 4), r("سارا", 50, noSales = 1)))
    assertEquals(listOf(Alert("مدیر", Kind.NO_SALE, 4, 0, 1, 50, 20_000)), got)
  }

  @Test
  fun noPeerSalesMeansNoUsualAndUnknownUserIsNeverFlagged() {
    assertEquals(emptyList(), CashierAlerts.alerts(listOf(r("علی", 100, 9, 9, 9))))
    assertEquals(emptyList(), CashierAlerts.alerts(listOf(r("علی", 100, voids = 9), r("مدیر", 0, voids = 1))))
    assertEquals(emptyList(), CashierAlerts.alerts(listOf(r("", 100, voids = 10), r("علی", 100, voids = 3))))
    assertEquals(listOf(Alert("علی", Kind.VOID, 3, 100, 0, 300, 900)), CashierAlerts.alerts(listOf(r("", 300), r("علی", 100, voids = 3))))
  }

  @Test
  fun orderIsKindThenRatioThenCountThenKey() {
    val got = CashierAlerts.alerts(listOf(r("ب", 50, 4, 8, 3), r("الف", 50, 4, 8, 0), r("ج", 400, 1, 2, 0), r("د", 0, 0, 0, 3)))
    assertEquals(
      listOf("الف void 720", "ب void 720", "الف discount 720", "ب discount 720", "د no_sale 50000", "ب no_sale 900"),
      got.map { "${it.key} ${it.kind.id} ${it.ratioPct}" },
    )
  }

  @Test
  fun policyIsClampedAndHugeCountsStayExact() {
    assertEquals(listOf(Alert("علی", Kind.VOID, 5, 100, 1, 100, 500)), CashierAlerts.alerts(listOf(r("علی", 100, 5, 4), r("سارا", 100, 1, 1)), Policy(5, 300)))
    assertEquals(2, CashierAlerts.alerts(listOf(r("علی", 100, voids = 1), r("سارا", 100, voids = 1)), Policy(0, 50)).size)
    assertEquals(listOf(1_000L), CashierAlerts.alerts(listOf(r("علی", 100, voids = 10), r("سارا", 100, voids = 1)), Policy(factorPct = 5_000)).map { it.ratioPct })
    assertEquals(listOf(Alert("علی", Kind.DISCOUNT, 3, 0, 0, 100, 30_000)), CashierAlerts.alerts(listOf(r("علی", -5, -3, 3), r("سارا", 100))))
    val big = CashierAlerts.alerts(listOf(r("علی", 20_000_000, voids = 200_000), r("سارا", 30_000_000, voids = 1), r("رضا", 1)))
    assertEquals(listOf(Alert("علی", Kind.VOID, 100_000, 10_000_000, 1, 10_000_000, 10_000_000)), big)
    //  بزرگ‌ترین ضرب زیرِ ۲^۵۳ می‌ماند تا JS همین عدد را بدهد
    assertTrue(CashierAlerts.MAX_EVENTS * CashierAlerts.MAX_INVOICES * 100L < (1L shl 53))
    assertTrue(1_000L * CashierAlerts.MAX_EVENTS * CashierAlerts.MAX_INVOICES < (1L shl 53))
  }
}
