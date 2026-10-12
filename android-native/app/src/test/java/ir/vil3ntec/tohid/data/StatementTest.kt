package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** صورت‌حسابِ مشتری — ماندهٔ ردیفِ آخر همان «الباقی»ِ صفحهٔ حساب است */
class StatementTest {

  @Test
  fun `مانده پس از هر ردیف و ماندهٔ آخر برابر بدهی`() {
    val d = ShopData(
      debtors = listOf(Debtor(id = "c1", name = "احمد/کریمی")),
      transactions = listOf(
        DebtTransaction("t2", "c1", "receive", 300.0, "2026-10-05", createdAt = 2),
        DebtTransaction("t1", "c1", "give", 1000.0, "2026-10-01", createdAt = 1),
        DebtTransaction("t3", "c1", "give", 50.0, "2026-10-09", "شکر", createdAt = 3),
        DebtTransaction("x", "other", "give", 999.0, "2026-10-02", createdAt = 4),
      ),
    )
    val sheet = ReportCsv.statement(d, "c1", "2026-10-12")
    assertEquals(4, sheet.rows.size)
    assertEquals(listOf("1000", "700", "750"), sheet.rows.drop(1).map { it[4] })
    assertEquals("شکر", sheet.rows[3][1])
    assertEquals(ShopStore.debt(d, "c1"), 750.0, 0.0)
    //  «/» در نامِ فایل نمی‌آید
    assertEquals("صورت‌حساب-احمد کریمی-2026-10-12", sheet.name)
  }
}
