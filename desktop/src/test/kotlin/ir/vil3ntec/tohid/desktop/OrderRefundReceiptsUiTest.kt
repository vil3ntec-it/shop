package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrRefundSlip
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderRefundReceiptDto
import ir.vil3ntec.tohid.core.model.OrderRefundSummaryDto
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.online.orders.DetailActions
import ir.vil3ntec.tohid.ui.v3.online.orders.DetailState
import ir.vil3ntec.tohid.ui.v3.online.orders.OrderDetailScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 *  E2E-RET-11 — بخشِ «رسیدهای اصلاحی» ِ صفحهٔ سفارش (`OrderDetailScreen`): فقط وقتی سرور رسید داد؛ هر رسید با شماره و
 *  «بازپرداخت … · مبلغِ درست …» ِ سرور، و لمس ⇒ همان رسید به چاپ. بی رسید (پرچمِ خاموش) ⇒ هیچ بخشی.
 */
class OrderRefundReceiptsUiTest {

  @get:Rule val rule = createComposeRule()

  private val printed = mutableListOf<String>()

  private val r1 = OrderRefundReceiptDto("71-R1", 1, 1000, 71, "cash", false, 125_000, 0, 30_000, 30_000, 95_000)
  private val r2 = OrderRefundReceiptDto("71-R2", 2, 2000, 71, "cash", true, 125_000, 30_000, 20_000, 50_000, 75_000)

  private fun show(receipts: List<OrderRefundReceiptDto>) {
    I18n.source = { Lang.FA }
    val o = OrderDto(id = "o1", orderNo = 71, status = "refunded_partial", subtotalMinor = 125_000, totalMinor = 125_000,
      refund = OrderRefundSummaryDto(125_000, 50_000, 75_000, receipts))
    val actions = DetailActions(onRetry = {}, onReload = {}, onStep = {}, onCall = {}, onCopy = {}, onMap = {},
      onPrintRefund = { printed += it.no })
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { OrderDetailScreen(DetailState(order = o, loading = false), actions, onBack = null) }
      }
    }
  }

  @Test fun eachServerReceiptIsListedWithItsAmountsAndTapPrintsThatOne() {
    show(listOf(r1, r2))
    rule.onNodeWithText(StrRefundSlip.section).performScrollTo().assertExists()
    rule.onNodeWithText(StrRefundSlip.row("300", "950")).assertExists()
    rule.onNodeWithText(StrRefundSlip.row("200", "750")).assertExists()
    rule.onNodeWithText(StrRefundSlip.title("71-R2")).performScrollTo().performClick()
    assertEquals(listOf("71-R2"), printed)
  }

  @Test fun noReceiptsFromServerShowsNoSection() {
    show(emptyList())
    assertEquals(0, rule.onAllNodesWithText(StrRefundSlip.section).fetchSemanticsNodes().size)
    assertEquals(0, rule.onAllNodesWithText(StrRefundSlip.print).fetchSemanticsNodes().size)
  }
}
