package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.LocalV3SystemBottom
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListActions
import ir.vil3ntec.tohid.ui.v3.money.DebtorsListScreen
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleActions
import ir.vil3ntec.tohid.ui.v3.sales.cart.SaleScreen
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ ۶۳w برای صاحب‌کار (صندوق، تاریخچهٔ فروش، مصارف، کالاها، انبار، قرض‌داران، تخفیف‌ها، گزارش‌ها):
 *  گوشیِ ۳۶۰×۷۶۰dp با چگالیِ ۲، روشن و تیره، فارسی — با نوارِ شناورِ پایین، همان‌طور که گوشی نشان می‌دهد.
 *  فقط با `E63W_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست؛ بی آن کنار می‌رود).
 */
@OptIn(ExperimentalTestApi::class)
class E63wShotsTest {

  private val out: File? = System.getenv("E63W_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private val noSale = SaleActions({}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
  private val noDebtors = DebtorsListActions(onBack = null, onOpen = {}, onEdit = {}, onDelete = {}, onAdd = {})

  /** پوستهٔ گوشی برای صفحه‌ای که مسیرش در آزمون پشتِ سدِ اشتراک است: همان جای نوار و همان نوار */
  @Composable
  private fun ShellLike(current: String, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalV3SystemBottom provides 0.dp, LocalDeskLayout provides false, ir.vil3ntec.tohid.ui.v3.LocalV3NavSearch provides true) {
      Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
        Box(Modifier.fillMaxSize().padding(bottom = PhoneNav.RESERVE)) { content() }
        PhoneNavBar(NavLayouts.DEFAULT, current, onPick = {}, onEdit = null, modifier = Modifier.align(Alignment.BottomCenter))
      }
    }
  }

  private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) = runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
    I18n.source = { Lang.FA }
    mainClock.autoAdvance = false
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(2f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) { content() }
      }
    }
    repeat(24) {
      mainClock.advanceTimeBy(250)
      Thread.sleep(60)
    }
    val img = captureToImage().toAwtImage()
    val dir = out!!.apply { mkdirs() }
    ImageIO.write(img, "png", File(dir, "$name-${if (dark) "dark" else "light"}-fa.png"))
  }

  private fun both(name: String, content: @Composable (dark: Boolean) -> Unit) {
    shot(name, false) { content(false) }
    shot(name, true) { content(true) }
  }

  private fun route(name: String, path: String, data: ShopData) = both(name) { dark ->
    val store = remember { RouteUiHarness.memoryStore(data) }
    V3RoutePreview(path, store, dark)
  }

  @Test
  fun shots() {
    Assume.assumeTrue("E63W_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val stock = StockDemo.full()
    val money = MoneyDemo.full()
    both("till") { ShellLike("sale") { SaleScreen(stock, emptyList(), 0.0, null, listMode = false, actions = noSale) } }
    route("sales-history", "sales", money)
    route("expenses", "expenses", money)
    route("products", "products", stock)
    route("stock", "stock", stock)
    both("debtors") { ShellLike("debtors") { DebtorsListScreen(money, null, null, noDebtors) } }
    route("reports", "reports", money)
    route("promos", "promos", stock)
  }
}
