package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
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
import androidx.compose.ui.unit.dp
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
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.customerPhone
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3DebtLight
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
import kotlin.math.abs

/**
 *  قرض‌داران به مدلِ پیشین برگشتند (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۴، `claude/e-rows-65q`؛ فهرستِ تخت و ردیف‌های بی‌رنگِ ۶۳q پس
 *  گرفته شد) — و کارهای پس از آن ماندند:
 *   • شبکهٔ کارت‌ها (`DebtorTile`): نام، مبلغِ درشت، کارتِ سرخ برای بدهکار و سبز برای صاف؛ × و مداد روی کارت؛ نگه‌داشتن ⇐
 *     همان سه کار؛ شمارهٔ صندوق‌دار زیرِ پرچمِ نقش‌محور پوشیده (CRM-15)؛
 *   • کارتِ سه‌عددی سه جعبهٔ جدای کنارِ هم: تعداد آبی، جمعِ قرض سرخ، رسیدها سبز (روی ۳۶۰dp در یک ردیف)؛
 *   • خالیِ دفتر «قرض‌دار تازه» و جستجوی بی نتیجه «نمایشِ همه» (روی همان کارتِ خالیِ پیشین)؛
 *   • ردیفِ حساب همان ردیفِ یک‌خطیِ ۴۶dp با زمینهٔ سرخِ برد و سبزِ رسید؛ «فاکتور #۱۲» ِ یادداشت زیرِ تاریخ، زدنی ⇒ همان
 *     فاکتور (نه برگهٔ ردیف)؛ فاکتورِ ناپیدا «پیدا نشد» ِ آرام؛
 *   • برگهٔ تراکنش (۶۳u) و «فاکتور #۱۲» ِ زدنیِ آن، و شمارهٔ زدنیِ تاریخچهٔ فروش، همان.
 */
@OptIn(ExperimentalTestApi::class)
class DebtorRowsE65qUiTest {

  @get:Rule val rule = createComposeRule()

  @Before fun fa() {
    I18n.source = { Lang.FA }
    Motion.reducedForTest = true
  }

  @After fun reset() {
    I18n.source = { Lang.FA }
    Motion.reducedForTest = null
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    ShopRole.rememberPolicy(DesktopContext, null)
  }

  private fun show(content: @Composable () -> Unit) = rule.setContent {
    CompositionLocalProvider(
      LocalContext provides DesktopContext,
      LocalView provides remember { android.view.View(DesktopContext) },
      LocalConfiguration provides Configuration(360, 760),
    ) { TohidTheme(ThemeChoice.LIGHT) { Box(Modifier.size(360.dp, 760.dp)) { Column { content() } } } }
  }

  /** dp ⇒ پیکسلِ همین آزمون */
  private fun px(dp: Float) = dp * rule.density.density

  private fun count(text: String, substring: Boolean = false) = rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

  private fun click(text: String) {
    rule.onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  /** سهمِ پیکسل‌هایی از یک گره که همان رنگِ [want] اند (با رواداریِ کم) — «کارت/ردیف به این رنگ است» */
  private fun share(node: SemanticsNodeInteraction, want: Color): Float {
    val px = node.captureToImage().toPixelMap()
    var hit = 0
    for (x in 0 until px.width) for (y in 0 until px.height) {
      val p = px[x, y]
      if (abs(p.red - want.red) < 0.02f && abs(p.green - want.green) < 0.02f && abs(p.blue - want.blue) < 0.02f) hit++
    }
    return hit.toFloat() / (px.width * px.height)
  }

  private val c = PhoneV3DebtLight
  private val today = todayIso()
  private val now = System.currentTimeMillis()
  private val dayMs = 86_400_000L
  private fun iso(daysAgo: Int) = ir.vil3ntec.tohid.core.calendar.CivilDays.addDays(today, -daysAgo)!!

  /* ─────────────────────────── فهرستِ قرض‌داران ─────────────────────────── */

  private val phone = "0799123456"
  private val book = ShopData(
    debtors = listOf(
      Debtor(id = "q-owe", name = "نسیم آزمونی", phone = phone),
      Debtor(id = "q-flat", name = "زهره صاف"),
    ),
    transactions = listOf(
      DebtTransaction("q-t1", "q-owe", "give", 300.0, today, createdAt = now),
      DebtTransaction("q-t2", "q-flat", "give", 120.0, iso(3), createdAt = now - 3 * dayMs),
      DebtTransaction("q-t3", "q-flat", "receive", 120.0, iso(1), createdAt = now - dayMs),
    ),
  )

  private class Calls {
    val open = ArrayList<String>()
    val edit = ArrayList<String>()
    val delete = ArrayList<String>()
    var add = 0
    fun actions() = DebtorsListActions(onBack = null, onOpen = { open += it }, onEdit = { edit += it }, onDelete = { delete += it }, onAdd = { add++ })
  }

  private fun tile(name: String) = rule.onAllNodes(hasText(name) and hasClickAction())[0]

  @Test fun debtorTilesShowNameBalanceAndTheRedOrGreenCard() {
    show { DebtorsListScreen(book, null, null, Calls().actions()) }
    rule.waitUntil(10_000) { count("نسیم آزمونی") > 0 }
    //  مبلغِ درشتِ کارت و زیرِ آن شماره یا «حساب صاف»
    assertTrue(count(money(300.0)) > 0)
    assertEquals(1, count("زهره صاف"))
    //  کارتِ بدهکار سرخ (زمینه و مرز)، کارتِ صاف سبز — نه کارتِ سفیدِ فهرستِ تخت
    val owe = tile("نسیم آزمونی")
    assertTrue("زمینهٔ سرخِ کارتِ بدهکار", share(owe, c.oweTint) > 0.35f)
    assertTrue("مرزِ سرخِ کارتِ بدهکار", share(owe, c.owe) > 0.005f)
    assertTrue("کارتِ بدهکار سبز نیست", share(owe, c.settledTint) < 0.01f)
    val flat = tile("زهره صاف")
    assertTrue("زمینهٔ سبزِ کارتِ صاف", share(flat, c.settledTint) > 0.35f)
    assertTrue("کارتِ صاف سرخ نیست", share(flat, c.oweTint) < 0.01f)
    //  دو کارت کنارِ هم در یک ردیفِ شبکه (نه فهرستِ تمام‌پهنا)
    val a = owe.fetchSemanticsNode().boundsInRoot
    val b = flat.fetchSemanticsNode().boundsInRoot
    assertEquals(a.top, b.top, 1f)
    assertTrue(a.width < px(180f) && b.width < px(180f))
  }

  @Test fun tappingATileOpensTheFileWithoutShowingCornerActions() {
    val calls = Calls()
    show { DebtorsListScreen(book, null, null, calls.actions()) }
    rule.waitUntil(10_000) { count("نسیم آزمونی") > 0 }
    click("نسیم آزمونی")
    assertEquals(listOf("q-owe"), calls.open)
    assertTrue(rule.onAllNodes(hasContentDescription(StrMoney.edit) and hasClickAction()).fetchSemanticsNodes().isEmpty())
    assertTrue(rule.onAllNodes(hasContentDescription(StrMoney.delete) and hasClickAction()).fetchSemanticsNodes().isEmpty())
    assertTrue(calls.edit.isEmpty())
    assertTrue(calls.delete.isEmpty())
  }

  @Test fun longPressKeepsOpenEditAndDelete() {
    val calls = Calls()
    show { DebtorsListScreen(book, null, null, calls.actions()) }
    rule.waitUntil(10_000) { count("زهره صاف") > 0 }
    tile("زهره صاف").performTouchInput { longClick() }
    rule.waitUntil(10_000) { count(StrMoney.openAccount) > 0 }
    assertEquals(1, count(StrMoney.edit))
    assertEquals(1, count(StrMoney.delete))
    click(StrMoney.edit)
    assertEquals(listOf("q-flat"), calls.edit)
    assertTrue(calls.open.isEmpty())
  }

  @Test fun statsAreThreeSeparateColouredBoxesInOneRow() {
    show { DebtorsListScreen(book, null, null, Calls().actions()) }
    rule.waitUntil(10_000) { count(StrMoney.statOwed, substring = true) > 0 }
    fun box(label: String) = rule.onAllNodes(hasText(label))[0]
    val people = box(StrMoney.statPeople)
    val owed = box(StrMoney.statOwed)
    val received = box(StrMoney.statReceived)
    //  همان عددها و برچسب‌ها: دو نفر، ۳۰۰ قرض، ۱۲۰ رسید
    assertTrue(rule.onAllNodes(hasText(StrMoney.statOwed) and hasText(money(300.0))).fetchSemanticsNodes().size == 1)
    assertTrue(rule.onAllNodes(hasText(StrMoney.statReceived) and hasText(money(120.0))).fetchSemanticsNodes().size == 1)
    assertTrue(rule.onAllNodes(hasText(StrMoney.statPeople) and hasText(ir.vil3ntec.tohid.plain(2))).fetchSemanticsNodes().size == 1)
    //  هر جعبه رنگِ خودش: تعداد آبی، قرض سرخ، رسید سبز
    assertTrue("تعداد آبی", share(people, c.infoTint) > 0.35f)
    assertTrue("قرض سرخ", share(owed, c.oweTint) > 0.35f)
    assertTrue("رسید سبز", share(received, c.settledTint) > 0.35f)
    //  سه جعبهٔ جدا، هم‌بلند، کنارِ هم در یک ردیف و درونِ ۳۶۰dp
    val r = listOf(people, owed, received).map { it.fetchSemanticsNode().boundsInRoot }.sortedBy { it.left }
    r.forEach { assertEquals(r[0].top, it.top, 1f); assertEquals(r[0].bottom, it.bottom, 1f) }
    assertTrue(r[0].right < r[1].left && r[1].right < r[2].left)
    assertTrue(r.first().left >= 0f && r.last().right <= px(360f))
  }

  @Test fun aCashierSeesAMaskedPhoneOnTheTile() {
    ShopRole.remember(DesktopContext, ShopRole.CASHIER)
    ShopRole.rememberPolicy(DesktopContext, """{"v":1,"role":"cashier","layers":[{},{}],"effective":{},"editable":[],"customerMask":true}""")
    show { DebtorsListScreen(book, null, null, Calls().actions()) }
    rule.waitUntil(10_000) { count("نسیم آزمونی") > 0 }
    val masked = customerPhone(phone, false)
    assertTrue(masked, masked.contains("•••• "))
    assertTrue("شمارهٔ پوشیده روی کارت", count(masked) > 0)
    assertEquals("شمارهٔ کامل روی کارت", 0, count(customerPhone(phone, true)))
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

  private class Opened {
    val ids = ArrayList<String>()
    val rows = ArrayList<String>()
    val sales = ArrayList<String>()
    var olderSales = 0
  }

  private fun showLedger(opened: Opened, links: Boolean = true, drawerOpen: Boolean = false) = show {
    val actions = DebtorLedgerActions(
      onBack = null, onCompose = {}, onEdit = {}, onDelete = {}, onOpenTx = { opened.rows += it }, onDeleteTx = {},
      onOpenSale = { opened.sales += it }, onOlderSales = { opened.olderSales++ },
    )
    if (links) {
      CompositionLocalProvider(LocalInvoiceLinks provides InvoiceLinks.of(file) { opened.ids += it }) {
        DebtorLedgerScreen("q-d", file, wide = false, actions = actions, drawerOpen = drawerOpen)
      }
    } else DebtorLedgerScreen("q-d", file, wide = false, actions = actions, drawerOpen = drawerOpen)
  }

  /** ردیف‌های حساب با نامِ نوعشان (پیکانِ ↗ برد / ↙ رسید برای صفحه‌خوان) */
  private fun ledgerRows(kind: String) = rule.onAllNodes(hasContentDescription(kind) and hasClickAction())

  @Test fun ledgerRowsAreTheOneLineRedAndGreenBoxes() {
    showLedger(Opened(), links = false)
    rule.waitUntil(10_000) { ledgerRows(StrMoney.giveTag).fetchSemanticsNodes().size == 2 }
    val gives = ledgerRows(StrMoney.giveTag)
    val receives = ledgerRows(StrMoney.receiveTag)
    assertEquals(1, receives.fetchSemanticsNodes().size)
    //  هر برد جعبهٔ سرخ، هر رسید جعبهٔ سبز
    for (i in 0 until 2) {
      assertTrue("برد سرخ", share(gives[i], c.oweTint) > 0.45f)
      assertTrue("برد سبز نیست", share(gives[i], c.settledTint) < 0.01f)
    }
    assertTrue("رسید سبز", share(receives[0], c.settledTint) > 0.45f)
    assertTrue("رسید سرخ نیست", share(receives[0], c.oweTint) < 0.01f)
    //  یک‌خطیِ ۴۶dp — ردیفِ فاکتوردار هم (فاکتور زیرِ تاریخ می‌نشیند، ردیف بلند نمی‌شود)
    (0 until 2).map { gives[it] }.plus(receives[0]).forEach { assertEquals(46f, it.fetchSemanticsNode().boundsInRoot.height / rule.density.density, 0.6f) }
    //  مبلغ با علامت و ماندهٔ پس از هر ردیف (۴۰۰ ⇒ ۲۵۰ ⇒ ۳۰۰)
    assertEquals(1, count(ir.vil3ntec.tohid.ui.v3.money.MoneyText.signed(150.0, false)))
    //  (همان ردیف: «مانده ۴۰۰» ِ «فاکتورهای این مشتری» جدا شمرده نمی‌شود)
    fun rowWith(kind: String, after: String) = rule.onAllNodes(hasContentDescription(kind) and hasText(StrMoney.balanceAfterShort(after))).fetchSemanticsNodes().size
    assertEquals(1, rowWith(StrMoney.giveTag, "400"))
    assertEquals(1, rowWith(StrMoney.receiveTag, "250"))
    assertEquals(1, rowWith(StrMoney.giveTag, "300"))
    //  یادداشتِ آزاد روی ردیف نیست (در برگه است)؛ فقط «فاکتور #12» ِ آن
    assertEquals(0, count("فروش نسیه", substring = true))
    assertEquals(1, count("فاکتور #99"))
  }

  @Test fun tappingTheInvoiceInALedgerRowOpensThatInvoiceNotTheRow() {
    val opened = Opened()
    showLedger(opened)
    rule.waitUntil(10_000) { rule.onAllNodes(hasText("فاکتور #99") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    //  «فاکتور #12» ِ ردیفِ برد (و اگر دیده می‌شود، ردیفِ «فاکتورهای این مشتری» که همان فاکتور را در «فروش‌ها» باز می‌کند)
    rule.onAllNodes(hasText("فاکتور #12") and hasClickAction()).fetchSemanticsNodes().indices.forEach {
      rule.onAllNodes(hasText("فاکتور #12") and hasClickAction())[it].performSemanticsAction(SemanticsActions.OnClick)
    }
    rule.waitForIdle()
    assertEquals(listOf("q-s12"), opened.ids)
    assertTrue(opened.sales.all { it == "q-s12" })
    assertTrue("زدنِ فاکتور برگهٔ ردیف را باز نکرد", opened.rows.isEmpty())
  }

  @Test fun aMissingInvoiceInALedgerRowSaysNotFound() {
    val opened = Opened()
    showLedger(opened)
    rule.waitUntil(10_000) { rule.onAllNodes(hasText("فاکتور #99") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    click("فاکتور #99")
    rule.waitUntil(10_000) { count(StrRows.invoiceMissing, substring = true) > 0 }
    assertTrue(opened.ids.isEmpty())
    assertTrue(opened.rows.isEmpty())
  }

  /**
   *  صاحب‌کار ۱۴۰۵/۰۸/۲۴: «فاکتورها توی همون بردگی یا رسید باشه، این‌جا نباید باشه» — فهرستِ جدای «فاکتورهای این مشتری» زیرِ
   *  تراکنش‌ها نیست؛ فاکتورِ نسیه در ردیفِ «برد» ِ خودش زدنی است و همهٔ فاکتورها (نقد هم) از دکمهٔ «فاکتورها» ِ «کارها و اطلاعات»
   *  ⇐ تاریخچهٔ فروشِ همین مشتری (`onOlderSales`). (جای آزمونِ «فاکتورهای این مشتری ردیف‌های پیشین‌اند» ِ ۶۵q.)
   */
  @Test fun invoicesAreInTheirRowsAndAllOfThemBehindTheDrawerButtonNotAListBelow() {
    val opened = Opened()
    showLedger(opened, drawerOpen = true)
    rule.waitUntil(10_000) { rule.onAllNodes(hasText("فاکتور #12") and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    assertEquals(0, count(StrMoney.invoices(1), substring = true))
    rule.waitUntil(10_000) { count(StrMoney.invoicesAction) > 0 }
    click(StrMoney.invoicesAction)
    assertEquals(1, opened.olderSales)
    assertTrue(opened.sales.isEmpty() && opened.rows.isEmpty())
  }

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

  @Test fun aMissingInvoiceInTheSheetShowsAQuietNotFound() {
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
}
