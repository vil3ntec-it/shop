package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.HeldClaim
import ir.vil3ntec.tohid.data.HeldConflict
import ir.vil3ntec.tohid.data.HeldExtras
import ir.vil3ntec.tohid.data.HeldInvoiceStore
import ir.vil3ntec.tohid.data.HeldInvoicesTest
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.pos.HeldDialog
import ir.vil3ntec.tohid.ui.components.pos.HeldInvoiceBar
import ir.vil3ntec.tohid.ui.components.pos.HeldInvoicesUi
import ir.vil3ntec.tohid.ui.components.pos.heldTwiceText
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Collections

/**
 *  POS-12 (S09) — آزمونِ خودکارِ صفحهٔ دو برگهٔ «در دستِ دستگاهِ X» و «یک فاکتور در دو دستگاه» روی همان `HeldInvoiceBar` ِ صندوقِ
 *  گوشی و کامپیوتر، با دستگاهِ دومِ ساختگی پشتِ `claim` (همان درِ `HeldSync.claim`): بازگرداندنِ فاکتوری که دستگاهِ «A1» گرفته
 *  برگهٔ هشدار را باز می‌کند و سبد دست نمی‌خورد؛ «نه» هیچ نمی‌گیرد؛ «باز کردن در این دستگاه» فقط با `force` می‌پرسد و همان فاکتور
 *  به سبد برمی‌گردد؛ برگهٔ «دوتایی» متنِ سری را می‌گوید و «دیدم» همان `ack` را می‌زند.
 */
class HeldClaimSheetsUiTest {

  @get:Rule val rule = createComposeRule()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val cart = CartStore(DesktopContext)
  private val store = HeldInvoiceStore(HeldInvoicesTest.MemDisk(), device = { "B2" }, clock = { 1_000L })
  private val asked = Collections.synchronizedList(ArrayList<Boolean>())
  private val acked = ArrayList<String>()
  private val data = ShopData(products = listOf(Product(id = "p1", name = "برنج", unit = "عدد", salePrice = 50.0)))

  @After fun after() { cart.clear(); scope.cancel() }

  /** فاکتورِ نگه‌داشتهٔ «برنج × ۲» و سبدِ خالی */
  private fun heldId(): String = runBlocking {
    cart.clear()
    cart.set(listOf(SalesEngine.CartLine("p1", 2.0)))
    store.hold(cart, HeldExtras(), "میز ۳", null)
    assertTrue(cart.snapshot().lines.isEmpty())
    store.book.value.items.single().id
  }

  private fun show(claim: (Boolean) -> HeldClaim): HeldInvoicesUi {
    I18n.source = { Lang.FA }
    val ui = HeldInvoicesUi(store, cart, scope, SnackbarHostState(), canDropStale = false)
    ui.data = data
    ui.claims = { true }
    ui.claim = { _, force -> asked += force; claim(force) }
    ui.ack = { acked += it }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { HeldInvoiceBar(ui) }
      }
    }
    return ui
  }

  @Test fun heldByAnotherDeviceOpensTheWarningSheetAndNoKeepsTheCartEmpty() {
    val id = heldId()
    val ui = show { force -> if (force) HeldClaim.Free else HeldClaim.Held("A1", "claimed", fresh = true) }
    ui.resume(id)
    rule.waitUntil(5_000) { rule.onAllNodesWithText(StrPos.heldClaimedBy("A1")).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText(StrPos.heldTakeWarn).assertExists()
    assertTrue(cart.snapshot().lines.isEmpty())
    rule.onNodeWithText(StrPos.no).performClick()
    rule.waitForIdle()
    assertTrue(rule.onAllNodesWithText(StrPos.heldTakeWarn).fetchSemanticsNodes().isEmpty())
    assertEquals(listOf(false), asked.toList())
    assertTrue(cart.snapshot().lines.isEmpty())
  }

  @Test fun takeOverAsksWithForceAndBringsTheSameInvoiceBack() {
    val id = heldId()
    val ui = show { force -> if (force) HeldClaim.Free else HeldClaim.Held("A1", "claimed", fresh = true) }
    ui.resume(id)
    rule.waitUntil(5_000) { rule.onAllNodesWithText(StrPos.heldTakeOver).fetchSemanticsNodes().isNotEmpty() }
    rule.onNodeWithText(StrPos.heldTakeOver).performClick()
    rule.waitUntil(5_000) { cart.snapshot().lines.isNotEmpty() }
    assertEquals(listOf(false, true), asked.toList())
    assertEquals(listOf("p1" to 2.0), cart.snapshot().lines.map { it.productId to it.quantity })
  }

  @Test fun twiceSheetNamesTheOtherSeriesAndSeenAcknowledges() {
    val ui = show { HeldClaim.Free }
    val c = HeldConflict(title = "میز ۳", series = "A1")
    rule.runOnIdle { ui.dialog = HeldDialog.Twice("h9", c) }
    rule.onNodeWithText(StrPos.heldTwiceTitle).assertExists()
    rule.onNodeWithText(heldTwiceText(c)).assertExists()
    rule.onNodeWithText(StrPos.heldSeen).performClick()
    rule.waitForIdle()
    assertEquals(listOf("h9"), acked)
    assertTrue(rule.onAllNodesWithText(StrPos.heldTwiceTitle).fetchSemanticsNodes().isEmpty())
  }
}
