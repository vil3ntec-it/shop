package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.audit.Sha256
import ir.vil3ntec.tohid.core.gateway.Payee
import ir.vil3ntec.tohid.core.gateway.SimpleAccount
import ir.vil3ntec.tohid.core.gateway.TillHesab
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrTillPay
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.desktop.TillShellHarness
import ir.vil3ntec.tohid.pay.platform.HesabPayEnv
import ir.vil3ntec.tohid.pay.platform.ShopQrPicture
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.desk.DeskSize
import ir.vil3ntec.tohid.ui.screens.DeskHeader
import ir.vil3ntec.tohid.ui.screens.DeskRail
import ir.vil3ntec.tohid.ui.screens.DeskStatusBar
import ir.vil3ntec.tohid.ui.screens.RailMode
import ir.vil3ntec.tohid.ui.theme.PhoneV3
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
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutSession
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «پس» ِ `claude/e-paystep` (صاحب‌کار: «وقتی ثبتِ فروش می‌زنم نباید پرداخت بشه…»؛ «پیش» همان `EPayShotsTest` روی
 *  `origin/main` است): تسویهٔ گوشی با «پرداخت ‹مبلغ›»، گامِ پرداخت (نقد با دکمهٔ تند و پولِ پس، پرداختِ چندتکه با کارت، نسیه،
 *  عکسِ QR ِ بارشدهٔ دکان در برگهٔ حساب‌پی) و صندوق و گامِ پرداختِ کامپیوتر — روشن و تیره، با «فقط نقد و نسیه» (پرچمِ خاموش).
 *  فقط با `PAYSTEP_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست).
 */
@OptIn(ExperimentalTestApi::class)
class PayStepShotsTest {

  private val out: File? = System.getenv("PAYSTEP_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val payload = "HP|+93799111222|shop"

  private val ledger: ShopData = run {
    val products = listOf(
      Product("p-rice", "برنجِ سلامِ هندی", "خوراکی", "کیلو", salePrice = 380.0),
      Product("p-oil", "روغنِ آفتاب‌گردانِ ۱٫۸ لیتری", "خوراکی", "عدد", salePrice = 450.0),
      Product("p-tea", "چای سبزِ احمد ۵۰۰ گرمی", "خوراکی", "عدد", salePrice = 320.0),
    )
    ShopData(
      products = products,
      productCategories = listOf("خوراکی"),
      warehouseEntries = products.map { WarehouseEntry(id = "w-${it.id}", productId = it.id, units = 50.0) },
      debtors = listOf(Debtor(id = "c1", name = "حاجی محمد کریمی", phone = "0700123456")),
    )
  }
  private val lines = listOf(CartLine("p-rice", 2.0), CartLine("p-oil", 1.0), CartLine("p-tea", 1.0))
  /** ۲ × ۳۸۰ + ۴۵۰ + ۳۲۰ */
  private val total = 1530.0

  private fun ComposeUiTest.settle(steps: Int = 16) = repeat(steps) {
    mainClock.advanceTimeBy(250)
    Thread.sleep(40)
  }

  private fun DesktopComposeUiTest.save(name: String, dark: Boolean) {
    val img = captureToImage().toAwtImage()
    ImageIO.write(img, "png", File(out!!.apply { mkdirs() }, "$name-after-${if (dark) "dark" else "light"}.png"))
  }

  private fun ComposeUiTest.tap(m: SemanticsMatcher) {
    onAllNodes(m)[0].performSemanticsAction(SemanticsActions.OnClick)
    settle(8)
  }

  private fun ComposeUiTest.tapText(text: String) = tap(hasText(text) and hasClickAction())

  private fun tile(name: String) = SemanticsMatcher("کاشیِ $name") { it.config.getOrNull(SemanticsActions.OnClick)?.label == name }

  private fun hesab(t: TillHesab?) {
    val env = HesabPayEnv.of(DesktopContext)
    env.forget()
    if (t != null && env.online()) HesabPayEnv::class.java.getDeclaredField("cached").apply { isAccessible = true }.set(env, System.currentTimeMillis() to t)
  }

  /** عکسِ QR ِ دکان همان‌طور که «انتخابِ عکسِ QR» آماده می‌کند ⇒ هش (در پوشهٔ همین دستگاه) */
  private fun picture(): String {
    val rows = QrCode.encode(payload, QrCode.LARGE_MAX_VERSION)
    val cell = 16
    val side = (rows.size + 8) * cell
    val img = java.awt.image.BufferedImage(side, side, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.color = java.awt.Color.BLACK
    for (y in rows.indices) for (x in rows.indices) if (rows[y][x]) g.fillRect((x + 4) * cell, (y + 4) * cell, cell, cell)
    g.color = java.awt.Color(0x0B, 0x57, 0x9E)
    g.fillRect(0, 0, side, cell * 2)
    g.dispose()
    val png = ByteArrayOutputStream().use { ImageIO.write(img, "png", it); it.toByteArray() }
    val jpeg = (runBlocking { ShopQrPicture.prepare(png) } as ShopQrPicture.Prep.Ok).jpeg
    val hash = Sha256.hex(Sha256.digest(jpeg))
    File(DesktopContext.filesDir, "hesabpay-qr").apply { mkdirs() }.resolve(hash).writeBytes(jpeg)
    return hash
  }

  @Composable
  private fun Phone(store: ShopStore, cart: CartStore, dark: Boolean) {
    val data by store.data.collectAsState()
    val nav = remember { V3Nav(V3Modules.table, "home", NavStack.of("home")).also { n -> n.isTab = { it in TillShellHarness.navRoutes }; n.openDeep("sale/checkout") } }
    val snackbar = remember { SnackbarHostState() }
    val toasts = rememberV3Toasts(snackbar)
    val shell = ShellContext(
      store = store, data = data, cart = cart, snackbar = snackbar, toasts = toasts,
      theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT, onTheme = {},
      bottomReserve = PhoneNav.RESERVE, wide = false,
      access = Access.all(AppMode.SHOP, emptySet()),
      navAllowed = NavCatalog.allowed(canManage = true, ordersLive = true),
    )
    CompositionLocalProvider(LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides false) {
      Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) { V3RouteHost(nav, shell, Modifier.fillMaxSize()) }
    }
  }

  private fun phone(dark: Boolean, name: String, till: TillHesab? = null, debtor: String? = null, steps: DesktopComposeUiTest.() -> Unit = {}) =
    runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      mainClock.autoAdvance = false
      hesab(till)
      CheckoutSession.reset()
      val store = RouteUiHarness.loadedStore(ledger)
      val cart = CartStore(DesktopContext).also { it.clear(); it.set(lines); it.setDebtor(debtor) }
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(2f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, 760),
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Phone(store, cart, dark) }
          }
        }
      }
      settle(28)
      steps()
      settle(12)
      save(name, dark)
      cart.clear()
      CheckoutSession.reset()
      hesab(null)
    }

  private val pay get() = StrTillPay.payAmount(PhoneSaleText.amount(total))

  @Composable
  private fun DeskWindow(store: ShopStore, cart: CartStore) {
    val data by store.data.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val toasts = rememberV3Toasts(snackbar)
    CompositionLocalProvider(LocalV3Toasts provides toasts, LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides true) {
      Column(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
          DeskRail(current = "sale", onPick = {}, compact = true, mode = RailMode.COMPACT, path = "sale")
          Column(Modifier.fillMaxSize().padding(start = DeskSize.sidebarCompact)) {
            DeskHeader(title = ir.vil3ntec.tohid.core.i18n.Str.sale, d = data, onBack = null, onMenu = {}, onSearch = {}, onOpen = {}, page = "sale", menuVisible = true)
            Box(Modifier.weight(1f).fillMaxWidth()) {
              ir.vil3ntec.tohid.ui.screens.DeskSaleScreen(store = store, cartStore = cart, d = data, snackbar = snackbar) {}
            }
          }
        }
        DeskStatusBar(data)
      }
    }
  }

  private fun desk(dark: Boolean, name: String, steps: DesktopComposeUiTest.() -> Unit = {}) = runDesktopComposeUiTest(1366, 768, testTimeout = 3.minutes) {
    I18n.source = { Lang.FA }
    mainClock.autoAdvance = false
    val store = RouteUiHarness.loadedStore(ledger)
    val cart = CartStore(DesktopContext).also { it.clear(); it.set(lines) }
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(1f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(1366, 768),
      ) {
        TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { DeskWindow(store, cart) }
        }
      }
    }
    settle(20)
    steps()
    settle(12)
    save(name, dark)
    cart.clear()
  }

  @Test
  fun shots() {
    Assume.assumeTrue("PAYSTEP_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    //  دستگاهِ صاحب‌کار: «فقط نقد و نسیه» (گامِ پرداخت به پرچم بسته نیست)
    ShopPolicy.setPayV2(DesktopContext, false)
    val hash = picture()
    val till = TillHesab(false, "simple", SimpleAccount("0799111222", payload, hash), payee = Payee("دکانِ احمد", "1222", "hp1_shot"), flag = true)
    try {
      for (dark in listOf(false, true)) {
        phone(dark, "phone-checkout")
        phone(dark, "phone-paystep-cash") {
          tapText(pay)
          tapText(PhoneSaleText.amount(2000.0))
        }
        phone(dark, "phone-paystep-split") {
          tapText(pay)
          onAllNodes(hasSetTextAction() and (androidx.compose.ui.test.hasContentDescription(StrSale.cashReceived) or hasText(StrSale.cashReceived)))[0].performTextInput("1000")
          settle(4)
          tapText(StrTillPay.received)
          tap(tile(StrTillPay.tileCard))
        }
        phone(dark, "phone-paystep-credit", debtor = "c1") {
          tapText(pay)
          tap(tile(StrTillPay.tileCredit))
        }
        phone(dark, "phone-hesab-qr-picture", till = till) {
          tapText(pay)
          settle(8)
          tap(hasText(StrTillPay.tileHesab) and hasClickAction())
          settle(24)
        }
        desk(dark, "desk-sale")
        desk(dark, "desk-paystep-cash") { tapText(StrTillPay.payAmount("${ir.vil3ntec.tohid.money(total)} ؋")) }
        //  کاشیِ «کارت» ِ کنارِ سبد خودش گامِ پرداخت را با کارت باز می‌کند
        desk(dark, "desk-paystep-card") { tapText(StrTillPay.tileCard) }
      }
    } finally {
      DesktopContext.getSharedPreferences("tohid-policy", 0).edit().remove("pay_v2").commit()
      File(DesktopContext.filesDir, "hesabpay-qr").deleteRecursively()
      CartStore(DesktopContext).clear()
      hesab(null)
    }
  }
}
