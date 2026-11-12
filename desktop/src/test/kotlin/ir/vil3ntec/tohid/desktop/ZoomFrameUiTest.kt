package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrImageChanges
import ir.vil3ntec.tohid.core.image.ImageQuality
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.products.ZoomFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  IMG-11 — قابِ بزرگ‌نماییِ «نسخه‌های عکس» (`ZoomFrame`، گوشی و کامپیوتر) با آزمونِ خودکارِ صفحه: نسخهٔ ۲۴۰۰×۱۸۰۰ در قابِ
 *  ۳۲۰×۲۴۰ (چگالیِ ۱) ⇒ آغاز ۱۰۰٪ با «کوچک‌تر» و «اندازهٔ نخست» ِ خاموش؛ «بزرگ‌تر» پله‌پله تا اندازهٔ واقعیِ عکس (۷۵۰٪ = هر پیکسل
 *  یک پیکسل) و نه بیشتر؛ «اندازهٔ نخست» ⇒ ۱۰۰٪. صفحه‌خوان همان کارهای دو انگشت را دارد (بزرگ/کوچک و دیدنِ هر سو).
 */
class ZoomFrameUiTest {

  @get:Rule val rule = createComposeRule()

  private fun button(text: String) = rule.onNode(hasText(text) and hasClickAction())
  private fun shows(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

  private fun show() {
    I18n.source = { Lang.FA }
    val m = ImageQuality.Measure(ImageQuality.Kind.JPEG, 2400, 1800, 600_000, 85)
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext, LocalDensity provides Density(1f)) {
        TohidTheme(ThemeChoice.LIGHT) { ZoomFrame(ImageBitmap(2400, 1800), m, 320.dp, 240.dp) }
      }
    }
  }

  /** کارِ سفارشیِ صفحه‌خوان روی قاب (همان کشیدنِ دو انگشت) */
  private fun act(label: String) {
    val frame = rule.onNode(hasContentDescription(StrImageChanges.zoomFrame))
    rule.runOnIdle { frame.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }.action() }
    rule.waitForIdle()
  }

  @Test fun screenReaderHasTheSameZoomAndPanAsTwoFingers() {
    show()
    val labels = rule.onNode(hasContentDescription(StrImageChanges.zoomFrame)).fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }
    assertEquals(
      listOf(StrImageChanges.zoomIn, StrImageChanges.zoomOut, StrImageChanges.zoomShowLeft, StrImageChanges.zoomShowRight, StrImageChanges.zoomShowTop, StrImageChanges.zoomShowBottom),
      labels,
    )
    act(StrImageChanges.zoomIn)
    assertTrue(shows(StrImageChanges.zoomLevel(150)))
    repeat(12) { act(StrImageChanges.zoomShowLeft); act(StrImageChanges.zoomShowTop) }   //  هرگز بیرون از قاب و بی خطا
    rule.onNode(hasContentDescription(StrImageChanges.zoomFrame)).assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, StrImageChanges.zoomLevel(150)))
    act(StrImageChanges.zoomOut)
    assertTrue(shows(StrImageChanges.zoomLevel(100)))
  }

  @Test fun buttonsZoomInStepsUpToRealPixelsAndBack() {
    show()
    assertTrue(shows(StrImageChanges.zoomLevel(100)))
    button(StrImageChanges.zoomOut).assertIsNotEnabled()
    button(StrImageChanges.zoomReset).assertIsNotEnabled()
    button(StrImageChanges.zoomIn).assertIsEnabled().performClick()
    rule.waitForIdle()
    assertTrue(shows(StrImageChanges.zoomLevel(150)))
    button(StrImageChanges.zoomOut).assertIsEnabled()
    repeat(4) { button(StrImageChanges.zoomIn).performClick(); rule.waitForIdle() }
    assertTrue("سقف همان اندازهٔ واقعیِ عکس", shows(StrImageChanges.zoomLevel(750)))
    button(StrImageChanges.zoomIn).assertIsNotEnabled()
    button(StrImageChanges.zoomReset).performClick()
    rule.waitForIdle()
    assertTrue(shows(StrImageChanges.zoomLevel(100)))
    button(StrImageChanges.zoomOut).assertIsNotEnabled()
  }
}
