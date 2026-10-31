package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.LedgerBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ماندهٔ پس از تسویه از جمعِ چند ردیف (بازبینیِ #470): سه بردِ اعشاری و یک رسید که دقیقاً می‌بندد.
 *  جمعِ Double −9.09e-13 می‌داد و حساب «۰ افغانی موجودی دارد» یا «بدهکار» خوانده می‌شد، در حالی که
 *  `DebtBook` صفر می‌دید. حالا هر جایی که مانده را جمع می‌زند به پول جمع می‌زند (`LedgerBalance`).
 */
class DebtSettledTest {

  private fun tx(id: String, who: String, type: String, amount: Double) =
    DebtTransaction(id = id, debtorId = who, type = type, amount = amount, date = "2026-10-01")

  /** c1: دقیقاً تسویه؛ c2: یک پول کمتر پرداخته */
  private val d = ShopData(
    debtors = listOf(Debtor(id = "c1", name = "صاف"), Debtor(id = "c2", name = "یک پول")),
    transactions = listOf(
      tx("a", "c1", "give", 966.77), tx("b", "c1", "give", 4963.8), tx("c", "c1", "give", 1920.32), tx("d", "c1", "receive", 7850.89),
      tx("e", "c2", "give", 966.77), tx("f", "c2", "give", 4963.8), tx("g", "c2", "give", 1920.32), tx("h", "c2", "receive", 7850.88),
    ),
  )

  @Test fun `جمعِ Double ِ همین ردیف‌ها پسماند دارد — وگرنه این آزمون چیزی نمی‌سنجید`() {
    assertNotEquals(0.0, 966.77 + 4963.8 + 1920.32 - 7850.89, 0.0)
  }

  @Test fun `تسویهٔ دقیق صفرِ دقیق و «حساب صاف» است`() {
    assertEquals(0.0, ShopStore.debt(d, "c1"), 0.0)
    assertEquals("حساب صاف است", DebtorEngine.stateText(ShopStore.debt(d, "c1")))
    assertEquals(0.0, DebtorEngine.account(d, "c1")!!.balance, 0.0)
    assertEquals(0.0, ReportEngine.debtors(d).first { it.debtor.id == "c1" }.remaining, 0.0)
    //  پسماندِ خامی که از جای دیگری برسد هم «صاف» است
    assertEquals("حساب صاف است", DebtorEngine.stateText(966.77 + 4963.8 + 1920.32 - 7850.89))
  }

  @Test fun `یک پول مانده هنوز بدهی است — همه یک‌جور`() {
    assertEquals(0.01, ShopStore.debt(d, "c2"), 0.0)
    assertEquals(1, LedgerBalance.sign(ShopStore.debt(d, "c2")))
    assertTrue(DebtorEngine.stateText(ShopStore.debt(d, "c2")).endsWith("افغانی بدهکار"))
    assertEquals(0.01, ReportEngine.debtors(d).first { it.debtor.id == "c2" }.remaining, 0.0)
    assertEquals(listOf("c2"), LedgerSummary.of(d, now = 0L).owing.map { it.id })
    assertEquals(0.01, DashboardEngine.board(d, "2026-10-01", 7, now = 0L).receivables, 0.0)
  }

  @Test fun `ماندهٔ ShopStore همان ماندهٔ DebtBook است، به پول`() {
    for (id in listOf("c1", "c2")) {
      val book = d.transactions.filter { it.debtorId == id }
        .sumOf { if (it.type == "give") DebtBook.minor(it.amount).minor else -DebtBook.minor(it.amount).minor }
      assertEquals(id, book, LedgerBalance.minorOf(ShopStore.debt(d, id)))
    }
  }
}
