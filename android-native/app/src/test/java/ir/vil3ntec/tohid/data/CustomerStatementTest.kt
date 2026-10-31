package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.receipt.StatementLayout
import ir.vil3ntec.tohid.core.receipt.StatementLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CRM-05 — ردیف‌های تصویرِ صورت‌حساب (`CustomerStatement`) همان ترتیب و ماندهٔ صورت‌حسابِ CSV
 *  (`ReportCsv.statement`) و دفترِ قرض (`ShopStore.debt`) را دارند، به پول.
 */
class CustomerStatementTest {

  private val d = ShopData(
    debtors = listOf(Debtor(id = "c1", name = "احمد"), Debtor(id = "c2", name = "زهرا")),
    transactions = listOf(
      //  ترتیبِ ثبتِ فهرست عمداً به‌هم‌ریخته: تاریخ، سپس زمانِ ثبت
      DebtTransaction(id = "t3", debtorId = "c1", type = "give", amount = 200.0, date = "2026-10-09", notes = "برنج", createdAt = 30),
      DebtTransaction(id = "t1", debtorId = "c1", type = "give", amount = 1000.0, date = "2026-10-01", notes = "جنس", createdAt = 10),
      DebtTransaction(id = "x", debtorId = "c2", type = "give", amount = 999.0, date = "2026-10-02", createdAt = 15),
      DebtTransaction(id = "t2", debtorId = "c1", type = "receive", amount = 400.5, date = "2026-10-05", createdAt = 20),
      DebtTransaction(id = "t2b", debtorId = "c1", type = "receive", amount = 0.25, date = "2026-10-05", createdAt = 25),
      //  ثبتِ دیرتر با تاریخِ قدیمی‌تر (برگهٔ جاافتاده) ⇒ به ترتیبِ تاریخ اول می‌آید، نه به ترتیبِ ثبت
      DebtTransaction(id = "t0", debtorId = "c1", type = "give", amount = 50.0, date = "2026-09-28", notes = "قدیمی", createdAt = 40),
    ),
  )

  @Test
  fun linesFollowDateThenEntryOrderWithRunningBalanceInPul() {
    assertEquals(
      listOf(
        StatementLine("2026-09-28", "قدیمی", 5_000, 0, 5_000),
        StatementLine("2026-10-01", "جنس", 100_000, 0, 105_000),
        StatementLine("2026-10-05", "", 0, 40_050, 64_950),
        StatementLine("2026-10-05", "", 0, 25, 64_925),
        StatementLine("2026-10-09", "برنج", 20_000, 0, 84_925),
      ),
      CustomerStatement.lines(d, "c1"),
    )
  }

  @Test
  fun finalBalanceIsTheDebtBookAndTheCsvStatement() {
    val lines = CustomerStatement.lines(d, "c1")
    assertEquals(Math.round(ShopStore.debt(d, "c1") * 100), StatementLayout.finalMinor(lines))
    val csv = ReportCsv.statement(d, "c1", "2026-10-30").rows.drop(1)
    assertEquals(csv.size, lines.size)
    assertEquals(csv.map { it[0] }, lines.map { it.dateIso })
  }

  @Test
  fun otherCustomersAndEmptyAccounts() {
    assertEquals(listOf(99_900L), CustomerStatement.lines(d, "c2").map { it.balanceMinor })
    assertTrue(CustomerStatement.lines(d, "nobody").isEmpty())
    assertEquals("صورت‌حساب-احمد-2026-10-30.png", CustomerStatement.fileName(d, "c1", "2026-10-30"))
  }
}
