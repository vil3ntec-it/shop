package ir.vil3ntec.tohid.ui

import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.Lang
import org.junit.Assert.assertEquals
import org.junit.Test

/** جهتِ ریشهٔ هر پنجره (`ProvideLang`) از زبان می‌آید */
class LangUiTest {

  @Test
  fun `فارسی و پشتو راست‌به‌چپ، انگلیسی چپ‌به‌راست`() {
    assertEquals(LayoutDirection.Rtl, directionOf(Lang.FA))
    assertEquals(LayoutDirection.Rtl, directionOf(Lang.PS))
    assertEquals(LayoutDirection.Ltr, directionOf(Lang.EN))
  }
}
