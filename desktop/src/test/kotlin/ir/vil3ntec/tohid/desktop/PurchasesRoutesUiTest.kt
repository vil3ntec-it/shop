package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
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
    assertTrue(h.has(PurchaseRowText.amount(views.getValue(PurchaseDemo.OVERDUE_NO)) + " • " + StrPurchases.overdueRow))
    assertTrue(h.has(PurchaseRowText.rowPay(views.getValue(PurchaseDemo.CREDIT_NO), today)!!.text, substring = true))
    assertFalse(h.has(StrPurchases.credit + " — ", substring = true))
    assertTrue(h.has(PurchaseRowText.amount(views.getValue(PurchaseDemo.PAID_NO)) + " • " + StrPurchases.paid))
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
    assertTrue(h.has(StrPurchases.remaining))
  }

  @Test fun payFromDetailIsPrefilledAndClearsThatPurchaseNotTheOlderOne() {
    val big = views.getValue(PurchaseDemo.BIG_NO)
    val store = disk()
    h.show(MoneyPaths.purchase(big.key), store)
    h.await(StrPurchases.barcode(PurchaseLineText.ltr(PurchaseDemo.FLOUR_CODE)), substring = true)
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

  @Test fun supplierPageListsItsPurchasesAndPayments() {
    h.show(MoneyPaths.supplier(PurchaseDemo.ALBORZ), data)
    h.await(StrPurchases.tabPurchases)
    assertTrue(h.has(StrPurchases.payments))
    assertTrue(h.has(StrPurchases.leftAll(money(3320.0)), substring = true))
    assertTrue(h.has(StrPurchases.noPayments))
    assertFalse(h.has("عمده‌فروشی پامیر"))
    assertTrue(h.has(StrPurchases.newPurchase))
  }

  /**
   *  «الان رسیده؟ نه» ⇐ همان سفارشِ خریدِ سرور: یک ساختن با کلیدِ فرم (دو لمس ⇒ یک درخواست)، یک فرستادن، و پیش‌پرداختِ کامل
   *  یک بار پس از سرور؛ هیچ خرید، موجودی یا بدهیِ دفتر تا «دریافتِ کالا».
   */
  @Test fun notReceivedNowSendsOnePurchaseOrderWithOnePrepaymentAndNoStock() {
    val fake = FakePurchaseOrders(h.server)
    val store = disk()
    h.show(MoneyPaths.newPurchase(PurchaseDemo.PAMIR), store)
    h.await(StrPurchases.receivedNow)
    h.click(StrMoney.pickItem)
    h.await("نمک")
    h.click("نمک")
    h.type(StrMoney.quantity, "10")
    h.click(StrPurchases.laterReceived)
    h.await(StrPurchases.laterHow)
    val save = rule.onAllNodes(hasText(StrMoney.newPurchase) and hasClickAction())[0]
    save.performSemanticsAction(SemanticsActions.OnClick)
    runCatching { save.performSemanticsAction(SemanticsActions.OnClick) }
    h.awaitThat { store.data.value.supplierPayments.isNotEmpty() }
    val root = FakePurchaseOrders.ROOT
    val creates = fake.writes().filter { it.path == root && it.method == "POST" }
    assertEquals(1, creates.size)
    assertTrue(!creates.single().key.isNullOrBlank())
    assertEquals(1, fake.writes("/po-new-1/send").size)
    assertEquals("sent", fake.order("po-new-1")!!.state)
    val pay = store.data.value.supplierPayments.single()
    assertEquals(creates.single().key + "-pay", pay.id)
    assertEquals(200.0, pay.amount, 0.0)
    assertEquals(StrPurchases.prepaidNote(fake.order("po-new-1")!!.no), pay.notes)
    //  کالا هنوز نیامده: نه خرید، نه موجودیِ تازه؛ پامیر پیش‌پرداخت دارد
    assertEquals(data.purchases.size, store.data.value.purchases.size)
    assertEquals(ShopStore.stock(data, "salt"), ShopStore.stock(store.data.value, "salt"), 0.0)
    assertEquals(ShopStore.supplierDebt(data, PurchaseDemo.PAMIR) - 200.0, ShopStore.supplierDebt(store.data.value, PurchaseDemo.PAMIR), 0.0)
  }
}
