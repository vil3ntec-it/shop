package ir.vil3ntec.tohid.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrDeskWork
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.perf.PosSpeed
import ir.vil3ntec.tohid.desktop.shots.StockDemo
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3MenuItem
import ir.vil3ntec.tohid.ui.v3.stock.DeskMovementsScreen
import ir.vil3ntec.tohid.ui.v3.stock.DeskStockScreen
import ir.vil3ntec.tohid.ui.v3.stock.DeskStockSort
import ir.vil3ntec.tohid.ui.v3.stock.StockFilter
import ir.vil3ntec.tohid.ui.v3.stock.StockItemActions
import ir.vil3ntec.tohid.ui.v3.stock.StockListActions
import ir.vil3ntec.tohid.ui.v3.stock.StockListModel
import ir.vil3ntec.tohid.ui.v3.stock.deskStockSorted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  «انبار» و «حرکات انبار» ِ **کامپیوتر** روی `ui/desk` (`claude/e-desk-d3a`): نوارِ ابزار (انبارگردانی، ورودِ چند قلم، «…» با
 *  ورود از فایل و کارهای افزوده، «ورود کالا»)، کارت‌های عدد ⇐ همان فیلتر، تراشه‌های فیلتر، ردیفِ آرام با تنها کارِ «ورود»،
 *  پنلِ کالا با برگشت، مرتب‌سازیِ خالص، ۲۰ هزار کالا تنبل، و جدولِ حرکت‌ها با تراشهٔ گونه و «باز کردن» ِ فاکتور.
 */
@OptIn(ExperimentalTestApi::class)
class DeskStockD3aUiTest {

  @get:Rule val rule = createComposeRule()

  private val calls = ArrayList<String>()

  @Before fun before() {
    I18n.source = { Lang.FA }
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
  }

  private fun actions() = StockListActions(
    open = { calls += "open:$it" }, entry = { calls += "entry:$it" }, adjust = { calls += "adjust:$it" }, scan = { calls += "scan" },
    count = { calls += "count" }, import = { calls += "import" }, bulk = { calls += "bulk" }, newProduct = { calls += "new" },
    saveEntry = { null }, extras = listOf(V3MenuItem(Icons.Outlined.Place, "محل‌های آزمون") { calls += "extra" }),
  )

  private fun itemActions() = StockItemActions(
    edit = { calls += "edit:$it" }, view = { calls += "view:$it" }, deleteProduct = {}, entry = { calls += "entry:$it" },
    adjust = { calls += "adjust:$it" }, deleteEntry = {}, movements = { calls += "moves:$it" }, copied = {},
  )

  private val selected = mutableStateOf<String?>(null)

  private fun open(d: ShopData) {
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1366, 768),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          DeskStockScreen(d, todayIso(), remember { actions() }, remember { itemActions() }, selected.value, places = false, StockFilter.ALL, "", onClose = { selected.value = null })
        }
      }
    }
    rule.waitUntil(10_000) { nodes(clickLabel(StrStock.swipeEntry)).isNotEmpty() || shown(StrStock.emptyTitle) }
  }

  private fun clickLabel(label: String) = SemanticsMatcher("زدن: $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
  private fun clickPrefix(prefix: String) = SemanticsMatcher("زدن: $prefix…") { it.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith(prefix) == true }
  private fun nodes(m: SemanticsMatcher) = rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes()
  private fun tap(m: SemanticsMatcher) { rule.onAllNodes(m, useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle() }
  /** گزینهٔ فهرستِ کشویی (درختِ ادغام‌شده: نوشته درونِ گزینهٔ زدنی است) */
  private fun menu(text: String) { rule.onAllNodes(hasText(text) and androidx.compose.ui.test.hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle() }
  private fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  /** ردیف‌های جدول: گره‌هایی که زدنشان کالا را باز می‌کند (نامِ کالا) و یک «ورود» درون دارند */
  private fun rows(d: ShopData): List<SemanticsNode> {
    val names = d.products.map { it.name }.toSet()
    return nodes(SemanticsMatcher("ردیف") { it.config.getOrNull(SemanticsActions.OnClick)?.label in names })
  }

  private fun actionsInside(row: SemanticsNode): Int {
    fun walk(n: SemanticsNode): Int = n.children.sumOf { ch -> (if (ch.config.getOrNull(SemanticsActions.OnClick) != null) 1 else 0) + walk(ch) }
    return walk(row)
  }

  @Test fun `هر کارِ پیشینِ انبار دیده و زده می‌شود و کارت‌ها همان فیلترند`() {
    val d = StockDemo.full()
    open(d)
    tap(clickLabel(StrStock.entryAction)); tap(clickLabel(StrStock.menuCount)); tap(clickLabel(StrStock.menuBulk)); tap(clickLabel(ir.vil3ntec.tohid.core.i18n.StrKit.scanBarcode))
    assertEquals(listOf("entry:null", "count", "bulk", "scan"), calls)
    //  «…» ⇐ ورود از فایل و کارهای افزوده
    tap(clickLabel(StrDeskWork.moreActions)); menu(StrStock.menuImport)
    tap(clickLabel(StrDeskWork.moreActions)); menu("محل‌های آزمون")
    assertEquals(listOf("import", "extra"), calls.drop(4))
    //  کارتِ «کم‌موجود» ⇐ فیلترِ «کم»؛ شمارِ ردیف‌ها همان شمارِ مدل
    val o = StockListModel.overview(d, todayIso())
    tap(clickPrefix(StrDeskWork.lowStock + ": "))
    assertEquals(o.counts[StockFilter.LOW], rows(d).size)
    tap(clickPrefix(StrDeskWork.outStock + ": "))
    assertEquals(o.counts[StockFilter.OUT], rows(d).size)
    //  تراشهٔ «همه»
    tap(clickLabel(StrStock.filterAll))
    assertTrue(rows(d).size > (o.counts[StockFilter.OUT] ?: 0))
    //  ردیف ⇐ باز کردنِ کالا؛ «ورود» ِ ردیف ⇐ ورودِ همان کالا
    val first = rows(d).first()
    first.config.getOrNull(SemanticsActions.OnClick)!!.action!!.invoke(); rule.waitForIdle()
    assertTrue(calls.last().startsWith("open:"))
    tap(clickLabel(StrStock.swipeEntry))
    assertTrue(calls.last().startsWith("entry:p"))
  }

  @Test fun `ردیفِ انبار آرام است — تنها یک کارِ خنثی («ورود»)`() {
    val d = StockDemo.full()
    open(d)
    val rows = rows(d)
    assertTrue(rows.isNotEmpty())
    rows.forEach { assertEquals("ردیفِ ${it.config.getOrNull(SemanticsActions.OnClick)?.label}", 1, actionsInside(it)) }
  }

  @Test fun `کالای باز در پنلِ کنار با برگشت`() {
    val d = StockDemo.full()
    selected.value = StockDemo.RICE
    open(d)
    rule.waitUntil(10_000) { nodes(clickLabel(ir.vil3ntec.tohid.core.i18n.StrKit.back)).isNotEmpty() }
    tap(clickLabel(ir.vil3ntec.tohid.core.i18n.StrKit.back))
    assertEquals(null, selected.value)
  }

  @Test fun `مرتب‌سازیِ جدولِ انبار — بی‌عدد همیشه آخر`() {
    val o = StockListModel.overview(StockDemo.full(), todayIso())
    val byStock = deskStockSorted(o.rows, DeskStockSort.STOCK, asc = true)
    assertEquals(o.rows.minOf { it.stock }, byStock.first().stock, 0.0)
    val byExpiry = deskStockSorted(o.rows, DeskStockSort.EXPIRY, asc = true)
    assertTrue("منقضی اول", byExpiry.first().expired)
    assertTrue("بی انقضا آخر", byExpiry.last().expiresInDays == null && !byExpiry.last().expired)
    val desc = deskStockSorted(o.rows, DeskStockSort.EXPIRY, asc = false)
    assertTrue("وارونه هم بی‌عدد آخر", desc.last().expiresInDays == null && !desc.last().expired)
    assertEquals(o.rows.size, desc.size)
  }

  @Test fun `۲۰ هزار کالا تنبل کشیده می‌شود`() {
    val d = PosSpeed.shop(products = 20_000, sales = 10).d
    open(d)
    val n = nodes(clickLabel(StrStock.swipeEntry)).size
    assertTrue("ردیف‌های کشیده‌شده: $n", n in 1..80)
  }

  @Test fun `حرکاتِ انبارِ کامپیوتر — جدول، تراشهٔ گونه و باز کردنِ فاکتور`() {
    val d = StockDemo.full()
    val opened = ArrayList<String>()
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext, LocalConfiguration provides Configuration(1366, 768)) {
        TohidTheme(ThemeChoice.LIGHT) { DeskMovementsScreen(d, StockDemo.RICE, todayIso(), onOpen = { opened += it }, onBack = {}) }
      }
    }
    rule.waitForIdle()
    val all = d.stockMovements.count { it.productId == StockDemo.RICE }
    //  هر حرکت یک ردیف: مقدارِ با نشانه («+24» و «−20»)
    assertTrue(shown("+" + ir.vil3ntec.tohid.qty(24.0)) && shown("−" + ir.vil3ntec.tohid.qty(20.0)))
    assertTrue(all >= 7)
    //  «باز کردن» ِ فروش ⇐ همان فاکتور
    tap(clickLabel(StrDeskWork.openLink))
    assertTrue(opened.single().contains("s4"))
    //  تراشهٔ گونهٔ «فروش» ⇐ فقط فروش (یک ردیفِ «−20»)
    tap(clickLabel(ir.vil3ntec.tohid.ui.v3.stock.deskKindLabel(ir.vil3ntec.tohid.ui.v3.stock.MoveKind.SALE)))
    assertTrue(shown("−" + ir.vil3ntec.tohid.qty(20.0)))
    assertTrue(!shown("+" + ir.vil3ntec.tohid.qty(48.0)))
  }
}
