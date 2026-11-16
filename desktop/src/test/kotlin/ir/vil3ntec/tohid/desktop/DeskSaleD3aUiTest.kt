package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeskWork
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.sync.v1.HeartbeatCache
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.sync.v1.SoftLock
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.desktop.perf.PosSpeed
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.ui.components.pos.PosProductCallbacks
import ir.vil3ntec.tohid.ui.screens.DeskCatalogGrid
import ir.vil3ntec.tohid.ui.screens.DeskCatalogTable
import ir.vil3ntec.tohid.ui.screens.DeskSaleScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  صندوقِ **کامپیوتر** روی `ui/desk` (`claude/e-desk-d3a`): کارهای پیشین همه هنوز دیده و زده می‌شوند (کادرِ اسکن، نمای
 *  جدول/کارت، دسته، قلمِ آزاد، خالی کردن، مشتری، روشِ پرداخت، ثبت، چاپِ آخر، شمارنده و ویرایشِ ردیف، نوارِ کلیدها)، راهِ
 *  صفحه‌کلید «اسکن ⇐ ۳* ⇐ + ⇐ F9» یک فروشِ نقد می‌نویسد، کاتالوگِ ۲۰ هزار کالا تنبل کشیده می‌شود و کاشیِ کاتالوگ موجودیِ
 *  قابل‌فروش را با همان واژه‌های پیشین می‌گوید.
 */
@OptIn(ExperimentalTestApi::class)
class DeskSaleD3aUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val draft get() = File(ctx.filesDir, CartStore.FILE)
  private val roles get() = ctx.getSharedPreferences("tohid-ledger", 0)
  private var roleWas: String? = null

  private fun clean() {
    CartStore.awaitWrites()
    draft.delete()
    HeartbeatCache(ctx).forget()
    SoftLock.refresh(ctx)
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
  }

  @Before fun before() {
    clean()
    I18n.source = { Lang.FA }
    roleWas = roles.getString("shop_role", null)
    ShopRole.remember(ctx, ShopRole.OWNER)
  }

  @After fun after() {
    roles.edit().apply { if (roleWas == null) remove("shop_role") else putString("shop_role", roleWas) }.commit()
    ShopRole.policyVersion.value++
    clean()
  }

  private val milk = Product(id = "d3-milk", name = "شیرِ پاکتیِ آزمون", unit = "عدد", salePrice = 45.0, category = "لبنیات", barcodes = listOf("6261000000011"))
  private val tea = Product(id = "d3-tea", name = "چای سبزِ آزمون", unit = "بسته", salePrice = 80.0, category = "خوراکی", barcodes = listOf("6261000000028"))
  private val rice = Product(id = "d3-rice", name = "برنجِ آزمون", unit = "کیلو", salePrice = 120.0, category = "خوراکی", barcodes = listOf("6261000000035"))

  private fun shop(): ShopData = ShopData(
    products = listOf(milk, tea, rice),
    productCategories = listOf("خوراکی", "لبنیات"),
    warehouseEntries = listOf(milk, tea, rice).map { WarehouseEntry(id = "w-${it.id}", productId = it.id, units = 50.0, unit = it.unit, date = "2026-11-01") },
    debtors = listOf(Debtor(id = "d3-c1", name = "مشتریِ آزمون")),
  )

  private class Till(val store: ShopStore, val cart: CartStore, val snackbar: SnackbarHostState)

  /**
   *  [memory]: دفترِ حافظه‌ای (`RouteUiHarness.loadedStore`)، بی ذخیره روی دیسک — برای کاتالوگِ ۲۰ هزار کالا که فقط کشیدن را
   *  می‌سنجد. ذخیرهٔ آن روی `ShopStore` ِ مشترکِ آزمون‌ها ~۱۲۳ هزار INSERT (و با ذخیرهٔ بعدی همان‌قدر DELETE) در صفِ همگام‌سازیِ
   *  مشترک می‌گذاشت (فایلِ ۸۰ مگابایتی) و `push` ِ آزمون‌های بعدی (`TillReceiveStaleTest`) دیگر روی حافظه نمی‌نشست.
   */
  private fun open(d: ShopData, memory: Boolean = false): Till {
    val store = if (memory) RouteUiHarness.loadedStore(d)
    else ShopStore(ctx).also { runBlocking { it.load() } }.also { assertTrue(runBlocking { it.save(d) }) }
    val t = Till(store, CartStore(ctx), SnackbarHostState())
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1366, 768),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          val live by t.store.data.collectAsState()
          DeskSaleScreen(store = t.store, cartStore = t.cart, d = live, snackbar = t.snackbar) {}
        }
      }
    }
    rule.waitForIdle()
    return t
  }

  private fun scanBox(): SemanticsNodeInteraction {
    val box = hasSetTextAction() and (hasText(StrSale.deskSearchHint) or hasAnyDescendant(hasText(StrSale.deskSearchHint)))
    rule.waitUntil(10_000) { rule.onAllNodes(box, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    val id = rule.onAllNodes(box, useUnmergedTree = true).fetchSemanticsNodes().first().id
    val scan = rule.onNode(SemanticsMatcher("کادرِ اسکن") { it.id == id })
    scan.performClick()
    rule.waitForIdle()
    scan.assertIsFocused()
    return scan
  }

  /** گره‌ای با کنشِ زدن به همین نامِ دسترسی */
  private fun clickLabel(label: String) = SemanticsMatcher("زدن: $label") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

  private fun count(m: SemanticsMatcher) = rule.onAllNodes(m, useUnmergedTree = true).fetchSemanticsNodes().size

  private fun tap(m: SemanticsMatcher) {
    rule.onAllNodes(m, useUnmergedTree = true)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private fun shown(text: String) = rule.onAllNodes(hasText(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  @Test fun `صفحه‌کلید — اسکن، ۳ ضرب، + و F9 ⇒ فروشِ نقد ثبت و سبد خالی و فوکوس در کادر`() {
    val t = open(shop())
    val scan = scanBox()
    val before = t.store.data.value.sales.size
    scan.performTextInput(milk.barcodes.single()); scan.performKeyInput { pressKey(Key.Enter) }
    scan.performTextInput("3*" + tea.barcodes.single()); scan.performKeyInput { pressKey(Key.Enter) }
    rule.waitForIdle()
    //  کادر خالی و ردیفِ چای انتخاب‌شده ⇒ «+» یکی بیشتر (همان `PosKeys.field`)
    scan.performKeyInput { pressKey(Key.NumPadAdd) }
    rule.waitForIdle()
    assertEquals(listOf(milk.id to 1.0, tea.id to 4.0), t.cart.lines.value.map { it.productId to it.quantity })
    //  جدولِ سبد: نام، جمعِ ردیف و «مبلغِ قابلِ پرداخت»
    assertTrue(shown(tea.name))
    assertTrue(shown(ir.vil3ntec.tohid.money(4 * 80.0)))
    assertTrue(shown(StrSale.payable))
    scan.performKeyInput { pressKey(Key.F9) }
    rule.waitUntil(20_000) { t.store.data.value.sales.size == before + 1 }
    val sale = t.store.data.value.sales.last()
    assertEquals(45.0 + 4 * 80.0, sale.finalTotal, 0.001)
    assertEquals("cash", sale.paymentMethod)
    rule.waitUntil(30_000) { t.cart.lines.value.isEmpty() }
    rule.waitForIdle()
    scan.assertIsFocused()
    //  «چاپِ فاکتورِ آخر» پس از فروش زدنی است و فاکتور را باز می‌کند
    tap(clickLabel(StrPos.printLastInvoice))
    assertTrue("فاکتورِ آخر باز نشد", shown(tea.name))
  }

  @Test fun `هر کارِ پیشینِ صندوق هنوز دیده و زده می‌شود`() {
    val t = open(shop())
    assertTrue(t.cart.set(listOf(SalesEngine.CartLine(milk.id, 2.0))))
    rule.waitForIdle()
    scanBox()
    //  نمای جدول/کارت، دسته‌ها و نوارِ کلیدها
    for (label in listOf(Str.tableView, Str.cardView, Str.all, StrDeskWork.keyCustomer, StrDeskWork.keyFreeItem, StrDeskWork.keyEditRow, StrDeskWork.keyAll)) {
      assertTrue("«$label» نیست", shown(label))
    }
    //  دکمه‌های سبد، مشتری، روش‌ها، ثبت و چاپ — هر کدام با نامِ دسترسی و زدنی
    for (label in listOf(StrSale.freeItemAlt, StrPos.emptyCart, StrPos.pickCustomer, StrPos.recordSaleBtn, StrPos.printLastInvoice, StrSale.oneMore(milk.name), StrSale.oneLess(milk.name), StrSale.editQtyPrice)) {
      assertTrue("«$label» زدنی نیست", count(clickLabel(label)) > 0)
    }
    assertTrue(shown(Str.cashSale) && shown(Str.credit))
    //  + شمارنده ⇒ ۳
    tap(clickLabel(StrSale.oneMore(milk.name)))
    assertEquals(3.0, t.cart.lines.value.single().quantity, 0.0)
    //  نمای کارتی ⇒ کارت‌ها با «افزودن به سبد»؛ زدنِ کارتِ چای ⇒ ردیفِ تازه
    tap(hasText(Str.cardView) and hasClickAction())
    tap(SemanticsMatcher("کارتِ چای") { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(tea.name) } == true && n.config.getOrNull(SemanticsActions.OnClick)?.label == StrSale.addToCart })
    assertEquals(setOf(milk.id, tea.id), t.cart.lines.value.map { it.productId }.toSet())
    tap(hasText(Str.tableView) and hasClickAction())
    //  قلمِ آزاد ⇒ پنجرهٔ خودش
    tap(clickLabel(StrSale.freeItemAlt))
    assertTrue(shown(StrSale.freeItem))
    rule.onAllNodes(hasText(Str.cancel) and hasClickAction())[0].performClick(); rule.waitForIdle()
    //  ویرایشِ ردیف (زدنِ قیمت) ⇒ برگهٔ تعداد/قیمت
    tap(clickLabel(StrSale.editQtyPrice))
    assertTrue(shown(milk.name))
    rule.onAllNodes(hasText(Str.cancel) and hasClickAction())[0].performClick(); rule.waitForIdle()
    //  مشتری ⇒ فهرستِ مشتری‌ها
    tap(clickLabel(StrPos.pickCustomer))
    assertTrue(shown(StrSalePages.chooseDebtor))
    rule.onAllNodes(hasText("مشتریِ آزمون", substring = true) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle()
    rule.waitUntil(5_000) { t.cart.debtorId.value == "d3-c1" }
    //  مشتریِ انتخاب‌شده ⇒ «برداشتنِ مشتری»
    assertTrue(count(clickLabel(StrPos.removeCustomer)) > 0 || rule.onAllNodes(SemanticsMatcher("×") { n -> n.config.getOrNull(SemanticsProperties.ContentDescription)?.contains(StrPos.removeCustomer) == true }).fetchSemanticsNodes().isNotEmpty())
    //  خالی کردنِ سبد ⇒ پرسشِ تأیید
    tap(clickLabel(StrPos.emptyCart))
    assertTrue(shown(StrSale.newCartQ))
  }

  @Test fun `کاتالوگِ ۲۰ هزار کالا تنبل کشیده می‌شود`() {
    val s = PosSpeed.shop(products = 20_000, sales = 10)
    open(s.d, memory = true)
    scanBox()
    rule.waitForIdle()
    val rows = count(clickLabel(StrSale.addToCart))
    assertTrue("ردیف‌های کشیده‌شده: $rows", rows in 1..120)
  }

  @Test fun `کاشی و ردیفِ کاتالوگ موجودیِ قابل‌فروش را با همان واژه‌ها می‌گویند`() {
    val d = ShopData(
      products = listOf(Product(id = "cola", name = "نوشابه", unit = "عدد", salePrice = 60.0), Product(id = "rice", name = "برنج", unit = "کیلو", salePrice = 120.0, minStock = 5.0)),
      warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "cola", units = 5.0), WarehouseEntry(id = "w2", productId = "rice", units = 4.0)),
    )
    //  دو نوشابه کنار رفته (نگه‌داشته/رزرو) ⇒ سه؛ برنج زیرِ حد ⇒ «کم»
    val cb = PosProductCallbacks(stockOf = { if (it == "cola") 3.0 else ShopStore.stock(d, it) }, qtyOf = { 0.0 }, onAdd = {})
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          androidx.compose.foundation.layout.Column {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.height(androidx.compose.ui.unit.Dp(240f))) {
              DeskCatalogGrid(d.products, -1, false, cb, androidx.compose.foundation.lazy.grid.rememberLazyGridState(), androidx.compose.ui.unit.Dp(160f))
            }
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.height(androidx.compose.ui.unit.Dp(240f))) {
              DeskCatalogTable(d.products, -1, false, cb, androidx.compose.foundation.lazy.rememberLazyListState())
            }
          }
        }
      }
    }
    rule.waitForIdle()
    //  هر دو نما (کارت و جدول) همان واژه را دارند
    assertEquals(2, rule.onAllNodes(hasText(StrSale.inStockQty(qty(3.0) + " عدد")), useUnmergedTree = true).fetchSemanticsNodes().size)
    assertEquals(2, rule.onAllNodes(hasText(StrSale.lowQty(qty(4.0) + " کیلو")), useUnmergedTree = true).fetchSemanticsNodes().size)
  }
}
