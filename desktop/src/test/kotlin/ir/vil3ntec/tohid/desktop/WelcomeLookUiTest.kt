package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.onboarding.AuthStep
import ir.vil3ntec.tohid.core.onboarding.AuthUi
import ir.vil3ntec.tohid.data.LocationFix
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.sync.SavedLogins
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.Brand
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3Depth
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthActions
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthEnv
import ir.vil3ntec.tohid.ui.v3.onboarding.AuthFlow
import ir.vil3ntec.tohid.ui.v3.onboarding.LocalWelcomeForeground
import ir.vil3ntec.tohid.ui.v3.onboarding.LockBody
import ir.vil3ntec.tohid.ui.v3.onboarding.WelcomeClock
import ir.vil3ntec.tohid.ui.v3.onboarding.WelcomeProbe
import ir.vil3ntec.tohid.ui.v3.onboarding.WelcomeTags
import ir.vil3ntec.tohid.ui.v3.onboarding.WelcomeTime
import ir.vil3ntec.tohid.ui.v3.onboarding.rememberWelcomeClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 *  صفحهٔ ورودِ قدیمی (صاحب‌کار ۱۴۰۵/۰۸/۲۴: «همونی که سه خط داشت و یک دایره بالا که تکون می‌خورد»):
 *   • ورود، ساختِ حساب و «فراموشی» پس‌زمینه (`WelcomeBackground`) و نشانِ چرخان (`WelcomeMark`) را دارند — یک پس‌زمینه برای
 *     همهٔ گام‌ها و یک نشان وسطِ بالا؛ قفلِ رمز همان `BrandMark` ِ پیشین را دارد و پس‌زمینهٔ ورود نه؛
 *   • نشان با هیچ نوشته‌ای (فارسی، دری، پشتو، انگلیسی) و با «برگشت» روی هم نمی‌افتد و نوشته‌ها زیرِ آن‌اند؛
 *   • ساعت فقط وقتی کسی روی صفحه است قاب می‌خواهد: صفحه رفت، برنامه پس‌زمینه، برگه رویش، یا «کاهشِ حرکت» ⇒ صفر قاب؛
 *     دوباره پیش‌رو ⇒ از همان لحظه ادامه، نه از آغاز و نه با پرش؛
 *   • زمان‌ها همان `rememberInfiniteTransition` ِ پیشین (۳۰ و ۹ ثانیهٔ پس‌زمینه، ۱۴ و ۳ ثانیهٔ نشان)؛
 *   • زیرعنوانِ روی تیره‌ترین آبیِ موج ≥ ۴٫۵:۱.
 */
class WelcomeLookUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val env = AuthEnv(ready = true, otpReady = true, saved = emptyList(), done = false, secondsLeft = { 45 })

  private fun forgetMotion() = ctx.getSharedPreferences("tohid", 0).edit().remove("motion").commit()

  @Before fun setUp() {
    I18n.source = { Lang.FA }
    forgetMotion()
    Motion.reducedForTest = false
    Motion.load(ctx)
    WelcomeProbe.frames = 0
  }

  @After fun tearDown() {
    Motion.reducedForTest = null
    forgetMotion()
    Motion.load(ctx)
    V3Depth.sheets.intValue = 0
    I18n.source = { Lang.FA }
  }

  private fun show(dir: LayoutDirection = LayoutDirection.Rtl, content: @Composable () -> Unit) = rule.setContent {
    CompositionLocalProvider(LocalContext provides ctx, LocalLayoutDirection provides dir) {
      TohidTheme(ThemeChoice.LIGHT) {
        //  پهنای گوشی
        Box(Modifier.size(360.dp, 760.dp)) { content() }
      }
    }
  }

  @Composable
  private fun Login(ui: AuthUi = AuthUi(), onClose: (() -> Unit)? = null) = AuthFlow(ui, env, NoActions, onClose)

  private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

  /** چند قاب با ساعتِ دستی (۱۰۰ میلی‌ثانیه هر بار) */
  private fun frames(ms: Long) {
    var left = ms
    while (left > 0) { rule.mainClock.advanceTimeBy(minOf(left, 100L)); left -= 100L }
  }

  @Test fun loginSignupAndForgotHaveTheOldBackgroundAndRotatingMark() {
    var ui by mutableStateOf(AuthUi())
    show { Login(ui, onClose = {}) }
    for (state in listOf(AuthUi(), AuthUi(register = true), AuthUi(step = AuthStep.FORGOT), AuthUi(step = AuthStep.JOIN))) {
      ui = state
      rule.mainClock.advanceTimeBy(2_000)
      rule.waitForIdle()
      rule.onAllNodesWithTag(WelcomeTags.BACKGROUND).assertCountEquals(1)
      rule.onAllNodesWithTag(WelcomeTags.MARK).assertCountEquals(1)
      //  پس‌زمینه تمام‌قاب، نشان وسطِ بالا
      val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
      val bg = bounds(WelcomeTags.BACKGROUND)
      assertEquals("${state.step}", root.width, bg.width, 1f)
      assertEquals("${state.step}", root.height, bg.height, 1f)
      val mark = bounds(WelcomeTags.MARK)
      assertTrue("${state.step}: وسط $mark در $root", abs(mark.center.x - root.center.x) < 2f)
      assertTrue("${state.step}: بالا $mark", mark.top < root.height * 0.2f)
    }
  }

  @Test fun theMarkNeverCollidesWithAnyLabelOrTheBackButtonInAllFourLanguages() {
    var lang by mutableStateOf(Lang.FA)
    rule.setContent {
      CompositionLocalProvider(LocalContext provides ctx, LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        TohidTheme(ThemeChoice.LIGHT) {
          Box(Modifier.size(360.dp, 760.dp)) { key(lang) { Login(AuthUi(register = true), onClose = {}) } }
        }
      }
    }
    for (l in listOf(Lang.FA, Lang.PRS, Lang.PS, Lang.EN)) {
      I18n.source = { l }
      lang = l
      rule.mainClock.advanceTimeBy(2_000)
      rule.waitForIdle()
      val mark = bounds(WelcomeTags.MARK)
      val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true).fetchSemanticsNodes()
      assertTrue("$l: نوشته‌ای نیست", texts.isNotEmpty())
      //  گرهِ بی‌اندازه (کادرِ بسته، گامِ رفته) دیده نمی‌شود
      texts.filter { it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }.forEach { n ->
        val b = n.boundsInRoot
        assertTrue("$l: «${n.config[SemanticsProperties.Text].joinToString()}» $b روی نشان $mark", !b.overlaps(mark) && b.top >= mark.bottom - 0.5f)
      }
      val back = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true).fetchSemanticsNodes()
        .filter { it.boundsInRoot.top < mark.bottom }
      assertTrue("$l: برگشت", back.isNotEmpty())
      back.forEach { assertTrue("$l: برگشت ${it.boundsInRoot} روی نشان $mark", !it.boundsInRoot.overlaps(mark)) }
    }
  }

  @Test fun theLockPinPadKeepsItsOwnLookWithoutTheLoginBackground() {
    show { LockBody(StrAuth.lockTitle, StrAuth.lockWrong, error = false, length = LockStore.NEW_PIN_LEN, filled = 0, shake = null, onDigit = {}, onErase = {}, onFinger = {}) }
    rule.mainClock.advanceTimeBy(1_000)
    rule.onAllNodesWithTag(WelcomeTags.BACKGROUND).assertCountEquals(0)
    rule.onAllNodesWithTag(WelcomeTags.MARK).assertCountEquals(0)
  }

  @Test fun theClockTicksOnlyWhileTheLoginIsOnScreen() {
    rule.mainClock.autoAdvance = false
    var shown by mutableStateOf(true)
    show { if (shown) Login() }
    frames(2_000)
    assertTrue("روی صفحه ⇒ قاب: ${WelcomeProbe.frames}", WelcomeProbe.frames > 10)
    shown = false
    frames(300)
    WelcomeProbe.frames = 0
    frames(3_000)
    assertEquals("صفحه رفت ⇒ هیچ قابی", 0L, WelcomeProbe.frames)
  }

  @Test fun inTheBackgroundNothingTicksAndOnReturnItContinuesFromTheSameMoment() {
    rule.mainClock.autoAdvance = false
    var foreground by mutableStateOf(true)
    var clock: WelcomeClock? = null
    show {
      CompositionLocalProvider(LocalWelcomeForeground provides foreground) {
        clock = rememberWelcomeClock()
        Login()
      }
    }
    frames(2_000)
    val before = clock!!.ms
    //  ساعتِ آزمون هر گام را به قابِ ۱۶ میلی‌ثانیه گرد می‌کند (۲۰ × ۱۱۲) ⇒ حدود، نه عددِ دقیق
    assertTrue("پیش‌رو ⇒ زمان جلو رفت: $before", before in 1_500L..2_500L)
    foreground = false
    frames(300)
    val paused = clock!!.ms
    WelcomeProbe.frames = 0
    frames(5_000)
    assertEquals("پس‌زمینه ⇒ هیچ قابی", 0L, WelcomeProbe.frames)
    assertEquals("پس‌زمینه ⇒ زمان ایستاد", paused, clock!!.ms)
    foreground = true
    frames(1_000)
    val after = clock!!.ms
    assertTrue("برگشت ⇒ از همان لحظه (نه صفر، نه با پرشِ پنج ثانیه): $paused ⇒ $after", after > paused && after - paused < 2_000L)
  }

  @Test fun underAnOpenSheetNothingTicks() {
    rule.mainClock.autoAdvance = false
    show { Login() }
    frames(500)
    V3Depth.sheets.intValue = 1
    frames(300)
    WelcomeProbe.frames = 0
    frames(3_000)
    assertEquals("برگه رویش ⇒ هیچ قابی", 0L, WelcomeProbe.frames)
    V3Depth.sheets.intValue = 0
    frames(1_000)
    assertTrue("برگه بسته ⇒ دوباره", WelcomeProbe.frames > 0)
  }

  @Test fun withReducedMotionNoClockIsEverStarted() {
    Motion.reducedForTest = true
    Motion.load(ctx)
    assertTrue(!Motion.enabled)
    rule.mainClock.autoAdvance = false
    show { Login() }
    frames(3_000)
    rule.onAllNodesWithTag(WelcomeTags.MARK).assertCountEquals(1)
    assertEquals("کاهشِ حرکت ⇒ هیچ قابی", 0L, WelcomeProbe.frames)
  }

  @Test fun theHeaderTextStaysReadableOverTheDeepestBlueOfTheWave() {
    var c: PhoneV3Colors? = null
    rule.setContent { TohidTheme(ThemeChoice.LIGHT) { c = PhoneV3.colors } }
    rule.waitForIdle()
    val colors = c!!
    //  تیره‌ترین رنگِ موج (`WAVE_EDGE` ِ `WelcomeDecor.kt`: آمیزهٔ ۵۲٪ ِ برند با سفید) — زیرعنوان و «۱ از ۳» ِ `GateFrame`
    val wave = lerp(Color.White, Brand.primary, 0.52f)
    val muted = lerp(colors.inkMuted, colors.ink, 0.5f)
    fun ratio(a: Color, b: Color): Float { val x = a.luminance(); val y = b.luminance(); return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f) }
    assertTrue("زیرعنوان روی موج: ${ratio(muted, wave)}", ratio(muted, wave) >= 4.5f)
    assertTrue("عنوان روی موج: ${ratio(colors.ink, wave)}", ratio(colors.ink, wave) >= 7f)
  }

  @Test fun theTimingsAreThoseOfTheOldInfiniteTransitions() {
    //  flow: خطی ۳۰ ثانیه، از نو
    assertEquals(0f, WelcomeTime.restart(0, 30_000), 1e-6f)
    assertEquals(0.5f, WelcomeTime.restart(15_000, 30_000), 1e-6f)
    assertEquals(0f, WelcomeTime.restart(30_000, 30_000), 1e-6f)
    //  breathe: ۰٫۸۵ ⇒ ۱٫۱۲ در ۹ ثانیه و برگشت (سینوسی؛ نیمه دقیقاً وسط)
    assertEquals(0.85f, WelcomeTime.reverse(0, 9_000, 0.85f, 1.12f), 1e-4f)
    assertEquals(0.985f, WelcomeTime.reverse(4_500, 9_000, 0.85f, 1.12f), 2e-3f)
    assertEquals(1.12f, WelcomeTime.reverse(9_000, 9_000, 0.85f, 1.12f), 1e-4f)
    assertEquals(0.985f, WelcomeTime.reverse(13_500, 9_000, 0.85f, 1.12f), 2e-3f)
    assertEquals(0.85f, WelcomeTime.reverse(18_000, 9_000, 0.85f, 1.12f), 1e-4f)
    //  spin: یک دور در ۱۴ ثانیه؛ glow ۰٫۳۵ ⇒ ۰٫۶ در ۳ ثانیه
    assertEquals(90f, WelcomeTime.restart(3_500, 14_000) * 360f, 1e-3f)
    assertEquals(0.6f, WelcomeTime.reverse(3_000, 3_000, 0.35f, 0.6f), 1e-4f)
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
