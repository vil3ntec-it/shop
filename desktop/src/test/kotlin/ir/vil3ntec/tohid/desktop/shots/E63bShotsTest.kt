package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.PurchaseDemo
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.money.MoneyPaths
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های «خریدها» (e-rows-63b) برای صاحب‌کار: فهرست، جزئیاتِ یک خرید و صفحهٔ تأمین‌کننده — گوشیِ ۳۶۰×۷۶۰dp با چگالیِ ۲،
 *  روشن و تیره، فارسی. فقط با `E63B_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست؛ بی آن کنار می‌رود).
 */
@OptIn(ExperimentalTestApi::class)
class E63bShotsTest {

  private val out: File? = System.getenv("E63B_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private fun shot(name: String, dark: Boolean, path: String, data: ShopData) = runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
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
          val store = remember { RouteUiHarness.memoryStore(data) }
          V3RoutePreview(path, store, dark)
        }
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

  private fun both(name: String, path: String, data: ShopData) {
    shot(name, false, path, data)
    shot(name, true, path, data)
  }

  @Test
  fun shots() {
    Assume.assumeTrue("E63B_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val d = PurchaseDemo.full()
    val big = PurchaseViews.all(d, todayIso()).first { it.no == PurchaseDemo.BIG_NO }
    both("purchases-list", MoneyPaths.PURCHASING, d)
    both("purchase-detail", MoneyPaths.purchase(big.key), d)
    both("supplier-page", MoneyPaths.supplier(PurchaseDemo.ALBORZ), d)
    both("suppliers-tab", MoneyPaths.purchasing(tab = "suppliers"), d)
    both("new-purchase", MoneyPaths.newPurchase(PurchaseDemo.ALBORZ), d)
  }
}
