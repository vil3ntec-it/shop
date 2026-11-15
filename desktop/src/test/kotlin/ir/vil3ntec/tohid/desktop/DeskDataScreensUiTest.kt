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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material.icons.outlined.MoreHoriz
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrCustomerPages
import ir.vil3ntec.tohid.core.i18n.StrDeskData
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrLook
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.shots.D3bDemo
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.ui.desk.DeskCalmRow
import ir.vil3ntec.tohid.ui.desk.DeskIconButton
import ir.vil3ntec.tohid.ui.screens.DeskCustomersScreen
import ir.vil3ntec.tohid.ui.screens.DeskFreshReports
import ir.vil3ntec.tohid.ui.screens.DeskHistoryPage
import ir.vil3ntec.tohid.ui.screens.DeskPurchasingScreen
import ir.vil3ntec.tohid.ui.screens.ReportFresh
import ir.vil3ntec.tohid.ui.screens.deskDebtTone
import ir.vil3ntec.tohid.ui.theme.PhoneV3DebtDark
import ir.vil3ntec.tohid.ui.theme.PhoneV3DebtLight
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.money.DeskExpensesScreen
import ir.vil3ntec.tohid.ui.v3.money.DeskReceiptsScreen
import ir.vil3ntec.tohid.ui.v3.money.ExpensesActions
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryActions
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryBook
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryFilter
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 *  صفحه‌های داده‌ایِ کامپیوتر (`claude/e-desk-d3b`) در پنجرهٔ ۱۳۶۶×۷۶۸ ِ واقعیِ Compose با دفترِ نمایشیِ [D3bDemo]:
 *   • تاریخچه: پنج کارتِ دوره، کادرِ کشوییِ دوره، زدنِ ردیف ⇐ پنلِ فاکتور با «نمایش و چاپ»، برگشت و لغو، و «…» با دیدنِ فاکتور،
 *     چاپِ A4 و اصلاح — هر کار به همان `HistoryActions` (همان مسیرهای نسخهٔ ۳) می‌رسد؛ ۳۰ هزار فاکتور تنبل کشیده می‌شوند؛
 *   • مشتری‌ها: سه جعبهٔ رنگیِ قرض‌داران، پرونده با «برد»/«رسید»، رنگِ سرخ/سبزِ حساب همان `PhoneV3Debt`؛ ۵ هزار مشتری تنبل؛
 *   • خرید، مصارف، رسیدها و گزارش‌ها: کارت‌ها، نوارِ ابزار، پنل و کارهای هر ردیف؛
 *   • ردیفِ آرام: یک خطِ عنوان، یک خطِ کم‌رنگ، مبلغ و وضعیت به واژه، دست‌بالا یک کارِ خنثی.
 */
@OptIn(ExperimentalTestApi::class)
class DeskDataScreensUiTest {

  @Before fun up() { I18n.source = { Lang.FA }; ShopRole.remember(DesktopContext, ShopRole.OWNER) }
  @After fun down() { I18n.source = { Lang.FA } }

  private fun ui(block: ComposeUiTest.(set: (@Composable () -> Unit) -> Unit) -> Unit) = runDesktopComposeUiTest(1366, 768, testTimeout = 3.minutes) {
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
  private fun ComposeUiTest.count(text: String, substring: Boolean = false) = onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().size
  private fun ComposeUiTest.await(text: String, substring: Boolean = false) = waitUntil(timeoutMillis = 20_000) { has(text, substring) }
  private fun ComposeUiTest.tap(text: String, substring: Boolean = false) {
    onAllNodes(hasText(text, substring = substring) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
  }
  private fun ComposeUiTest.tapDesc(desc: String) {
    onAllNodes(hasContentDescription(desc) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
  }

  private class Calls {
    val log = ArrayList<String>()
    fun actions() = HistoryActions(
      open = { log += "open:$it" }, ret = { log += "ret:$it" }, cancel = { log += "cancel:$it" }, fix = { log += "fix:$it" },
      returnReceipt = { log += "receipt:$it" }, drawer = { log += "drawer" }, noReceipt = { log += "none" }, scanReceipt = null,
      newSale = { log += "new" }, back = null, payReview = { log += "review" }, print = { log += "print:${it.key}" },
    )
  }

  private fun book(d: ShopData): HistoryBook {
    val now = ReportBridge.nowMs()
    return HistoryBook.build(d, ReportBridge.deviceClock(now), now, gregorian = false)
  }

  /* ------------------------------ تاریخچه ------------------------------ */

  @Test fun historyHasFiveKpisAPeriodDropdownAndTheInvoicePaneWithEveryAction() = ui { set ->
    val d = D3bDemo.full()
    val b = book(d)
    val calls = Calls()
    val store = RouteUiHarness.memoryStore(d)
    set {
      DeskHistoryPage(
        store = store, d = d, snackbar = remember { SnackbarHostState() }, book = b,
        query = remember { HistoryQuery(HistoryFilter.ALL, "", SalesPeriods.Unit.MONTH) }, actions = calls.actions(),
        returnsV2 = true, canCancel = true, askCancel = false, drawer = null, reviews = 2, showMoney = true, banner = null, shopTag = null,
      )
    }
    await(StrReports.netSales)
    for (k in listOf(StrDeskData.kpiSales, Str.cash, Str.credit, StrHistory.returns, StrReports.netSales)) assertTrue(k, has(k))
    assertTrue("کادرِ کشوییِ دوره", has(StrLook.period + ":"))
    val sale = b.pick(SalesPeriods.Unit.MONTH, null).sales.first { it.status != "cancelled" && it.paymentMethod == "cash" }
    val number = "#" + ir.vil3ntec.tohid.ui.v3.sales.history.ReceiptModel.number(sale)
    tap(number, substring = true)
    await(StrDeskData.invoiceTitle(number))
    assertTrue(has(StrHistory.print))
    tap(StrSale.returnOrExchange)
    tap(StrSale.cancelSale)
    assertTrue(calls.log.toString(), "ret:${sale.id}" in calls.log && "cancel:${sale.id}" in calls.log)
    tapDesc(StrDeskData.moreForInvoice)
    for (k in listOf(StrSale.viewInvoice, StrInvoices.printA4, StrSale.fixInvoice)) assertTrue(k, has(k))
    tap(StrSale.fixInvoice)
    assertTrue("fix:${sale.id}" in calls.log)
    //  کارهای نوار: صندوق و «…» (چاپِ دوره، برگشتِ بی رسید، فروشِ تازه)
    tap(StrSale.drawerWord)
    tapDesc(ir.vil3ntec.tohid.core.i18n.StrA11y.moreActions)
    tap(StrDeskData.printPeriod)
    tapDesc(ir.vil3ntec.tohid.core.i18n.StrA11y.moreActions)
    tap(StrSale.noReceiptReturn)
    assertTrue(calls.log.toString(), "drawer" in calls.log && calls.log.any { it.startsWith("print:") } && "none" in calls.log)
  }

  @Test fun historyWithThirtyThousandInvoicesDrawsOnlyTheVisibleRows() = ui { set ->
    val d = D3bDemo.big(customers = 50, lines = 150_000)
    val b = book(d)
    val store = RouteUiHarness.memoryStore(d)
    set {
      DeskHistoryPage(
        store = store, d = d, snackbar = remember { SnackbarHostState() }, book = b,
        query = remember { HistoryQuery(HistoryFilter.ALL, "", SalesPeriods.Unit.YEAR) }, actions = Calls().actions(),
        returnsV2 = false, canCancel = true, askCancel = false, drawer = null, reviews = 0, showMoney = true, banner = null, shopTag = null,
      )
    }
    await(StrHistory.invoices(ir.vil3ntec.tohid.plain(b.pick(SalesPeriods.Unit.YEAR, null).sales.size)))
    val rows = onAllNodes(hasText("#1", substring = true) and hasClickAction()).fetchSemanticsNodes().size
    assertTrue("ردیف‌های ساخته‌شده: $rows", rows in 1..60)
  }

  /* ------------------------------ مشتری‌ها ------------------------------ */

  @Test fun customersKeepTheThreeColouredBoxesAndTheRedGreenLedger() = ui { set ->
    val d = D3bDemo.full()
    val store = RouteUiHarness.memoryStore(d)
    set { DeskCustomersScreen(store, d, remember { SnackbarHostState() }) }
    await(StrMoney.statOwed)
    for (k in listOf(StrMoney.statPeople, StrMoney.statOwed, StrMoney.statReceived)) assertTrue(k, has(k))
    assertTrue(has(StrDeskData.pickCustomer))
    tap("جلیل احمدی")
    await(StrCustomerPages.tabStatement, substring = true)
    assertTrue(has(StrMoney.giveTag))
    //  «فاکتورها» فقط زبانه و پیوندِ ردیفِ «برد» (کهنه‌ترین ردیف، بالای دفتر) — فهرستِ جدای فاکتور نیست
    assertTrue(has("#12"))
    //  ردیفِ «رسید» پایین‌ترِ دفتر است (جدولِ تنبل) — تا آن پیمایش
    onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction()).fetchSemanticsNodes().indices.forEach { i ->
      runCatching { onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())[i].performScrollToNode(hasText(StrMoney.receiveTag)) }
    }
    waitForIdle()
    assertTrue(has(StrMoney.receiveTag))
    assertTrue(has(StrCustomerPages.receivePayment) && has(StrCustomerPages.manualCredit))
    tap(Str.invoices, substring = true)
    await(StrCustomerPages.payable)
  }

  @Test fun debtorColoursAreThePhoneRedAndGreen() {
    for (c in listOf(PhoneV3DebtLight, PhoneV3DebtDark)) {
      assertEquals(c.owe to c.oweTint, deskDebtTone(250.0, c))
      assertEquals(c.settled to c.settledTint, deskDebtTone(0.0, c))
      assertEquals(c.settled to c.settledTint, deskDebtTone(-40.0, c))
    }
  }

  @Test fun fiveThousandCustomersAreLazy() = ui { set ->
    val d = D3bDemo.big(customers = 5_000, lines = 50)
    val store = RouteUiHarness.memoryStore(d)
    set { DeskCustomersScreen(store, d, remember { SnackbarHostState() }) }
    waitUntil(timeoutMillis = 60_000) { has("مشتری 0") || has("مشتری 1", substring = true) }
    val rows = onAllNodes(hasText("مشتری ", substring = true) and hasClickAction()).fetchSemanticsNodes().size
    assertTrue("ردیف‌های ساخته‌شده: $rows", rows in 1..40)
  }

  /* ------------------------------ خرید، مصارف، رسیدها ------------------------------ */

  @Test fun purchasingHasKpisInvoicePaneAndSupplierLedger() = ui { set ->
    val d = D3bDemo.full()
    val store = RouteUiHarness.memoryStore(d)
    set { DeskPurchasingScreen(store, d, remember { SnackbarHostState() }) }
    await(StrPurchasePages.weOweSuppliers)
    assertTrue(has(StrPurchasePages.thisMonthPurchases))
    tap(StrPurchasePages.suppliers)
    await(StrDeskData.pickSupplier)
    tap("شرکت پخش البرز")
    await(StrPurchasePages.paySupplierKey)
    tap(StrPurchasePages.paySupplierKey)
    await(StrCustomerPages.amountAfn)
  }

  @Test fun expensesSelectAndDeleteWithTheSameQuestion() = ui { set ->
    val d = D3bDemo.full()
    val log = ArrayList<String>()
    val actions = ExpensesActions(
      onBack = null, onNew = { log += "new" }, onOpen = { log += "open:$it" }, onDelete = { log += "delete:$it" },
      onPickMonth = { _, _ -> log += "month" }, onBreakdown = { _, _, _ -> },
    )
    set { DeskExpensesScreen(d, null, null, actions) }
    await(StrMoney.byCategory)
    tap("کرایهٔ دکان")
    await(StrMoney.edit)
    tap(StrMoney.edit)
    tap(StrMoney.delete)
    await(StrMoney.deleteExpenseTitle)
    onAllNodes(hasText(StrMoney.delete) and hasClickAction()).fetchSemanticsNodes().size.let { assertTrue(it >= 1) }
    tap(StrMoney.newExpense)
    assertTrue(log.toString(), "new" in log && log.any { it.startsWith("open:") })
  }

  @Test fun receiptsOpenTheDebtorOfARow() = ui { set ->
    val d = D3bDemo.full()
    val opened = ArrayList<String>()
    set { DeskReceiptsScreen(d, null, null, null, onOpenDebtor = { opened += it }, onOpenExpense = { opened += "x:$it" }) }
    await(StrMoney.tabDebtorReceipts)
    waitUntil(timeoutMillis = 10_000) { has("جلیل احمدی") }
    tap("جلیل احمدی")
    assertEquals(MoneyDemo.OWING, opened.firstOrNull())
  }

  /* ------------------------------ گزارش‌ها ------------------------------ */

  @Test fun reportsShowKpisAndTheTrendNextToTheTable() = ui { set ->
    val d = D3bDemo.full()
    ReportFresh.on.value = true
    set { DeskFreshReports(d, remember { SnackbarHostState() }) }
    await(StrReports.reportType + ":")
    tap(StrReports.reportType + ":", substring = true)
    //  گزینهٔ کادرِ کشویی (`DeskMenuRow` حالتِ «انتخاب» دارد) — نه سرستونِ زدنیِ «فروش» ِ جدولِ «امروز» با همان نوشته، که
    //  اگر بارِ «امروز» پیش از این ضربه رسیده باشد نخستین گره است و فقط جدول را مرتب می‌کند
    onAllNodes(hasText(ReportFreshModelLabel.sales()) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected))[0]
      .performSemanticsAction(SemanticsActions.OnClick)
    waitForIdle()
    await(StrReports.netSales)
    waitUntil(timeoutMillis = 20_000) { has(ir.vil3ntec.tohid.core.i18n.StrDesk.salesChart) || has(StrDeskData.salesByHour) || has(StrDeskData.salesByMonth) }
    assertTrue(has(ir.vil3ntec.tohid.core.report.ReportBoard.DIM_LABELS.getValue("payment")))
  }

  /* ------------------------------ ردیفِ آرام ------------------------------ */

  @Test fun calmRowHasOneTitleOneMutedLineAmountStatusAndAtMostOneAction() = ui { set ->
    set {
      DeskCalmRow(
        title = "فاکتور ۱۲", sub = "امروز • ۱۴:۰۵", amount = "1,200 ؋", status = StrInvoices.recorded, selected = false, onClick = {},
        action = { DeskIconButton(androidx.compose.material.icons.Icons.Outlined.MoreHoriz, "کار", {}) },
      )
    }
    waitForIdle()
    for (t in listOf("فاکتور ۱۲", "امروز • ۱۴:۰۵", "1,200 ؋", StrInvoices.recorded)) assertEquals(t, 1, count(t))
    //  خودِ ردیف + یک کار
    assertEquals(2, onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
  }
}

/** نامِ زبانهٔ «فروش» ِ گزارشِ تازه — همان برچسبِ مدل */
private object ReportFreshModelLabel {
  fun sales(): String = ir.vil3ntec.tohid.ui.screens.ReportFreshModel.tabs(true).first { it.id == "sales" }.label
}
