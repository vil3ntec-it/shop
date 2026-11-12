package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrStaffTime
import ir.vil3ntec.tohid.ui.screens.PeriodRow
import ir.vil3ntec.tohid.ui.screens.StaffTimeCard
import ir.vil3ntec.tohid.ui.screens.StaffTimeUi
import ir.vil3ntec.tohid.ui.screens.TeamRow
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  STF-07 — کارتِ «ورود و خروج» ِ «مدیریت» (`StaffTimeCard`، گوشی و کامپیوتر) با آزمونِ خودکارِ صفحه: بیرون ⇒ «ثبتِ ورود» و یک زدن؛
 *  سرِ کار ⇒ ساعتِ ورود و «ثبتِ خروج»؛ صفِ آفلاین شمارِ نرسیده‌ها را می‌گوید؛ در حینِ کار دکمه بسته است؛ صاحب و مدیر کارمندِ
 *  هنوز سرِ کار را با «ثبتِ خروجِ …» می‌بندند و «۷ روز و این ماه» با «دیر ثبت‌شده» دیده می‌شود؛ شاگرد بخشِ کارمندان را نمی‌بیند.
 */
class StaffTimeCardUiTest {

  @get:Rule val rule = createComposeRule()

  private var punches = 0
  private val clockedOut = ArrayList<TeamRow>()

  private fun ui(open: String? = null, queued: Int = 0, busy: Boolean = false, manager: Boolean = false, team: List<TeamRow> = emptyList(), periods: List<PeriodRow> = emptyList()) =
    StaffTimeUi(open, queued, busy, null, manager, team, 0, periods, null, null, null)

  private fun show(u: StaffTimeUi) {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { StaffTimeCard(u, onPunch = { punches++ }, onClockOutFor = { clockedOut += it }, onSaveRates = {}) }
      }
    }
  }

  @Test fun offDutyShowsClockInAndOneTapPunchesOnce() {
    show(ui())
    rule.onNodeWithText(StrStaffTime.out).assertExists()
    rule.onNodeWithText(StrStaffTime.clockIn).performClick()
    rule.waitForIdle()
    assertEquals(1, punches)
    assertTrue(rule.onAllNodesWithText(StrStaffTime.team).fetchSemanticsNodes().isEmpty())
  }

  @Test fun onDutyOfflineShowsSinceClockOutAndTheQueuedCount() {
    show(ui(open = "08:30", queued = 2))
    rule.onNodeWithText(StrStaffTime.inSince("08:30")).assertExists()
    rule.onNodeWithText(StrStaffTime.clockOut).assertExists()
    rule.onNodeWithText(StrStaffTime.queued(2)).assertExists()
  }

  @Test fun whileBusyTheButtonIsClosed() {
    show(ui(busy = true))
    rule.onNodeWithText(StrStaffTime.clockIn).assertIsNotEnabled()
    rule.onNodeWithText(StrStaffTime.clockIn).performClick()
    assertEquals(0, punches)
  }

  @Test fun managerClosesAnOpenShiftAndSeesPeriodsWithLatePunches() {
    val ali = TeamRow("m2", "علی", "3:10", open = true)
    show(ui(manager = true, team = listOf(ali, TeamRow("m3", "زهرا", "7:00", open = false)), periods = listOf(PeriodRow("علی", "20:00", "80:00", 0, late = 1))))
    rule.onNodeWithText(StrStaffTime.team).assertExists()
    rule.onNodeWithText("3:10 · ${StrStaffTime.stillIn}").assertExists()
    rule.onNodeWithText("7:00").assertExists()
    assertTrue(rule.onAllNodesWithText(StrStaffTime.clockOutFor("زهرا")).fetchSemanticsNodes().isEmpty())
    rule.onNodeWithText(StrStaffTime.clockOutFor("علی")).performClick()
    rule.waitForIdle()
    assertEquals(listOf(ali), clockedOut)
    rule.onNodeWithText(StrStaffTime.periods).assertExists()
    rule.onNodeWithText(StrStaffTime.weekWorked("20:00")).assertExists()
    rule.onNodeWithText(StrStaffTime.lateRecorded(1)).assertExists()
  }
}
