package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «ادغامِ تکراری‌ها» (CRM-10): پشتِ تنظیمِ خاموش، بی گروهِ در حالِ حذف، پیش‌نمایش همان موتور */
class DebtorMergeModelTest {

  private val ledger = ShopData(
    debtors = listOf(
      Debtor(id = "a", name = "احمد", phone = "0700123456", createdAt = 1),
      Debtor(id = "b", name = "احمد کریمی", phone = "0700123456", createdAt = 2),
      Debtor(id = "c", name = "زهرا", createdAt = 3),
    ),
    transactions = listOf(
      DebtTransaction(id = "t1", debtorId = "a", type = "give", amount = 300.0, date = "2026-10-01"),
      DebtTransaction(id = "t2", debtorId = "b", type = "give", amount = 200.0, date = "2026-10-02"),
      DebtTransaction(id = "t3", debtorId = "b", type = "receive", amount = 50.0, date = "2026-10-03"),
    ),
  )

  @Test fun offShowsNothing() {
    assertTrue(DebtorMergeModel.groups(ledger, on = false, hidden = emptySet()).isEmpty())
  }

  @Test fun samePhoneGroupedWithBusiestKept() {
    val g = DebtorMergeModel.groups(ledger, on = true, hidden = emptySet())
    assertEquals(1, g.size)
    assertTrue(g[0].strong)
    assertEquals("b", g[0].keep.id)
    assertEquals(setOf("a", "b"), g[0].members.map { it.id }.toSet())
  }

  @Test fun groupWithPendingDeleteIsHidden() {
    assertTrue(DebtorMergeModel.groups(ledger, on = true, hidden = setOf("a")).isEmpty())
  }

  @Test fun previewSumsBalancesAndRejectsMergedOrSelf() {
    val p = DebtorMergeModel.preview(ledger, "b", "a")!!
    assertEquals(1, p.transactions)
    assertEquals(450.0, p.balanceAfter, 0.001)
    assertNull(DebtorMergeModel.preview(ledger, "a", "a"))
    val merged = ledger.copy(debtors = ledger.debtors.map { if (it.id == "a") it.copy(mergedInto = "b") else it })
    assertNull(DebtorMergeModel.preview(merged, "b", "a"))
  }
}
