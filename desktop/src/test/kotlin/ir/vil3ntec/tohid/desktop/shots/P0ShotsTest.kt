package ir.vil3ntec.tohid.desktop.shots

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
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.FakePurchaseOrders
import ir.vil3ntec.tohid.desktop.PoDemoOrders
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.desktop.TestBackend
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «پیش/پس» ِ کارِ `claude/e-phone-p0` (کالبدِ یکسانِ صفحه) برای صاحب‌کار و هماهنگ‌کننده: جزئیاتِ کالا، جزئیاتِ انبار،
 *  فهرست و جزئیاتِ سفارشِ خرید (با سرورِ ساختگیِ [FakePurchaseOrders]، همان دادهٔ `PurchaseOrderRoutesUiTest`) — گوشیِ
 *  ۳۶۰×۷۶۰dp با چگالیِ ۲، فارسی و راست‌به‌چپ، روز و شب، مسیر همان‌طور که برنامه بازش می‌کند (`V3RoutePreview`). «پیش» همین آزمون
 *  روی سرِ شاخه پیش از کوچ است. فقط با `P0_SHOTS=<پوشه>` (`<نام>-<light|dark>.png`)؛ `P0_SHOTS_ONLY=a,b` فقط همین نام‌ها.
 */
@OptIn(ExperimentalTestApi::class)
class P0ShotsTest {

  private val out: File? = System.getenv("P0_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val only: Set<String>? = System.getenv("P0_SHOTS_ONLY")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.toSet()

  /** یک مسیر در یک تم؛ [server] ⇒ سرورِ ساختگیِ سفارشِ خرید در طولِ همین قاب */
  private fun shot(name: String, path: String, data: ShopData, dark: Boolean, server: Boolean) {
    val backend = if (server) TestBackend().also { FakePurchaseOrders(it.server, seed = PoDemoOrders.all) } else null
    try {
      runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
        I18n.source = { Lang.FA }
        mainClock.autoAdvance = false
        setContent {
          CompositionLocalProvider(
            LocalDensity provides Density(2f, 1f),
            LocalContext provides DesktopContext,
            LocalView provides remember { android.view.View(DesktopContext) },
            LocalConfiguration provides Configuration(360, 760),
          ) {
            TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
              CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val store = remember { RouteUiHarness.loadedStore(data) }
                V3RoutePreview(path, store, dark = dark)
              }
            }
          }
        }
        //  پاسخِ سرورِ ساختگی روی رشتهٔ دیگری می‌آید؛ ساعت را آرام جلو می‌بریم تا برسد و حرکت‌ها بنشینند
        repeat(32) {
          mainClock.advanceTimeBy(250)
          Thread.sleep(80)
        }
        ImageIO.write(captureToImage().toAwtImage(), "png", File(out!!.apply { mkdirs() }, "$name-${if (dark) "dark" else "light"}.png"))
      }
    } finally {
      backend?.close()
    }
  }

  @Test
  fun shots() {
    Assume.assumeTrue("P0_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val stock = StockDemo.full()
    val money = MoneyDemo.full()
    val pages = listOf(
      Triple("product-detail", "products/${StockDemo.RICE}", false),
      Triple("stock-item", "stock/${StockDemo.RICE}", false),
      Triple("po-list", "purchasing/orders", true),
      Triple("po-draft", "purchasing/orders/${PoDemoOrders.draft.id}", true),
      Triple("po-partial", "purchasing/orders/${PoDemoOrders.partial.id}", true),
    )
    for ((name, path, server) in pages) {
      if (only != null && name !in only) continue
      for (dark in listOf(false, true)) shot(name, path, if (server) money else stock, dark, server)
    }
  }
}
