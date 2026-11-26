package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeliver
import ir.vil3ntec.tohid.core.i18n.StrGiftCard
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrLayaway
import ir.vil3ntec.tohid.core.i18n.StrLineSeller
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.core.i18n.StrSpecialOrder
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.QuoteStore
import ir.vil3ntec.tohid.data.Quotes
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.pos.PosNotesPrefs
import ir.vil3ntec.tohid.ui.components.pos.PriceListPrefs
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.nav.ShellFrame
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.sales.cart.TillSpec
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 *  صندوقِ گوشی پس از دستورِ صاحب‌کار (۱۴۰۵/۰۸/۲۴: «بخش فروش اون نوارها نباشن … سبد صدها دکمه داره … سبد سیاه است اما
 *  دکمه‌های پایین سفید، خیلی مسخره شده») در پنجرهٔ واقعیِ Compose — `SaleRoute` با ابزارهای واقعیِ سبد درونِ همان قراردادِ پوسته:
 *   • حالتِ تمرکز: نوارِ شناورِ برنامه نیست، برگشتِ سربرگ کار می‌کند و نوارِ سبد تنها چیزِ پایین است؛
 *   • نوار و برگهٔ سبد رنگِ سطحِ تم را دارند (روز روشن، شب سطحِ برآمدهٔ تیره) — نه تختهٔ سیاه؛
 *   • سبدِ باز پیش‌فرض دست‌بالا ۵ چیزِ زدنی (جدا از قلم‌ها) دارد و هیچ‌کدام از ابزارها رویش نیست؛
 *   • هر ابزاری که روشن است در «…» هست و همان پنجره را باز می‌کند؛ ابزارِ خاموش و فهرستِ خالی هیچ ردیفی ندارند.
 */
@OptIn(ExperimentalTestApi::class)
class SaleFocusUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val data = ShopData(
    products = listOf(
      Product("p-choc", "شکلات", "شکلات‌ها", "عدد", salePrice = 120.0, wholesalePrice = 100.0),
      Product("p-juice", "آب‌میوهٔ انار", "آبمیوه", "عدد", salePrice = 60.0),
      Product("p-biscuit", "بیسکویت", "خوراکی", "بسته", salePrice = 45.0),
    ),
    productCategories = listOf("آبمیوه", "شکلات‌ها", "خوراکی"),
    warehouseEntries = listOf("p-choc", "p-juice", "p-biscuit").map { WarehouseEntry(id = "w-$it", productId = it, units = 50.0) },
  )
  private val three = listOf(CartLine("p-choc", 3.0), CartLine("p-juice", 1.0), CartLine("p-biscuit", 2.0))
  private lateinit var cart: CartStore
  private lateinit var nav: V3Nav

  @Before fun up() {
    TillShellHarness.reset()
    ShopRole.remember(ctx, ShopRole.OWNER)
    I18n.source = { Lang.FA }
  }

  @After fun down() {
    runCatching { cart.clear() }
    TillShellHarness.reset()
  }

  private fun show(lines: List<CartLine>, dark: Boolean = false) {
    cart = CartStore(ctx)
    cart.clear()
    if (lines.isNotEmpty()) assertTrue(cart.set(lines))
    nav = TillShellHarness.nav()
    val store = RouteUiHarness.loadedStore(data)
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) { TillShellHarness.Shell(nav, store, cart, dark) }
      }
    }
    rule.waitForIdle()
  }

  private fun nodes(m: SemanticsMatcher): List<SemanticsNode> = rule.onAllNodes(m, useUnmergedTree = false).fetchSemanticsNodes()
  private fun has(m: SemanticsMatcher) = nodes(m).isNotEmpty()
  private fun click(m: SemanticsMatcher) {
    rule.onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.mainClock.advanceTimeBy(1_500)
    rule.waitForIdle()
  }

  private val openCart = SemanticsMatcher("باز کردنِ سبد") { it.config.getOrNull(SemanticsActions.OnClick)?.label == StrSale.openCart }
  private val inSheet = hasAnyAncestor(hasTestTag(TillSpec.SHEET_TAG))
  private val more = hasContentDescription(StrSale.cartMore) and hasClickAction()

  /** تبِ «خانه» ِ نوارِ شناورِ برنامه */
  private val navTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasContentDescription(Str.home)

  @Test
  fun `حالتِ تمرکز — صندوق نوارِ شناور ندارد و برگشتِ سربرگ به خانه می‌رود`() {
    show(three)
    assertTrue("قراردادِ پوسته: صندوق بی نوار", ShellFrame.bar(true, "sale", TillShellHarness.navRoutes, ir.vil3ntec.tohid.ui.v3.nav.V3Modules.sections, false, false, false, false) == ShellFrame.Bar.NONE)
    assertFalse("نوارِ شناورِ برنامه روی صندوق کشیده نمی‌شود", has(navTab))
    assertTrue("نوارِ سبد هست", has(hasTestTag(TillSpec.BAR_TAG)))
    //  تنها چیزِ پایین نوارِ سبد است: هر دکمهٔ نیمهٔ پایین درونِ همان کارت است (جدا از کالاهای فهرست)
    val bar = nodes(hasTestTag(TillSpec.BAR_TAG)).single().boundsInRoot
    val half = rule.onRoot().fetchSemanticsNode().boundsInRoot.height / 2
    val low = nodes(hasClickAction()).filter { it.boundsInRoot.top > half && it.boundsInRoot.top >= bar.top - 1f }
    assertEquals("پایین: اسکن، سبد، پرداخت", 3, low.size)
    assertTrue(low.all { it.boundsInRoot.top >= bar.top - 1f && it.boundsInRoot.bottom <= bar.bottom + 1f })
    //  برگشتِ سربرگ: صندوق از خانه باز شده بود ⇒ خانه
    val back = hasContentDescription(StrKit.back) and hasClickAction()
    assertTrue("پیکانِ برگشت در سربرگ", nodes(back).single().boundsInRoot.top < half)
    assertEquals("sale", nav.current.substringBefore('?'))
    click(back)
    assertEquals("برگشت ⇒ خانه", "home", nav.current)
  }

  @Test
  fun `کشیدن از وسط کالاها سبد را باز و کشیدن روی ردیف آن را می‌بندد`() {
    show(three)
    val catalog = hasTestTag(TillSpec.CATALOG_TAG)
    val openSheet = hasTestTag(TillSpec.SHEET_TAG)
    val closed = nodes(catalog).single().boundsInRoot.bottom
    val start = nodes(catalog).single().boundsInRoot.center
    val rootHeight = rule.onRoot().fetchSemanticsNode().boundsInRoot.height

    rule.onRoot().performTouchInput {
      down(start)
      moveTo(start - Offset(0f, rootHeight * 0.1f))
    }
    rule.waitForIdle()
    assertTrue("صفحه قبل از رها کردن انگشت بالا می‌رود", nodes(catalog).single().boundsInRoot.bottom < closed)
    assertTrue("سبد زیر صفحه در حال باز شدن است", has(openSheet))

    rule.onRoot().performTouchInput {
      moveTo(start - Offset(0f, rootHeight * 0.37f))
      up()
    }
    rule.mainClock.advanceTimeBy(1_500)
    rule.waitForIdle()
    assertTrue("سبد باز می‌ماند", has(hasText(StrSale.pay) and inSheet))
    val grip = nodes(hasTestTag(TillSpec.ROW_TAG)).first().boundsInRoot.center
    rule.onRoot().performTouchInput {
      swipe(grip, grip + Offset(0f, rootHeight * 0.52f), durationMillis = 390)
    }
    rule.mainClock.advanceTimeBy(1_500)
    rule.waitForIdle()
    assertFalse("کشیدن به پایین سبد را می‌بندد", has(openSheet))
    assertEquals("صفحه فروش به جایش برمی‌گردد", closed, nodes(catalog).single().boundsInRoot.bottom, 1f)
    assertEquals("کشیدن تعداد قلم را عوض نمی‌کند", three, cart.lines.value)
  }

  @Test
  fun emptyCatalogSwipeDoesNotRevealCart() {
    show(emptyList())
    rule.onRoot().performTouchInput {
      swipe(center, center - Offset(0f, height * 0.35f), durationMillis = 300)
    }
    rule.waitForIdle()
    assertFalse(has(hasTestTag(TillSpec.SHEET_TAG)))
    assertTrue(cart.lines.value.isEmpty())
  }

  @Test
  fun `نمای سبد روی زمینه تیره در تم روز`() = surfaces(dark = false)

  @Test
  fun `نمای سبد روی زمینه تیره در تم شب`() = surfaces(dark = true)

  private fun surfaces(dark: Boolean) {
    show(three, dark)
    val want = ir.vil3ntec.tohid.ui.theme.shopColors(ir.vil3ntec.tohid.ui.theme.ShopTheme.IVORY, true).backgroundSubtle
    assertClose("نوارِ سبد", want, pixelNearStart(TillSpec.BAR_TAG))
    val catalog = hasTestTag(TillSpec.CATALOG_TAG)
    val closedBottom = nodes(catalog).single().boundsInRoot.bottom
    click(openCart)
    rule.mainClock.advanceTimeBy(1_500)
    rule.waitForIdle()
    assertTrue("با لمس سبد، فهرست کالاها در همان صفحه بالا می‌رود", nodes(catalog).single().boundsInRoot.bottom < closedBottom)
    assertTrue(has(hasTestTag(TillSpec.SHEET_TAG)))
    val sheet = pixelNearStart(TillSpec.SHEET_TAG, fromTop = 40f)
    assertClose("برگهٔ سبد", want, sheet)
    assertTrue("سطح سبد تیره است", luminance(sheet) < 0.1f)
  }

  @Test
  fun `سبدِ باز پیش‌فرض دست‌بالا پنج چیزِ زدنی دارد و هیچ ابزاری رویش نیست`() {
    TillShellHarness.allTools()
    show(three)
    click(openCart)
    assertTrue(has(hasTestTag(TillSpec.SHEET_TAG)))
    assertEquals("سه قلم با −/+", 3, nodes(hasTestTag(TillSpec.ROW_TAG)).size)
    val primary = nodes(hasClickAction() and inSheet and !hasAnyAncestor(hasTestTag(TillSpec.ROW_TAG)) and !hasTestTag(TillSpec.ROW_TAG) and !hasContentDescription(StrSale.closeCart))
    assertTrue("چیزهای اصلیِ برگه: ${primary.size}", primary.size <= 5)
    assertTrue("«…»", has(more))
    assertTrue("مشتری در یک خط", has(hasText(StrSale.walkIn) and inSheet))
    assertTrue("«پرداخت» در برگه", has(hasText(StrSale.pay) and inSheet))
    for (gone in listOf(StrPos.quoteFromCart, StrSpecialOrder.newOrder, StrDeliver.button, StrSalePages.invoiceNote, StrGiftCard.sell, StrLayaway.newLayaway, StrPos.priceListRetailChip, StrSale.pickDebtorForCredit)) {
      assertFalse("روی برگه نیست: $gone", has(hasText(gone, substring = true)))
    }
  }

  @Test
  fun `هر ابزارِ روشن در «…» هست و همان پنجره را باز می‌کند`() {
    TillShellHarness.allTools()
    //  یک پیش‌فاکتورِ ذخیره‌شده ⇒ ردیفِ «پیش‌فاکتورها (۱)»
    QuoteStore.update(ctx) { b -> (Quotes.create(b, data, three, null, null, ir.vil3ntec.tohid.todayIso(), 7, 1L, "q-1", "آزمون") as Quotes.Result.Ok).book }
    show(three)
    click(openCart)
    click(more)
    val titles = listOf(
      StrPos.newInvoice, StrSalePages.invoiceNote, StrSale.wholesaleOn, StrLineSeller.button, StrPos.quoteFromCart,
      StrLayaway.newLayaway, StrDeliver.button, StrSalePages.receiveTitle, StrSpecialOrder.newOrder, StrPos.quoteList(1),
      StrGiftCard.sell, StrGiftCard.check, StrGiftCard.liability,
    )
    //  پیش‌فاکتورها از دیسک خوانده می‌شوند (`QuoteStore.read` بیرون از رشتهٔ رابط)
    rule.waitUntil(10_000) { rule.mainClock.advanceTimeBy(200); has(hasText(StrPos.quoteList(1))) }
    for (t in titles) assertTrue("«…» ردیفِ «$t» را دارد", has(hasText(t) and hasClickAction()))
    //  هر ردیف توضیحِ یک‌خطی دارد
    for (h in listOf(StrSale.hintQuoteNew, StrSale.hintPriceList, StrSale.hintDeliverLater, StrSalePages.printedOnReceipt)) assertTrue(h, has(hasText(h, substring = true)))
    //  «یادداشتِ فاکتور» ⇒ همان پنجرهٔ یادداشت
    click(hasText(StrSalePages.invoiceNote) and hasClickAction())
    assertTrue("پنجرهٔ یادداشت", has(hasText(StrSalePages.record)))
  }

  @Test
  fun `لیستِ قیمت از «…» عوض می‌شود و فقط آن‌وقت نشانِ کم‌رنگ کنارِ جمع می‌آید`() {
    PriceListPrefs.set(ctx, true)
    show(three)
    click(openCart)
    assertFalse("خرده (پیش‌فرض) ⇒ بی نشان", has(hasText(StrPos.priceListWholesaleChip)))
    click(more)
    click(hasText(StrSale.wholesaleOn) and hasClickAction())
    assertTrue("عمده ⇒ «لیستِ قیمت: عمده» کنارِ جمع", has(hasText(StrPos.priceListWholesaleChip) and inSheet))
    rule.waitUntil(5_000) { cart.lines.value.first { it.productId == "p-choc" }.unitPrice == 100.0 }
    click(more)
    assertTrue("و برگشت از همان‌جا", has(hasText(StrSale.retailBack) and hasClickAction()))
  }

  @Test
  fun `ابزارِ خاموش و فهرستِ خالی هیچ ردیفی در «…» ندارند`() {
    //  همه خاموش (یادداشت هم) ⇒ «…» اصلاً نیست
    PosNotesPrefs.set(ctx, false)
    show(three)
    click(openCart)
    assertFalse("بی ابزار ⇒ بی «…»", has(more))
  }

  @Test
  fun `فهرستِ خالی — پیش‌فاکتورها، سفارش‌ها و امانی‌های صفر — ردیف ندارد`() {
    ir.vil3ntec.tohid.data.QuotePref.set(ctx, true)
    ir.vil3ntec.tohid.data.SpecialOrderPref.set(ctx, true)
    ir.vil3ntec.tohid.data.LayawayPref.set(ctx, true)
    show(three)
    click(openCart)
    click(more)
    assertTrue(has(hasText(StrPos.quoteFromCart) and hasClickAction()))
    assertTrue(has(hasText(StrSpecialOrder.newOrder) and hasClickAction()))
    assertTrue(has(hasText(StrLayaway.newLayaway) and hasClickAction()))
    for (zero in listOf(StrPos.quoteList(0), StrSpecialOrder.list(0), StrSale.specialOrders, StrSale.layaways, StrLayaway.list(0, 0))) {
      assertFalse("ردیفِ صفر نیست: $zero", has(hasText(zero)))
    }
    //  ابزارهای خاموش هم نیستند
    for (off in listOf(StrGiftCard.sell, StrDeliver.button, StrSalePages.receiveTitle, StrSale.wholesaleOn, StrLineSeller.button)) assertFalse(off, has(hasText(off)))
  }

  @Test
  fun `برگهٔ سبد درونِ همان پنجره است — بارکدخوان و میان‌برها با سبدِ باز هم می‌رسند`() {
    //  پنجرهٔ جدا (`ModalBottomSheet`، `Dialog`، `Popup`) کلیدها را از `hidScanner` ِ صفحه می‌دزدید
    fun src(rel: String) = listOf("../android-native/app/src/main/java", "android-native/app/src/main/java").map { File(it, "ir/vil3ntec/tohid/$rel") }.first { it.isFile }.readText()
    val frame = src("ui/v3/sales/cart/TillFrame.kt")
    for (x in listOf("ModalBottomSheet", "Dialog(", "Popup(", "V3Sheet(")) assertFalse("TillFrame ⇐ $x", frame.contains(x))
    assertTrue(src("ui/v3/sales/cart/SaleRoute.kt").contains("modifier = Modifier.hidScanner("))
    //  رنگِ نوار و برگه فقط سطحِ تم؛ هیچ رنگِ سیاهِ سبدِ پیشین (`onBlack`، `blackRaised`، `v.cart`) و هیچ HEX
    for (rel in listOf("ui/v3/sales/cart/TillFrame.kt", "ui/v3/sales/cart/TillCart.kt", "ui/components/pos/TillCatalog.kt")) {
      val s = src(rel)
      for (x in listOf("onBlack", "blackRaised", "colors.cart", "c.cart", "v.cart", "Color(0x")) assertFalse("$rel ⇐ $x", s.contains(x))
    }
    assertTrue(frame.contains(".background(cartColors.backgroundSubtle)"))
  }

  /* ---------------- رنگ ---------------- */

  /** رنگِ پیکسلی نزدیکِ لبهٔ چپِ گره (در راست‌به‌چپ انتهای ردیف: زمینهٔ خودِ کارت، نه نوشته) */
  private fun pixelNearStart(tag: String, fromTop: Float? = null): Color {
    val b = nodes(hasTestTag(tag)).single().boundsInRoot
    val img = rule.onRoot().captureToImage().toAwtImage()
    val x = (b.left + 6f).toInt().coerceIn(0, img.width - 1)
    val y = (if (fromTop != null) b.top + fromTop else b.top + b.height / 2).toInt().coerceIn(0, img.height - 1)
    return Color(img.getRGB(x, y))
  }

  private fun luminance(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

  private fun assertClose(what: String, want: Color, got: Color) {
    val d = abs(want.red - got.red) + abs(want.green - got.green) + abs(want.blue - got.blue)
    assertTrue("$what: می‌خواست $want، دید $got", d < 0.06f)
  }
}
