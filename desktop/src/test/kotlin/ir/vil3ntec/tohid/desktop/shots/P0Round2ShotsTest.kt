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
import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.data.StockPlacesPref
import ir.vil3ntec.tohid.ui.PromoLive
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  دورِ دومِ عکس‌های «پیش/پس» ِ `claude/e-phone-p0`: صفحه‌های پیشینِ `Chrome.SHELL` که روی کالبدِ یکسان آمدند — فاکتورهای تأمین‌کننده
 *  (فهرست و یک سفارش، سرورِ ساختگیِ [FakePurchaseOrders])، تغییرِ عکس‌ها و استودیو (بی سرور: حالتِ «روشن نیست»)، محل‌ها و
 *  بسته‌بندی (دو درِ روشن)، گزارشِ آنلاین و شعبه‌ها (بی سرور)، تخفیف‌ها (دو قاعدهٔ نمایشی، مدیر). گوشیِ ۳۶۰×۷۶۰dp، چگالیِ ۲،
 *  فارسی، روز و شب. فقط با `P0R2_SHOTS=<پوشه>`؛ `P0R2_ONLY=a,b` فقط همین نام‌ها.
 */
@OptIn(ExperimentalTestApi::class)
class P0Round2ShotsTest {

  private val out: File? = System.getenv("P0R2_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val only: Set<String>? = System.getenv("P0R2_ONLY")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.toSet()

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

  private fun setDemo(on: Boolean) =
    PromoLive::class.java.getDeclaredField("isDemo").apply { isAccessible = true }.setBoolean(PromoLive, on)

  @Test
  fun shots() {
    Assume.assumeTrue("P0R2_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    StockPlacesPref.setPlaces(DesktopContext, true)
    StockPlacesPref.setRepack(DesktopContext, true)
    val demo = PromoLive.isDemo
    val before = PromoLive.snap.value
    //  حالتِ نمایشی تا `refresh` سرور را نپرسد؛ پس از عکس‌ها همان حالتِ پیشین (همان JVM ِ آزمون‌های دیگر)
    setDemo(true)
    PromoLive.snap.value = PromoEngine.Snapshot(
      enabled = true, manage = true,
      rules = listOf(
        PromoCore.Rule(id = "r1", name = "۱۰٪ چای سبز", kind = "percent", bp = 1000, channels = listOf("pos")),
        PromoCore.Rule(id = "r2", name = "کدِ عید", kind = "percent", bp = 500, code = "EID", channels = listOf("pos")),
      ),
    )
    try {
      val money = MoneyDemo.full()
      val stock = StockDemo.full()
      val pages = listOf(
        Triple("bills-list", "purchasing/bills", true),
        Triple("bill-detail", "purchasing/bills/${PoDemoOrders.partial.id}", true),
        Triple("image-changes", "stock/images/changes", false),
        Triple("image-change", "stock/images/changes/ch1", false),
        Triple("image-studio", "stock/images/studio", false),
        Triple("stock-places", "stock/places", false),
        Triple("online-report", "reports/online", false),
        Triple("branches-report", "reports/branches", false),
        Triple("promos", "promos", false),
      )
      for ((name, path, server) in pages) {
        if (only != null && name !in only) continue
        for (dark in listOf(false, true)) shot(name, path, if (server) money else stock, dark, server)
      }
    } finally {
      setDemo(demo)
      PromoLive.snap.value = before
      StockPlacesPref.setPlaces(DesktopContext, false)
      StockPlacesPref.setRepack(DesktopContext, false)
    }
  }
}
