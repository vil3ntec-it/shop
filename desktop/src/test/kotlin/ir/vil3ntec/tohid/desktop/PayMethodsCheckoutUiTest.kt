package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.gateway.HesabPayShop
import ir.vil3ntec.tohid.core.gateway.Payee
import ir.vil3ntec.tohid.core.gateway.SimpleAccount
import ir.vil3ntec.tohid.core.gateway.TillHesab
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrLedger
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.RouteArgs
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutRoute
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutSession
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  e-paymethods — برگهٔ «تسویهٔ فروش» ِ گوشیِ نسخهٔ ۳ (مسیرِ واقعیِ `sale/checkout` با دفترِ روی دیسک و ثبتِ واقعی) با پنلِ
 *  ترکیبی: همهٔ روش‌های دکان کاشی‌اند (نقد، کارت، حساب‌پی، کیف پول، حواله، نسیه) و هر کاشی همان کارِ پنل را می‌کند:
 *   - نقد ِ خودکار: «ثبت فروش» بی هیچ زدنی همان فروشِ نقدیِ کامل (پیش‌فرضِ «نقدی» ِ پیشین)؛
 *   - کارت: ردیفِ کارت با کلِ فاکتور، بی شمارهٔ تأیید ثبت نمی‌شود، با آن ردیفِ ثبتِ دستی (`manual`)؛
 *   - نسیه: بی مشتری اول «انتخابِ مشتری»، پس از گزینش همهٔ فاکتور به حسابِ همان مشتری؛
 *   - حساب‌پی: حسابِ تازه در ۲۴ ساعتِ امنیتی ⇒ کاشیِ خاموش با زمانِ فعال شدن؛ آماده ⇒ برگهٔ حساب‌پی برای همهٔ فاکتور؛
 *     راه‌اندازی‌نشده ⇒ هیچ کاشی و پیوندِ «راه‌اندازیِ حساب‌پی» فقط برای صاحب.
 */
@OptIn(ExperimentalTestApi::class)
class PayMethodsCheckoutUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val rice = Product(id = "pm-rice", name = "برنجِ آزمونِ روش‌ها", unit = "کیلو", salePrice = 380.0)
  private val tea = Product(id = "pm-tea", name = "چای آزمونِ روش‌ها", unit = "عدد", salePrice = 320.0)
  private val data = ShopData(
    products = listOf(rice, tea),
    warehouseEntries = listOf(WarehouseEntry(id = "pm-w1", productId = rice.id, units = 20.0), WarehouseEntry(id = "pm-w2", productId = tea.id, units = 20.0)),
    debtors = listOf(Debtor(id = "pm-c1", name = "مشتریِ آزمونِ روش‌ها", phone = "0700111222")),
  )
  /** ۲ × ۳۸۰ + ۳۲۰ */
  private val total = 1080.0
  private val env get() = HesabPayEnv.of(ctx)
  private val cached = HesabPayEnv::class.java.getDeclaredField("cached").apply { isAccessible = true }
  private lateinit var store: ShopStore
  private lateinit var nav: V3Nav

  private fun clean() {
    ShopPolicy.setPayV2(ctx, false)
    CartStore.awaitWrites()
    File(ctx.filesDir, CartStore.FILE).delete()
    ctx.filesDir.listFiles().orEmpty().filter { it.name.startsWith("shop-data.json") }.forEach { it.deleteRecursively() }
    ShopRole.remember(ctx, ShopRole.OWNER)
    CheckoutSession.reset()
    env.forget()
  }

  @Before fun up() {
    I18n.source = { Lang.FA }
    clean()
    ShopPolicy.setPayV2(ctx, true)
  }

  @After fun down() = clean()

  private fun hesab(t: TillHesab) {
    assumeTrue("بی شبکه حساب‌پی پنهان است", env.online())
    cached.set(env, System.currentTimeMillis() to t)
  }

  private fun open() {
    store = ShopStore(ctx).also { runBlocking { it.load() } }
    assertTrue(runBlocking { store.save(data) })
    val cart = CartStore(ctx)
    assertTrue(cart.set(listOf(SalesEngine.CartLine(rice.id, 2.0), SalesEngine.CartLine(tea.id, 1.0))))
    CheckoutSession.reset()
    nav = V3Nav(V3Modules.table, "home", NavStack.of("home")).also { n -> n.isTab = { it in TillShellHarness.navRoutes }; n.openDeep(SalesPaths.CHECKOUT) }
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          val live by store.data.collectAsState()
          val snackbar = remember { SnackbarHostState() }
          val toasts = rememberV3Toasts(snackbar)
          val shell = ShellContext(
            store = store, data = live, cart = cart, snackbar = snackbar, toasts = toasts,
            theme = ThemeChoice.LIGHT, onTheme = {}, bottomReserve = PhoneNav.RESERVE, wide = false,
            access = Access.all(AppMode.SHOP, emptySet()), navAllowed = NavCatalog.allowed(canManage = true, ordersLive = true),
          )
          CompositionLocalProvider(LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides false) {
            Box(Modifier.fillMaxSize()) {
              val scope = remember(shell) { RouteScope(RouteArgs(SalesPaths.CHECKOUT, emptyMap()), nav, shell) }
              scope.CheckoutRoute()
            }
          }
        }
      }
    }
    rule.waitUntil(15_000) { has(StrTillPay.methodsTitle) }
  }

  private fun has(text: String, substring: Boolean = false) = rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

  private fun tap(m: SemanticsMatcher) {
    rule.onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  /** کاشیِ روش با همین نام (برچسبِ زدنیِ `v3Pressable`) */
  private fun tile(name: String) = SemanticsMatcher("کاشیِ $name") { it.config.getOrNull(SemanticsActions.OnClick)?.label == name }

  private fun record() = tap(hasText(StrSale.recordSale) and hasClickAction())

  private fun awaitSale() = rule.waitUntil(15_000) { store.data.value.sales.isNotEmpty() }.let { store.data.value.sales.single() }

  private val methodNames = listOf(StrTillPay.tileCash, StrTillPay.tileCard, StrTillPay.tileWallet, StrTillPay.tileTransfer, StrTillPay.tileCredit)

  @Test fun everyShopMethodIsATileAndCashIsTheOneTapDefault() {
    open()
    for (n in methodNames) assertTrue("کاشیِ «$n» نیست", rule.onAllNodes(tile(n)).fetchSemanticsNodes().isNotEmpty())
    //  بی حساب‌پیِ دکان (بی نت یا بی حساب) کاشیِ حساب‌پی نیست
    assertTrue(rule.onAllNodes(tile(StrTillPay.tileHesab)).fetchSemanticsNodes().isEmpty())
    //  «نقد» ِ خودکار همان مبلغِ لازم است ⇒ «ثبت فروش» همان فروشِ نقدیِ کاملِ پیشین
    assertEquals("1080", CheckoutSession.pay.cash)
    record()
    val sale = awaitSale()
    assertEquals(total, sale.finalTotal, 1e-9)
    assertEquals("cash", sale.paymentMethod)
    assertEquals(listOf("cash" to 108_000L), sale.payments.orEmpty().filter { it.amountMinor > 0 }.map { it.kind to it.amountMinor })
    assertEquals(0.0, sale.debtGiven, 1e-9)
  }

  @Test fun cardNeedsTheTerminalApprovalNumberThenRecordsAManualCardRow() {
    open()
    tap(tile(StrTillPay.tileCard))
    //  نقدِ خودکار کنار رفت و کلِ فاکتور به کارت آمد
    assertEquals("", CheckoutSession.pay.cash)
    assertEquals(listOf("afpay_card" to "1080"), CheckoutSession.pay.rows.map { it.method to it.amount })
    assertTrue(has(StrTillPay.cardRefHint))
    //  بی شمارهٔ تأییدِ رسیدِ کارت‌خوان ثبت نمی‌شود
    record()
    rule.waitForIdle()
    assertTrue(store.data.value.sales.isEmpty())
    assertTrue("پیامِ «شمارهٔ مرجع»", has(StrLedger.refRequired("«کارت AfPay»"), substring = true) || has(StrSale.checkRow))
    rule.onAllNodes(hasSetTextAction() and (hasContentDescription(StrSale.refNumber) or hasText(StrSale.refNumber)))[0].performTextInput("TRM-55120")
    rule.waitForIdle()
    record()
    val sale = awaitSale()
    val card = sale.payments.orEmpty().single { it.amountMinor > 0 }
    assertEquals(listOf("afpay_card", "card", "TRM-55120", "manual"), listOf(card.method, card.kind, card.ref, card.verification))
    assertEquals(108_000L, card.amountMinor)
    assertEquals("card", sale.paymentMethod)
  }

  @Test fun creditWithoutACustomerOpensThePickerAndThenPutsTheWholeSaleOnTheAccount() {
    open()
    tap(tile(StrTillPay.tileCredit))
    assertTrue("بی مشتری اول «انتخابِ مشتری»: ${nav.current}", nav.current.startsWith("sale/checkout/customer"))
    assertTrue(store.data.value.sales.isEmpty())
    //  گزینشِ مشتری (همان نتیجهٔ برگهٔ انتخاب) ⇒ باقی به حسابش
    rule.runOnIdle { nav.finish("pm-c1") }
    rule.waitForIdle()
    assertEquals("pm-c1", CheckoutSession.form?.debtorId)
    assertEquals("", CheckoutSession.pay.cash)
    record()
    val sale = awaitSale()
    assertEquals("credit", sale.paymentMethod)
    assertEquals("pm-c1", sale.debtorId)
    assertEquals(total, sale.debtGiven, 1e-9)
  }

  @Test fun hesabPayPendingIsADisabledTileWithTheActivationTime() {
    val until = System.currentTimeMillis() + 5 * 3_600_000L
    hesab(TillHesab(gateway = false, mode = HesabPayShop.OFF, simple = null, flag = true, pendingUntil = until))
    open()
    rule.waitUntil(10_000) { has(StrTillPay.tileHesab) }
    val node = rule.onAllNodes(hasText(StrTillPay.tileHesab)).fetchSemanticsNodes().single()
    assertTrue("کاشیِ حساب‌پیِ در انتظار خاموش است", node.config.getOrNull(SemanticsProperties.Disabled) != null)
    assertTrue(has(StrTillPay.tileSoon))
    assertTrue(has(StrTillPay.hesabPending(SaleTime.stamp(until))))
    //  زدنِ کاشیِ خاموش هیچ برگه‌ای باز نمی‌کند و ۲۴ ساعت کوتاه نمی‌شود
    runCatching { rule.onAllNodes(hasText(StrTillPay.tileHesab))[0].performSemanticsAction(SemanticsActions.OnClick) }
    rule.waitForIdle()
    assertNull(CheckoutSession.hesab)
    assertFalse(has(StrTillPay.hesabSetup))
  }

  @Test fun hesabPayReadyOpensTheShopsOwnQrForTheWholeInvoice() {
    hesab(TillHesab(false, HesabPayShop.SIMPLE, SimpleAccount("0700000000", ""), payee = Payee("دکانِ آزمون", "0000", "fp-pm"), flag = true))
    open()
    rule.waitUntil(10_000) { has(StrTillPay.tileHesab) }
    tap(hasText(StrTillPay.tileHesab) and hasClickAction())
    val ask = CheckoutSession.hesab
    assertNotNull("برگهٔ حساب‌پی باز نشد", ask)
    assertEquals(SalesPaths.HESAB, nav.current)
    //  نقدِ خودکار کنار رفت ⇒ حساب‌پی همهٔ فاکتور را می‌گیرد؛ پول فقط به حسابِ خودِ دکان (`TillHesab.simple` ِ سرور)
    assertEquals("", CheckoutSession.pay.cash)
    assertEquals(total, ask!!.total, 1e-9)
    assertTrue(store.data.value.sales.isEmpty())
  }

  @Test fun hesabPayNotSetUpShowsOnlyTheOwnersSetupLink() {
    hesab(TillHesab(gateway = false, mode = HesabPayShop.OFF, simple = null, flag = true))
    open()
    rule.waitUntil(10_000) { has(StrTillPay.hesabSetup) }
    assertFalse(has(StrTillPay.tileHesab))
  }

  @Test fun staffNeverSeesTheSetupLink() {
    ShopRole.remember(ctx, ShopRole.STAFF)
    hesab(TillHesab(gateway = false, mode = HesabPayShop.OFF, simple = null, flag = true))
    open()
    rule.waitForIdle()
    assertFalse(has(StrTillPay.hesabSetup))
  }
}
