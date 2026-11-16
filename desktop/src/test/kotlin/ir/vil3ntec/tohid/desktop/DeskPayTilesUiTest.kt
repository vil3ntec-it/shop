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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.gateway.HesabPayShop
import ir.vil3ntec.tohid.core.gateway.TillHesab
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import ir.vil3ntec.tohid.ui.screens.DeskSaleScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  e-paymethods — صندوقِ کامپیوتر (`DeskSaleScreen` ِ واقعی، دفترِ روی دیسک) با پنلِ پرداخت: همهٔ روش‌های دکان کنارِ سبد
 *  کاشی‌اند و هر کاشی پنلِ پرداخت را با همان روش باز می‌کند؛ «کارت» ردیفِ کارت با کلِ فاکتور می‌گذارد و با شمارهٔ تأیید
 *  همان ردیفِ ثبتِ دستی ثبت می‌شود؛ حساب‌پیِ ۲۴ ساعتِ در انتظار کاشیِ خاموش با زمانِ فعال شدن است.
 */
@OptIn(ExperimentalTestApi::class)
class DeskPayTilesUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val oil = Product(id = "dpt-oil", name = "روغنِ آزمونِ کاشی", unit = "عدد", salePrice = 450.0)
  private val data = ShopData(products = listOf(oil), warehouseEntries = listOf(WarehouseEntry(id = "dpt-w", productId = oil.id, units = 9.0)))
  private val env get() = HesabPayEnv.of(ctx)
  private val cached = HesabPayEnv::class.java.getDeclaredField("cached").apply { isAccessible = true }
  private lateinit var store: ShopStore

  private fun clean() {
    ShopPolicy.setPayV2(ctx, false)
    CartStore.awaitWrites()
    File(ctx.filesDir, CartStore.FILE).delete()
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
    env.forget()
  }

  @Before fun up() {
    I18n.source = { Lang.FA }
    clean()
    ShopPolicy.setPayV2(ctx, true)
  }

  @After fun down() = clean()

  private fun open() {
    store = ShopStore(ctx).also { runBlocking { it.load() } }
    assertTrue(runBlocking { store.save(data) })
    val cart = CartStore(ctx)
    assertTrue(cart.set(listOf(SalesEngine.CartLine(oil.id, 2.0))))
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1366, 800),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          val live by store.data.collectAsState()
          DeskSaleScreen(store = store, cartStore = cart, d = live, snackbar = remember { SnackbarHostState() }) {}
        }
      }
    }
    rule.waitUntil(15_000) { has(StrTillPay.tileCard) }
  }

  private fun has(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

  private fun tap(m: SemanticsMatcher) {
    rule.onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  @Test fun everyMethodIsBesideTheCartAndCardOpensThePanelWithAWholeInvoiceCardRow() {
    open()
    for (n in listOf(StrTillPay.tileCash, StrTillPay.tileCard, StrTillPay.tileWallet, StrTillPay.tileTransfer, StrTillPay.tileCredit)) {
      assertTrue("کاشیِ «$n» کنارِ سبد نیست", has(n))
    }
    tap(hasText(StrTillPay.tileCard) and hasClickAction())
    rule.waitUntil(10_000) { has(StrPos.payPanel) }
    //  ردیفِ کارتِ AfPay با کلِ فاکتور (۲ × ۴۵۰)؛ شمارهٔ تأییدِ رسیدِ کارت‌خوان را صندوقدار می‌نویسد
    assertTrue(has("کارت AfPay"))
    assertTrue(has(StrTillPay.cardRefHint))
    rule.onAllNodes(hasSetTextAction() and hasText(StrPos.reference))[0].performTextInput("POS-7781")
    rule.waitForIdle()
    tap(hasText(StrPos.recordSaleEnter) and hasClickAction())
    rule.waitUntil(15_000) { store.data.value.sales.isNotEmpty() }
    val sale = store.data.value.sales.single()
    val row = sale.payments.orEmpty().single { it.amountMinor > 0 }
    assertEquals(listOf("afpay_card", "POS-7781", "manual"), listOf(row.method, row.ref, row.verification))
    assertEquals(90_000L, row.amountMinor)
  }

  @Test fun pendingHesabPayIsADisabledTileWithTheTimeItBecomesActive() {
    assumeTrue("بی شبکه حساب‌پی پنهان است", env.online())
    val until = System.currentTimeMillis() + 3 * 3_600_000L
    cached.set(env, System.currentTimeMillis() to TillHesab(gateway = false, mode = HesabPayShop.OFF, simple = null, flag = true, pendingUntil = until))
    open()
    rule.waitUntil(10_000) { has(StrTillPay.tileHesab) }
    val tile = rule.onAllNodes(hasText(StrTillPay.tileHesab)).fetchSemanticsNodes().first()
    assertTrue("کاشیِ حساب‌پیِ در انتظار خاموش است", tile.config.getOrNull(SemanticsProperties.Disabled) != null)
    assertTrue(has(StrTillPay.hesabPending(SaleTime.stamp(until))))
  }
}
