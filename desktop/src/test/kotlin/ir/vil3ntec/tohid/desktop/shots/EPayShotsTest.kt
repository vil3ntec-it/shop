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
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.nav.Access
import ir.vil3ntec.tohid.core.nav.AppMode
import ir.vil3ntec.tohid.core.nav.NavStack
import ir.vil3ntec.tohid.data.CartStore
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.desktop.TillShellHarness
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
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
import ir.vil3ntec.tohid.ui.v3.nav.RouteArgs
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.nav.ShellContext
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.nav.V3Nav
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleRoute
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutRoute
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CheckoutSession
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ `claude/e-paymethods` (صاحب‌کار: «جای پرداخت چرا حساب‌های کاربری نمیاد … این‌ها همه باید بیایند یا نقدی»):
 *  برگهٔ «تسویهٔ فروش» ِ گوشی (۳۶۰×۷۶۰dp، چگالیِ ۲) و صندوقِ کامپیوتر (۱۳۶۶×۷۶۸) — روشن و تیره، فارسی و راست‌به‌چپ — با
 *  دستگاهی که هرگز «نقد و نسیه | ترکیبی و شیفت» را انتخاب نکرده (کلیدِ `pay_v2` نیست). پیش‌فرضِ همین دستگاه همان کاری است که
 *  شروعِ برنامه (`ShopStore.loadAndSummarize`) می‌کند؛ پیش از این شاخه آن قدم نبود و این‌جا بی‌اثر می‌ماند.
 *  فقط با `EPAY_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست)؛ `EPAY_TAG` (`before`/`after`) پسوندِ نام.
 */
@OptIn(ExperimentalTestApi::class)
class EPayShotsTest {

  private val out: File? = System.getenv("EPAY_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val tag: String = System.getenv("EPAY_TAG")?.takeIf { it.isNotBlank() } ?: "after"

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

  /** دستگاهی که هرگز انتخاب نکرده؛ پس از این شاخه همان قدمِ شروعِ برنامه (با بازتاب، تا پیش از شاخه هم کامپایل شود) */
  private fun neverChose() {
    DesktopContext.getSharedPreferences("tohid-policy", 0).edit().remove("pay_v2").commit()
    runCatching {
      val c = Class.forName("ir.vil3ntec.tohid.data.PayV2Pref")
      c.getMethod("adopt", android.content.Context::class.java, ShopData::class.java, Long::class.javaPrimitiveType)
        .invoke(c.getField("INSTANCE").get(null), DesktopContext, ledger, System.currentTimeMillis())
    }
  }

  private fun forget() = DesktopContext.getSharedPreferences("tohid-policy", 0).edit().remove("pay_v2").commit()

  private fun ComposeUiTest.settle(steps: Int = 16) = repeat(steps) {
    mainClock.advanceTimeBy(250)
    Thread.sleep(40)
  }

  private fun DesktopComposeUiTest.save(name: String, dark: Boolean) {
    val img = captureToImage().toAwtImage()
    ImageIO.write(img, "png", File(out!!.apply { mkdirs() }, "$name-$tag-${if (dark) "dark" else "light"}.png"))
  }

  /** صندوقِ گوشی و برگهٔ تسویه رویش — همان دو مسیرِ `sale` و `sale/checkout` */
  @Composable
  private fun PhoneCheckout(store: ShopStore, cart: CartStore, dark: Boolean) {
    val data by store.data.collectAsState()
    val nav = remember { V3Nav(V3Modules.table, "home", NavStack.of("home")).also { n -> n.isTab = { it in TillShellHarness.navRoutes }; n.openDeep("sale") } }
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
      Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
        val sale = remember(nav, shell) { RouteScope(RouteArgs("sale", emptyMap()), nav, shell) }
        sale.SaleRoute()
        val checkout = remember(nav, shell) { RouteScope(RouteArgs("sale/checkout", emptyMap()), nav, shell) }
        checkout.CheckoutRoute()
      }
    }
  }

  /** حالِ حساب‌پیِ دکان در حافظهٔ یک‌دقیقه‌ایِ `HesabPayEnv` (همان پاسخِ `/shop/payments/config`)؛ `null` ⇒ پاک */
  private fun hesab(t: ir.vil3ntec.tohid.core.gateway.TillHesab?) {
    val env = ir.vil3ntec.tohid.pay.platform.HesabPayEnv.of(DesktopContext)
    env.forget()
    if (t != null && env.online()) {
      ir.vil3ntec.tohid.pay.platform.HesabPayEnv::class.java.getDeclaredField("cached").apply { isAccessible = true }.set(env, System.currentTimeMillis() to t)
    }
  }

  private fun phone(dark: Boolean, name: String = "phone-checkout", till: ir.vil3ntec.tohid.core.gateway.TillHesab? = null) = runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
    I18n.source = { Lang.FA }
    mainClock.autoAdvance = false
    neverChose()
    hesab(till)
    CheckoutSession.reset()
    val store = RouteUiHarness.loadedStore(ledger)
    val cart = CartStore(DesktopContext).also { it.clear(); it.set(lines) }
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(2f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { PhoneCheckout(store, cart, dark) }
        }
      }
    }
    settle(28)
    save(name, dark)
    cart.clear()
    CheckoutSession.reset()
    hesab(null)
  }

  /** پنجرهٔ کامپیوتر: سربرگ، منوی کناریِ فشرده، صندوق و نوارِ وضعیت (همان `D3aShotsTest`) */
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

  private fun desk(dark: Boolean, openPanel: Boolean) = runDesktopComposeUiTest(1366, 768, testTimeout = 3.minutes) {
    I18n.source = { Lang.FA }
    mainClock.autoAdvance = false
    neverChose()
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
    if (openPanel) {
      onAllNodes(hasText(StrPos.recordSaleBtn) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
      settle(12)
    }
    save(if (openPanel) "desk-pay-panel" else "desk-sale", dark)
    cart.clear()
  }

  /** فقط «پس»: حساب‌پیِ آماده (QR ِ خودِ دکان)، حسابِ تازه در ۲۴ ساعتِ امنیتی، و راه‌اندازی‌نشده (پیوندِ صاحب) */
  private fun afterOnly(dark: Boolean) {
    val payee = ir.vil3ntec.tohid.core.gateway.Payee("دکانِ احمد", "1222", "hp1_shot")
    phone(dark, "phone-checkout-hesab-ready", ir.vil3ntec.tohid.core.gateway.TillHesab(false, "simple", ir.vil3ntec.tohid.core.gateway.SimpleAccount("0799111222", ""), payee = payee, flag = true))
    phone(dark, "phone-checkout-hesab-pending", ir.vil3ntec.tohid.core.gateway.TillHesab(false, "off", null, flag = true, pendingUntil = System.currentTimeMillis() + 7 * 3_600_000L))
    phone(dark, "phone-checkout-hesab-setup", ir.vil3ntec.tohid.core.gateway.TillHesab(false, "off", null, flag = true))
  }

  @Test
  fun shots() {
    Assume.assumeTrue("EPAY_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    try {
      for (dark in listOf(false, true)) {
        phone(dark)
        desk(dark, openPanel = false)
        //  پیش از این شاخه «ثبتِ فروش» ِ کامپیوتر بی پنل همان دم فروش را ثبت می‌کرد ⇒ پنل فقط در «پس»
        if (tag != "before") {
          desk(dark, openPanel = true)
          afterOnly(dark)
        }
      }
    } finally {
      forget()
      CartStore(DesktopContext).clear()
    }
  }
}
