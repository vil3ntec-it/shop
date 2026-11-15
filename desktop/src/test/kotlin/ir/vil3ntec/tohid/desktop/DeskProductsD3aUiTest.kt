package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeskWork
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrProductPages
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.perf.PosSpeed
import ir.vil3ntec.tohid.desktop.shots.StockDemo
import ir.vil3ntec.tohid.ui.screens.DeskProductsScreen
import ir.vil3ntec.tohid.ui.screens.ProductListModel
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  «کالاها» ِ **کامپیوتر** روی `ui/desk` (`claude/e-desk-d3a`): هر کارِ پیشین (ورودِ انبار، چاپ، خروجیِ اکسل، «افزودن کالا» با
 *  فوکوس روی ردیفِ تازه، کارت‌های عدد ⇐ زبانه، دسته، زبانه‌ها، نمای جدول/کارت، مرتب‌سازیِ سرستون، پنلِ ویرایش و نوارِ کارِ
 *  گروهی) هنوز دیده و زده می‌شود؛ ردیف آرام است (جز تیکِ انتخاب هیچ کارِ دیگری)؛ ۲۰ هزار کالا تنبل کشیده می‌شود.
 */
@OptIn(ExperimentalTestApi::class)
class DeskProductsD3aUiTest {

  @get:Rule val rule = createComposeRule()

  private val warehouse = ArrayList<String?>()
  private val opened = ArrayList<String>()

  @Before fun before() {
    I18n.source = { Lang.FA }
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
  }

  private fun open(d: ShopData, width: Int = 1366): ShopStore {
    val store = RouteUiHarness.memoryStore(d)
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(width, 768),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          val live by store.data.collectAsState()
          DeskProductsScreen(store, live, remember { SnackbarHostState() }, onOpenWarehouse = { warehouse += it }, onOpenProduct = { opened += it })
        }
      }
    }
    rule.waitForIdle()
    return store
  }

  private fun clickLabel(label: String) = SemanticsMatcher("زدن: $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }
  private fun clickPrefix(prefix: String) = SemanticsMatcher("زدن: $prefix…") { it.config.getOrNull(SemanticsActions.OnClick)?.label?.startsWith(prefix) == true }
  private fun nodes(m: SemanticsMatcher) = rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes()
  private fun tap(m: SemanticsMatcher) { rule.onAllNodes(m, useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle() }
  private fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
  private val rowPrefix = StrProductPages.editItem + ": "

  /** کارهای زدنیِ درونِ یک ردیف (جز خودِ ردیف) — تیکِ انتخاب کار نیست، انتخاب است */
  private fun actionsInside(row: SemanticsNode): Int {
    fun walk(n: SemanticsNode): Int = n.children.sumOf { ch ->
      val acts = if (ch.config.getOrNull(SemanticsActions.OnClick) != null && ch.config.getOrNull(SemanticsProperties.Role) != androidx.compose.ui.semantics.Role.Checkbox) 1 else 0
      acts + walk(ch)
    }
    return walk(row)
  }

  @Test fun `هر کارِ پیشینِ کالاها دیده و زده می‌شود`() {
    val d = StockDemo.full()
    open(d)
    //  کارهای سرِ صفحه
    tap(clickLabel(StrProductPages.stockIn))
    assertEquals(listOf<String?>(null), warehouse)
    for (label in listOf(StrHistory.print, StrProductPages.excelExport, StrProductPages.addItem)) assertTrue("«$label» زدنی نیست", nodes(clickLabel(label)).isNotEmpty())
    //  «افزودن کالا» ⇐ فوکوس روی خانهٔ نامِ ردیفِ «کالای تازه»
    tap(clickLabel(StrProductPages.addItem))
    val focused = rule.onAllNodes(isFocused(), useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue("خانهٔ نامِ کالای تازه فوکوس نگرفت", focused.any { it.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(StrHome.newItem) == true })
    //  کارتِ «کم‌موجود» ⇐ فقط کالاهای کم (زبانهٔ LOW)
    val stockOf: (String) -> Double = { ShopStore.stock(d, it) }
    val low = ProductListModel.rows(d.products, stockOf, "", ProductListModel.Tab.LOW, null, ProductListModel.Sort.NAME, true)
    tap(clickPrefix(Str.lowStock + ": "))
    assertEquals(low.size, nodes(clickPrefix(rowPrefix)).size)
    //  زبانهٔ «همه» با شمار
    tap(clickPrefix(Str.all + " "))
    assertTrue(nodes(clickPrefix(rowPrefix)).size > low.size)
    //  سرستونِ «فروش ؋» ⇐ مرتب‌سازی (پیکانِ ▲ روی همان سرستون)
    tap(clickLabel(StrDeskWork.sortBy(StrDeskWork.colPrice)))
    assertTrue(nodes(SemanticsMatcher("▲") { it.config.getOrNull(SemanticsProperties.StateDescription) == "▲" }).isNotEmpty())
    //  زدنِ ردیف ⇐ پنلِ ویرایش؛ Esc ⇐ بسته
    tap(clickPrefix(rowPrefix))
    assertTrue(shown(StrProductPages.saveKeys))
    rule.onRoot().performKeyInput { pressKey(Key.Escape) }
    rule.waitForIdle()
    //  تیکِ یک ردیف ⇐ نوارِ کارِ گروهی (فعال/غیرفعال)
    //  نخستین تیکِ ردیفِ دیده‌شده (نه تیکِ «همه»)
    rule.onAllNodes(SemanticsMatcher("تیکِ ردیف") { n ->
      n.config.getOrNull(SemanticsProperties.Role) == androidx.compose.ui.semantics.Role.Checkbox &&
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.none { it == StrProductPages.selectAllVisible } == true
    }, useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    assertTrue(shown(StrProducts.activate) && shown(StrProducts.deactivate))
    //  نمای کارتی
    tap(hasText(Str.cardView) and hasClickAction())
    assertTrue(nodes(clickPrefix(rowPrefix)).isNotEmpty())
  }

  @Test fun `ردیفِ جدولِ کالا آرام است — جز تیکِ انتخاب هیچ کارِ دیگری`() {
    open(StockDemo.full(), width = 1920)
    val rows = nodes(clickPrefix(rowPrefix))
    assertTrue(rows.isNotEmpty())
    rows.forEach { assertTrue("ردیفِ ${it.config.getOrNull(SemanticsActions.OnClick)?.label} بیش از یک کار دارد", actionsInside(it) <= 1) }
  }

  @Test fun `۲۰ هزار کالا تنبل کشیده می‌شود`() {
    open(PosSpeed.shop(products = 20_000, sales = 10).d)
    val n = nodes(clickPrefix(rowPrefix)).size
    assertTrue("ردیف‌های کشیده‌شده: $n", n in 1..80)
  }
}
