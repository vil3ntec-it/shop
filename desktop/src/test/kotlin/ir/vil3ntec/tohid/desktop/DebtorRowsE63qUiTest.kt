package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrRows
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.InvoiceLinks
import ir.vil3ntec.tohid.ui.v3.LocalInvoiceLinks
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorLedgerScreen
import ir.vil3ntec.tohid.ui.v3.money.DebtorTxSheet
import ir.vil3ntec.tohid.ui.v3.money.DebtorTxSheetActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListScreen
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  قرض‌داران و پروندهٔ قرض‌دار با قاعدهٔ سادگیِ ردیف (`claude/e-rows-63q`)، و «روی فاکتور که بزنم همان فاکتور» (`InvoiceRef`):
 *   • زدنِ ردیفِ قرض‌دار پرونده را باز می‌کند؛ نگه‌داشتن همان سه کارِ پیشین (باز، ویرایش، حذف)؛ × و مدادِ روی کارت دیگر نیست؛
 *   • وضعیت متنِ آرام است: «• قرض‌دار» و برای سررسیدِ گذشته «• سررسید گذشته»؛ آخرین حرکت کوتاه («امروز …»، بی سالِ جاری)؛
 *   • خالیِ دفتر «قرض‌دار تازه» و جستجوی بی نتیجه «نمایشِ همه» دارد؛
 *   • ردیفِ حساب: «مبلغ • برد/رسید»، تاریخِ کوتاه، یادداشت و ماندهٔ کوچک؛
 *   • «فاکتور #۱۲» ِ برگهٔ تراکنش (یادداشت و سرِ اقلام) همان فاکتور را باز می‌کند؛ شمارهٔ ردیفِ تاریخچهٔ فروش و یادداشتِ
 *     ردیفِ حساب در مسیرِ واقعی صفحهٔ همان فاکتور را؛ فاکتورِ ناپیدا «پیدا نشد» ِ آرام، بی خطا.
 */
@OptIn(ExperimentalTestApi::class)
class DebtorRowsE63qUiTest {

  @get:Rule val rule = createComposeRule()

  @Before fun fa() {
    I18n.source = { Lang.FA }
    Motion.reducedForTest = true
  }

  @After fun reset() {
    I18n.source = { Lang.FA }
    Motion.reducedForTest = null
  }

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

  private val today = todayIso()
  private val now = System.currentTimeMillis()
  private val dayMs = 86_400_000L
  private fun iso(daysAgo: Int) = ir.vil3ntec.tohid.core.calendar.CivilDays.addDays(today, -daysAgo)!!

  /* ─────────────────────────── فهرستِ قرض‌داران ─────────────────────────── */

  private val book = ShopData(
    debtors = listOf(Debtor(id = "q-owe", name = "نسیم آزمونی", phone = "0700000001"), Debtor(id = "q-late", name = "کریم دیرکرد")),
    transactions = listOf(
      DebtTransaction("q-t1", "q-owe", "give", 300.0, today, createdAt = now),
      DebtTransaction("q-t2", "q-late", "give", 500.0, iso(40), createdAt = now - 40 * dayMs, dueDate = iso(30)),
    ),
  )

  private class Calls {
    val open = ArrayList<String>()
    val edit = ArrayList<String>()
    val delete = ArrayList<String>()
    var add = 0
    fun actions() = DebtorsListActions(onBack = null, onOpen = { open += it }, onEdit = { edit += it }, onDelete = { delete += it }, onAdd = { add++ })
  }

  @Test fun tappingADebtorRowOpensTheFile() {
    val calls = Calls()
    show { DebtorsListScreen(book, null, null, calls.actions()) }
    rule.waitUntil(10_000) { count("نسیم آزمونی") > 0 }
    click("نسیم آزمونی")
    assertEquals(listOf("q-owe"), calls.open)
    //  × و مدادِ گوشهٔ کارتِ پیشین روی ردیف نیستند
    rule.onAllNodes(hasContentDescription(StrMoney.edit)).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
  }

  @Test fun longPressKeepsOpenEditAndDelete() {
    val calls = Calls()
    show { DebtorsListScreen(book, null, null, calls.actions()) }
    rule.waitUntil(10_000) { count("کریم دیرکرد") > 0 }
    rule.onAllNodes(hasText("کریم دیرکرد") and hasClickAction())[0].performTouchInput { longClick() }
    rule.waitUntil(10_000) { count(StrMoney.openAccount) > 0 }
    assertEquals(1, count(StrMoney.edit))
    assertEquals(1, count(StrMoney.delete))
    click(StrMoney.edit)
    rule.waitForIdle()
    assertEquals(listOf("q-late"), calls.edit)
    assertTrue(calls.open.isEmpty())
  }

  @Test fun quietStatusTextForADebtAndAnOverdueDebt() {
    show { DebtorsListScreen(book, null, null, Calls().actions()) }
    rule.waitUntil(10_000) { count("نسیم آزمونی") > 0 }
    assertEquals(1, count("300 ؋ • " + StrRows.owes))
    assertEquals(1, count("500 ؋ • " + StrRows.overdue))
  }

  @Test fun lastActivityIsAShortDate() {
    show { DebtorsListScreen(book, null, null, Calls().actions()) }
    rule.waitUntil(10_000) { count("نسیم آزمونی") > 0 }
    //  امروز ⇒ «امروز ساعت»؛ ۴۰ روز پیش ⇒ «روز ماه» (بی سال اگر همین سال است، هرگز «۱۴۰۵/۰۷/…» ِ کامل)
    assertEquals(1, count(ir.vil3ntec.tohid.core.i18n.Str.today + " ", substring = true))
    assertEquals(0, count("/", substring = true))
  }

  @Test fun anEmptyBookOffersNewDebtor() {
    val calls = Calls()
    show { DebtorsListScreen(ShopData(), null, null, calls.actions()) }
    rule.waitUntil(10_000) { count(StrMoney.emptyDebtorsTitle) > 0 }
    click(StrMoney.newDebtor)
    assertEquals(1, calls.add)
  }

  @Test fun aSearchWithNoMatchOffersShowAll() {
    show { DebtorsListScreen(book, null, "هیچ‌کسِ ناپیدا", Calls().actions()) }
    rule.waitUntil(10_000) { count(StrMoney.noDebtorMatchTitle) > 0 }
    click(StrMoney.showAll)
    rule.waitUntil(10_000) { count("نسیم آزمونی") > 0 }
    assertEquals(0, count(StrMoney.noDebtorMatchTitle))
  }

  /* ─────────────────────────── پروندهٔ قرض‌دار ─────────────────────────── */

  private val rice = Product(id = "q-rice", name = "برنجِ پرونده", unit = "کیلو", salePrice = 200.0)
  private val file = ShopData(
    products = listOf(rice),
    debtors = listOf(Debtor(id = "q-d", name = "سمیع پرونده")),
    sales = listOf(Sale("q-s12", 12, finalTotal = 400.0, paymentMethod = "credit", debtorId = "q-d", remaining = 400.0, createdAt = now - 2 * dayMs, date = iso(2))),
    saleItems = listOf(SaleItem("q-i1", "q-s12", rice.id, quantity = 2.0, unitPrice = 200.0, totalPrice = 400.0)),
    transactions = listOf(
      DebtTransaction("q-g", "q-d", "give", 400.0, iso(2), notes = "فروش نسیه — فاکتور #12", createdAt = now - 2 * dayMs),
      DebtTransaction("q-r", "q-d", "receive", 150.0, today, createdAt = now),
      DebtTransaction("q-x", "q-d", "give", 50.0, today, notes = "فاکتور #99", createdAt = now + 1),
    ),
  )

  private fun ledgerActions() = DebtorLedgerActions(
    onBack = null, onCompose = {}, onEdit = {}, onDelete = {}, onOpenTx = {}, onDeleteTx = {}, onOpenSale = {}, onOlderSales = {},
  )

  @Test fun ledgerRowsCarryDirectionShortDateNoteAndBalance() {
    show { DebtorLedgerScreen("q-d", file, wide = false, actions = ledgerActions(), drawerOpen = false) }
    rule.waitUntil(10_000) { count("400 ؋ • " + StrMoney.giveTag) > 0 }
    assertEquals(1, count("150 ؋ • " + StrMoney.receiveTag))
    //  ماندهٔ پس از هر ردیف کوچک در پایان (۴۰۰ ⇒ ۲۵۰ ⇒ ۳۰۰)
    assertEquals(1, count(StrMoney.balanceAfterShort("400")))
    assertEquals(1, count(StrMoney.balanceAfterShort("250")))
    assertEquals(1, count(StrMoney.balanceAfterShort("300")))
    //  یادداشت کنارِ تاریخِ کوتاه، بی سالِ جاری (بی پیوند بیرون از پوسته)
    assertEquals(1, count("فروش نسیه — فاکتور #12", substring = true))
    assertEquals(0, count("/", substring = true))
    //  فاکتورهای مشتری: نامِ کالا، نه «فاکتور #۱۲»؛ «• مانده ۴۰۰» به رنگِ بدهی
    assertEquals(1, count(rice.name, substring = true))
    assertEquals(1, count("400 ؋ • " + StrMoney.remaining("400")))
  }

  private class Opened { val ids = ArrayList<String>() }

  private fun showSheet(txId: String, opened: Opened) {
    val tx = file.transactions.first { it.id == txId }
    val dBook = DebtBook.book(file, "q-d", today)
    show {
      CompositionLocalProvider(LocalInvoiceLinks provides InvoiceLinks.of(file) { opened.ids += it }) {
        DebtorTxSheet(tx, dBook, receipt = null, desk = true, actions = DebtorTxSheetActions(onClose = {}, onDelete = {}, onSaveDue = {}))
      }
    }
  }

  @Test fun tappingTheInvoiceInTheDebtorSheetOpensThatInvoice() {
    val opened = Opened()
    showSheet("q-g", opened)
    rule.waitUntil(10_000) { rule.onAllNodes(hasText("فاکتور #12") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    //  یادداشت («فروش نسیه — فاکتور #12»؛ فقط همان بخش) و سرِ اقلام — هر دو همان فاکتور
    val links = rule.onAllNodes(hasText("فاکتور #12") and hasClickAction())
    assertEquals(2, links.fetchSemanticsNodes().size)
    links[0].performSemanticsAction(SemanticsActions.OnClick)
    links[1].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    assertEquals(listOf("q-s12", "q-s12"), opened.ids)
    assertEquals(1, count("فروش نسیه — "))
  }

  @Test fun aMissingInvoiceShowsAQuietNotFound() {
    val opened = Opened()
    showSheet("q-x", opened)
    rule.waitUntil(10_000) { rule.onAllNodes(hasText("فاکتور #99") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    click("فاکتور #99")
    rule.waitUntil(10_000) { count(StrRows.invoiceMissing, substring = true) > 0 }
    assertTrue(opened.ids.isEmpty())
    //  برگه سرِ جاست
    assertEquals(1, count("فاکتور #99"))
  }

  @Test fun tappingTheNumberInASalesHistoryRowOpensThatInvoice() {
    val cash = ShopData(
      products = listOf(rice),
      sales = listOf(Sale("q-cash", 12, finalTotal = 200.0, paidAmount = 200.0, createdAt = now, date = today)),
      saleItems = listOf(SaleItem("q-ci", "q-cash", rice.id, quantity = 1.0, unitPrice = 200.0, totalPrice = 200.0)),
    )
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    RouteUiHarness(rule).use { h ->
      h.show(SalesPaths.HISTORY, cash)
      h.awaitThat { rule.onAllNodes(hasText("#12") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
      h.click("#12")
      h.await(StrSale.saleInvoiceNo("12"))
    }
  }

  /**
   *  ردیفِ حساب: «فاکتور #12» ِ یادداشت و «#12» ِ «فاکتورهای این مشتری» همان فاکتور را باز می‌کنند و به زدنِ خودِ ردیف
   *  (برگهٔ جزئیات) نمی‌رسند. (مسیرِ واقعیِ `debtors/{id}` در آزمون پشتِ قفلِ اشتراک است — `VipGate` با سرورِ ساختگی —
   *  پس سیم‌کشیِ پوسته را آزمونِ تاریخچهٔ بالا می‌سنجد و این‌جا خودِ صفحه با همان `InvoiceLinks`.)
   */
  @Test fun tappingTheInvoiceInALedgerRowNoteOpensThatInvoice() {
    val opened = Opened()
    val rowsOpened = ArrayList<String>()
    val actions = DebtorLedgerActions(
      onBack = null, onCompose = {}, onEdit = {}, onDelete = {}, onOpenTx = { rowsOpened += it }, onDeleteTx = {}, onOpenSale = {}, onOlderSales = {},
    )
    show {
      CompositionLocalProvider(LocalInvoiceLinks provides InvoiceLinks.of(file) { opened.ids += it }) {
        DebtorLedgerScreen("q-d", file, wide = false, actions = actions, drawerOpen = false)
      }
    }
    rule.waitUntil(10_000) { rule.onAllNodes(hasText("فاکتور #12") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    click("فاکتور #12")
    //  «فاکتورهای این مشتری» پایینِ فهرست است — اول تا آن‌جا پیمایش
    rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("#12") and hasClickAction())
    click("#12")
    assertEquals(listOf("q-s12", "q-s12"), opened.ids)
    assertTrue(rowsOpened.isEmpty())
  }
}
