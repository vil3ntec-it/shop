package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoPermsDto
import ir.vil3ntec.tohid.data.PoTotalsDto
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.money.PoListRow
import ir.vil3ntec.tohid.ui.v3.rememberV3Stagger
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.history.EmptyLine
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryActions
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryModel
import ir.vil3ntec.tohid.ui.v3.sales.history.InvoiceRow
import ir.vil3ntec.tohid.ui.v3.sales.history.MoneyKind
import ir.vil3ntec.tohid.ui.v3.sales.history.MoneyKindKey
import ir.vil3ntec.tohid.ui.v3.sales.history.RowCtx
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleActionsSheet
import ir.vil3ntec.tohid.ui.v3.shell.AlertGroup
import ir.vil3ntec.tohid.ui.v3.shell.ShopAlerts
import ir.vil3ntec.tohid.ui.v3.stock.MovementsScreen
import ir.vil3ntec.tohid.ui.v3.stock.StockPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  سادگیِ ردیف‌ها (`claude/e-rows-63d`) روی همان کدِ گوشیِ نسخهٔ ۳ و کامپیوتر:
 *   • زدنِ ردیفِ فاکتور فاکتور را باز می‌کند (هم تکه، هم مسیرِ واقعیِ `sales` ⇐ `sales/{id}`)؛ نگه داشتن برگهٔ همهٔ کارها؛
 *     برگه دیدن، برگشت، اصلاح و لغو را دارد (کارهای دوم گم نشده‌اند)؛
 *   • وضعیت متنِ آرام است: نسیه «• نسیه»، نقدیِ عادی بی هیچ برچسبی (قرصِ «نقدی» دیگر نیست)؛
 *   • ردیفِ سفارشِ خرید: تنها قرصِ «دریافتِ کالا» همان کار را می‌کند و به زدنِ ردیف (جزئیات) نمی‌رسد؛ «تکمیل» بی قرص؛
 *   • ردیفِ هشدار: قرصِ کار (تمدید) مقصدِ خودش را باز می‌کند و خودِ ردیف مقصدِ ردیف را؛
 *   • خالیِ «حرکات انبار» و دورهٔ بی فاکتور دکمهٔ گامِ بعدی دارند.
 */
@OptIn(ExperimentalTestApi::class)
class RowsE63dUiTest {

  @get:Rule val rule = createComposeRule()

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { I18n.source = { Lang.FA } }

  private fun show(content: @Composable () -> Unit) = rule.setContent {
    CompositionLocalProvider(
      LocalContext provides DesktopContext,
      LocalView provides remember { android.view.View(DesktopContext) },
      LocalConfiguration provides Configuration(360, 760),
    ) { TohidTheme(ThemeChoice.LIGHT) { Column { content() } } }
  }

  private fun count(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

  private fun click(text: String) {
    rule.onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  /* ─────────────────────────── فاکتور ─────────────────────────── */

  private val rice = Product(id = "r63-rice", name = "برنجِ آزمونی", unit = "کیلو", salePrice = 120.0)
  private val book = ShopData(
    products = listOf(rice),
    debtors = listOf(Debtor(id = "r63-d", name = "نسیم آزمونی")),
    sales = listOf(
      Sale("r63-cash", 21, finalTotal = 120.0, paidAmount = 120.0, createdAt = 1, date = "2026-11-14"),
      Sale("r63-credit", 22, finalTotal = 240.0, paidAmount = 0.0, remaining = 240.0, paymentMethod = "credit", debtorId = "r63-d", createdAt = 2, date = "2026-11-14"),
    ),
    saleItems = listOf(
      SaleItem("r63-i1", "r63-cash", rice.id, quantity = 1.0, unitPrice = 120.0, totalPrice = 120.0),
      SaleItem("r63-i2", "r63-credit", rice.id, quantity = 2.0, unitPrice = 120.0, totalPrice = 240.0),
    ),
  )

  private class Calls {
    val open = ArrayList<String>()
    val ret = ArrayList<String>()
    val menu = ArrayList<String>()
    var newSale = 0
    fun actions() = HistoryActions(
      open = { open += it }, ret = { ret += it }, cancel = {}, fix = {}, returnReceipt = {}, drawer = {},
      noReceipt = null, scanReceipt = null, newSale = { newSale++ }, back = null,
    )
  }

  @Test fun tappingASaleRowOpensItsInvoiceAndLongPressOpensAllActions() {
    val calls = Calls()
    val ctx = RowCtx(book, null, false, true, calls.actions(), false, today = "2026-11-14") { calls.menu += it }
    show { book.sales.forEachIndexed { i, s -> InvoiceRow(s, ctx, first = i == 0, last = i == book.sales.lastIndex, withDate = false) } }
    //  عنوانِ معنادار: مشتری برای نسیه، نامِ کالا برای نقدی
    assertEquals(1, count("نسیم آزمونی"))
    assertEquals(1, count(rice.name))
    click("نسیم آزمونی")
    assertEquals(listOf("r63-credit"), calls.open)
    rule.onAllNodes(hasText(rice.name) and hasClickAction())[0].performTouchInput { longClick() }
    rule.waitForIdle()
    assertEquals(listOf("r63-cash"), calls.menu)
  }

  /**
   *  ۶۵h (عمدی): وضعیت هنوز نوشته است، نه قرص — ولی صاحب‌کار «رنگ بده» خواست: هر ردیف، نقدیِ عادی هم، نقطهٔ رنگی و واژهٔ
   *  پرداختش را دارد («● نقد» سبز، «● نسیه» کهربایی؛ `MoneyKindKey` نوعِ رنگ را برای آزمون می‌گوید). پیش‌تر «نقدیِ عادی
   *  برچسبی ندارد» و «مبلغ • نسیه» یک نوشته بود.
   */
  @Test fun statusIsQuietInlineTextAndEverySaleShowsItsPaymentColourWord() {
    val calls = Calls()
    val ctx = RowCtx(book, null, false, true, calls.actions(), false, today = "2026-11-14") { calls.menu += it }
    show { book.sales.forEach { s -> InvoiceRow(s, ctx, first = true, last = true, withDate = false) } }
    val credit = HistoryModel.row(book, book.sales[1], false)
    fun kindOf(word: String) = rule.onAllNodes(hasText(word) and SemanticsMatcher.keyIsDefined(MoneyKindKey)).fetchSemanticsNodes().map { it.config[MoneyKindKey] }
    assertEquals(listOf(MoneyKind.CREDIT), kindOf(credit.payWord))
    assertEquals(listOf(MoneyKind.CASH), kindOf(StrHistory.cash))
    assertEquals("قرصِ «نقدی» نیست — فقط واژهٔ کوتاهِ «نقد»", 0, count(StrSale.cashPay, substring = true))
    assertEquals("شماره کوچک در زیرعنوان مانده (جستجو و برگشت با شماره)", 1, count("#21", substring = true))
  }

  @Test fun secondaryActionsStayReachableFromTheLongPressSheet() {
    val calls = Calls()
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val row = HistoryModel.row(book, book.sales[0], false, today = "2026-11-14")
    show { SaleActionsSheet(row, canCancel = true, returnsV2 = false, actions = calls.actions(), onClose = {}) }
    rule.waitUntil(5_000) { count(StrSale.viewInvoice) > 0 }
    assertEquals(1, count(StrSale.fixInvoice))
    assertEquals(1, count(StrSale.cancelSale))
    if (row.canReturn) {
      click(StrSale.returnWord)
      rule.waitUntil(5_000) { calls.ret.isNotEmpty() }
      assertEquals(listOf("r63-cash"), calls.ret)
    } else {
      click(StrSale.viewInvoice)
      rule.waitUntil(5_000) { calls.open.isNotEmpty() }
      assertEquals(listOf("r63-cash"), calls.open)
    }
  }

  @Test fun anEmptyPeriodOffersTheNextStep() {
    var sold = 0
    show { EmptyLine("امروز هنوز فاکتوری ثبت نشده است", StrSale.newSale) { sold++ } }
    click(StrSale.newSale)
    assertEquals(1, sold)
  }

  @Test fun tappingARowInTheRealHistoryRouteOpensTheInvoicePage() {
    val tea = Product(id = "r63-tea", name = "چایِ فهرستی", unit = "بسته", salePrice = 250.0)
    var seq = 0
    val data = (SalesEngine.record(
      ShopData(products = listOf(tea), warehouseEntries = listOf(WarehouseEntry(id = "r63-w", productId = tea.id, units = 5.0))),
      listOf(SalesEngine.CartLine(tea.id, 1.0)), SalesEngine.Checkout(seller = "صندوقدار"), todayIso(), System.currentTimeMillis(), { "r63-${seq++}" },
    ) as SalesEngine.Result.Ok).data
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    RouteUiHarness(rule).use { h ->
      h.show(SalesPaths.HISTORY, data)
      //  دفترِ تاریخچه در پس‌زمینه ساخته می‌شود؛ فاکتورِ امروز (ساعتِ همین لحظه) زیرِ «امروز» است
      h.await(tea.name)
      h.click(tea.name)
      h.await(StrSale.print)
    }
  }

  /* ─────────────────────────── سفارشِ خرید ─────────────────────────── */

  private fun po(status: String) = PurchaseOrderDto(
    id = "r63-po", no = 7, supplierName = "پخشِ آزمونی", status = status,
    allowed = if (status == "sent" || status == "partial") listOf(PurchaseOrderCore.Action.RECEIVE.wire) else emptyList(),
    lines = listOf(PoLineDto(id = "l1")), totals = PoTotalsDto(orderedMinor = 50_000, remainingMinor = 20_000),
  )

  @Test fun thePurchaseOrderRowsOnePrimaryActionReceivesWithoutOpeningDetails() {
    var opened = 0
    var received = 0
    show { PoListRow(po("partial"), PoPermsDto(manage = true, receive = true), "2026-11-14", onOpen = { opened++ }, onReceive = { received++ }) }
    assertEquals(1, count(" • " + StrGoods.poPartial, substring = true))
    click(StrPurchasePages.receiveGoods)
    assertEquals(1, received)
    assertEquals(0, opened)
    click("پخشِ آزمونی")
    assertEquals(1, opened)
    assertEquals(1, received)
  }

  @Test fun aCompletePurchaseOrderShowsNoPillAndNoStatus() {
    show { PoListRow(po("complete"), PoPermsDto(manage = true, receive = true), "2026-11-14", onOpen = {}, onReceive = {}) }
    assertEquals(0, count(StrPurchasePages.receiveGoods))
    assertEquals(0, count(StrGoods.poComplete, substring = true))
    assertEquals(1, count("#7", substring = true))
  }

  @Test fun withoutReceivePermissionThereIsNoPillButTheRowStillOpens() {
    var opened = 0
    show { PoListRow(po("sent"), PoPermsDto(manage = true, receive = false), "2026-11-14", onOpen = { opened++ }, onReceive = {}) }
    assertEquals(0, count(StrPurchasePages.receiveGoods))
    click("پخشِ آزمونی")
    assertEquals(1, opened)
  }

  /* ─────────────────────────── هشدار و خالی ─────────────────────────── */

  @Test fun anAlertsPillOpensItsOwnTargetAndTheRowOpensTheRow() {
    val opened = ArrayList<String>()
    val item = ShopAlerts.Item(
      key = "sub", kind = ShopAlerts.Kind.SUBSCRIPTION, title = "اشتراک رو به پایان", subtitle = "۳ روز مانده",
      action = "تمدید", route = "account/subscription", actionRoute = "account/subscription/plans", tone = V3Tone.WARNING, urgent = true,
    )
    show { AlertGroup(StrHome.groupUrgent, listOf(item), rememberV3Stagger(), 0) { opened += it } }
    click("تمدید")
    assertEquals(listOf("account/subscription/plans"), opened)
    click("اشتراک رو به پایان")
    assertEquals(listOf("account/subscription/plans", "account/subscription"), opened)
  }

  @Test fun emptyStockMovementsOfferTheFirstStockEntry() {
    val p = Product(id = "r63-new", name = "کالای بی حرکت", unit = "عدد", salePrice = 10.0)
    val opened = ArrayList<String>()
    show { MovementsScreen(ShopData(products = listOf(p)), p.id, "2026-11-14", onOpen = { opened += it }, onBack = {}) }
    rule.waitUntil(5_000) { count(StrStock.noMovements) > 0 }
    click(StrReports.stockEntry)
    assertEquals(listOf(StockPaths.entry(p.id)), opened)
    assertFalse(opened.isEmpty())
    assertTrue(StrReports.stockEntry.isNotBlank())
  }
}
