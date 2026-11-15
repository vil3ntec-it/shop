package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
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
import ir.vil3ntec.tohid.ui.v3.money.PurchaseRowText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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

  @Before fun up() { h = RouteUiHarness(rule) }

  @After fun down() = h.close()

  @Test fun listRowsShowSupplierItemsPaymentAndReceipt() {
    h.show(MoneyPaths.PURCHASING, data)
    h.await(StrPurchases.tabPurchases)
    val big = views.getValue(PurchaseDemo.BIG_NO)
    h.await(PurchaseRowText.items(big), substring = true)
    assertEquals("آرد، روغن و 3 قلمِ دیگر", PurchaseRowText.items(big))
    assertTrue(h.has(StrPurchases.partly(money(3320.0)), substring = true))
    assertTrue(h.has(StrPurchases.overdue(money(2100.0)), substring = true))
    assertTrue(h.has(StrPurchases.credit + " — ", substring = true))
    assertTrue(h.has(StrPurchases.paid + " • " + StrPurchases.received, substring = true))
    assertTrue(h.has("شرکت پخش البرز"))
    assertTrue(h.has("عمده‌فروشی پامیر"))
    //  تنها دکمهٔ شناور
    assertTrue(h.has(StrPurchases.newPurchase))
  }

  @Test fun detailShowsEveryLineWithBarcodeBatchAndExpiry() {
    h.show(MoneyPaths.purchase(views.getValue(PurchaseDemo.BIG_NO).key), data)
    h.await(StrPurchases.barcode(PurchaseDemo.FLOUR_CODE), substring = true)
    assertTrue(h.has(StrPurchases.lines(5)))
    assertTrue(h.has(StrPurchases.batch("A-17"), substring = true))
    assertTrue(h.has(StrPurchases.barcode("TEA-01"), substring = true))
    assertTrue(h.has(StrPurchases.noCode, substring = true))
    assertTrue(h.has("آرد"))
    assertTrue(h.has("50 کیلو × 40 ؋", substring = true))
    assertTrue(h.has(StrPurchases.remaining))
  }

  @Test fun payFromDetailIsPrefilledAndClearsThatPurchaseNotTheOlderOne() {
    val big = views.getValue(PurchaseDemo.BIG_NO)
    h.show(MoneyPaths.purchase(big.key), data)
    h.await(StrPurchases.barcode(PurchaseDemo.FLOUR_CODE), substring = true)
    h.click(StrMoney.pay)
    h.await(StrMoney.recordPayment)
    assertTrue(h.has(PurchaseBook.payNote(PurchaseDemo.BIG_NO), substring = true))
    h.click(StrMoney.recordPayment)
    val store = h.store!!
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

  @Test fun payFilterDropdownNarrowsTheList() {
    h.show(MoneyPaths.PURCHASING, data)
    h.await(StrPurchases.payAll)
    h.click(StrPurchases.payAll)
    h.await(StrPurchases.overdueFilter)
    h.click(StrPurchases.overdueFilter)
    h.awaitThat { !h.has("عمده‌فروشی پامیر") }
    assertTrue(h.has(StrPurchases.overdue(money(2100.0)), substring = true))
    assertFalse(h.has(StrPurchases.partly(money(3320.0)), substring = true))
  }

  @Test fun supplierPageListsItsPurchasesAndPayments() {
    h.show(MoneyPaths.supplier(PurchaseDemo.ALBORZ), data)
    h.await(StrPurchases.tabPurchases)
    assertTrue(h.has(StrPurchases.payments))
    assertTrue(h.has(StrPurchases.partly(money(3320.0)), substring = true))
    assertTrue(h.has(StrPurchases.noPayments))
    assertFalse(h.has("عمده‌فروشی پامیر"))
    assertTrue(h.has(StrPurchases.newPurchase))
  }
}
