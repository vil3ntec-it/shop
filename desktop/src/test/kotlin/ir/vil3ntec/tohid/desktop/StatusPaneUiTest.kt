package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.PresenceStatusDto
import ir.vil3ntec.tohid.data.repo.StorefrontSettingsDto
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.online.storefront.StatusPane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  PRES-01/04/05/09 — آزمونِ رابطِ Compose ِ خودِ برگهٔ «وضعیت» (`StatusPane`، همان صفحهٔ گوشی و کامپیوتر):
 *  «آنلاین تا 20:30» به دقیقهٔ روز فرستاده می‌شود و ساعتِ نادرست هیچ نمی‌فرستد؛ کادرِ بزرگ فقط `status` ِ سرور را می‌گوید
 *  (اعلامِ گذشته یا پایانِ ساعتِ کاری ⇒ «بسته»، نه اعلامِ محلی)؛ اعلامِ نرسیده ⇒ «هنوز تأیید نشده» با «دوباره بفرست» و دکمهٔ
 *  «ثبت» بسته، بی ادعای ثبت.
 */
class StatusPaneUiTest {

  @get:Rule val rule = createComposeRule()

  private val declared = mutableListOf<Pair<String, Int?>>()
  private var resent = 0

  /** 2026-11-12T16:00Z = ۲۰:۳۰ ِ کابل (UTC+4:30) */
  private val at2030Kabul = 1_794_499_200_000L

  private fun show(settings: StorefrontSettingsDto, pending: Boolean = false, sending: Boolean = false, notice: String? = null, confirmed: Int = 0) {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          StatusPane(settings, pending, sending, notice, confirmed, { m, u -> declared += m to u }, { resent++ }, PaddingValues())
        }
      }
    }
  }

  private fun settings(status: PresenceStatusDto?) = StorefrontSettingsDto(slug = "dokan", enabled = true, status = status)

  private fun count(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().size

  @Test fun onlineUntilIsSentAsMinuteOfDayAndServerConfirmedTimeIsShown() {
    show(settings(PresenceStatusDto(badge = "declared_until", mode = "online_until", declaredUntil = at2030Kabul, localTime = "18:10")))
    rule.onNodeWithText(StrOnline.presShortUntil("20:30")).assertExists()
    rule.onNodeWithText(StrOnline.presenceMode("online_until")).performScrollTo().performClick()
    rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("20:30")
    rule.onNodeWithText(StrOnline.submit).performScrollTo().assertIsEnabled().performClick()
    assertEquals(listOf("online_until" to 20 * 60 + 30), declared)
  }

  @Test fun wrongUntilTimeShowsTheErrorAndSendsNothing() {
    show(settings(PresenceStatusDto(badge = "open_online", mode = "auto")))
    rule.onNodeWithText(StrOnline.presenceMode("online_until")).performScrollTo().performClick()
    rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("25:99")
    rule.onNodeWithText(StrOnline.submit).performScrollTo().performClick()
    rule.onNodeWithText(StrOnline.untilError, substring = true).assertExists()
    assertEquals(emptyList<Pair<String, Int?>>(), declared)
  }

  @Test fun expiredDeclarationOrEndOfHoursShowsOnlyTheServerClosedStatus() {
    //  اعلامِ «آنلاین تا 20:30» گذشته و ساعتِ کاری تمام: سرور «بسته» و حالتِ «خودکار» می‌دهد؛ برگه همان را می‌گوید
    show(settings(PresenceStatusDto(badge = "closed", mode = "auto", declaredUntil = null, hoursSet = true, localTime = "21:05")))
    rule.onNodeWithText(StrOnline.presShortClosed).assertExists()
    rule.onNodeWithText(StrOnline.presClosed).assertExists()
    rule.onNodeWithText(StrOnline.currentDeclaration(StrOnline.presenceMode("auto"))).assertExists()
    assertEquals(0, count(StrOnline.presShortUntil("20:30")))
    //  تمدیدِ معتبرِ صاحب دکان از همین برگه: «آنلاین تا 22:00» ⇒ همان اعلام به سرور
    rule.onNodeWithText(StrOnline.presenceMode("online_until")).performScrollTo().performClick()
    rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("22:00")
    rule.onNodeWithText(StrOnline.submit).performScrollTo().performClick()
    assertEquals(listOf("online_until" to 22 * 60), declared)
  }

  @Test fun unsentDeclarationSaysNotConfirmedOffersResendAndBlocksSubmit() {
    show(settings(PresenceStatusDto(badge = "open_unconfirmed", mode = "auto")), pending = true, notice = StrOnline.presenceNotConfirmed)
    rule.onAllNodesWithText(StrOnline.presenceNotConfirmed).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    rule.onNodeWithText(StrOnline.submit).assertIsNotEnabled()
    rule.onNodeWithText(StrOnline.resend).performClick()
    assertEquals(1, resent)
    assertEquals(emptyList<Pair<String, Int?>>(), declared)
  }

  @Test fun whileResendingThereIsNoSecondResendButton() {
    show(settings(PresenceStatusDto(badge = "open_unconfirmed", mode = "auto")), pending = true, sending = true)
    rule.onAllNodesWithText(StrOnline.presenceNotConfirmed).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    assertEquals(0, count(StrOnline.resend))
  }

  @Test fun noStatusFromServerYetSaysSoInsteadOfAClaim() {
    show(settings(null))
    rule.onNodeWithText(StrOnline.statusNotYet).assertExists()
    assertEquals(0, count(StrOnline.sfCustomerSees))
  }
}
