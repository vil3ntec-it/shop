package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import ir.vil3ntec.tohid.ui.v3.V3_FORM_PRIMARY_TAG
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.i18n.StrPurchaseWorkflow
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
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  PUR-11 روی صفحه‌ها: فاکتورِ خریدِ دالری در فرمِ گوشی و فاکتورِ کامپیوتر ⇒ بهای کالا و انبار به معادلِ افغانی، بدهیِ دالریِ
 *  تأمین‌کننده؛ صفحهٔ تأمین‌کننده ماندهٔ هر ارز را نشان می‌دهد؛ پرداختِ دالری با نرخِ دیگر ⇒ اختلافِ نرخِ ثابت.
 *  کلیدِ دکانِ «خرید به ارزِ دیگر» (`PurchaseFxPref`) پیش‌فرض خاموش است: بی آن کادرِ ارز نیست، ولی بدهیِ ارزیِ پیشین دیده و
 *  پرداخت می‌شود (آزمون‌های صفحهٔ تأمین‌کننده و پرداخت عمداً با کلیدِ خاموش‌اند).
 */
@OptIn(ExperimentalTestApi::class)
class PurchaseFxUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var h: RouteUiHarness
  private val ctx = DesktopContext
  private val data = ShopData(
    products = listOf(Product(id = "tea", name = "چای سبز", unit = "بسته", purchasePrice = 650.0, barcodes = listOf("6260011"))),
    suppliers = listOf(Supplier(id = "dubai", name = "دوبی‌تریدرز")),
  )

  private fun disk(d: ShopData = data): ShopStore = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.replaceAll(d)) } }

  private fun clean() {
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
    PurchaseFxPref.set(ctx, false)
  }

  @Before fun up() { clean(); I18n.source = { Lang.FA }; h = RouteUiHarness(rule) }

  @After fun down() { h.close(); clean() }

  /** فاکتورِ ۱۰۰ دالری (۱۰ بسته × ۱۰ دالر) با نرخِ ۷۰، نسیه */
  private fun withUsdBill(): ShopData = (PurchaseBook.record(
    data, "dubai", listOf(PurchaseBook.Line("tea", 10.0, 0.0, fxPrice = 1_000)), 0.0, "2026-11-10", "", "2026-11-10", 1,
    { "id${seq++}" }, key = "bill", fx = PurchaseFx.Rate("USD", 7_000, 1),
  ) as PurchaseBook.Result.Ok).data
  private var seq = 0

  private fun replace(label: String, value: String) {
    rule.onAllNodes(hasSetTextAction() and (hasContentDescription(label) or hasText(label)))[0].performTextReplacement(value)
    rule.waitForIdle()
  }

  @Test fun phoneFormHasNoCurrencyPickerWhileTheShopSwitchIsOff() {
    assertFalse(PurchaseFxPref.on(ctx))
    h.show(MoneyPaths.newPurchase("dubai"), disk())
    h.await(StrMoney.pickItem)
    assertFalse(h.has(StrSafeCur.currency))
    assertFalse(h.has(StrPurchaseFx.section))
  }

  @Test fun phoneFormShowsTheCurrencyPickerWhenTheShopSwitchIsOn() {
    PurchaseFxPref.set(ctx, true)
    h.show(MoneyPaths.newPurchase("dubai"), disk())
    h.await(StrSafeCur.currency)
  }

  @Test fun phoneFormRecordsAUsdBillAtTheRecordedRate() {
    PurchaseFxPref.set(ctx, true)
    val store = disk()
    h.show(MoneyPaths.newPurchase("dubai"), store)
    //  ارز ⇐ دالر: بخشِ «ارز و نرخ»، قیمت به دالر
    h.click(StrSafeCur.currency)
    h.await(StrFx.usd)
    h.click(StrFx.usd)
    h.await(StrPurchaseFx.section)
    replace(StrPurchaseFx.rateLabel("1", "USD"), "70")
    h.click(StrMoney.pickItem)
    h.await("چای سبز")
    h.click("چای سبز")
    h.type(StrMoney.quantity, "10")
    h.type(StrPurchaseFx.priceIn("USD"), "10")
    rule.onNodeWithTag(V3_FORM_PRIMARY_TAG).performSemanticsAction(SemanticsActions.OnClick)
    h.await(StrPurchaseWorkflow.paymentLater)
    h.click(StrPurchaseWorkflow.paymentLater)
    h.click(StrPurchaseWorkflow.confirmPurchase)
    h.awaitThat { store.data.value.purchases.isNotEmpty() }
    val d = store.data.value
    val p = d.purchases.single()
    assertEquals("USD", p.currency)
    assertEquals(10_000L, p.fxTotalMinor)
    assertEquals(7_000L, p.fxRate)
    assertEquals(7_000.0, p.totalAmount, 0.0)
    //  بهای کالا = معادلِ افغانی ÷ مقدار؛ انبار به همان بها
    assertEquals(700.0, d.products.single().purchasePrice, 0.0)
    assertEquals(10.0, ShopStore.stock(d, "tea"), 0.0)
    assertEquals(70_000L, d.warehouseEntries.single().price.let { Math.round(it * 100) })
    //  بدهی: ۱۰۰ دالر (۷٬۰۰۰ افغانیِ دفتری)
    assertEquals(PurchaseFx.Account("USD", 10_000, 700_000), SupplierFx.account(d, "dubai", "USD"))
    assertEquals(7_000.0, ShopStore.supplierDebt(d, "dubai"), 0.0)
  }

  @Test fun supplierPageShowsTheBalanceOfEachCurrency() {
    assertFalse(PurchaseFxPref.on(ctx))
    h.show(MoneyPaths.supplier("dubai"), withUsdBill())
    h.await(StrPurchaseFx.byCurrency)
    assertTrue(h.has(StrPurchaseFx.owed("100 USD", "7,000 ؋"), substring = true))
    h.await(StrPurchaseWorkflow.statement)
    assertTrue(h.has("7,000 ؋"))
  }

  @Test fun phonePaymentInUsdAtAnotherRateRecordsTheExchangeDifference() {
    assertFalse(PurchaseFxPref.on(ctx))
    val store = disk(withUsdBill())
    h.show(MoneyPaths.paySupplier("dubai"), store)
    h.await(StrPurchaseFx.account)
    h.click("USD")
    h.await(StrPurchaseFx.payWith)
    replace(StrPurchaseFx.rateLabel("1", "USD"), "72")
    h.type(StrPurchaseFx.amountIn("USD"), "40")
    h.await(StrPurchaseFx.fxLoss("80 ؋"), substring = true)
    h.click(StrMoney.recordPayment)
    h.awaitThat { store.data.value.supplierPayments.isNotEmpty() }
    val pay = store.data.value.supplierPayments.single()
    assertEquals("USD", pay.currency)
    assertEquals(4_000L, pay.fxAmountMinor)
    assertEquals(7_200L, pay.fxRate)
    assertEquals(8_000L, pay.fxDiffMinor)
    assertEquals(2_800.0, pay.amount, 0.0)
    assertEquals(PurchaseFx.Account("USD", 6_000, 420_000), SupplierFx.account(store.data.value, "dubai", "USD"))
  }

  @Test fun usdDebtStaysPayableInFullWhileTheShopSwitchIsOff() {
    assertFalse(PurchaseFxPref.on(ctx))
    val store = disk(withUsdBill())
    h.show(MoneyPaths.paySupplier("dubai"), store)
    h.await(StrPurchaseFx.account)
    h.click("USD")
    h.await(StrPurchaseFx.payWith)
    replace(StrPurchaseFx.rateLabel("1", "USD"), "70")
    h.type(StrPurchaseFx.amountIn("USD"), "100")
    h.click(StrMoney.recordPayment)
    h.awaitThat { store.data.value.supplierPayments.isNotEmpty() }
    val d = store.data.value
    assertEquals(PurchaseFx.Account("USD", 0, 0), SupplierFx.account(d, "dubai", "USD"))
    assertEquals(0.0, ShopStore.supplierDebt(d, "dubai"), 0.0)
    assertEquals(null, d.supplierPayments.single().fxDiffMinor)
  }
}
