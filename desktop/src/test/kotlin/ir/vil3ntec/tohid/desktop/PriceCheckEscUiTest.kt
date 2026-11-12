package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.components.pos.PriceCheckDialog
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  S17 — «Esc فوکوس را به کادرِ اسکن برمی‌گرداند» (POS-15): همان `PriceCheckDialog` ِ Ctrl+I ِ صندوقِ کامپیوتر.
 *  ۱) صفحه: پنجره با فوکوس روی کادرِ خودش باز می‌شود؛ Esc آن را می‌بندد و `onDismiss` ِ هم‌شکلِ `DeskSale` فوکوس را به کادرِ
 *  اسکن برمی‌گرداند؛ پنجره چیزی در دفتر نمی‌نویسد. ۲) سیم‌کشیِ `DeskSale`: همان `onDismiss` ِ پنجره `refocus()` است و
 *  `refocus` همان `searchFocus` ِ کادرِ اسکن/جست‌وجو را می‌خواهد (متنِ کد؛ اگر کسی این سیم را بِبُرد سرخ می‌شود).
 */
@OptIn(ExperimentalTestApi::class)
class PriceCheckEscUiTest {

  @get:Rule val rule = createComposeRule()

  @Test fun escapeClosesThePriceCheckAndFocusReturnsToTheScanBox() {
    I18n.source = { Lang.FA }
    val d = ShopData()
    var open by mutableStateOf(true)
    var dismissed = 0
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          val scan = remember { FocusRequester() }
          var query by remember { mutableStateOf("") }
          Column {
            OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.focusRequester(scan).testTag("scan"))
          }
          LaunchedEffect(Unit) { runCatching { scan.requestFocus() } }
          //  همان شکلِ `DeskSale`: `onDismiss = { priceCheck = false; refocus() }`
          if (open) PriceCheckDialog(d, "2026-11-12", showCost = false, onDismiss = { dismissed++; open = false; runCatching { scan.requestFocus() } })
        }
      }
    }
    rule.waitForIdle()
    //  پنجره فوکوس را به کادرِ خودش برده است
    rule.onNodeWithTag("scan").assertIsNotFocused()
    rule.onNode(isFocused()).performKeyInput { pressKey(Key.Escape) }
    rule.waitForIdle()
    assertEquals(1, dismissed)
    assertEquals(false, open)
    rule.onNodeWithTag("scan").assertIsFocused()
    //  قیمت‌پرسی هیچ چیز نمی‌نویسد
    assertEquals(ShopData(), d)
  }

  @Test fun deskSaleWiresPriceCheckDismissToTheScanBoxFocus() {
    val src = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/DeskSale.kt").readText()
    val call = src.substringAfter("PriceCheckDialog(", "").substringBefore("places =")
    assertTrue("پنجرهٔ قیمت‌پرسی در DeskSale پیدا نشد", call.isNotEmpty())
    assertTrue("onDismiss ِ قیمت‌پرسی باید فوکوس را برگرداند", call.contains("onDismiss = { priceCheck = false; refocus() }"))
    assertTrue(src.contains("fun refocus() { runCatching { searchFocus.requestFocus() } }"))
    //  `searchFocus` روی همان کادرِ اسکن/جست‌وجوی صندوق است
    val box = src.substringBefore(".focusRequester(searchFocus)").takeLast(400)
    assertTrue("searchFocus باید روی کادرِ جست‌وجو/اسکن باشد", box.contains("StrSale.deskSearchHint"))
  }
}
