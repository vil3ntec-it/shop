package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ShopTheme
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.ThemeStore
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.shell.DeskHomeScreen
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  ۶۸c — عکسِ خانه پیش و پس از پالتِ لوکس (هماهنگ‌کننده: «دقیقاً چه چیزی در خانه عوض شد»): خانهٔ گوشی ۳۶۰×۷۶۰dp (فارسی، تمِ
 *  «آسمان» و «شامگاه»، روز و شب) و خانهٔ کامپیوتر ۱۳۶۶×۷۶۸ (روز)، با همان دفترِ نمایشی (`MoneyDemo.full`) و «کاهشِ حرکت» (حالتِ
 *  نشسته). «پیش» همین آزمون روی شاخهٔ پایه است. فقط با `E68C_HOME=<پوشه>` (`home-<sky|dusk>-<light|dark>.png`، `home-desk-light.png`).
 */
@OptIn(ExperimentalTestApi::class)
class E68cHomeShotsTest {

  private val out: File? = System.getenv("E68C_HOME")?.takeIf { it.isNotBlank() }?.let(::File)

  private fun frame(file: String, widthDp: Int, heightDp: Int, density: Float, dark: Boolean, desk: Boolean, content: @Composable () -> Unit) =
    runDesktopComposeUiTest((widthDp * density).toInt(), (heightDp * density).toInt(), testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(density, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(widthDp, heightDp),
          LocalDeskLayout provides desk,
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
              Box(Modifier.fillMaxSize().background(Shop.colors.bg)) { content() }
            }
          }
        }
      }
      repeat(24) {
        mainClock.advanceTimeBy(250)
        Thread.sleep(60)
      }
      ImageIO.write(captureToImage().toAwtImage(), "png", File(out!!.apply { mkdirs() }, file))
    }

  @Test
  fun shots() {
    Assume.assumeTrue("E68C_HOME نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val reduced = Motion.reducedForTest
    Motion.reducedForTest = true
    Motion.load(DesktopContext)
    val data = MoneyDemo.full()
    try {
      for (theme in listOf(ShopTheme.SKY, ShopTheme.DUSK)) for (dark in listOf(false, true)) {
        ThemeStore.preview(theme)
        frame("home-${theme.id}-${if (dark) "dark" else "light"}.png", 360, 760, 2f, dark, desk = false) {
          val store = remember { RouteUiHarness.loadedStore(data) }
          V3RoutePreview("home", store, dark = dark)
        }
      }
      ThemeStore.preview(ShopTheme.SKY)
      frame("home-desk-light.png", 1366, 768, 1f, dark = false, desk = true) {
        DeskHomeScreen(data, onOpen = {})
      }
    } finally {
      ThemeStore.preview(ShopTheme.SKY)
      Motion.reducedForTest = reduced
      Motion.load(DesktopContext)
    }
  }
}
