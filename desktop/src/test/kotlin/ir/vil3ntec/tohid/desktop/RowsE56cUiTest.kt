package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.audit.ChainCheck
import ir.vil3ntec.tohid.core.audit.DocChainAudit
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrDocChain
import ir.vil3ntec.tohid.core.i18n.StrHeldTwice
import ir.vil3ntec.tohid.core.i18n.StrLostDevice
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.data.HeldConflict
import ir.vil3ntec.tohid.ui.components.pos.heldTwiceText
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.account.team.LostDeviceReplaySheet
import ir.vil3ntec.tohid.ui.v3.analytics.DocChainCheckScreen
import ir.vil3ntec.tohid.ui.v3.analytics.DocChainCheckState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 *  رابطِ سه ردیفِ `claude/e-rows-56c` روی همان کدِ گوشیِ نسخهٔ ۳ و کامپیوتر:
 *   SEC-02 «بررسیِ یکپارچگیِ اسناد» (سالم، ناسازگار با فهرستِ «عوض شده»/«در دفتر نیست»، خاموش؛ «دوباره بسنج»)؛
 *   S44 برگهٔ «بازیابی از پشتیبان» (شرح و دکمهٔ انتخابِ فایل)؛ S09 متنِ «یک فاکتور در دو دستگاه» پس از لغوِ خودکار یا گزارش.
 */
class RowsE56cUiTest {

  @get:Rule val rule = createComposeRule()

  private fun report(ok: Boolean) = DocChainAudit.Report(
    chain = "doc/shp/B1/1", epoch = 1,
    links = if (ok) ChainCheck(ok = true, length = 4) else ChainCheck(ok = false, length = 2, index = 2, reason = "hash_mismatch"),
    total = 4, byType = linkedMapOf("sale" to 2, "debt" to 1, "return" to 1),
    changed = if (ok) emptyList() else listOf(DocChainAudit.Finding("debt", "dtx_77", 3)),
    missing = if (ok) emptyList() else listOf(DocChainAudit.Finding("return", "ret_9", 4)),
    waiting = if (ok) 0 else 2, unanchored = 0, acked = 3, halted = "", docFrom = 1_000, docAt = 2_000, lastNo = 9,
  )

  private fun show(state: DocChainCheckState, onRecheck: () -> Unit = {}) = rule.setContent {
    CompositionLocalProvider(LocalContext provides DesktopContext) {
      TohidTheme(ThemeChoice.LIGHT) { DocChainCheckScreen(state, { "T$it" }, onRecheck, onBack = null) }
    }
  }

  @Test fun `بررسیِ یکپارچگی — سالم با شمارِ هر نوع، و «دوباره بسنج» دوباره می‌سنجد`() {
    I18n.source = { Lang.FA }
    var again = 0
    show(DocChainCheckState.Done(report(ok = true))) { again++ }
    rule.onNodeWithText(StrDocChain.okTitle).assertExists()
    rule.onNodeWithText(StrDocChain.okBody(4, 3)).assertExists()
    rule.onNodeWithText(StrDocChain.count(StrDocChain.typeDebt, 1)).assertExists()
    rule.onNodeWithText(StrDocChain.upTo("T2000")).assertExists()
    rule.onNodeWithText(StrDocChain.recheck).performClick()
    rule.waitForIdle()
    assertEquals(1, again)
  }

  @Test fun `بررسیِ یکپارچگی — ناسازگار با حلقهٔ شکسته، سندِ عوض‌شده و رفته، و در انتظار`() {
    I18n.source = { Lang.FA }
    show(DocChainCheckState.Done(report(ok = false)))
    rule.onNodeWithText(StrDocChain.badTitle).assertExists()
    rule.onNodeWithText(StrDocChain.brokenAt(3)).assertExists()
    rule.onNodeWithText(StrDocChain.changedTitle).assertExists()
    rule.onNodeWithText("dtx_77").assertExists()
    rule.onNodeWithText(StrDocChain.missingTitle).assertExists()
    rule.onNodeWithText("ret_9").assertExists()
    rule.onNodeWithText(StrDocChain.waiting(2)).assertExists()
  }

  @Test fun `بررسیِ یکپارچگی — خاموش یا بی زنجیره پیامِ روشن`() {
    I18n.source = { Lang.FA }
    show(DocChainCheckState.Off)
    rule.onNodeWithText(StrDocChain.off).assertExists()
  }

  @Test fun `بازیابی از پشتیبان — برگه با شرح و دکمهٔ انتخابِ فایل`() {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { LostDeviceReplaySheet("dev_row_1", onDismiss = {}) }
      }
    }
    rule.onNodeWithText(StrLostDevice.title).assertExists()
    rule.onNodeWithText(StrLostDevice.body).assertExists()
    assertEquals(1, rule.onAllNodesWithText(StrLostDevice.pick).fetchSemanticsNodes().size)
  }

  @Test fun `یک فاکتور در دو دستگاه — متنِ لغوِ خودکار، گزارش و همان پیامِ پیشین`() {
    I18n.source = { Lang.FA }
    val c = HeldConflict("کریمی", "A1", "resumed", 5)
    assertEquals(StrPos.heldTwice("کریمی", "A1"), heldTwiceText(c))
    assertEquals(StrPos.heldTwice("کریمی", "A1"), heldTwiceText(c.copy(sale = "B-k1")))
    assertEquals(StrHeldTwice.cancelled("کریمی", "A1"), heldTwiceText(c.copy(sale = "B-k1", kept = "K1", cancelled = true)))
    assertEquals(StrHeldTwice.reported("کریمی", "A1"), heldTwiceText(c.copy(sale = "B-k1", kept = "K1")))
  }
}
