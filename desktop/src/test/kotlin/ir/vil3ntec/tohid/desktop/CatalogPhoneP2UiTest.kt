package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrA11y
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrNewProduct
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrReach
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductSuggestPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.desktop.shots.StockDemo
import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.products.ProductsListSpec
import ir.vil3ntec.tohid.ui.v3.products.QuickProductSpec
import ir.vil3ntec.tohid.ui.v3.stock.StockListSpec
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  کالاها، گدام و «کالای تازه» ِ گوشی (۱۴۰۵/۰۸/۲۴، صاحب‌کار: «یا خیلی شلوغ استن یا به درد نخور»؛ «سربرگِ گدام رو می‌خوام چی
 *  کار؟»؛ یک کالا نصفِ صفحه را گرفته بود؛ فرمِ چندردیفی برای یک کالا سنگین بود) — روی مسیرِ واقعی در قابِ ۳۶۰×۷۶۰dp:
 *   • ردیفِ کالا ≤ ۷۲dp و دست‌کم ۸ ردیفِ کامل در یک صفحهٔ گوشی؛ یک کالا ⇒ یک ردیفِ کوتاه؛
 *   • شبکه سه‌ستونه با کاشیِ کوچک — یک کالا هرگز کارتِ بزرگ نیست؛
 *   • نه کالاها و نه گدام کارتِ خلاصهٔ بالا ندارند؛ همان عددها یک خطِ کم‌رنگ در پایانِ فهرست‌اند؛
 *   • دکمهٔ «کالای تازه» فرمِ ساده را باز می‌کند: فقط نام و قیمتِ فروش، «بیشتر» بسته؛ باز ⇒ قیمتِ خرید، کد، موجودی و تاریخ‌ها و
 *     دسته/واحد به کادرِ کشویی؛ ذخیره با دو کادر و واحدِ پیش‌فرض؛ کادرِ لازمِ خالی ⇒ خطای درجا و هیچ ثبتی؛
 *   • «ثبتِ چند کالا با هم» در «…» ِ کالاها و گدام ⇐ فرمِ چندردیفی.
 */
class CatalogPhoneP2UiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private lateinit var store: ShopStore

  private fun clean() {
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
  }

  @Before fun up() {
    I18n.source = { Lang.FA }
    clean()
    ShopRole.remember(ctx, ShopRole.OWNER)
    //  تراشهٔ «دستهٔ پیشنهادی» زیرِ نام نیاید (همان پیش‌فرضِ آزمونِ فرمِ چندردیفی)
    ProductSuggestPref.set(ctx, false)
    //  نمای فهرست (ترجیحِ دستگاه) پیش‌فرض باشد
    ctx.getSharedPreferences("tohid-ui", 0).edit().remove("products_layout").commit()
  }

  @After fun down() {
    clean()
    ctx.getSharedPreferences("tohid-ui", 0).edit().remove("products_layout").commit()
    I18n.source = { Lang.FA }
  }

  /** مسیرِ دیده‌شده و دفترش — هر [phone] ِ تازه همین را عوض می‌کند (یک `setContent` در هر آزمون) */
  private val shown = mutableStateOf<Pair<String, ShopStore>?>(null)
  private var started = false

  /** همان مسیرِ برنامه در قابِ گوشیِ واقعی (۳۶۰×۷۶۰dp) — جای فهرست زیرِ سربرگ و بالای نوارِ پایین همان گوشی است */
  private fun phone(path: String, data: ShopData, disk: Boolean = false) {
    //  [disk] ⇒ دفترِ واقعیِ روی پوشهٔ دادهٔ آزمون (صفحه‌ای که می‌نویسد، همان راهِ `NewProductFormUiTest`)
    store = if (!disk) RouteUiHarness.loadedStore(data)
    else ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.save(data)) } }
    shown.value = path to store
    if (!started) {
      started = true
      rule.setContent {
        CompositionLocalProvider(
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, 760),
        ) {
          TohidTheme(ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
              Box(Modifier.requiredSize(360.dp, 760.dp)) {
                shown.value?.let { (p, s) -> key(p, s) { V3RoutePreview(p, s, dark = false) } }
              }
            }
          }
        }
      }
    }
    rule.waitForIdle()
  }

  private fun dp(px: Float): Float = px / rule.density.density

  private fun bounds(tag: String): List<Rect> =
    rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }

  private fun count(text: String) = rule.onAllNodes(hasText(text) or hasContentDescription(text)).fetchSemanticsNodes().size

  private fun click(matcher: SemanticsMatcher) {
    rule.onAllNodes(matcher and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private fun click(text: String) = click(hasText(text) or hasContentDescription(text))

  private fun fields(label: String) = rule.onAllNodes(hasSetTextAction() and hasContentDescription(label)).fetchSemanticsNodes().size

  private val dropdown = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)

  private fun dropdowns(label: String) = rule.onAllNodes(dropdown and hasContentDescription(label)).fetchSemanticsNodes().size

  /** دفترِ عکسِ صاحب‌کار: یک کالا «شکلات»، ۱۰ کارتنِ ۱۵تایی = ۱۵۰ عدد، خرید ۱۰۰ و فروش ۱۲۰ ؋ */
  private fun one(): ShopData {
    val p = Product(id = "c1", name = "شکلات", unit = "عدد", purchasePrice = 100.0, salePrice = 120.0, minStock = 5.0, category = "شکلات ها")
    val e = WarehouseEntry(id = "e1", productId = "c1", cartons = 10.0, perCarton = 15.0, units = 150.0, unit = "عدد", price = 100.0, date = todayIso(), createdAt = 1_000)
    return ShopData(products = listOf(p), productCategories = listOf("آبمیوه", "شکلات ها"), warehouseEntries = listOf(e))
  }

  private fun awaitRows(n: Int) = rule.waitUntil(15_000) { bounds(V3DataRowSpec.ROW_TAG).size >= n }

  @Test fun productRowsAreCompactAndAtLeastEightFitOnOnePhoneScreen() {
    phone("products?view=list", StockDemo.full())
    awaitRows(8)
    rule.waitForIdle()
    val list = bounds(ProductsListSpec.LIST_TAG).single()
    val rows = bounds(V3DataRowSpec.ROW_TAG)
    val tall = rows.filter { dp(it.height) > 72.5f }
    assertTrue("ردیفِ بلندتر از ۷۲dp: ${tall.map { dp(it.height) }}", tall.isEmpty())
    val whole = rows.count { it.top >= list.top - 1f && it.bottom <= list.bottom + 1f }
    assertTrue("ردیف‌های کاملِ دیده‌شده در ۳۶۰×۷۶۰: $whole (فهرست ${dp(list.height)}dp)", whole >= 8)
  }

  @Test fun oneProductIsOneShortRowAndTheGridNeverMakesItAGiantCard() {
    phone("products?view=list", one())
    awaitRows(1)
    assertTrue(dp(bounds(V3DataRowSpec.ROW_TAG).single().height) <= 72.5f)
    //  دو خط: نام، و «۱۲۰ ؋ • ۱۵۰ عدد» (عادی ⇒ بی واژهٔ وضعیت)
    assertTrue(count("شکلات") > 0)
    assertTrue(rule.onAllNodes(hasText("${money(120.0)} ؋", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    phone("products?view=grid", one())
    rule.waitUntil(15_000) { bounds(ProductsListSpec.CELL_TAG).isNotEmpty() }
    val cell = bounds(ProductsListSpec.CELL_TAG).single()
    assertTrue("پهنای کاشیِ تنها: ${dp(cell.width)}dp", dp(cell.width) <= 360f / ProductsListSpec.GRID_COLUMNS)
    assertTrue("بلندیِ کاشیِ تنها: ${dp(cell.height)}dp", dp(cell.height) <= 220f)
  }

  @Test fun theGridHasThreeColumnsOfSmallCells() {
    phone("products?view=grid", StockDemo.full())
    rule.waitUntil(15_000) { bounds(ProductsListSpec.CELL_TAG).size >= 6 }
    val cells = bounds(ProductsListSpec.CELL_TAG)
    val firstRow = cells.filter { kotlin.math.abs(it.top - cells.minOf { c -> c.top }) < 2f }
    assertEquals(ProductsListSpec.GRID_COLUMNS, firstRow.size)
    assertTrue(cells.all { dp(it.width) <= 360f / 3 })
  }

  @Test fun productsHaveNoSummaryCardAndTheFooterKeepsBothNumbers() {
    phone("products", one())
    awaitRows(1)
    //  کارتِ «سود بالقوه» ِ بالا نیست؛ همان دو عدد در خطِ پایانِ فهرست
    assertEquals(0, count(StrProducts.statPotential))
    assertEquals(0, count(StrProducts.statPotentialCaption))
    val footer = StrProducts.listFooter(money(1.0), money(150 * 20.0))
    rule.waitUntil(15_000) { count(footer) > 0 }
    val f = bounds(ProductsListSpec.FOOTER_TAG).single()
    val row = bounds(V3DataRowSpec.ROW_TAG).single()
    assertTrue("خطِ جمع زیرِ ردیف است، نه بالا", f.top >= row.bottom - 1f)
  }

  @Test fun theStockListHasNoSummaryCardOnThePhoneAndTheFooterKeepsTheNumbers() {
    phone("stock", StockDemo.full())
    awaitRows(3)
    assertEquals("کارتِ «ارزشِ تقریبی» ِ بالا", 0, count(StrStock.statValue))
    assertEquals(0, count(StrStock.statCartons))
    phone("stock", one())
    awaitRows(1)
    assertEquals(0, count(StrStock.statValue))
    val footer = StrStock.listFooter(money(1.0), qty(10.0), qty(150.0), money(15_000.0))
    rule.waitUntil(15_000) { count(footer) > 0 }
    assertTrue(bounds(StockListSpec.FOOTER_TAG).single().top >= bounds(V3DataRowSpec.ROW_TAG).single().bottom - 1f)
  }

  @Test fun theNewProductButtonOpensTheSimpleFormWithOnlyTheRequiredFields() {
    phone("products", StockDemo.full())
    awaitRows(3)
    click(StrProducts.newProduct)
    rule.waitUntil(15_000) { fields(StrProducts.name) > 0 }
    assertEquals(1, fields(StrProducts.name))
    assertEquals(1, fields(StrProducts.salePrice))
    //  «بیشتر» بسته: هیچ کادرِ اختیاری و هیچ کادرِ کشویی
    for (label in listOf(StrProducts.purchasePrice, StrNewProduct.code, StrNewProduct.stock, StrStock.made, StrStock.expiry)) assertEquals(label, 0, fields(label))
    assertEquals(0, dropdowns(StrProducts.category) + dropdowns(StrProducts.unit))
    assertEquals(0, count(StrProducts.minStock))
    assertEquals(0, count(StrProducts.addRow))
    val more = rule.onNode(hasTestTag(QuickProductSpec.MORE_TAG)).fetchSemanticsNode()
    assertEquals(StrA11y.collapsed, more.config[SemanticsProperties.StateDescription])
    //  باز ⇒ همهٔ کادرهای اختیاری؛ دسته و واحد کادرِ کشویی (نه دیوارِ تراشه)
    click(hasTestTag(QuickProductSpec.MORE_TAG))
    for (label in listOf(StrProducts.purchasePrice, StrNewProduct.code, StrNewProduct.stock, StrStock.made, StrStock.expiry)) assertEquals(label, 1, fields(label))
    assertEquals(1, dropdowns(StrProducts.category))
    assertEquals(1, dropdowns(StrProducts.unit))
    //  تراشه‌ای برای هر واحد نیست
    assertEquals(0, rule.onAllNodes(hasText("کیلو") and hasClickAction()).fetchSemanticsNodes().size)
  }

  @Test fun theSimpleFormSavesWithNameAndPriceAndRefusesEmptyRequiredFields() {
    phone("products/new", StockDemo.full(), disk = true)
    rule.waitUntil(15_000) { fields(StrProducts.name) > 0 }
    val before = store.data.value.products.size
    click(StrKit.save)
    assertTrue("خطای درجای نام و قیمتِ فروش", count(StrKit.required) >= 2)
    assertEquals(before, store.data.value.products.size)
    rule.onAllNodes(hasSetTextAction() and hasContentDescription(StrProducts.name))[0].performTextInput("چای سیاه")
    rule.onAllNodes(hasSetTextAction() and hasContentDescription(StrProducts.salePrice))[0].performTextInput("85")
    rule.waitForIdle()
    click(StrKit.save)
    rule.waitUntil(15_000) { store.data.value.products.size == before + 1 }
    val p = store.data.value.products.single { it.name == "چای سیاه" }
    assertEquals(85.0, p.salePrice, 0.0)
    assertEquals(0.0, p.purchasePrice, 0.0)
    //  واحدِ خالی ⇐ نخستین واحدِ دکان
    assertEquals(StockDemo.full().productUnits.first(), p.unit)
    assertEquals("", p.category)
    assertTrue(store.data.value.warehouseEntries.none { it.productId == p.id })
  }

  @Test fun aBrokenOptionalFieldOpensMoreAndShowsItsError() {
    phone("products/new", StockDemo.full())
    rule.waitUntil(15_000) { fields(StrProducts.name) > 0 }
    rule.onAllNodes(hasSetTextAction() and hasContentDescription(StrProducts.name))[0].performTextInput("ماست")
    rule.onAllNodes(hasSetTextAction() and hasContentDescription(StrProducts.salePrice))[0].performTextInput("60")
    click(hasTestTag(QuickProductSpec.MORE_TAG))
    //  تاریخ بی موجودی، سپس «بیشتر» بسته و «ذخیره» ⇒ «بیشتر» باز و خطای «موجودی»
    rule.onAllNodes(hasSetTextAction() and hasContentDescription(StrStock.expiry))[0].performTextInput("14051210")
    click(hasTestTag(QuickProductSpec.MORE_TAG))
    val n = store.data.value.products.size
    click(StrKit.save)
    rule.waitUntil(15_000) { count(StrNewProduct.stockForDates) > 0 }
    assertEquals(StrA11y.expanded, rule.onNode(hasTestTag(QuickProductSpec.MORE_TAG)).fetchSemanticsNode().config[SemanticsProperties.StateDescription])
    assertEquals(n, store.data.value.products.size)
  }

  @Test fun bulkEntryIsInTheOverflowOfProductsAndStock() {
    phone("products", StockDemo.full())
    awaitRows(3)
    click(StrKit.more)
    click(StrReach.bulkProducts)
    rule.waitUntil(15_000) { count(StrProducts.addRow) > 0 }
    assertTrue(count(StrProducts.saveAll) > 0)
    phone("stock", StockDemo.full())
    awaitRows(3)
    click(StrKit.more)
    click(StrReach.bulkProducts)
    //  گوشی: همان فرمِ چندردیفیِ کالاها (کادرِ کشویی)، نه برگهٔ پیشین با دیوارِ تراشهٔ واحد
    rule.waitUntil(15_000) { count(StrProducts.addRow) > 0 }
    assertEquals(0, count(ir.vil3ntec.tohid.core.i18n.StrCatalogPages.newProductsSub))
  }
}
