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
import ir.vil3ntec.tohid.core.i18n.StrPay
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
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.nav.V3RouteHost
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutSession
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  e-paystep — صندوقِ گوشیِ نسخهٔ ۳ در دو گام (مسیرهای واقعیِ `sale/checkout` و `sale/checkout/pay` در `V3RouteHost`، دفترِ روی دیسک و
 *  ثبتِ واقعی). صاحب‌کار: «وقتی ثبتِ فروش می‌زنم نباید پرداخت بشه؛ باید ببره پیشِ بخشِ پرداخت…». هر آزمون یک راه:
 *   - «پرداخت ‹مبلغ›» هیچ ثبت نمی‌کند و گامِ پرداخت را باز می‌کند؛ «بازگشت به سبد» ⇒ تسویه با سبدِ دست‌نخورده؛
 *   - نقد: «دریافت شد» (خالی = دقیق)، یک فروش حتی با دو زدنِ پیاپی؛ دکمهٔ تند و پولِ پس؛
 *   - کارت: «کارت‌خوان تأیید کرد» بی شمارهٔ تأیید (صاحب‌کار ۱۴۰۵/۰۸/۲۶) و با آن؛ نقدِ بخشی + کارت برای باقی؛
 *   - نسیه: بی مشتری اول «انتخابِ مشتری»، سپس «ثبت به حسابِ مشتری»؛
 *   - حساب‌پی: حسابِ ۲۴ ساعتِ در انتظار ⇒ کاشیِ خاموش با زمان؛ «ساده» ⇒ برگهٔ QR و «پول رسید» ِ دستی ⇒ همان فروش یک بار.
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
  private lateinit var cart: CartStore
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

  //  گامِ پرداخت به پرچمِ `payV2` بسته نیست: همهٔ آزمون‌ها با «فقط نقد و نسیه» (خاموش)
  @Before fun up() { I18n.source = { Lang.FA }; clean() }

  @After fun down() = clean()

  private fun hesab(t: TillHesab) {
    assumeTrue("بی شبکه حساب‌پی پنهان است", env.online())
    cached.set(env, System.currentTimeMillis() to t)
  }

  private fun open() {
    store = ShopStore(ctx).also { runBlocking { it.load() } }
    assertTrue(runBlocking { store.save(data) })
    cart = CartStore(ctx)
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
            Box(Modifier.fillMaxSize()) { V3RouteHost(nav, shell, Modifier.fillMaxSize()) }
          }
        }
      }
    }
    rule.waitUntil(15_000) { has(payLabel) }
  }

  private val payLabel get() = StrTillPay.payAmount(PhoneSaleText.amount(total))

  private fun has(text: String, substring: Boolean = false) = rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty()

  private fun tap(m: SemanticsMatcher) {
    rule.onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private fun tapText(text: String) = tap(hasText(text) and hasClickAction())

  /** کاشیِ روش با همین نام (برچسبِ زدنیِ `v3Pressable`) */
  private fun tile(name: String) = SemanticsMatcher("کاشیِ $name") { it.config.getOrNull(SemanticsActions.OnClick)?.label == name }

  private fun type(label: String, value: String) {
    rule.onAllNodes(hasSetTextAction() and (hasContentDescription(label) or hasText(label)))[0].performTextInput(value)
    rule.waitForIdle()
  }

  /** «پرداخت ‹مبلغ›» ⇒ گامِ پرداخت */
  private fun toPay() {
    tapText(payLabel)
    rule.waitUntil(10_000) { nav.current == SalesPaths.PAY && has(StrTillPay.amountDue) }
  }

  private fun awaitSale() = rule.waitUntil(15_000) { store.data.value.sales.isNotEmpty() }.let { store.data.value.sales.single() }

  @Test fun payRecordsNothingAndShowsEveryMethodAndBackKeepsTheCart() {
    open()
    assertFalse("«ثبت فروش» دیگر در تسویه نیست", has(StrSale.recordSale))
    toPay()
    assertTrue(store.data.value.sales.isEmpty())
    for (n in listOf(StrTillPay.tileCash, StrTillPay.tileCard, StrTillPay.tileWallet, StrTillPay.tileTransfer, StrTillPay.tileCredit)) {
      assertTrue("کاشیِ «$n» نیست", rule.onAllNodes(tile(n)).fetchSemanticsNodes().isNotEmpty())
    }
    assertTrue(has(PhoneSaleText.amount(total)))
    tapText(StrTillPay.backToCart)
    rule.waitUntil(10_000) { nav.current == SalesPaths.CHECKOUT && has(payLabel) }
    assertTrue(store.data.value.sales.isEmpty())
    assertEquals(listOf(rice.id to 2.0, tea.id to 1.0), cart.lines.value.map { it.productId to it.quantity })
  }

  @Test fun cashReceivedRecordsExactlyOneSaleEvenOnADoubleTap() {
    open()
    toPay()
    val button = rule.onAllNodes(hasText(StrTillPay.received) and hasClickAction())[0]
    button.performSemanticsAction(SemanticsActions.OnClick)
    runCatching { button.performSemanticsAction(SemanticsActions.OnClick) }
    val sale = awaitSale()
    rule.waitForIdle()
    assertEquals(1, store.data.value.sales.size)
    assertEquals("cash", sale.paymentMethod)
    assertEquals(listOf("cash" to 108_000L), sale.payments.orEmpty().filter { it.amountMinor > 0 }.map { it.kind to it.amountMinor })
  }

  @Test fun quickCashChipShowsChangeAndRecordsIt() {
    open()
    toPay()
    tap(tile(StrTillPay.tileCash))
    tapText(PhoneSaleText.amount(1100.0))
    assertTrue(has(StrTillPay.changeDue(PhoneSaleText.amount(20.0))))
    tapText(StrTillPay.received)
    val sale = awaitSale()
    assertEquals(110_000L, sale.tenderedMinor)
    assertEquals(2_000L, sale.changeMinor)
  }

  @Test fun partCashThenTheCardTerminalApprovesTheRestWithoutAReference() {
    open()
    toPay()
    type(StrSale.cashReceived, "500")
    tapText(StrTillPay.received)
    rule.waitUntil(5_000) { has(StrTillPay.remainingDue) && has(StrTillPay.otherForRest) }
    assertTrue(store.data.value.sales.isEmpty())
    assertTrue(has(PhoneSaleText.amount(580.0)))
    tap(tile(StrTillPay.tileCard))
    tapText(StrTillPay.cardApproved)
    val sale = awaitSale()
    assertEquals(listOf("cash" to 50_000L, "afpay_card" to 58_000L), sale.payments.orEmpty().filter { it.amountMinor > 0 }.map { it.method to it.amountMinor })
    assertNull(sale.payments.orEmpty().single { it.method == "afpay_card" }.ref)
  }

  @Test fun cardWithTheOptionalApprovalNumberKeepsIt() {
    open()
    toPay()
    tap(tile(StrTillPay.tileCard))
    type(StrTillPay.approvalOptional, "TRM-55120")
    tapText(StrTillPay.cardApproved)
    val card = awaitSale().payments.orEmpty().single { it.amountMinor > 0 }
    assertEquals(listOf("afpay_card", "card", "TRM-55120", "manual", 108_000L), listOf(card.method, card.kind, card.ref, card.verification, card.amountMinor))
  }

  @Test fun creditWithoutACustomerPicksOneThenPutsTheSaleOnTheAccount() {
    open()
    toPay()
    tap(tile(StrTillPay.tileCredit))
    rule.waitUntil(5_000) { nav.current.startsWith("sale/checkout/customer") }
    assertTrue(store.data.value.sales.isEmpty())
    rule.runOnIdle { nav.finish("pm-c1") }
    //  برگشت به تسویه (جمع با همان مشتری) و همان گام با «نسیه»
    rule.waitUntil(10_000) { nav.current == SalesPaths.PAY && has(StrTillPay.toAccountButton) }
    tapText(StrTillPay.toAccountButton)
    val sale = awaitSale()
    assertEquals("credit", sale.paymentMethod)
    assertEquals("pm-c1", sale.debtorId)
    assertEquals(total, sale.debtGiven, 1e-9)
  }

  @Test fun hesabPayPendingIsADisabledTileWithTheActivationTime() {
    val until = System.currentTimeMillis() + 5 * 3_600_000L
    hesab(TillHesab(gateway = false, mode = HesabPayShop.OFF, simple = null, flag = true, pendingUntil = until))
    open()
    toPay()
    rule.waitUntil(10_000) { has(StrTillPay.tileHesab) }
    val node = rule.onAllNodes(hasText(StrTillPay.tileHesab)).fetchSemanticsNodes().single()
    assertTrue("کاشیِ حساب‌پیِ در انتظار خاموش است", node.config.getOrNull(SemanticsProperties.Disabled) != null)
    assertTrue(has(StrTillPay.hesabPending(SaleTime.stamp(until))))
    runCatching { rule.onAllNodes(hasText(StrTillPay.tileHesab))[0].performSemanticsAction(SemanticsActions.OnClick) }
    rule.waitForIdle()
    assertNull(CheckoutSession.hesab)
    assertEquals(SalesPaths.PAY, nav.current)
  }

  @Test fun hesabPaySimpleShowsTheShopsQrAndTheManualConfirmRecordsTheSaleOnce() {
    hesab(TillHesab(false, HesabPayShop.SIMPLE, SimpleAccount("0700000000", ""), payee = Payee("دکانِ آزمون", "0000", "fp-pm"), flag = true))
    open()
    toPay()
    rule.waitUntil(10_000) { has(StrTillPay.tileHesab) }
    tap(hasText(StrTillPay.tileHesab) and hasClickAction())
    rule.waitUntil(10_000) { nav.current == SalesPaths.HESAB && has(StrPay.moneyArrived) }
    assertEquals(total, CheckoutSession.hesab!!.total, 1e-9)
    assertTrue(store.data.value.sales.isEmpty())
    val arrived = rule.onAllNodes(hasText(StrPay.moneyArrived) and hasClickAction())[0]
    arrived.performSemanticsAction(SemanticsActions.OnClick)
    runCatching { arrived.performSemanticsAction(SemanticsActions.OnClick) }
    val sale = awaitSale()
    rule.waitUntil(10_000) { CheckoutSession.done != null }
    assertEquals(1, store.data.value.sales.size)
    val row = sale.payments.orEmpty().single { it.amountMinor > 0 }
    assertEquals(listOf("hesabpay", 108_000L), listOf(row.method, row.amountMinor))
  }

  /** صاحب‌کار ۱۴۰۵/۰۸/۲۶: عکسِ QR ِ بارشدهٔ دکان (همان که «انتخابِ عکسِ QR» آماده می‌کند) در پوشهٔ همین دستگاه ⇒ هش */
  private fun uploadedPicture(text: String): String {
    val rows = ir.vil3ntec.tohid.core.qr.QrCode.encode(text, ir.vil3ntec.tohid.core.qr.QrCode.LARGE_MAX_VERSION)
    val cell = 14
    val side = (rows.size + 8) * cell
    val img = java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.color = java.awt.Color.BLACK
    for (y in rows.indices) for (x in rows.indices) if (rows[y][x]) g.fillRect((x + 4) * cell, (y + 4) * cell, cell, cell)
    g.dispose()
    val png = java.io.ByteArrayOutputStream().use { javax.imageio.ImageIO.write(img, "png", it); it.toByteArray() }
    val ok = runBlocking { ir.vil3ntec.tohid.pay.platform.ShopQrPicture.prepare(png) } as ir.vil3ntec.tohid.pay.platform.ShopQrPicture.Prep.Ok
    assertEquals(text, ok.payload)
    val hash = ir.vil3ntec.tohid.core.audit.Sha256.hex(ir.vil3ntec.tohid.core.audit.Sha256.digest(ok.jpeg))
    File(ctx.filesDir, "hesabpay-qr").apply { mkdirs() }.resolve(hash).writeBytes(ok.jpeg)
    return hash
  }

  @Test fun theShopsUploadedQrPictureIsShownBigInThePaymentStepAndToTheCustomer() {
    val payload = "HP|+93700000000|shop"
    val hash = uploadedPicture(payload)
    try {
      hesab(TillHesab(false, HesabPayShop.SIMPLE, SimpleAccount("0700000000", payload, hash), payee = Payee("دکانِ آزمون", "0000", "fp-pm"), flag = true))
      open()
      toPay()
      rule.waitUntil(10_000) { has(StrTillPay.tileHesab) }
      tap(hasText(StrTillPay.tileHesab) and hasClickAction())
      rule.waitUntil(10_000) { nav.current == SalesPaths.HESAB && has(StrPay.moneyArrived) }
      //  همان عکس (خوانده و سنجیده) بزرگ در برگه و روی نمایشگرِ مشتری، نه QR ِ کشیده
      rule.waitUntil(15_000) { rule.onAllNodes(hasContentDescription(StrTillPay.shopQrImage)).fetchSemanticsNodes().isNotEmpty() }
      rule.waitUntil(5_000) { ir.vil3ntec.tohid.pay.CustomerPay.view.value?.picture != null }
      assertEquals(payload, ir.vil3ntec.tohid.pay.CustomerPay.view.value!!.qrPayload)
      tap(hasText(StrPay.moneyArrived) and hasClickAction())
      val sale = awaitSale()
      assertEquals("hesabpay", sale.payments.orEmpty().single { it.amountMinor > 0 }.method)
    } finally {
      File(ctx.filesDir, "hesabpay-qr").deleteRecursively()
    }
  }

  @Test fun staffNeverSeesTheSetupLink() {
    ShopRole.remember(ctx, ShopRole.STAFF)
    hesab(TillHesab(gateway = false, mode = HesabPayShop.OFF, simple = null, flag = true))
    open()
    toPay()
    assertFalse(has(StrTillPay.hesabSetup))
  }

  @Test fun ownerSeesTheSetupLinkWhenHesabPayIsNotSetUp() {
    hesab(TillHesab(gateway = false, mode = HesabPayShop.OFF, simple = null, flag = true))
    open()
    toPay()
    rule.waitUntil(10_000) { has(StrTillPay.hesabSetup) }
    assertFalse(has(StrTillPay.tileHesab))
  }
}
