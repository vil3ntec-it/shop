package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.shell.SearchScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  صاحب‌کار ۱۴۰۵/۰۸/۲۳ (تبلت): صفحه‌کلید فقط با زدنِ خودِ کادر باز شود و زدنِ هر جای دیگرِ صفحه آن را ببندد.
 *   ۱) جست‌وجو («همهٔ بخش‌ها»، `search`) با باز شدن کادر را تمرکز نمی‌دهد (پیش‌تر `requestFocus` ِ خودکار)؛
 *   ۲) زدنِ کادر تمرکز می‌دهد و همان می‌ماند؛ زدنِ جای خالیِ صفحه تمرکز را برمی‌دارد (`v3ClearFocusOnTap`) —
 *      هم در جست‌وجو و هم در هر صفحهٔ `V3Scaffold` ِ کیت (یک تغییرِ مرکزی).
 */
class TapOutsideFocusUiTest {

  @get:Rule val rule = createComposeRule()

  @Before fun setUp() { I18n.source = { Lang.FA } }

  private fun show(content: @Composable () -> Unit) {
    rule.setContent { CompositionLocalProvider(LocalContext provides DesktopContext) { TohidTheme(ThemeChoice.LIGHT) { content() } } }
    rule.waitForIdle()
  }

  /** زدن روی پایینِ پنجره — جای خالی (نه کادر، نه دکمه) */
  private fun tapEmptyBottom() {
    rule.onRoot().performTouchInput { click(Offset(width / 2f, height - 40f)) }
    rule.waitForIdle()
  }

  @Test fun searchDoesNotFocusTheFieldOnOpenAndTapOutsideClearsFocus() {
    show {
      SearchScreen(
        d = ShopData(), sections = emptyList(), initialQuery = "",
        onOpen = {}, onScan = {}, onBack = null, bottomReserve = 0.dp,
      )
    }
    val field = rule.onNode(hasSetTextAction())
    field.assertIsNotFocused()
    field.performClick()
    rule.waitForIdle()
    field.assertIsFocused()
    tapEmptyBottom()
    field.assertIsNotFocused()
  }

  @Test fun anyKitScaffoldPageClearsFocusOnATapOutsideButKeepsItOnTheFieldItself() {
    show {
      V3Scaffold(title = "آزمون", largeTitle = false) { pad ->
        var text by remember { mutableStateOf("") }
        Column(Modifier.padding(pad).padding(16.dp)) {
          V3TextField(text, { text = it }, "نام", Modifier.fillMaxWidth())
          Text("بیرونِ کادر")
        }
      }
    }
    val field = rule.onNode(hasSetTextAction())
    field.assertIsNotFocused()
    field.performClick()
    rule.waitForIdle()
    field.assertIsFocused()
    //  زدنِ دوباره روی خودِ کادر تمرکز را نمی‌گیرد
    field.performClick()
    rule.waitForIdle()
    field.assertIsFocused()
    tapEmptyBottom()
    field.assertIsNotFocused()
  }
}
