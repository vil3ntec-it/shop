package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrLine
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.core.i18n.StrTill
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.core.pos.PriceApproval
import ir.vil3ntec.tohid.data.PriceAsk
import ir.vil3ntec.tohid.ui.screens.CartLineDialog
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 *  S45 در پنجرهٔ واقعیِ Compose ِ **صندوقِ کامپیوتر** — کادرِ ردیفِ سبد (`CartLineDialog`، همان که `DeskSale` باز می‌کند) با
 *  «قیمت و تخفیفِ ردیف» ِ روشن («فقط بالا») و درِ «درخواستِ تأیید» (`PriceAsk`):
 *  مدیر قیمت را پایین می‌آورد ⇒ پیامِ رد و دکمهٔ «درخواستِ تأیید» ⇒ دلیل و «تأیید با این دلیل» ⇒ «تأیید شد: مدیر» ⇒ «ثبت» با همان
 *  قیمت و یک تأییدِ `PRICE_DOWN_BLOCKED` برای سابقه.
 *  ⛔ باگِ یافتهٔ `claude/e-rows-31w`: کادر متنِ ردِ صفحه را جای کدِ ردِ هسته به `PriceApproval.askable` می‌داد ⇒ دکمه هرگز دیده
 *  نمی‌شد و آزمونِ پیشین فقط متنِ کد را می‌خواند.
 */
@OptIn(ExperimentalTestApi::class)
class ScenarioS45DeskUiTest {

  @get:Rule val rule = createComposeRule()

  private val confirmed = CopyOnWriteArrayList<Double?>()
  private val granted = CopyOnWriteArrayList<PriceApproval.Grant>()

  @Before fun lang() { I18n.source = { Lang.FA } }

  private fun show() = rule.setContent {
    CompositionLocalProvider(LocalContext provides DesktopContext) {
      TohidTheme(ThemeChoice.LIGHT) {
        CartLineDialog(
          name = "چای", unit = "دانه", quantity = 1.0, unitPrice = 100.0, basePrice = 100.0, custom = false,
          onDismiss = {}, onConfirm = { _, price -> confirmed += price },
          linePolicy = LinePricing.Policy(on = true, exempt = false, direction = LinePricing.Direction.UP, mayReduce = true),
          priceAsk = PriceAsk("manager", "مدیر", "u-manager") { granted += it },
        )
      }
    }
  }

  private fun has(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

  private fun tap(text: String) {
    rule.onAllNodes(hasText(text) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  private fun type(label: String, value: String) {
    val field: SemanticsMatcher = hasSetTextAction() and (hasContentDescription(label) or hasText(label))
    rule.onAllNodes(field)[0].performTextReplacement(value)
    rule.waitForIdle()
  }

  @Test fun managerLowersAPriceAndTheApprovalDoorOpensAndRecordsTheGrant() {
    show()
    rule.onAllNodes(isToggleable())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
    type(StrSale.unitPrice, "80")
    rule.waitUntil(10_000) { has(StrLine.priceDownBlocked) }
    assertTrue("ردِ قاعدهٔ جهت «درخواستِ تأیید» دارد", has(StrTill.askApproval))

    tap(StrTill.askApproval)
    rule.waitUntil(10_000) { has(StrTill.priceTitle) }
    type(StrPos.reasonKept, "مشتریِ همیشگی")
    tap(StrPos.creditApproveSelf)
    rule.waitUntil(10_000) { has(StrTill.approvedBy("مدیر")) }

    tap(StrSalePages.record)
    rule.waitUntil(10_000) { confirmed.isNotEmpty() }
    assertEquals(80.0, confirmed.single()!!, 0.0)
    val g = granted.single()
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, g.code)
    assertEquals("مدیر", g.by)
    assertEquals("مشتریِ همیشگی", g.reason)
    assertEquals(10_000L, g.listMinor)
    assertEquals(8_000L, g.priceMinor)
  }
}
