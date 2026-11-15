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
import ir.vil3ntec.tohid.core.onboarding.AuthStep
import ir.vil3ntec.tohid.core.onboarding.AuthUi
import ir.vil3ntec.tohid.data.LocationFix
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthActions
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthEnv
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthFlow
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ ۶۷l برای صاحب‌کار («صفحهٔ ورودِ قدیمی: سه خط و دایرهٔ بالا که تکان می‌خورد»): ورود، ساختِ حساب و
 *  «رمز را فراموش کرده‌ام» — گوشیِ ۳۶۰×۷۶۰dp (چگالیِ ۲) و پنجرهٔ کامپیوترِ ۱۲۸۰×۸۰۰dp (چگالیِ ۱)، روز و شب، فارسی/دری/پشتو
 *  راست‌به‌چپ و انگلیسی چپ‌به‌راست. فقط با `E67L_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست)؛ `E67L_TAG` (`before`/`after`)
 *  پسوندِ نامِ فایل است: `<پوشه>/<صفحه>-<phone|desk>-<زبان>-<tag>-<light|dark>.png`.
 */
@OptIn(ExperimentalTestApi::class)
class E67lShotsTest {

  private val out: File? = System.getenv("E67L_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val tag: String = System.getenv("E67L_TAG")?.takeIf { it.isNotBlank() } ?: "after"

  private fun shot(name: String, lang: Lang, dark: Boolean, desk: Boolean, content: @Composable () -> Unit) {
    val (wDp, hDp, dens) = if (desk) Triple(1280, 800, 1f) else Triple(360, 760, 2f)
    runDesktopComposeUiTest((wDp * dens).toInt(), (hDp * dens).toInt(), testTimeout = 3.minutes) {
      I18n.source = { lang }
      mainClock.autoAdvance = false
      setContent {
        CompositionLocalProvider(
          LocalDensity provides Density(dens, 1f),
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(wDp, hDp),
          ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout provides desk,
        ) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) { content() }
          }
        }
      }
      repeat(16) {
        mainClock.advanceTimeBy(250)
        Thread.sleep(40)
      }
      val img = captureToImage().toAwtImage()
      val dir = out!!.apply { mkdirs() }
      ImageIO.write(img, "png", File(dir, "$name-${if (desk) "desk" else "phone"}-${lang.code}-$tag-${if (dark) "dark" else "light"}.png"))
    }
  }

  private val env = AuthEnv(ready = true, otpReady = true, saved = emptyList(), done = false, secondsLeft = { 45 })

  @Test
  fun shots() {
    Assume.assumeTrue("E67L_SHOTS نیست", out != null)
    try {
      for (dark in listOf(false, true)) {
        for (desk in listOf(false, true)) shot("login", Lang.FA, dark, desk) { AuthFlow(AuthUi(), env, NoActions, onClose = null) }
        shot("signup", Lang.FA, dark, false) { AuthFlow(AuthUi(register = true), env, NoActions, onClose = null) }
        shot("forgot", Lang.FA, dark, false) { AuthFlow(AuthUi(step = AuthStep.FORGOT), env, NoActions, onClose = {}) }
      }
      for (lang in listOf(Lang.PRS, Lang.PS, Lang.EN)) shot("login", lang, false, false) { AuthFlow(AuthUi(), env, NoActions, onClose = null) }
      if (tag == "after") ring()
    } finally {
      I18n.source = { Lang.FA }
    }
  }

  /** هشت قابِ سرِ صفحهٔ ورود، هر ۱٫۷۵ ثانیه (۴۵ درجه از حلقهٔ ۱۴ ثانیه‌ای) ⇒ `ring-<n>.png` (برای نوارِ قاب‌ها) */
  private fun ring() = runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
    I18n.source = { Lang.FA }
    mainClock.autoAdvance = false
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(2f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { AuthFlow(AuthUi(), env, NoActions, onClose = null) }
        }
      }
    }
    repeat(8) { mainClock.advanceTimeBy(250); Thread.sleep(30) }
    val dir = File(out!!, "ring").apply { mkdirs() }
    for (i in 0 until 8) {
      ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "ring-$i.png"))
      repeat(7) { mainClock.advanceTimeBy(250); Thread.sleep(20) }
    }
  }

  private object NoActions : AuthActions {
    override fun edit(change: (AuthUi) -> AuthUi) {}
    override fun submitForm() {}
    override fun requestReset() {}
    override fun requestOtp() {}
    override fun resend() {}
    override fun submitCode(code: String, newPassword: String) {}
    override fun completeSignup(fix: LocationFix?) {}
    override fun joinWithStaffCode() {}
    override fun google() {}
    override fun skip() {}
    override fun pickSaved(entry: SavedLogins.Entry) {}
    override fun forgetSaved(entry: SavedLogins.Entry) {}
  }
}
