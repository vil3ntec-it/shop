package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import ir.vil3ntec.tohid.core.debt.DebtReversal
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 *  S89 — حسابِ قرض‌دار (`DebtorLedgerScreen`) با سندِ معکوس: اصل و معکوس هر دو در فهرست می‌مانند (سابقه روشن) و به‌جای «مانده»
 *  برچسبِ «معکوس‌شده»/«معکوس» می‌گیرند؛ دفترِ بی معکوس هیچ‌کدام را ندارد.
 */
class DebtReverseLedgerUiTest {

  @get:Rule val rule = createComposeRule()

  private val base = ShopData(
    debtors = listOf(Debtor(id = "d1", name = "احمد")),
    transactions = listOf(
      DebtTransaction("g1", "d1", "give", 400.0, "2026-08-01", createdAt = 1),
      DebtTransaction("r1", "d1", "receive", 150.0, "2026-08-10", createdAt = 2),
    ),
  )
  private val reversed = base.copy(
    transactions = base.transactions + DebtTransaction("r1-rv", "d1", "give", 150.0, "2026-08-20", createdAt = 3, refId = "r1", kind = DebtReversal.KIND),
  )

  private fun show(d: ShopData) {
    I18n.source = { Lang.FA }
    val actions = DebtorLedgerActions(
      onBack = null, onCompose = {}, onEdit = {}, onDelete = {}, onOpenTx = {}, onDeleteTx = {}, onOpenSale = {}, onOlderSales = {},
    )
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { DebtorLedgerScreen("d1", d, wide = false, actions = actions, drawerOpen = false) }
      }
    }
  }

  private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

  @Test fun reversalAndReversedRowsAreLabelled() {
    show(reversed)
    rule.waitUntil(15_000) { count(StrMoney.reversedTag) > 0 }
    assertEquals(1, count(StrMoney.reversedTag))
    assertEquals(1, count(StrMoney.reversalTag))
  }

  @Test fun ledgerWithoutReversalHasNoTags() {
    show(base)
    rule.waitUntil(15_000) { count("احمد") > 0 }
    assertEquals(0, count(StrMoney.reversedTag) + count(StrMoney.reversalTag))
  }
}
