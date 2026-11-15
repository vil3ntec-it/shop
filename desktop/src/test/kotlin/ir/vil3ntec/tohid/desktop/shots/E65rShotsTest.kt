package ir.vil3ntec.tohid.desktop.shots

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
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.screens.MoreShot
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ ۶۵r برای صاحب‌کار («یک سبکِ ردیف»): «بیشتر»، صفحهٔ اولِ تنظیمات، «دکان و امنیت»، «فروش و پرداخت»،
 *  «کالا و انبار»، «دستگاه‌ها» و «حساب من» — گوشیِ ۳۶۰×۷۶۰dp با چگالیِ ۲، فارسی و راست‌به‌چپ، روشن و تیره، با پوسته و
 *  نوارِ شناور همان‌طور که برنامه بازشان می‌کند (`V3RoutePreview`).
 *  فقط با `E65R_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست)؛ `E65R_TAG` (`before`/`after`) پسوندِ نامِ فایل است:
 *  `<پوشه>/<صفحه>-<tag>-<light|dark>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class E65rShotsTest {

  private val out: File? = System.getenv("E65R_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val tag: String = System.getenv("E65R_TAG")?.takeIf { it.isNotBlank() } ?: "after"

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
        TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
          //  فارسی راست‌به‌چپ — همان جهتِ گوشی
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
        }
      }
    }
    repeat(24) {
      mainClock.advanceTimeBy(250)
      Thread.sleep(60)
    }
    val img = captureToImage().toAwtImage()
    val dir = out!!.apply { mkdirs() }
    ImageIO.write(img, "png", File(dir, "$name-$tag-${if (dark) "dark" else "light"}.png"))
  }

  private fun route(name: String, path: String, data: ShopData) {
    for (dark in listOf(false, true)) shot(name, dark) {
      val store = remember { loadedStore(data) }
      V3RoutePreview(path, store, dark)
    }
  }

  /** دفترِ حافظه‌ایِ «خوانده‌شده» — بی آن صفحه‌ها اسکلت می‌کشند */
  private fun loadedStore(d: ShopData): ir.vil3ntec.tohid.data.ShopStore = RouteUiHarness.memoryStore(d).also { s ->
    @Suppress("UNCHECKED_CAST")
    (ir.vil3ntec.tohid.data.ShopStore::class.java.getDeclaredField("_loaded").apply { isAccessible = true }.get(s) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>).value = true
  }

  @Test
  fun shots() {
    Assume.assumeTrue("E65R_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val d = StockDemo.full()
    route("more", "more", d)
    MoreShot.scrollToEnd.value = true
    try { route("more-settings", "more", d) } finally { MoreShot.scrollToEnd.value = false }
    route("settings", "settings", d)
    route("settings-shop", "settings/shop", d)
    route("settings-sales", "settings/sales", d)
    route("settings-goods", "settings/goods", d)
    route("devices", "devices", d)
    route("account", "profile", d)
  }
}
