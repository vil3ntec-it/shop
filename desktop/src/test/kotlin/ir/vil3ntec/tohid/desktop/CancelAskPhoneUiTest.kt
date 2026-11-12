package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  STF-02 — صفحهٔ فاکتورِ **گوشیِ نسخهٔ ۳** (`sales/{id}`، `InvoiceRoute`) برای نقشِ «نیازمندِ تأیید» ِ `sales.cancel` روی دستگاهِ
 *  Sync v3 (`CancelApproval.offered`): «لغوِ فاکتور» در منو هست و به برگهٔ تأییدِ مدیر (`CancelRoute`، شاخهٔ `askable`) می‌رسد؛
 *  «اصلاحِ فاکتور» (که برگهٔ لغو آن را نمی‌پوشاند) نه. دکانِ v1 همان امروز: نه لغو، نه اصلاح.
 *  ⛔ روی `claude/batch-57` (e69b30114) ورودِ لغو با `canCancel` پنهان بود و شاخهٔ `askable` از رابط نمی‌رسید.
 */
@OptIn(ExperimentalTestApi::class)
class CancelAskPhoneUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val tea = Product(id = "ca-tea", name = "چایِ آزمونی", unit = "بسته", salePrice = 250.0)
  private var seq = 0
  private val data = (SalesEngine.record(
    ShopData(products = listOf(tea), warehouseEntries = listOf(WarehouseEntry(id = "ca-w", productId = tea.id, units = 5.0))),
    listOf(SalesEngine.CartLine(tea.id, 1.0)), SalesEngine.Checkout(seller = "صندوقدار"), "2026-11-10", 1L, { "ca-${seq++}" },
  ) as SalesEngine.Result.Ok).data
  private val sale = data.sales.single()
  private lateinit var h: RouteUiHarness

  private fun clean() {
    ShopRole.remember(ctx, ShopRole.OWNER)
    ShopRole.rememberPolicy(ctx, null)
    SyncChoice.set(ctx, SyncChoice.V1, 0)
  }

  @Before fun up() {
    clean()
    ShopRole.remember(ctx, ShopRole.CASHIER)
    ShopRole.rememberPolicy(ctx, """{"v":1,"layers":[{"sales.cancel":{"mode":"approve"}}]}""")
    h = RouteUiHarness(rule)
  }

  @After fun down() { h.close(); clean() }

  private fun more() {
    h.awaitThat { rule.onAllNodes(hasContentDescription(StrKit.more) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    rule.onAllNodes(hasContentDescription(StrKit.more) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  @Test fun onSyncV3TheCancelEntryReachesTheManagerApprovalSheet() {
    assertEquals("", SyncChoice.set(ctx, SyncChoice.V3, 0))
    h.show(SalesPaths.invoice(sale.id), data)
    more()
    runCatching { h.await(StrSale.cancelSale, ms = 5_000) }
      .onFailure { throw AssertionError("«لغوِ فاکتور» برای نقشِ «نیازمندِ تأیید» در منوی فاکتور نیست", it) }
    assertFalse("«اصلاح» را برگهٔ لغو نمی‌پوشاند", h.has(StrSale.fixInvoice))
    h.click(StrSale.cancelSale)
    h.await(StrSale.cancelApproval)
  }

  @Test fun onSyncV1TheRoleStillHasNoCancelEntry() {
    h.show(SalesPaths.invoice(sale.id), data)
    h.await(StrSale.print)
    if (rule.onAllNodes(hasContentDescription(StrKit.more) and hasClickAction()).fetchSemanticsNodes().isNotEmpty()) more()
    assertFalse(h.has(StrSale.cancelSale))
    assertFalse(h.has(StrSale.fixInvoice))
  }
}
