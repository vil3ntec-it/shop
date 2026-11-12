package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.SalePayment
import ir.vil3ntec.tohid.ui.components.pos.PayPanelState
import ir.vil3ntec.tohid.ui.components.pos.PaySplitSection
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 *  S06 ⌨ — پرداختِ ترکیبی با **کلیدهای واقعیِ پنل** (PAY-01، PAY-06، PAY-07): همان `PaySplitSection` که پنجرهٔ F9 ِ
 *  صندوقِ کامپیوتر (`PayPanelDialog`) و برگهٔ پرداختِ گوشی نشان می‌دهند. فاکتورِ ۲٬۴۵۰: ۱۰۰۰ در کادرِ نقد، PgDn ⇒ ردیفِ
 *  HesabPay با فوکوس روی «مرجع»، تایپِ مرجع، Enter ⇒ کادرِ مبلغ، ۱۴۵۰، Enter ⇒ ثبت. حاصل همان دو ردیفِ `PayFlowTest` «S06»
 *  است (HesabPay «ثبتِ دستی»، جمعِ دقیق). سنجشِ کلیدها فقط آزمون است — کدِ پنل دست نخورد.
 */
@OptIn(ExperimentalTestApi::class)
class PaySplitKeysUiTest {

  @get:Rule val rule = createComposeRule()

  private val state = PayPanelState()
  private var submitted = 0

  private fun show() {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          val cash = remember { FocusRequester() }
          PaySplitSection(state, 2450.0, null, null, 44.dp, cash, onSubmit = { submitted++ })
          //  پنجرهٔ F9 هم با فوکوس روی کادرِ نقد باز می‌شود
          LaunchedEffect(Unit) { cash.requestFocus() }
        }
      }
    }
    rule.waitForIdle()
  }

  private fun focused() = rule.onNode(isFocused())

  @Test fun pageDownOpensHesabPayRowThenEnterMovesToAmountAndSubmits() {
    show()
    focused().performTextInput("1000")
    focused().performKeyInput { pressKey(Key.PageDown) }
    rule.waitForIdle()
    assertEquals(1, state.rows.size)
    assertEquals("hesabpay", state.rows.single().method)

    //  فوکوس روی کادرِ «مرجع» ِ ردیفِ تازه
    focused().performTextInput("HP-77")
    focused().performKeyInput { pressKey(Key.Enter) }
    rule.waitForIdle()
    //  Enter به کادرِ مبلغ رفت، نه ثبت
    assertEquals(0, submitted)
    focused().performTextInput("1450")
    assertEquals(PayFlow.DraftRow("hesabpay", "HP-77", "1450"), state.rows.single())

    //  PAY-04: Enter ِ ثبت فقط پس از نگهبانِ باز شدنِ پنل
    Thread.sleep(CashKeys.ENTER_GUARD_MS + 50)
    focused().performKeyInput { pressKey(Key.Enter) }
    rule.waitForIdle()
    assertEquals(1, submitted)

    val draft = state.draft(2450.0, null)
    val ok = PayFlow.checkout(draft) as PayFlow.Outcome.Ok
    val f = PayFlow.saleFields(ok.value)
    assertEquals(
      listOf(SalePayment("cash", "cash", 100000, "confirmed"), SalePayment("hesabpay", "wallet", 145000, "pending", "HP-77", "manual")),
      f.payments,
    )
    assertEquals(245000L, f.payments.sumOf { it.amountMinor })
  }

  @Test fun pageDownOnTheRowCyclesTheMethodWithoutAddingARow() {
    show()
    focused().performKeyInput { pressKey(Key.PageDown) }
    rule.waitForIdle()
    val methods = state.methods().map { it.id }
    assertEquals(methods.first(), state.rows.single().method)
    focused().performKeyInput { pressKey(Key.PageDown) }
    rule.waitForIdle()
    assertEquals(1, state.rows.size)
    assertEquals(methods[1 % methods.size], state.rows.single().method)
    focused().performKeyInput { pressKey(Key.PageUp) }
    rule.waitForIdle()
    assertEquals(methods.first(), state.rows.single().method)
  }
}
