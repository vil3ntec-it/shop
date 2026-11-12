package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.pay.FxRate
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.data.FxTillFlow
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.ui.components.pos.PayPanelState
import ir.vil3ntec.tohid.ui.components.pos.PaySplitSection
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  PAY-10 — کادرِ «ارزِ نقد» در پنلِ پرداختِ صندوقِ کامپیوتر (F9، `PayPanelDialog` ⇐ `PaySplitSection`)، همان قاعدهٔ
 *  `FxTillFlow` ِ گوشی: فقط با جدولِ نرخِ روشنِ دکان (`state.fxRates`)؛ خالی ⇒ همان پنلِ امروز.
 */
@OptIn(ExperimentalTestApi::class)
class DeskFxTenderUiTest {

  @get:Rule val rule = createComposeRule()

  private val t0 = 1_800_000_000_000L
  private val usd = FxRate("USD", 7000, 1, "سرای شهزاده", t0, "مدیر")
  private val pkr = FxRate("PKR", 24600, 1000, "سرای شهزاده", t0, "مدیر")

  private fun show(state: PayPanelState) {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          val cash = remember { FocusRequester() }
          PaySplitSection(state, 7000.0, null, null, 44.dp, cash, onSubmit = {})
          LaunchedEffect(Unit) { cash.requestFocus() }
        }
      }
    }
    rule.waitForIdle()
  }

  @Test fun foreignCashPaysTheInvoiceAtTheShopRate() {
    val state = PayPanelState().also { it.fxRates = listOf(usd, pkr); it.clock = { t0 + 60_000 } }
    show(state)
    rule.onAllNodesWithText(StrFx.tillTitle).assertCountEquals(1)
    rule.onNode(hasSetTextAction() and hasText(StrFx.amountIn(FxTillFlow.name("USD")))).performTextInput("100")
    rule.waitForIdle()
    assertEquals("100", state.fxAmount)
    rule.onAllNodesWithText(StrFx.afnValue(ir.vil3ntec.tohid.money(7000.0)), substring = true).assertCountEquals(1)
    val ok = PayFlow.checkout(state.draft(7000.0, null)) as PayFlow.Outcome.Ok
    val fx = ok.value.payments.single { it.method == Pay.FX }
    assertEquals(700_000L, fx.amount)
    //  تراشهٔ کلدار ⇒ همان کادر با ارزِ دیگر
    rule.onNode(hasText(FxTillFlow.name("PKR"))).performClick()
    rule.waitForIdle()
    assertEquals("PKR", state.fxTender()?.currency)
  }

  @Test fun noShopRatesMeansTodaysPanel() {
    show(PayPanelState())
    rule.onAllNodesWithText(StrFx.tillTitle).assertCountEquals(0)
  }

  @Test fun deskTillPassesTheShopRatesToThePanel() {
    val src = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/DeskSale.kt").readText()
    assertTrue(src.contains("fxRates = remember { ir.vil3ntec.tohid.data.FxTillPref.tillRates(context) }"))
    val panel = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/components/pos/PayPanel.kt").readText()
    assertTrue(panel.contains("it.fxRates = fxRates; it.clock = ir.vil3ntec.tohid.data.ReportBridge::nowMs"))
  }
}
