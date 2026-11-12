package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadItem
import ir.vil3ntec.tohid.ui.v3.sales.cart.KeypadSheet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  S110 — صندوقِ بومی زیرِ «کاهشِ حرکت» ِ گوشی:
 *   ۱) پیش‌فرض همان تنظیمِ گوشی است (`Motion.choice = auto`): گوشیِ «کاهشِ حرکت» ⇒ برنامه هم بی‌حرکت، بی آن‌که کاربر
 *      جایی چیزی بزند (پیش‌تر پیش‌فرض `full` بود و تنظیمِ گوشی نادیده می‌ماند)؛ «انیمیشن روشن» ِ خودِ کاربر همچنان برنده.
 *   ۲) صفحه‌کلیدِ مقدارِ صندوق (`KeypadSheet` روی `V3Sheet`): «افزودن به سبد» زیرِ کاهشِ حرکت در همان قاب کار می‌کند — برگه
 *      بی انیمیشنِ پایین‌رفتن کنار می‌رود؛ با حرکتِ روشن همان کار پس از پایانِ انیمیشن است (مرزِ مقابل، تا سنجه کور نباشد).
 */
class ReducedMotionTillUiTest {

  @get:Rule val rule = createComposeRule()

  private val ctx = DesktopContext
  private val tea = Product(id = "tea", name = "چای", unit = "عدد", salePrice = 90.0)

  private fun forgetChoice() = ctx.getSharedPreferences("tohid", 0).edit().remove("motion").commit()

  @Before fun setUp() { I18n.source = { Lang.FA }; forgetChoice() }

  @After fun tearDown() {
    Motion.reducedForTest = null
    forgetChoice()
    Motion.load(ctx)
  }

  @Test fun phoneReduceMotionIsTheDefaultAndTheUsersOwnChoiceStillWins() {
    Motion.reducedForTest = true
    Motion.load(ctx)
    assertEquals("auto", Motion.choice)
    assertFalse("گوشیِ «کاهشِ حرکت» ⇒ برنامه بی‌حرکت، بی هیچ تنظیمی", Motion.enabled)
    assertEquals(0, MotionTokens.ms(MotionTokens.LONG))
    Motion.set(ctx, "full")
    assertTrue("«انیمیشن روشن» ِ خودِ کاربر برنده است", Motion.enabled)
    Motion.reducedForTest = false
    forgetChoice()
    Motion.load(ctx)
    assertTrue("گوشیِ بی «کاهشِ حرکت» ⇒ حرکت روشن", Motion.enabled)
  }

  /** «افزودن به سبد» با ۳ عدد؛ تا چند قاب پس از زدن، کارِ دکمه انجام شده؟ */
  private fun addAfterFrames(frames: Int): Double? {
    var got: Double? = null
    rule.setContent {
      CompositionLocalProvider(LocalContext provides ctx) {
        TohidTheme(ThemeChoice.LIGHT) { KeypadSheet(KeypadItem(tea, stock = 10.0, inCart = 0.0), { got = it }, {}, initial = "3") }
      }
    }
    rule.waitForIdle()
    rule.mainClock.autoAdvance = false
    rule.onNodeWithText(StrSale.addToCart).performClick()
    repeat(frames) { rule.mainClock.advanceTimeByFrame() }
    return got
  }

  @Test fun underReducedMotionTheKeypadAddsInTheSameFrameWithoutWaitingForTheSheet() {
    Motion.reducedForTest = true
    Motion.load(ctx)
    assertEquals(3.0, addAfterFrames(2) ?: -1.0, 0.0)
  }

  @Test fun withMotionOnTheSameAddWaitsForTheSheetAnimation() {
    Motion.reducedForTest = false
    Motion.load(ctx)
    assertTrue(Motion.enabled)
    assertEquals("با حرکتِ روشن برگه پیش از کار پایین می‌رود", null, addAfterFrames(2))
  }
}
