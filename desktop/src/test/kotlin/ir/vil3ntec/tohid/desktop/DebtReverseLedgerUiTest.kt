package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.test.junit4.createComposeRule
import ir.vil3ntec.tohid.core.debt.DebtReversal
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Rule
import org.junit.Test

/**
 *  S89 — حسابِ قرض‌دار (`debtors/{id}`) با سندِ معکوس: اصل و معکوس هر دو در فهرست می‌مانند (سابقه روشن) و به‌جای «مانده»
 *  برچسبِ «معکوس‌شده»/«معکوس» می‌گیرند.
 */
class DebtReverseLedgerUiTest {

  @get:Rule val rule = createComposeRule()

  @Test fun reversalAndReversedRowsAreLabelled() {
    val d = ShopData(
      debtors = listOf(Debtor(id = "d1", name = "احمد")),
      transactions = listOf(
        DebtTransaction("g1", "d1", "give", 400.0, "2026-08-01", createdAt = 1),
        DebtTransaction("r1", "d1", "receive", 150.0, "2026-08-10", createdAt = 2),
        DebtTransaction("r1-rv", "d1", "give", 150.0, "2026-08-20", createdAt = 3, refId = "r1", kind = DebtReversal.KIND),
      ),
    )
    RouteUiHarness(rule).use { h ->
      h.show("debtors/d1", d)
      h.await(StrMoney.reversedTag)
      h.await(StrMoney.reversalTag)
    }
  }
}
