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
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.PayMethodsPref
import ir.vil3ntec.tohid.data.ShopSettingsKeys
import ir.vil3ntec.tohid.ui.components.pos.PayPanelState
import ir.vil3ntec.tohid.ui.components.pos.PaySplitSection
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  PAY-06 — «روش‌های پرداخت قابل تعریف»: دکان روش‌هایی را که نمی‌پذیرد از پنلِ پرداختِ صندوق برمی‌دارد
 *  (`tohid-policy:pay_methods_off`، تنظیمِ دکان؛ خالی ⇒ همان پنلِ امروز). نقد و کارتِ هدیه/اعتبار (با تنظیمِ خودشان) بیرون‌اند.
 */
@OptIn(ExperimentalTestApi::class)
class PayMethodsSettingTest {

  @get:Rule val rule = createComposeRule()

  @After fun clean() = PayMethodsPref.save(DesktopContext, emptySet())

  @Test fun emptyChoiceIsTodaysPanel() {
    assertEquals(PayFlow.panelMethods(false), PayFlow.panelMethods(false, emptySet()))
    assertEquals(PayFlow.panelMethods(true), PayFlow.panelMethods(true, emptySet()))
  }

  @Test fun offMethodsLeaveThePanelOnly() {
    val ids = PayFlow.panelMethods(false, setOf("e_money", "m_paisa")).map { it.id }
    assertFalse("e_money" in ids)
    assertFalse("m_paisa" in ids)
    assertTrue("hesabpay" in ids && "hawala" in ids)
    //  با کارتِ هدیه روشن هم، خاموشیِ روش‌های دیگر می‌ماند
    assertFalse("e_money" in PayFlow.panelMethods(true, setOf("e_money")).map { it.id })
  }

  @Test fun onlyOrdinaryNonCashMethodsCanBeTurnedOff() {
    val toggle = PayFlow.toggleableMethods().map { it.id }
    assertEquals(listOf("hesabpay", "e_money", "my_money", "atoma_pay", "m_paisa", "afpay_card", "hawala"), toggle)
    //  نقد، کارتِ هدیه/اعتبار (تنظیمِ خودشان) و شناسهٔ ناشناس نادیده
    assertEquals(setOf("hesabpay"), PayFlow.offMethods(setOf(Pay.CASH, "gift_card", Pay.STORE_CREDIT, "bogus", "hesabpay")))
    assertEquals(emptySet<String>(), PayFlow.offMethods(null))
    //  همه را خاموش کردن ⇒ پنل فقط نقد (و قرض با مشتری) دارد، نه خطا
    assertEquals(emptyList<String>(), PayFlow.panelMethods(false, toggle.toSet()).map { it.id })
  }

  @Test fun storedForTheWholeShop() {
    assertEquals("set", ShopSettingsKeys.synced(ShopSettingsKeys.POLICY, "pay_methods_off")?.type)
    assertEquals(emptySet<String>(), PayMethodsPref.off(DesktopContext))
    PayMethodsPref.set(DesktopContext, "e_money", on = false)
    PayMethodsPref.set(DesktopContext, "hawala", on = false)
    assertEquals(setOf("e_money", "hawala"), PayMethodsPref.off(DesktopContext))
    PayMethodsPref.set(DesktopContext, "e_money", on = true)
    assertEquals(setOf("hawala"), PayMethodsPref.off(DesktopContext))
    //  مقدارِ خراب روی دیسک (نقد، ناشناس) پنل را نمی‌شکند
    PayMethodsPref.save(DesktopContext, setOf("cash", "zzz"))
    assertEquals(emptySet<String>(), PayMethodsPref.off(DesktopContext))
  }

  @Test fun panelPageDownSkipsTheMethodsTheShopTurnedOff() {
    I18n.source = { Lang.FA }
    val state = PayPanelState().also { it.off = setOf("hesabpay", "e_money") }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          val cash = remember { FocusRequester() }
          PaySplitSection(state, 2450.0, null, null, 44.dp, cash, onSubmit = {})
          LaunchedEffect(Unit) { cash.requestFocus() }
        }
      }
    }
    rule.waitForIdle()
    rule.onNode(isFocused()).performKeyInput { pressKey(Key.PageDown) }
    rule.waitForIdle()
    assertEquals("my_money", state.rows.single().method)
    assertFalse(state.methods().any { it.id == "hesabpay" || it.id == "e_money" })
  }

  @Test fun everyTillPassesTheShopChoiceToThePanel() {
    fun src(rel: String) = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/$rel").readText()
    assertTrue(src("ui/components/pos/PayPanel.kt").contains("fun methods(): List<PayMethod> = PayFlow.panelMethods(stored, off)"))
    assertTrue(src("ui/screens/DeskSale.kt").contains("offMethods = remember { ir.vil3ntec.tohid.data.PayMethodsPref.off(context) }"))
    assertTrue(src("ui/screens/SaleDialogs.kt").contains("it.off = ir.vil3ntec.tohid.data.PayMethodsPref.off(context)"))
    assertTrue(src("ui/v3/sales/checkout/CheckoutRoute.kt").contains("CheckoutSession.pay.off = offMethods"))
    //  تنظیم در «پرداخت و صندوق»، فقط با «ترکیبی و شیفت» (پنلِ روش‌ها همان‌جاست)
    assertTrue(src("ui/v3/settings/sections/SalesToggleScreens.kt").contains("if (payV2) item(key = \"methods\") { PayMethodsSettings() }"))
  }

  @Test fun panelReadsTheSameObjectWhenNothingIsOff() {
    //  هیچ خاموشی ⇒ همان شیءِ جدولِ امروز (بی کپی)
    assertSame(PayFlow.panelMethods(false).first(), PayFlow.panelMethods(false, emptySet()).first())
  }
}
