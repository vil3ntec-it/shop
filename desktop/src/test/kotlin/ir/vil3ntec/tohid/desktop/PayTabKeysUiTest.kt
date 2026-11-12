package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.keymap.Chord
import ir.vil3ntec.tohid.core.keymap.KeyName
import ir.vil3ntec.tohid.ui.components.pos.PayPanelDialog
import ir.vil3ntec.tohid.ui.components.pos.PayPanelKey
import ir.vil3ntec.tohid.ui.components.pos.payPanelKey
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  POS-41 — Tab/Shift+Tab ِ پنلِ پرداختِ صندوقِ کامپیوتر (`sale.payment.nextField`/`prevField` ِ keymap.json): فوکوس به
 *  کادر/دکمهٔ بعدی و قبلی، روی همان پنجرهٔ واقعیِ F9 (`PayPanelDialog`). کلید از keymap است (`payPanelKey`).
 */
@OptIn(ExperimentalTestApi::class)
class PayTabKeysUiTest {

  @get:Rule val rule = createComposeRule()

  private fun focusedId(): Int = rule.onNode(isFocused()).fetchSemanticsNode().id

  private fun show() {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          PayPanelDialog(total = 2450.0, customerId = null, customerName = null, busy = false, target = 44.dp, onDismiss = {}, onConfirm = {})
        }
      }
    }
    rule.waitForIdle()
    //  پنجرهٔ آزمون فوکوسِ پنجره را همیشه خودکار نمی‌دهد ⇒ کادرِ نقد (نخستین کادرِ پنل) را خودمان می‌گیریم
    if (rule.onAllNodes(isFocused()).fetchSemanticsNodes().isEmpty()) rule.onAllNodes(hasSetTextAction())[0].performClick()
    rule.waitForIdle()
  }

  @Test fun tabMovesToTheNextControlAndShiftTabComesBack() {
    show()
    val cash = focusedId()
    rule.onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
    rule.waitForIdle()
    val next = focusedId()
    assertNotEquals(cash, next)
    rule.onNode(isFocused()).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
    rule.waitForIdle()
    assertEquals(cash, focusedId())
  }

  @Test fun tabGoesFromTheReferenceFieldToTheAmountFieldOfTheSameRow() {
    show()
    //  PgDn ⇒ ردیفِ تازه با فوکوس روی «مرجع»
    rule.onNode(isFocused()).performKeyInput { pressKey(Key.PageDown) }
    rule.waitForIdle()
    val ref = focusedId()
    rule.onNode(isFocused()).performKeyInput { pressKey(Key.Tab) }
    rule.waitForIdle()
    val amount = rule.onNode(isFocused()).fetchSemanticsNode()
    assertNotEquals(ref, amount.id)
    assertTrue("Tab باید به کادرِ مبلغ برود", SemanticsActions.SetText in amount.config)
    rule.onNode(isFocused()).performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
    rule.waitForIdle()
    assertEquals(ref, focusedId())
  }

  @Test fun tabKeysComeFromTheKeymap() {
    fun press(c: Chord) = payPanelKey({ c in it.keys }, canCustomer = false)
    assertEquals(PayPanelKey.NEXT_FIELD, press(Chord(KeyName.Tab)))
    assertEquals(PayPanelKey.PREV_FIELD, press(Chord(KeyName.Tab, shift = true)))
    assertNull(press(Chord(KeyName.Tab, alt = true)))
  }
}
