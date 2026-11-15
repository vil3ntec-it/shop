package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrRate
import ir.vil3ntec.tohid.core.rate.RateAction
import ir.vil3ntec.tohid.core.rate.RateChoice
import ir.vil3ntec.tohid.core.rate.RateTarget
import ir.vil3ntec.tohid.rate.RateStoreAndroid
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.rate.RateSheet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  RATE-01 — برگهٔ «ستاره بدهید» (`RateSheet`): هر سه دکمه دقیقاً یک انتخاب می‌دهند و برگه می‌رود؛ کشیدن/بیرون همان «بعداً»؛
 *  زدنِ دوباره انتخاب را عوض نمی‌کند؛ متن نامِ فروشگاهِ همین ساخت را دارد و در هیچ زبانی شمارِ ستاره یا پرسش ندارد.
 *  کامپیوتر خودش هرگز برگه را نمی‌سازد (`RateStoreAndroid.target`).
 */
class RateSheetUiTest {

  @get:Rule val rule = createComposeRule()

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { I18n.source = { Lang.FA } }

  private fun show(target: RateTarget = RateTarget.PLAY): MutableList<RateChoice> {
    val picked = ArrayList<RateChoice>()
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          var open by remember { mutableStateOf(true) }
          if (open) RateSheet(target) { picked += it; open = false }
        }
      }
    }
    rule.waitUntil(10_000) { count(StrRate.rate) > 0 }
    return picked
  }

  private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

  private fun pick(button: String, want: RateChoice) {
    val picked = show()
    rule.onNodeWithText(button).performClick()
    rule.waitUntil(10_000) { picked.isNotEmpty() }
    rule.waitForIdle()
    assertEquals(listOf(want), picked)
    rule.waitUntil(10_000) { count(StrRate.rate) == 0 }
  }

  @Test fun rateButtonAlwaysGoesToTheStore() = pick(StrRate.rate, RateChoice.RATE)

  @Test fun laterButtonSnoozes() = pick(StrRate.later, RateChoice.LATER)

  @Test fun neverButtonStopsForGood() = pick(StrRate.never, RateChoice.NEVER)

  @Test fun secondTapDoesNotChangeTheChoice() {
    val picked = show()
    rule.onNodeWithText(StrRate.never).performClick()
    runCatching { rule.onNodeWithText(StrRate.rate).performClick() }
    rule.waitUntil(10_000) { picked.isNotEmpty() }
    rule.waitForIdle()
    assertEquals(listOf(RateChoice.NEVER), picked)
  }

  @Test fun textNamesTheStoreOfThisBuild() {
    show(RateTarget.PLAY)
    assertTrue(count(StrRate.bodyPlay) > 0)
    assertTrue(count(StrRate.title) > 0)
    assertTrue(StrRate.bodyPlay.contains("گوگل‌پلی"))
    assertTrue(StrRate.bodyBazaar.contains("کافه‌بازار"))
  }

  @Test fun noStarCountNoQuestionNoRewardInAnyLanguage() {
    for (lang in Lang.entries) {
      I18n.source = { lang }
      for (s in listOf(StrRate.title, StrRate.bodyPlay, StrRate.bodyBazaar, StrRate.rate, StrRate.later, StrRate.never)) {
        assertFalse("$lang: «$s» شمارِ ستاره دارد", Regex("[0-9۰-۹٠-٩]|پنج|five|5 star", RegexOption.IGNORE_CASE).containsMatchIn(s))
        assertFalse("$lang: «$s» پرسش است (دروازه‌گذاری)", s.contains('?') || s.contains('؟'))
        assertFalse("$lang: «$s» جایزه می‌دهد", Regex("جایزه|هدیه|تخفیف|reward|gift|discount|bonus", RegexOption.IGNORE_CASE).containsMatchIn(s))
      }
    }
  }

  @Test fun desktopAndDirectBuildsNeverGetASheet() {
    assertEquals(RateTarget.NONE, RateStoreAndroid.target("play", preview = false, desktop = true))
    assertEquals(RateTarget.NONE, RateStoreAndroid.target("direct", preview = false, desktop = false))
    assertEquals(RateTarget.NONE, RateStoreAndroid.target("play", preview = true, desktop = false))
    assertEquals("میزبانِ آیفونِ دکان هنوز نمی‌نشاند", RateTarget.NONE, RateStoreAndroid.target("appstore", preview = false, desktop = false))
    assertEquals(RateTarget.PLAY, RateStoreAndroid.target("play", preview = false, desktop = false))
    assertEquals(RateTarget.BAZAAR, RateStoreAndroid.target("bazaar", preview = false, desktop = false))
    //  درِ رسمیِ «نظر و امتیاز» ِ کافه‌بازار
    assertEquals("android.intent.action.EDIT", RateStoreAndroid.intentAction(RateAction.EDIT))
    assertEquals("android.intent.action.VIEW", RateStoreAndroid.intentAction(RateAction.VIEW))
  }
}
