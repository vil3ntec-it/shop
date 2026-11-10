package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.pos.PromoLines
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 *  PRM-18 — «نمایشِ تخفیف‌های قابلِ اعمال به صندوق‌دار»: نخستین آزمونِ خودکارِ **صفحه** با کتابخانهٔ رسمیِ آزمونِ Compose
 *  (تصمیمِ ۴-۲۵ (۳) الف، ۱۴۰۵/۰۸/۱۹؛ فقط آزمون — `testImplementation`). همان `PromoLines` که سبدِ صندوقِ گوشی و کامپیوتر
 *  زیرِ ردیف‌ها نشان می‌دهد: نامِ هر قاعدهٔ اعمال‌شده دیده می‌شود و متنِ دسترس‌پذیری نام و مبلغ را می‌گوید؛ بی قاعده هیچ ردیفی.
 */
class PromoLinesUiTest {

  @get:Rule val rule = createComposeRule()

  private fun show(applied: List<PromoEngine.Applied>) {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { PromoLines(applied) }
      }
    }
  }

  @Test fun eachAppliedRuleIsShownByNameWithItsAmount() {
    show(listOf(PromoEngine.Applied("r1", "ده درصدِ برنج", 50.0), PromoEngine.Applied("r2", "دو بخر یکی ببر", 20.0)))
    rule.onNodeWithText("ده درصدِ برنج").assertExists()
    rule.onNodeWithText("دو بخر یکی ببر").assertExists()
    rule.onNodeWithContentDescription(StrPos.promoA11y("ده درصدِ برنج", money(50.0))).assertExists()
    rule.onNodeWithContentDescription(StrPos.promoA11y("دو بخر یکی ببر", money(20.0))).assertExists()
  }

  @Test fun noAppliedRuleShowsNoRow() {
    show(emptyList())
    assertEquals(0, rule.onAllNodesWithText("ده درصدِ برنج").fetchSemanticsNodes().size)
  }
}
