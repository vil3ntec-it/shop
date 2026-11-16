package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import ir.vil3ntec.tohid.ui.v3.settings.sections.SettingsShotState
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عاملِ e-newinstall — عکس‌های صفحهٔ همگام‌سازی در حالت‌های تازهٔ گره (گوشیِ ۳۶۰×۷۶۰dp، چگالیِ ۲، فارسی، روز و شب):
 *  مهمان («وارد حساب نشده‌اید» + ورود)، حسابِ بی دکان (+ ساختن/پیوستن)، ردِ سرور با پیامِ خودش، خطای ناشناخته با «جزئیات»،
 *  و برگهٔ «جزئیات» با «رونوشت». فقط با `NI_SHOTS=<پوشه>`.
 */
@OptIn(ExperimentalTestApi::class)
class NewInstallSyncShotsTest {

  private val out: File? = System.getenv("NI_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)

  private fun frame(file: String, dark: Boolean, heightDp: Int = 760) =
    runDesktopComposeUiTest(720, heightDp * 2, testTimeout = 3.minutes) {
      I18n.source = { Lang.FA }
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(2f, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(360, heightDp),
          LocalDeskLayout provides false,
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
              Box(Modifier.fillMaxSize().background(PhoneV3.colors.bg)) {
                val store = remember { RouteUiHarness.loadedStore(ShopData()) }
                V3RoutePreview("settings/data/sync", store, dark = dark)
              }
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

  private fun state(name: String, signedIn: Boolean, error: String?, code: String?, details: Boolean = false) {
    SettingsShotState.signedIn = signedIn
    SettingsShotState.syncError = error
    SettingsShotState.syncCode = code
    SettingsShotState.syncDetailsOpen = details
    for (dark in listOf(false, true)) frame("$name-${if (dark) "dark" else "light"}.png", dark)
  }

  @Test
  fun shots() {
    Assume.assumeTrue("NI_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val reduced = Motion.reducedForTest
    Motion.reducedForTest = true
    Motion.load(DesktopContext)
    try {
      state("sync-guest", signedIn = false, error = null, code = null)
      state("sync-no-shop", signedIn = true, error = "برای این حساب دکانی ثبت نشده است", code = "no_shop")
      state("sync-refused", signedIn = true, error = "این قابلیت در اشتراکِ فعلی نیست", code = "feature_locked")
      val tech = "java.net.ProtocolException: unexpected end of stream on https://api.vill3n.top/api/v1/sync/v1/push"
      state("sync-unknown", signedIn = true, error = tech, code = null)
      state("sync-details-sheet", signedIn = true, error = tech, code = null, details = true)
    } finally {
      SettingsShotState.signedIn = null
      SettingsShotState.syncError = null
      SettingsShotState.syncCode = null
      SettingsShotState.syncDetailsOpen = false
      Motion.reducedForTest = reduced
    }
  }
}
