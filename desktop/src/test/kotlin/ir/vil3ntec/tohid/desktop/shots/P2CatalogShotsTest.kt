package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.common.ViewModePref
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.SummaryFold
import ir.vil3ntec.tohid.ui.v3.products.ProductFormNav
import ir.vil3ntec.tohid.ui.v3.products.ProductRowDraft
import ir.vil3ntec.tohid.ui.v3.products.QuickProductScreen
import ir.vil3ntec.tohid.ui.v3.rememberV3Toasts
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «پیش/پس» ِ کالاها، گدام و «کالای تازه» ِ گوشی (۱۴۰۵/۰۸/۲۴، صاحب‌کار: «هیچ کدوم از این‌ها رو دوست ندارم … تمامِ بخش‌ها
 *  رو می‌خوام درست و اصولی») — گوشیِ ۳۶۰×۷۶۰dp با چگالیِ ۲، فارسی، روز و شب؛ دفترِ پرِ ۴۰ کالا (`StockDemo.full`: کم، تمام‌شده،
 *  نزدیکِ انقضا و منقضی) و دفترِ یک‌کالاییِ عکسِ صاحب‌کار («شکلات»). فقط با `P2_SHOTS=<پوشه>` (`<نام>-<light|dark>.png`).
 */
@OptIn(ExperimentalTestApi::class)
class P2CatalogShotsTest {

  private val out: File? = System.getenv("P2_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  /** `P2_ONLY=products-list,stock-one` ⇒ فقط همین عکس‌ها */
  private val only: Set<String>? = System.getenv("P2_ONLY")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.toSet()

  private fun wanted(name: String) = only == null || name in only

  private fun frame(file: String, dark: Boolean, heightDp: Int = 760, content: @Composable () -> Unit) =
    runDesktopComposeUiTest(720, heightDp * 2, testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(2f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, heightDp),
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
          }
        }
      }
      repeat(24) {
        mainClock.advanceTimeBy(250)
        Thread.sleep(60)
      }
      ImageIO.write(captureToImage().toAwtImage(), "png", File(out!!.apply { mkdirs() }, file))
    }

  /** یک مسیر، روز و شب (کارتِ خلاصه به حالتِ پیش‌فرض: باز، همان که صاحب‌کار دید) */
  private fun route(name: String, path: String, data: ShopData, heightDp: Int = 760) {
    if (!wanted(name)) return
    for (dark in listOf(false, true)) {
      frame("$name-${if (dark) "dark" else "light"}.png", dark, heightDp) {
        val store = remember { RouteUiHarness.loadedStore(data) }
        V3RoutePreview(path, store, dark = dark)
      }
    }
  }

  /** دفترِ عکسِ صاحب‌کار: یک کالا «شکلات» (دستهٔ «شکلات ها»)، ۱۰ کارتنِ ۱۵تایی = ۱۵۰ عدد، فروش ۱۲۰ ؋ */
  private fun one(): ShopData {
    val p = Product(id = "c1", name = "شکلات", unit = "عدد", purchasePrice = 100.0, salePrice = 120.0, minStock = 5.0, category = "شکلات ها")
    val e = WarehouseEntry(id = "e1", productId = "c1", cartons = 10.0, perCarton = 15.0, units = 150.0, unit = "عدد", price = 100.0, date = todayIso(), createdAt = 1_000)
    return ShopData(products = listOf(p), productCategories = listOf("آبمیوه", "شکلات ها"), warehouseEntries = listOf(e))
  }

  @Test
  fun shots() {
    Assume.assumeTrue("P2_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    //  کارتِ خلاصه باز (پیش‌فرض) — همان حالِ عکسِ صاحب‌کار
    val raw = DesktopContext.getSharedPreferences(ViewModePref.PREFS, 0)
    raw.edit().remove(SummaryFold.key("products")).remove(SummaryFold.key("stock")).commit()
    val full = StockDemo.full()
    route("products-list", "products?view=list", full)
    route("products-grid", "products?view=grid", full)
    route("products-one-list", "products?view=list", one())
    route("products-one-grid", "products?view=grid", one())
    route("stock-list", "stock", full)
    route("stock-one", "stock", one())
    route("newproduct", "products/new", full)
    quick("newproduct-more", full, 760)
    quick("newproduct-more-full", full, 1180)
    route("newproduct-bulk", "products/new?many=1", full)
    route("stock-bulk", "stock/bulk-products", full)
  }

  /** فرمِ ساده با «بیشتر» ِ باز و پیش‌نویسِ پر (همان کالای عکسِ صاحب‌کار) */
  private fun quick(name: String, data: ShopData, heightDp: Int) {
    if (!wanted(name)) return
    val seed = ProductRowDraft(name = "شکلات", sale = "120", purchase = "100", category = "شکلات ها", unit = "عدد", stock = "150", expiry = "14060120")
    for (dark in listOf(false, true)) {
      frame("$name-${if (dark) "dark" else "light"}.png", dark, heightDp) {
        val toasts = rememberV3Toasts(remember { SnackbarHostState() })
        val store = remember { RouteUiHarness.memoryStore(data) }
        CompositionLocalProvider(LocalV3Toasts provides toasts) {
          androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(PhoneV3.colors.bg)) {
            QuickProductScreen(store, data, toasts, null, ProductFormNav(back = {}, scan = {}), seed = seed, startMore = true) { _, _ -> }
          }
        }
      }
    }
  }
}
