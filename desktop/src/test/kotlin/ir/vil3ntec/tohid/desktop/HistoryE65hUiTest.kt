package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.SalesPeriods
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.desktop.shots.HistoryDemo
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.sales.history.DrawerLine
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryActions
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryBook
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryFilter
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryQuery
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryScreen
import ir.vil3ntec.tohid.ui.v3.sales.history.MoneyKind
import ir.vil3ntec.tohid.ui.v3.sales.history.MoneyKindKey
import ir.vil3ntec.tohid.ui.v3.sales.history.PeriodLabel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  ۶۵h — «تاریخچه فروش» ِ گوشی (۳۶۰dp، راست‌به‌چپ) روی همان `HistoryScreen` ِ برنامه، با دفترِ نمایشیِ روزِ پر
 *  ([HistoryDemo.busy]: نقد، نسیه، کارت، یک برگشت، یک قرضِ وصول‌شده):
 *   • هر عدد نام دارد: «برگشت دادید»، «خالصِ امروز»، «قرضِ وصول‌شده» با مبلغِ همان جمعِ X/Z؛
 *   • هر نوعِ پرداخت رنگ **و** واژه دارد (نقد، نسیه، کارت) و برگشت/خالص/قرض هم؛
 *   • سرخطِ روز جمعِ فروشِ همان روز را دارد؛
 *   • تنها کارِ «برگشتِ کالا» به همهٔ راه‌های برگشت می‌رسد (QR، پیدا کردنِ فاکتور، بی رسید) و راهنمای خاکستریِ بلند نیست؛
 *   • حالِ صندوق ردیفِ وضعیت است و صفحهٔ صندوق را باز می‌کند؛ زدنِ ردیف فاکتور را باز می‌کند.
 */
class HistoryE65hUiTest {

  @get:Rule val rule = createComposeRule()

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { I18n.source = { Lang.FA } }

  private class Calls {
    val open = ArrayList<String>()
    var scan = 0
    var none = 0
    var drawer = 0
    fun actions() = HistoryActions(
      open = { open += it }, ret = {}, cancel = {}, fix = {}, returnReceipt = {}, drawer = { drawer++ },
      noReceipt = { none++ }, scanReceipt = { scan++ }, newSale = {}, back = null, print = {},
    )
  }

  private fun show(d: ShopData, calls: Calls): HistoryBook {
    val now = ReportBridge.nowMs()
    val book = HistoryBook.build(d, ReportBridge.deviceClock(now), now, gregorian = false)
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
        //  گوشی: جست‌وجو با دکمهٔ نوار/ذره‌بین (نه کادرِ همیشگیِ کامپیوتر) و یک ستون
        LocalDeskLayout provides false,
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(Modifier.requiredSize(360.dp, 2400.dp)) {
              HistoryScreen(
                d = d, book = book, returnsV2 = true, canCancel = true,
                drawer = DrawerLine(false, StrSale.drawerClosed),
                query = remember { HistoryQuery(HistoryFilter.ALL, "") },
                actions = calls.actions(),
              )
            }
          }
        }
      }
    }
    rule.waitForIdle()
    return book
  }

  private fun count(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

  private fun click(text: String) {
    rule.onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  /** نوعِ رنگِ گره‌هایی که این نوشته را دارند (رنگ + واژه) */
  private fun kinds(text: String): List<MoneyKind> =
    rule.onAllNodes(hasText(text, substring = true) and SemanticsMatcher.keyIsDefined(MoneyKindKey)).fetchSemanticsNodes().map { it.config[MoneyKindKey] }

  /** همهٔ نوشته‌های گره‌هایی که رنگِ [kind] را نشان می‌دهند */
  private fun textsOf(kind: MoneyKind): List<String> =
    rule.onAllNodes(SemanticsMatcher.expectValue(MoneyKindKey, kind)).fetchSemanticsNodes()
      .flatMap { n -> n.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

  @Test fun everyNumberUnderTheSummaryHasAPlainLabelAndItsColour() {
    val book = show(HistoryDemo.busy(), Calls())
    val t = book.current(SalesPeriods.Unit.DAY).totals
    assertEquals(1, t.returns)
    assertEquals(listOf(MoneyKind.RETURN), kinds(StrHistory.refunded))
    assertEquals(listOf(MoneyKind.NET), kinds(StrHistory.netToday))
    assertEquals(listOf(MoneyKind.DEBT_IN), kinds(StrHistory.debtCollected))
    //  هر مبلغ در همان ردیفِ نام‌دارِ خودش
    val refunds = textsOf(MoneyKind.RETURN)
    assertTrue(refunds.toString(), refunds.containsAll(listOf(StrHistory.refunded, "−" + PeriodLabel.amount(t.refunds), StrHistory.refundTimes(ir.vil3ntec.tohid.plain(1)))))
    val net = textsOf(MoneyKind.NET)
    assertTrue(net.toString(), net.containsAll(listOf(StrHistory.netToday, PeriodLabel.amount(t.net), StrHistory.netNote)))
    val debt = textsOf(MoneyKind.DEBT_IN)
    assertTrue("قرضِ وصول‌شده فروش نیست: $debt", debt.containsAll(listOf(StrHistory.debtCollected, PeriodLabel.amount(t.receipts), StrHistory.debtCollectedNote)))
    //  واژه‌های کهنهٔ نامفهوم نه: «برگشت (۱)»، «دریافتِ بدهی: …»
    assertEquals(0, count(StrHistory.returns + " (", substring = true))
    assertEquals(0, count(StrHistory.receipts + ":", substring = true))
  }

  @Test fun eachPaymentKindHasAColourAndAWordInItsRow() {
    show(HistoryDemo.busy(), Calls())
    assertTrue(kinds(StrHistory.cash).contains(MoneyKind.CASH))
    assertTrue(kinds(StrHistory.credit).all { it == MoneyKind.CREDIT } && kinds(StrHistory.credit).size == 2)
    assertEquals(listOf(MoneyKind.CARD), kinds("کارت"))
    //  فاکتورِ برگشت‌خورده: خطِ سرخِ «برگشت شد: ۲۵۰ ؋» با آیکن
    assertEquals(listOf(MoneyKind.RETURN), kinds(StrHistory.rowRefunded(ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText.amount(250.0))))
    //  نامِ بلندِ مشتری هم ردیفِ زدنیِ خودش را دارد (عنوانِ یک‌خطی، بریده با «…»)
    assertEquals(1, rule.onAllNodes(hasText(HistoryDemo.LONG_NAME) and hasClickAction()).fetchSemanticsNodes().size)
  }

  @Test fun theDayHeaderShowsThatDaysSalesTotal() {
    val book = show(HistoryDemo.busy(), Calls())
    val today = book.current(SalesPeriods.Unit.DAY)
    val header = rule.onAllNodes(hasText(StrHistory.daySales(PeriodLabel.amount(today.totals.sales))) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
      .fetchSemanticsNodes()
    assertEquals(1, header.size)
    val texts = header[0].config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
    assertTrue(texts.toString(), Str.today in texts)
    assertTrue(texts.toString(), PeriodLabel.counts(today.totals) in texts)
  }

  @Test fun theOneReturnActionReachesEveryReturnPath() {
    val calls = Calls()
    show(HistoryDemo.busy(), calls)
    assertEquals("یک کارِ برگشت", 1, count(StrHistory.returnGoods))
    assertEquals("راهنمای خاکستریِ بلند نیست", 0, count(StrSale.returnsHint))
    assertEquals("دکمهٔ جدای «برگشتِ بی رسید» زیرِ خلاصه نیست", 0, count(StrSale.noReceiptReturn))
    //  QR ِ رسید
    click(StrHistory.returnGoods)
    rule.waitUntil(5_000) { count(StrSale.scanReceiptQr) > 0 }
    assertEquals(1, count(StrHistory.findInvoice))
    assertEquals(1, count(StrSale.noReceiptReturn))
    click(StrSale.scanReceiptQr)
    rule.waitUntil(5_000) { calls.scan == 1 }
    //  بی رسید
    click(StrHistory.returnGoods)
    rule.waitUntil(5_000) { count(StrSale.noReceiptReturn) > 0 }
    click(StrSale.noReceiptReturn)
    rule.waitUntil(5_000) { calls.none == 1 }
    //  پیدا کردنِ فاکتور با شماره یا نام ⇐ جست‌وجوی همین صفحه باز می‌شود
    click(StrHistory.returnGoods)
    rule.waitUntil(5_000) { count(StrHistory.findInvoice) > 0 }
    click(StrHistory.findInvoice)
    rule.waitUntil(5_000) { count(StrSale.historySearch) > 0 }
    assertEquals(1, calls.scan)
    assertEquals(1, calls.none)
  }

  @Test fun theDrawerIsAStatusRowAndARowTapOpensTheInvoice() {
    val calls = Calls()
    show(HistoryDemo.busy(), calls)
    assertEquals(1, count(StrHistory.drawerClosedHint))
    assertEquals("«صندوق بسته است» دیگر قرصِ کارتِ خلاصه نیست", 0, count(StrSale.drawerClosed))
    click(StrHistory.drawerClosedHint)
    assertEquals(1, calls.drawer)
    click(HistoryDemo.LONG_NAME)
    assertEquals(listOf(HistoryDemo.LONG), calls.open)
  }

  @Test fun anEmptyDayIsShortFriendlyAndOffersANewSale() {
    show(HistoryDemo.empty(), Calls())
    assertEquals(1, count(StrHistory.emptyTodayTitle))
    assertEquals(1, count(StrSale.newSale))
    //  همان عکسِ صاحب‌کار: فروشِ صفر و ۵۰۰ ؋ قرض ⇒ «قرضِ وصول‌شده»، نه «دریافتِ قرض»
    assertEquals(listOf(MoneyKind.DEBT_IN), kinds(StrHistory.debtCollected))
    assertEquals(0, count(StrHistory.refunded))
  }
}
