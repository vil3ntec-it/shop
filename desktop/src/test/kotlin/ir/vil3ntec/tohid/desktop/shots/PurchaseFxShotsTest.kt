package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.DesktopComposeUiTest
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
import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrSafeCur
import ir.vil3ntec.tohid.core.purchase.PurchaseFx
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.SupplierFx
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.scan.ScanRouter
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های PUR-11 برای صاحب‌کار: فرمِ «ثبت خرید» به دالر، جزئیاتِ خریدِ دالری، صفحهٔ تأمین‌کننده با «بدهی به تفکیکِ ارز» و برگهٔ
 *  پرداختِ دالری با اختلافِ نرخ — گوشیِ ۳۶۰×۷۶۰dp (چگالیِ ۲) روشن و تیره؛ و فاکتورِ خریدِ کامپیوتر به دالر و حسابِ تأمین‌کننده
 *  در ۱۳۶۶×۷۶۸. فقط با `PUR11_SHOTS=<پوشه>` (آزمونِ سد نیست؛ بی آن کنار می‌رود).
 */
@OptIn(ExperimentalTestApi::class)
class PurchaseFxShotsTest {

  private val out: File? = System.getenv("PUR11_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private var seq = 0
  private fun id() = "s${seq++}"

  private val base = ShopData(
    products = listOf(
      Product(id = "tea", name = "چای سبزِ دوبی", unit = "بسته", purchasePrice = 650.0, barcodes = listOf("6260011")),
      Product(id = "oil", name = "روغنِ آفتاب‌گردان", unit = "قطی", purchasePrice = 420.0, barcodes = listOf("6260012")),
    ),
    suppliers = listOf(Supplier(id = "dubai", name = "دوبی‌تریدرز", phone = "0799000111")),
  )

  /** فاکتورِ دالری (۱۰ بسته × ۱۰ دالر + ۲۰ قطی × ۶٫۲۵ دالر) به نرخِ ۷۰ با ۵۰ دالر پیش‌پرداخت، و پرداختِ ۴۰ دالر به نرخِ ۷۲ */
  private fun ledger(): ShopData {
    var d = (PurchaseBook.record(
      base, "dubai",
      listOf(PurchaseBook.Line("tea", 10.0, 0.0, fxPrice = 1_000), PurchaseBook.Line("oil", 20.0, 0.0, fxPrice = 625)),
      0.0, "2026-11-10", "بارِ دوبی", "2026-11-10", 1, ::id, key = "bill", fx = PurchaseFx.Rate("USD", 7_000, 1), fxPaid = 5_000,
    ) as PurchaseBook.Result.Ok).data
    d = (SupplierFx.pay(d, "dubai", "USD", 4_000, false, PurchaseFx.Rate("USD", 7_200, 1), "2026-11-14", "", "2026-11-14", 2, ::id, key = "pay") as LedgerEngine.Result.Ok).data
    return d
  }

  private fun DesktopComposeUiTest.save(name: String, dark: Boolean) {
    waitForIdle()
    repeat(12) { mainClock.advanceTimeBy(250); Thread.sleep(40) }
    val img = captureToImage().toAwtImage()
    ImageIO.write(img, "png", File(out!!.apply { mkdirs() }, "$name-${if (dark) "dark" else "light"}.png"))
  }

  private fun ComposeUiTest.click(text: String) {
    waitUntil(timeoutMillis = 20_000) { onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
  }

  private fun ComposeUiTest.typeIn(label: String, value: String, replace: Boolean = false) {
    val m = hasSetTextAction() and (hasContentDescription(label) or hasText(label, substring = true))
    waitUntil(timeoutMillis = 20_000) { onAllNodes(m).fetchSemanticsNodes().isNotEmpty() }
    if (replace) onAllNodes(m)[0].performTextReplacement(value) else onAllNodes(m)[0].performTextInput(value)
    waitForIdle()
  }

  private fun phone(name: String, dark: Boolean, path: String, data: ShopData, drive: DesktopComposeUiTest.() -> Unit = {}) =
    runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(2f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, 760),
          //  راست‌به‌چپ مثلِ برنامه (برگه‌ها و صفحه‌ها همان چیدمانِ گوشی)
          LocalLayoutDirection provides LayoutDirection.Rtl,
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            val store = remember { RouteUiHarness.memoryStore(data) }
            V3RoutePreview(path, store, dark)
          }
        }
      }
      waitForIdle()
      drive()
      save(name, dark)
    }

  private fun desk(name: String, data: ShopData, drive: DesktopComposeUiTest.() -> Unit) = runDesktopComposeUiTest(1366, 768, testTimeout = 3.minutes) {
    I18n.source = { Lang.FA }
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(1f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1366, 768),
        LocalDeskLayout provides true,
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.fillMaxSize()) {
              val store = remember { RouteUiHarness.memoryStore(data) }
              val d by store.data.collectAsState()
              DeskPurchasingScreen(store, d, remember { SnackbarHostState() })
            }
          }
        }
      }
    }
    waitForIdle()
    drive()
    save(name, false)
  }

  @Test
  fun shots() {
    Assume.assumeTrue("PUR11_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val d = ledger()
    val bill = PurchaseBook.invoices(d).single()
    for (dark in listOf(false, true)) {
      phone("phone-new-purchase-usd", dark, MoneyPaths.newPurchase("dubai"), base) {
        click(StrSafeCur.currency)
        click(StrFx.usd)
        typeIn(StrPurchaseFx.rateLabel("1", "USD"), "70.25", replace = true)
        click(StrMoney.pickItem)
        click("چای سبزِ دوبی")
        typeIn(StrMoney.quantity, "10")
        typeIn(StrPurchaseFx.priceIn("USD"), "10")
      }
      phone("phone-purchase-detail-usd", dark, MoneyPaths.purchase(bill.key), d)
      phone("phone-supplier-usd", dark, MoneyPaths.supplier("dubai"), d)
      phone("phone-pay-usd", dark, MoneyPaths.paySupplier("dubai"), d) {
        click("USD")
        typeIn(StrPurchaseFx.rateLabel("1", "USD"), "71", replace = true)
        typeIn(StrPurchaseFx.amountIn("USD"), "30")
      }
    }
    desk("desk-new-invoice-usd", base) {
      click(StrPurchasePages.newPurchaseKey)
      onAllNodes(hasText(StrSafeCur.afn) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
      waitForIdle()
      click(StrFx.usd)
      typeIn(StrPurchaseFx.rateLabel("1", "USD"), "70.25")
      runOnIdle { ScanRouter.offer("6260011") }
      typeIn(StrPurchaseFx.priceIn("USD"), "12.5")
      runOnIdle { ScanRouter.offer("6260012") }
      waitUntil(timeoutMillis = 20_000) { onAllNodes(hasSetTextAction() and hasContentDescription(StrPurchaseFx.priceIn("USD"))).fetchSemanticsNodes().size >= 2 }
      onAllNodes(hasSetTextAction() and hasContentDescription(StrPurchaseFx.priceIn("USD")))[1].performTextInput("6.25")
      waitForIdle()
    }
    desk("desk-supplier-usd", d) {
      click(StrPurchasePages.suppliers)
      click("دوبی‌تریدرز")
      click(StrPurchasePages.paySupplierKey)
      click("USD")
    }
    //  حالِ همین دفتر برای گزارشِ عکس‌ها
    File(out!!, "ledger.txt").writeText(
      listOf(
        "bill fx=${bill.fxTotal} afn=${bill.total} rate=${bill.fxRate}",
        "accounts=${SupplierFx.accounts(d, "dubai")}",
        "debtAfn=${ir.vil3ntec.tohid.data.ShopStore.supplierDebt(d, "dubai")}",
        "payments=${d.supplierPayments.map { "${it.fxAmountMinor} ${it.currency} rate=${it.fxRate} diff=${it.fxDiffMinor} amount=${it.amount}" }}",
        "tea cost=${d.products.first { it.id == "tea" }.purchasePrice} oil cost=${d.products.first { it.id == "oil" }.purchasePrice}",
      ).joinToString("\n") + "\n",
    )
  }
}
