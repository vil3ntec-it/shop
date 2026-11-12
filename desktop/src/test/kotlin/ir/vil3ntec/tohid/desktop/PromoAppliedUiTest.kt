package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.PromoEngine
import ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.sales.checkout.PromoApplied
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  PRM-18 — برگهٔ پرداختِ صندوقِ نسخهٔ ۳ (`CheckoutSheet.PromoApplied`): هر تخفیفِ خودکارِ اعمال‌شده با نامِ قاعده، برچسبِ
 *  «تخفیفِ خودکار» و مبلغِ منفی‌اش دیده می‌شود؛ بی قاعده هیچ کارتی. (سبد و صندوقِ کامپیوتر: `PromoLinesUiTest`.)
 */
class PromoAppliedUiTest {

  @get:Rule val rule = createComposeRule()

  private fun show(applied: List<PromoEngine.Applied>) {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { Column { PromoApplied(applied) } }
      }
    }
  }

  @Test fun everyAppliedRuleIsShownWithItsNameLabelAndNegativeAmount() {
    show(listOf(PromoEngine.Applied("r1", "ده درصدِ برنج", 50.0), PromoEngine.Applied("r2", "دو بخر یکی ببر", 20.0)))
    rule.onNodeWithText("ده درصدِ برنج").assertExists()
    rule.onNodeWithText("دو بخر یکی ببر").assertExists()
    assertEquals(2, rule.onAllNodesWithText(StrSale.autoDiscount).fetchSemanticsNodes().size)
    rule.onNodeWithText(PhoneSaleText.amount(-50.0)).assertExists()
    rule.onNodeWithText(PhoneSaleText.amount(-20.0)).assertExists()
  }

  @Test fun noAppliedRuleShowsNothing() {
    show(emptyList())
    assertTrue(rule.onAllNodesWithText(StrSale.autoDiscount).fetchSemanticsNodes().isEmpty())
  }
}
