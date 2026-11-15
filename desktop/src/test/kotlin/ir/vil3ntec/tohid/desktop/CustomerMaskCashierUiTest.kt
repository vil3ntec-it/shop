package ir.vil3ntec.tohid.desktop

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import ir.vil3ntec.tohid.core.crm.CustomerMask
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.components.customerPhone
import ir.vil3ntec.tohid.ui.screens.DeskCustomersScreen
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  CRM-15 — پوشاندن بر پایهٔ نقش در صفحهٔ واقعیِ «مشتری‌ها» ِ کامپیوتر (`DeskCustomersScreen`): صندوق‌دار با پرچمِ
 *  `shop.customer_role_mask` (همان `policy.customerMask` ِ سرور) شماره را «•••• ۴۵۶» می‌بیند و شمارهٔ کامل هیچ جای صفحه
 *  نیست؛ صاحب کامل؛ پرچمِ خاموش ⇒ صندوق‌دار همان امروز (کامل). تصمیم از راهِ `ShopRole` ⇐ `PermPolicy` ⇐ `CustomerMask`.
 */
class CustomerMaskCashierUiTest {

  @get:Rule val rule = createComposeRule()

  private val phone = "0799123456"
  private val d = ShopData(debtors = listOf(Debtor(id = "c1", name = "مشتریِ آزمون", phone = phone)))
  private val on = """{"v":1,"role":"cashier","layers":[{},{}],"effective":{},"editable":[],"customerMask":true}"""
  private val off = """{"v":1,"role":"cashier","layers":[{},{}],"effective":{},"editable":[]}"""

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() {
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    ShopRole.rememberPolicy(DesktopContext, null)
    I18n.source = { Lang.FA }
  }

  private fun show() {
    val store = ShopStore(DesktopContext).also { runBlocking { it.load() } }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { DeskCustomersScreen(store, d, SnackbarHostState()) }
      }
    }
    rule.waitUntil(10_000) { rule.onAllNodes(hasText("مشتریِ آزمون", substring = true)).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun seen(text: String) = rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
  private fun anyDigits(digits: String) = rule.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes()
    .any { n -> n.config.toString().filter { it.isDigit() }.let { ir.vil3ntec.tohid.core.text.LocaleFormat.displayDigits(it, "latin") }.contains(digits) }

  @Test fun cashierSeesOnlyTheLastThreeDigitsUnderTheRoleMask() {
    ShopRole.remember(DesktopContext, ShopRole.CASHIER)
    ShopRole.rememberPolicy(DesktopContext, on)
    assertFalse(ShopRole.allows(DesktopContext, CustomerMask.KEY))
    show()
    val masked = customerPhone(phone, false)
    assertTrue(masked, masked.contains("•••• ") && masked.filter { it.isDigit() }.length == 3)
    assertTrue("شمارهٔ پوشیده دیده نشد: $masked", seen(masked))
    assertFalse("شمارهٔ کامل در صفحهٔ صندوق‌دار", seen(customerPhone(phone, true)))
    assertFalse("رقم‌های شمارهٔ کامل جایی از صفحه", anyDigits("0799123"))
  }

  @Test fun ownerSeesTheFullNumber() {
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    ShopRole.rememberPolicy(DesktopContext, on.replace("\"cashier\"", "\"owner\""))
    show()
    assertTrue(seen(customerPhone(phone, true)))
  }

  @Test fun flagOffCashierSeesTheNumberAsToday() {
    ShopRole.remember(DesktopContext, ShopRole.CASHIER)
    ShopRole.rememberPolicy(DesktopContext, off)
    assertTrue(ShopRole.allows(DesktopContext, CustomerMask.KEY))
    assertEquals(false, ShopRole.customerMask.value)
    show()
    assertTrue(seen(customerPhone(phone, true)))
  }
}
