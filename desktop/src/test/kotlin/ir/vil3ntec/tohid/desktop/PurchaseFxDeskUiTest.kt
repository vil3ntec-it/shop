package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrCustomerPages
import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.core.purchase.PurchaseFx
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.PurchaseFxPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.SupplierFx
import ir.vil3ntec.tohid.scan.ScanRouter
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 *  PUR-11 روی فاکتورِ خریدِ کامپیوتر (`DeskPurchasing`): ارز، نرخ، قیمتِ دالری ⇒ بهای افغانی و بدهیِ دالری. کلیدِ دکانِ «خرید به
 *  ارزِ دیگر» (`PurchaseFxPref`) خاموش ⇒ کادرِ ارز نیست، ولی بدهیِ دالریِ پیشین در پروندهٔ تأمین‌کننده دیده و پرداخت می‌شود.
 */
@OptIn(ExperimentalTestApi::class)
class PurchaseFxDeskUiTest {

  private val ctx = DesktopContext
  private val data = ShopData(
    products = listOf(Product(id = "tea", name = "چای سبز", unit = "بسته", purchasePrice = 650.0, barcodes = listOf("6260011"))),
    suppliers = listOf(Supplier(id = "dubai", name = "دوبی‌تریدرز")),
  )

  private fun clean() {
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
    PurchaseFxPref.set(ctx, false)
  }

  @Before fun up() { clean(); I18n.source = { Lang.FA } }

  @After fun down() { clean() }

  private fun desk(block: ComposeUiTest.(set: (@Composable () -> Unit) -> Unit) -> Unit) = runDesktopComposeUiTest(1366, 768, testTimeout = 3.minutes) {
    block { content ->
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(1f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(1366, 768),
          LocalDeskLayout provides true,
        ) {
          TohidTheme(ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Box(Modifier.fillMaxSize()) { content() } }
          }
        }
      }
    }
  }

  private fun ComposeUiTest.has(text: String, substring: Boolean = false) = onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()
  private fun ComposeUiTest.await(text: String, substring: Boolean = false) = waitUntil(timeoutMillis = 20_000) { has(text, substring) }
  private fun ComposeUiTest.tap(text: String) {
    onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
  }

  private fun ComposeUiTest.field(label: String) = onAllNodes(hasSetTextAction() and hasContentDescription(label))[0]

  @Test fun desktopInvoiceHasNoCurrencyPickerWhileTheShopSwitchIsOff() = desk { set ->
    assertFalse(PurchaseFxPref.on(ctx))
    val store = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.replaceAll(data)) } }
    set {
      val d by store.data.collectAsState()
      DeskPurchasingScreen(store, d, remember { SnackbarHostState() })
    }
    await(StrPurchasePages.newPurchaseKey)
    tap(StrPurchasePages.newPurchaseKey)
    await(StrPurchasePages.saveInvoiceKey)
    assertFalse(has(StrSafeCur.currency))
    assertFalse(has(StrSafeCur.afn))
  }

  @Test fun desktopUsdDebtStaysVisibleAndPayableWhileTheShopSwitchIsOff() = desk { set ->
    assertFalse(PurchaseFxPref.on(ctx))
    var seq = 0
    val bill = (PurchaseBook.record(
      data, "dubai", listOf(PurchaseBook.Line("tea", 10.0, 0.0, fxPrice = 1_000)), 0.0, "2026-11-10", "", "2026-11-10", 1,
      { "id${seq++}" }, key = "bill", fx = PurchaseFx.Rate("USD", 7_000, 1),
    ) as PurchaseBook.Result.Ok).data
    val store = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.replaceAll(bill)) } }
    set {
      val d by store.data.collectAsState()
      DeskPurchasingScreen(store, d, remember { SnackbarHostState() })
    }
    await(StrPurchasePages.suppliers)
    tap(StrPurchasePages.suppliers)
    await("دوبی‌تریدرز")
    tap("دوبی‌تریدرز")
    await(StrPurchaseFx.byCurrency, substring = true)
    tap(StrPurchasePages.paySupplierKey)
    await(StrPurchaseFx.account)
    tap("USD")
    await(StrPurchaseFx.payWith)
    field(StrPurchaseFx.rateLabel("1", "USD")).performTextReplacement("70")
    field(StrPurchaseFx.amountIn("USD")).performTextInput("100")
    waitForIdle()
    tap(StrCustomerPages.saveKey)
    waitUntil(timeoutMillis = 20_000) { store.data.value.supplierPayments.isNotEmpty() }
    val d = store.data.value
    assertEquals(PurchaseFx.Account("USD", 0, 0), SupplierFx.account(d, "dubai", "USD"))
    assertEquals(0.0, ShopStore.supplierDebt(d, "dubai"), 0.0)
  }

  @Test fun desktopInvoiceInUsdKeepsTheAfnCostAndTheUsdDebt() = desk { set ->
    PurchaseFxPref.set(ctx, true)
    val store = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.replaceAll(data)) } }
    set {
      val d by store.data.collectAsState()
      DeskPurchasingScreen(store, d, remember { SnackbarHostState() })
    }
    await(StrPurchasePages.newPurchaseKey)
    tap(StrPurchasePages.newPurchaseKey)
    onAllNodes(hasText(StrSafeCur.afn) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
    await(StrFx.usd)
    tap(StrFx.usd)
    await(StrPurchaseFx.priceIn("USD"))
    onAllNodes(hasSetTextAction() and hasContentDescription(StrPurchaseFx.rateLabel("1", "USD")))[0].performTextInput("70")
    //  بارکدخوان ⇐ ردیفِ تازه (قیمتِ افغانیِ کالا پیش‌پُر نمی‌شود)
    runOnIdle { ScanRouter.offer("6260011") }
    waitUntil(timeoutMillis = 20_000) { onAllNodes(hasSetTextAction() and hasContentDescription(StrPurchaseFx.priceIn("USD"))).fetchSemanticsNodes().isNotEmpty() && has("چای سبز") }
    onAllNodes(hasSetTextAction() and hasContentDescription(StrPurchaseFx.priceIn("USD")))[0].performTextInput("12.5")
    waitForIdle()
    //  جمعِ ارزی و معادلِ افغانیِ زنده (چپ‌به‌راستِ جدا)
    await(SupplierFx.ltr(SupplierFx.text(1_250, "USD") + " = 875 ؋"))
    tap(StrPurchasePages.saveInvoiceKey)
    waitUntil(timeoutMillis = 20_000) { store.data.value.purchases.isNotEmpty() }
    val d = store.data.value
    val p = d.purchases.single()
    assertEquals("USD", p.currency)
    assertEquals(1_250L, p.fxTotalMinor)
    assertEquals(875.0, p.totalAmount, 0.0)
    assertEquals(875.0, d.products.single().purchasePrice, 0.0)
    assertEquals(1.0, ShopStore.stock(d, "tea"), 0.0)
    assertEquals(PurchaseFx.Account("USD", 1_250, 87_500), SupplierFx.account(d, "dubai", "USD"))
  }
}
