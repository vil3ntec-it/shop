package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import ir.vil3ntec.tohid.ui.v3.V3_FORM_PRIMARY_TAG
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrPurchaseWorkflow
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import ir.vil3ntec.tohid.ui.v3.money.PurchaseLineText
import ir.vil3ntec.tohid.ui.v3.money.PurchaseRowText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 *  «خریدها» (e-rows-63b) با `ShellContext` ِ واقعی ([RouteUiHarness]) روی [PurchaseDemo]:
 *   • هر ردیفِ فهرست تأمین‌کننده، اقلام («آرد، روغن و ۳ قلمِ دیگر»)، حالِ پرداخت و رسیدن را دارد؛
 *   • جزئیات بارکد، سری و انقضای هر ردیف را نشان می‌دهد؛
 *   • «پرداخت» ِ جزئیات با ماندهٔ همان خرید پیش‌پُر است و همان خرید را صاف می‌کند (نه کهنه‌تر را)؛
 *   • «دریافتِ کالا» ِ سفارشِ باز همان صفحهٔ دریافتِ سفارش را باز می‌کند؛
 *   • کادرِ کشوییِ پرداخت فهرست را تنگ می‌کند.
 */
@OptIn(ExperimentalTestApi::class)
class PurchasesRoutesUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var h: RouteUiHarness
  private val data = PurchaseDemo.full()
  private val today = todayIso()
  private val views = PurchaseViews.all(data, today).associateBy { it.no }

  private val ctx = DesktopContext

  /** دفترِ روی دیسک (همان راهِ `ExchangeGuardsPhoneUiTest`) — برای آزمونی که صفحه در دفتر می‌نویسد */
  private fun disk(): ShopStore = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.replaceAll(data)) } }

  private fun clean() {
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ir.vil3ntec.tohid.data.ShopRole.remember(ctx, ir.vil3ntec.tohid.data.ShopRole.OWNER)
  }

  @Before fun up() { clean(); h = RouteUiHarness(rule) }

  @After fun down() { h.close(); clean() }

  @Test fun listRowsShowSupplierItemsPaymentAndReceipt() {
    h.show(MoneyPaths.PURCHASING, data)
    h.await(StrPurchases.tabPurchases)
    val big = views.getValue(PurchaseDemo.BIG_NO)
    h.await(PurchaseRowText.items(big), substring = true)
    assertEquals("آرد، روغن و 3 قلمِ دیگر", PurchaseRowText.items(big))
    //  ۶۸c (عمدی، کالبدِ `V3DataRow`): خطِ مبلغِ یک‌خطی و کوتاه — «۸٬۳۲۰ ؋ • ۳٬۳۲۰ ؋ مانده»، «سررسید ۴ قوس» به‌جای «نسیه —
    //  سررسید …»، «۲٬۱۰۰ ؋ • سررسید گذشته» (همهٔ مبلغ مانده)، و «رسیده» ِ عادی در فهرست نمی‌آید (جزئیات هنوز «نیمه‌پرداخت … •
    //  رسیده» و «سررسید گذشته — … مانده» را دارد)
    assertTrue(h.has(StrPurchases.leftAll(money(3320.0)), substring = true))
    assertFalse(h.has(StrPurchases.partly(money(3320.0)), substring = true))
    //  p0r3 (عمدی، خواستِ صاحب‌کار): مبلغ هم‌خطِ عنوان (نوشتهٔ جدا) و وضعیت در خطِ دومِ همان ردیف («… · سررسید گذشته»)
    assertTrue(h.has(PurchaseRowText.amount(views.getValue(PurchaseDemo.OVERDUE_NO))))
    assertTrue(h.has(" · " + StrPurchases.overdueRow, substring = true))
    assertTrue(h.has(PurchaseRowText.rowPay(views.getValue(PurchaseDemo.CREDIT_NO), today)!!.text, substring = true))
    assertFalse(h.has(StrPurchases.credit + " — ", substring = true))
    assertTrue(h.has(PurchaseRowText.amount(views.getValue(PurchaseDemo.PAID_NO))))
    assertTrue(h.has(" · " + StrPurchases.paid, substring = true))
    assertFalse(h.has(StrPurchases.paid + " • " + StrPurchases.received, substring = true))
    assertTrue(h.has("شرکت پخش البرز"))
    assertTrue(h.has("عمده‌فروشی پامیر"))
    //  تنها دکمهٔ شناور
    assertTrue(h.has(StrPurchases.newPurchase))
  }

  @Test fun detailShowsEveryLineWithBarcodeBatchAndExpiry() {
    h.show(MoneyPaths.purchase(views.getValue(PurchaseDemo.BIG_NO).key), data)
    h.await(StrPurchases.barcode(PurchaseLineText.ltr(PurchaseDemo.FLOUR_CODE)), substring = true)
    assertTrue(h.has(StrPurchases.lines(5)))
    assertTrue(h.has(StrPurchases.batch(PurchaseLineText.ltr("A-17")), substring = true))
    assertTrue(h.has(StrPurchases.barcode(PurchaseLineText.ltr("TEA-01")), substring = true))
    assertTrue(h.has(StrPurchases.noCode, substring = true))
    assertTrue(h.has("آرد"))
    assertTrue(h.has("50 کیلو × 40 ؋", substring = true))
    rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(StrPurchases.remaining))
    assertTrue(h.has(StrPurchases.remaining))
  }

  @Test fun payFromDetailIsPrefilledAndClearsThatPurchaseNotTheOlderOne() {
    val big = views.getValue(PurchaseDemo.BIG_NO)
    val store = disk()
    h.show(MoneyPaths.purchase(big.key), store)
    h.await(StrPurchases.barcode(PurchaseLineText.ltr(PurchaseDemo.FLOUR_CODE)), substring = true)
    rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(StrMoney.pay))
    h.click(StrMoney.pay)
    h.await(StrMoney.recordPayment)
    assertTrue(h.has(PurchaseBook.payNote(PurchaseDemo.BIG_NO), substring = true))
    h.click(StrMoney.recordPayment)
    h.awaitThat { store.data.value.supplierPayments.isNotEmpty() }
    val pay = store.data.value.supplierPayments.single()
    assertEquals(3320.0, pay.amount, 0.0)
    assertEquals(PurchaseBook.payNote(PurchaseDemo.BIG_NO), pay.notes)
    val after = PurchaseViews.all(store.data.value, today).associateBy { it.no }
    assertEquals(PurchaseViews.Pay.PAID, after.getValue(PurchaseDemo.BIG_NO).pay)
    assertEquals(2100.0, after.getValue(PurchaseDemo.OVERDUE_NO).remaining, 0.0)
    assertEquals(ShopStore.supplierDebt(data, PurchaseDemo.ALBORZ) - 3320.0, ShopStore.supplierDebt(store.data.value, PurchaseDemo.ALBORZ), 0.0)
  }

  @Test fun openOrdersShowAsNotReceivedAndReceiveOpensTheOrdersReceivePage() {
    FakePurchaseOrders(h.server, seed = PoDemoOrders.all)
    h.show(MoneyPaths.PURCHASING, data)
    h.await(StrPurchases.notReceived, substring = true)
    assertTrue(h.has(StrPurchases.partReceived, substring = true))
    //  پیش‌نویس و کامل خرید نیستند
    assertFalse(h.has(StrPurchases.orderNo(PoDemoOrders.draft.no), substring = true))
    assertFalse(h.has(StrPurchases.orderNo(PoDemoOrders.complete.no), substring = true))
    h.click(StrPurchasePages.receiveGoods)
    h.await(StrPurchaseOrders.receiveAll)
  }

  /** ۶۸c (عمدی): پرداخت، تأمین‌کننده و رسیدن در برگهٔ «فیلتر» (ردیفِ کادرها دیگر بریده نمی‌شود)؛ همان کادرِ کشوییِ پرداخت */
  @Test fun payFilterDropdownNarrowsTheList() {
    h.show(MoneyPaths.PURCHASING, data)
    h.await(StrHistory.filter)
    assertFalse("کادرِ پرداخت دیگر در ردیفِ بالای فهرست نیست", h.has(StrPurchases.payAll))
    h.click(StrHistory.filter)
    h.await(StrPurchases.payAll)
    h.click(StrPurchases.payAll)
    h.await(StrPurchases.overdueFilter)
    h.click(StrPurchases.overdueFilter)
    h.click(ir.vil3ntec.tohid.core.i18n.Str.close)
    h.awaitThat { !h.has("عمده‌فروشی پامیر") }
    assertTrue(h.has(StrPurchases.overdueRow, substring = true))
    assertFalse(h.has(StrPurchases.leftAll(money(3320.0)), substring = true))
  }

  @Test fun supplierTabOpensASeparateAccountFromItsTile() {
    h.show(MoneyPaths.PURCHASING, data)
    h.await(StrPurchasePages.suppliers)
    h.click(StrPurchasePages.suppliers)
    h.await("شرکت پخش البرز")
    assertTrue(h.has("عمده‌فروشی پامیر"))
    h.click("شرکت پخش البرز")
    h.await(StrPurchaseWorkflow.statement)
    assertFalse(h.has("عمده‌فروشی پامیر"))
  }

  @Test fun supplierPageListsItsPurchasesAndPayments() {
    h.show(MoneyPaths.supplier(PurchaseDemo.ALBORZ), data)
    h.await(StrPurchaseWorkflow.statement)
    assertTrue(h.has(StrMoney.payment))
    assertFalse(h.has("عمده‌فروشی پامیر"))
    assertTrue(h.has(StrPurchases.newPurchase))
    val reference = "#${PurchaseDemo.BIG_NO}"
    rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(reference))
    h.click(reference)
    h.await(StrPurchases.lines(5))
    assertTrue(h.has("آرد"))
  }

  @Test fun newLineClosesThePreviousBarcodeFieldAndReopensWithItsValue() {
    h.show(MoneyPaths.newPurchase(PurchaseDemo.PAMIR), data)
    h.await(StrPurchaseWorkflow.findProduct)
    h.type(StrPurchaseWorkflow.optionalBarcode, "123456")
    rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(StrMoney.addPurchaseRow))
    h.click(StrMoney.addPurchaseRow)
    h.await(StrMoney.purchaseRow(2))
    rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(StrMoney.purchaseRow(1)))
    assertFalse(h.has("123456"))
    h.click(StrMoney.purchaseRow(1))
    h.await("123456")
  }

  @Test fun partialPaymentUsesTheTypedAmountAndShowsTheBalance() {
    val store = disk()
    h.show(MoneyPaths.newPurchase(PurchaseDemo.PAMIR), store)
    h.await(StrPurchaseWorkflow.findProduct)
    h.click(StrPurchaseWorkflow.findProduct)
    h.await("نمک")
    h.click("نمک")
    h.type(StrMoney.quantity, "10")
    h.type(StrPurchaseWorkflow.optionalBarcode, "۹۸۷۶۵۴۳۲۱۰")
    assertFalse(h.has(StrPurchaseWorkflow.paymentPart))
    rule.onNodeWithTag(V3_FORM_PRIMARY_TAG).performSemanticsAction(SemanticsActions.OnClick)
    h.await(StrPurchaseWorkflow.paymentPart)
    h.click(StrPurchaseWorkflow.paymentPart)
    h.type(StrPurchaseWorkflow.paidAmount, "70")
    val count = store.data.value.purchases.size
    h.click(StrPurchaseWorkflow.confirmPurchase)
    h.awaitThat { store.data.value.purchases.size > count }
    val purchase = store.data.value.purchases.maxBy { it.createdAt }
    assertEquals(200.0, purchase.totalAmount, 0.0)
    assertEquals(70.0, purchase.paidAmount, 0.0)
    assertEquals(130.0, purchase.debt, 0.0)
    assertEquals("9876543210", purchase.barcode)
  }

  @Test fun unpaidNewCartonPurchaseIsBilledNowAndReceivedFromItsDetails() {
    val store = disk()
    h.show(MoneyPaths.newPurchase(PurchaseDemo.PAMIR), store)
    h.await(StrPurchaseWorkflow.loose)
    h.click(StrPurchaseWorkflow.loose)
    h.await(StrPurchaseWorkflow.carton)
    h.type(StrProducts.name, "چای کوهی")
    h.type(StrPurchaseWorkflow.cartons, "10")
    h.type(StrPurchaseWorkflow.pieces, "25")
    h.type(StrPurchaseWorkflow.cartonPrice, "600")
    h.type(StrPurchaseWorkflow.optionalBarcode, "9504000370640")
    h.type(StrPurchaseWorkflow.newSalePrice, "30")
    rule.onNodeWithTag(V3_FORM_PRIMARY_TAG).performSemanticsAction(SemanticsActions.OnClick)
    h.await(StrPurchaseWorkflow.paymentNow)
    h.click(StrPurchaseWorkflow.confirmPurchase)
    h.await(StrPurchaseWorkflow.paymentRequired)
    h.click(StrPurchaseWorkflow.paymentLater)
    h.click(StrPurchaseWorkflow.inTransit)
    h.await(StrPurchaseWorkflow.awaitingStock)
    h.click(StrPurchaseWorkflow.confirmPurchase)
    h.awaitThat { store.data.value.purchases.size > data.purchases.size }

    val recorded = store.data.value
    val purchase = recorded.purchases.last()
    val product = recorded.products.single { it.name == "چای کوهی" }
    assertEquals(product.id, purchase.productId)
    assertEquals(6000.0, purchase.debt, 0.0)
    assertEquals(0.0, ShopStore.stock(recorded, product.id), 0.0)
    assertEquals(30.0, product.salePrice, 0.0)
    val view = PurchaseViews.all(recorded, today).single { it.awaitingDelivery }
    assertEquals(listOf("9504000370640"), product.barcodes)

    h.show(MoneyPaths.purchase(view.key) + "?receive=1", store)
    h.await(StrPurchaseWorkflow.postDelivery)
    h.click(StrPurchaseWorkflow.postDelivery)
    h.awaitThat { ShopStore.stock(store.data.value, product.id) == 250.0 }
    assertEquals(24.0, ShopStore.product(store.data.value, product.id)!!.purchasePrice, 0.0)
    assertEquals(recorded.purchases, store.data.value.purchases)
  }
}
