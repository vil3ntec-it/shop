package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrNewProduct
import ir.vil3ntec.tohid.core.i18n.StrPriceCheck
import ir.vil3ntec.tohid.core.i18n.StrProductSuggest
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.OpeningStock
import ir.vil3ntec.tohid.data.ProductSuggestPref
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.products.NewProductsScreen
import ir.vil3ntec.tohid.ui.v3.products.ProductFormNav
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  فرمِ جمع‌وجورِ «کالای جدید» (۱۴۰۵/۰۸/۲۴) در پنجرهٔ واقعیِ Compose، با دفترِ روی پوشهٔ دادهٔ آزمون:
 *   • «حداقلِ موجودی» و «قیمتِ عمده» نیستند (حتی با «لیستِ قیمت در صندوق» ِ روشن)؛
 *   • دسته و واحد کادرِ کشویی‌اند و «+ تازه» از درونِ همان فهرست کار می‌کند؛
 *   • تاریخ‌ها با جداکنندهٔ خودکار نوشته می‌شوند، تولید پس از انقضا درجا رد می‌شود؛
 *   • «ذخیرهٔ همه» کالا و موجودیِ آغاز را یک‌جا می‌نویسد و دو بار زدن یکی است؛ ردیف‌های چندتایی و «افزودن از متن» همان‌اند.
 */
class NewProductFormUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val seedData = ShopData(productCategories = listOf("خوراکی", "روغن"), productUnits = listOf("عدد", "کارتن"))
  private lateinit var store: ShopStore
  private var saved: List<String>? = null

  private fun clean() {
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
    ShopRole.rememberPolicy(ctx, null)
    //  دو کلیدِ همین دستگاه به پیش‌فرض (آزمون‌های دیگر همان پیش‌فرض را می‌خوانند)
    ctx.getSharedPreferences("tohid-policy", 0).edit().remove("price_lists").remove("product_suggest").commit()
  }

  @Before fun up() {
    I18n.source = { Lang.FA }
    clean()
    //  تراشهٔ «دستهٔ پیشنهادی» کنارِ فهرستِ کشویی نیاید (آزمونِ «افزودن از متن» روشنش می‌کند)
    ProductSuggestPref.set(ctx, false)
  }

  @After fun down() = clean()

  private fun show() {
    store = ShopStore(ctx).also { s -> runBlocking { s.load(); assertTrue(s.save(seedData)) } }
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          val toasts = rememberV3Toasts(remember { SnackbarHostState() })
          CompositionLocalProvider(LocalV3Toasts provides toasts) {
            val live by store.data.collectAsState()
            NewProductsScreen(store, live, toasts, null, ProductFormNav(back = {}, scan = {})) { saved = it }
          }
        }
      }
    }
    rule.waitForIdle()
  }

  private fun field(label: String) = rule.onAllNodes(hasSetTextAction() and hasContentDescription(label))[0]

  private fun type(label: String, value: String) {
    field(label).performTextInput(value)
    rule.waitForIdle()
  }

  private fun click(text: String) {
    rule.onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private val dropdown = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList)

  private fun open(label: String) {
    rule.onNode(dropdown and hasContentDescription(label)).performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private fun shows(label: String, value: String) =
    rule.onNode(dropdown and hasContentDescription(label)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))

  private fun count(text: String) = rule.onAllNodes(hasText(text) or hasContentDescription(text)).fetchSemanticsNodes().size

  private fun fill(name: String, buy: String = "2400", sell: String = "2650", category: String = "روغن", unit: String = "کارتن") {
    type(StrProducts.name, name)
    open(StrProducts.category); click(category)
    open(StrProducts.unit); click(unit)
    type(StrProducts.purchasePrice, buy)
    type(StrProducts.salePrice, sell)
  }

  private fun awaitProducts(n: Int) = rule.waitUntil(15_000) { store.data.value.products.size == n }

  @Test fun theFormHasNoMinimumStockAndNoWholesaleEvenWithPriceLists() {
    PriceListPrefs.set(ctx, true)
    show()
    assertEquals(0, count(StrProducts.minStock))
    assertEquals(0, count(StrPriceCheck.wholesale))
    assertEquals(0, count(StrProducts.wholesaleHelper))
    //  کادرهایی که می‌ماند: نام، دو قیمت، کد/بارکد، و «اختیاری» (موجودی و دو تاریخ)
    for (label in listOf(StrProducts.name, StrProducts.purchasePrice, StrProducts.salePrice, StrNewProduct.code, StrNewProduct.stock, StrStock.made, StrStock.expiry)) {
      assertEquals(label, 1, rule.onAllNodes(hasSetTextAction() and hasContentDescription(label)).fetchSemanticsNodes().size)
    }
    assertTrue(count(StrNewProduct.optional) > 0)
  }

  @Test fun categoryAndUnitAreDropdownsAndAddNewWorksFromInsideTheList() {
    show()
    shows(StrProducts.category, StrNewProduct.choose)
    open(StrProducts.category)
    //  فهرست: دسته‌های دکان و «+ دستهٔ تازه» تهِ همان
    assertTrue(count("خوراکی") > 0 && count(StrProducts.newCategory) > 0)
    click("خوراکی")
    shows(StrProducts.category, "خوراکی")
    open(StrProducts.category); click(StrProducts.newCategory)
    type(StrProducts.category, "لبنیات")
    click(StrKit.add)
    shows(StrProducts.category, "لبنیات")
    open(StrProducts.unit); click(StrProducts.newUnit)
    type(StrProducts.unit, "بوجی")
    click(StrKit.add)
    shows(StrProducts.unit, "بوجی")
    type(StrProducts.name, "پنیر")
    type(StrProducts.purchasePrice, "300")
    type(StrProducts.salePrice, "350")
    click(StrProducts.saveAll)
    awaitProducts(1)
    val p = store.data.value.products.single()
    assertEquals("لبنیات" to "بوجی", p.category to p.unit)
    assertTrue("لبنیات" in store.data.value.productCategories && "بوجی" in store.data.value.productUnits)
  }

  @Test fun datesAndOpeningStockSaveOneProductAndOneEntryAndADoubleTapSavesOnce() {
    show()
    fill("روغن آفتاب ۵ لیتر")
    type(StrNewProduct.stock, "2")
    type(StrStock.made, "14050610")
    type(StrStock.expiry, "14051210")
    //  جداکنندهٔ خودکار: هشت رقم ⇒ «۱۴۰۵/۱۲/۱۰»
    assertTrue(count("1405/12/10") > 0)
    assertTrue(count(StrNewProduct.stockHelper("2", "کارتن", "4,800")) > 0)
    //  دو زدنِ پیاپی پیش از هر بازسازیِ صفحه: کارِ همان دکمه دو بار در یک قاب
    val save = rule.onAllNodes(hasText(StrProducts.saveAll) and hasClickAction())[0].fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
    rule.runOnIdle { save(); save() }
    awaitProducts(1)
    rule.waitUntil(15_000) { saved != null }
    val d = store.data.value
    val p = d.products.single()
    val e = d.warehouseEntries.single()
    assertEquals(p.id, e.productId)
    assertEquals(2.0, e.units, 0.0)
    assertEquals("کارتن", e.unit)
    assertEquals(2400.0, e.price, 0.0)
    assertEquals("2026-09-01" to "2027-03-01", e.productionDate to e.expiryDate)
    assertEquals(OpeningStock.NOTE, e.notes)
    assertEquals(1, d.stockMovements.size)
    assertEquals(listOf(p.id), saved)
    assertEquals(0.0 to 0.0, p.minStock to p.wholesalePrice)
  }

  @Test fun productionAfterExpiryIsAnInlineErrorAndNothingIsSaved() {
    show()
    fill("ماست")
    type(StrNewProduct.stock, "5")
    type(StrStock.made, "14051220")
    type(StrStock.expiry, "14051210")
    click(StrProducts.saveAll)
    assertTrue(count(StrGoods.madeAfterExpiry) > 0)
    //  تاریخ بی موجودی هم درجا: «تاریخ روی همین موجودی می‌نشیند»
    field(StrNewProduct.stock).performTextClearance()
    rule.waitForIdle()
    assertTrue(count(StrNewProduct.stockForDates) > 0)
    rule.waitForIdle()
    assertTrue(store.data.value.products.isEmpty())
  }

  @Test fun batchRowsAndAddFromTextStillWork() {
    ProductSuggestPref.set(ctx, true)
    show()
    //  نام‌هایی که واژهٔ هیچ دسته‌ای را ندارند ⇒ تراشهٔ «دستهٔ پیشنهادی» (همین کلید) کنارِ فهرست نمی‌آید
    fill("برنج کابلی", buy = "90", sell = "110", category = "خوراکی", unit = "عدد")
    click(StrProducts.addRow)
    fill("چای سبز", buy = "80", sell = "100", category = "خوراکی", unit = "عدد")
    assertTrue(count(StrProducts.readySummary(2, 2)) > 0)
    //  «افزودن از متن»: هر سطر یک ردیفِ تازه فقط با نام
    click(StrProductSuggest.fromText)
    type(StrProductSuggest.pasteLabel, "برنج سیلا\nقند")
    click(StrProductSuggest.addRows(2))
    assertTrue(count(StrProducts.readySummary(2, 4)) > 0)
    //  ردیفِ نیمه‌پر ⇒ به همان ردیف می‌پرد و هیچ چیز نوشته نمی‌شود
    click(StrProducts.saveAll)
    assertTrue(count(StrProducts.rowIncomplete(3)) > 0)
    assertTrue(store.data.value.products.isEmpty())
  }
}
