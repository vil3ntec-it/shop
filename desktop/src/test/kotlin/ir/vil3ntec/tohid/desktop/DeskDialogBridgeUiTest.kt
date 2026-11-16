package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.ui.components.common.SHOP_DIALOG_TAG
import ir.vil3ntec.tohid.ui.components.common.ShopAlertDialog
import ir.vil3ntec.tohid.ui.components.common.ShopButton
import ir.vil3ntec.tohid.ui.components.common.ShopTextButton
import ir.vil3ntec.tohid.ui.screens.ReprintReasonDialog
import ir.vil3ntec.tohid.ui.screens.WipeAllDialog
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 *  `claude/e-desk-d3d` — پلِ پنجره‌های مشترک (`ShopAlertDialog` ِ `ui/components/common/ShopDialogs.kt`) روی کامپیوتر، برای هر پنجرهٔ
 *  نمونه (عمومی، پاک‌کننده، «پاک کردنِ همه»، «دلیلِ چاپِ دوباره»):
 *   • Esc ⇒ همان `onDismissRequest` و کار انجام نمی‌شود؛
 *   • کارِ عادی: Enter روی پنجره ⇒ همان دکمهٔ «تأیید» (پس از پیش‌نیازِ خودِ پنجره، مثلاً برگزیدنِ دلیل)؛
 *   • کارِ پاک‌کننده: فوکوسِ آغاز روی «انصراف» ⇒ Enter همان «انصراف» است، نه کار.
 *  گوشی همان `AlertDialog` است (`LocalDeskLayout` = false) — پلِ گوشی فقط آرگومان‌ها را می‌گذراند.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class DeskDialogBridgeUiTest(private val case: Case) {

  class Log { val events = ArrayList<String>() }

  /** یک پنجره: نامِ آزمون، نوشته‌ای که باز بودنش را می‌گوید، پاک‌کننده یا نه، نوشتهٔ «انصراف»، آمادگی پیش از Enter و خودِ پنجره */
  class Case(
    val name: String,
    val shown: () -> String,
    val danger: Boolean,
    val cancel: () -> String,
    val prepare: (DeskDialogBridgeUiTest) -> Unit = {},
    val content: @Composable (Log) -> Unit,
  ) { override fun toString() = name }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun cases(): List<Case> = listOf(
      Case("generic", { "پرسشِ نمونه" }, danger = false, cancel = { Str.cancel }) { log ->
        ShopAlertDialog(
          onDismissRequest = { log.events += "dismiss" },
          title = { Text("پرسشِ نمونه") },
          text = { Text("متنِ نمونه") },
          confirmButton = { ShopButton(onClick = { log.events += "confirm" }) { Text("بله") } },
          dismissButton = { ShopTextButton(onClick = { log.events += "dismiss" }) { Text(Str.cancel) } },
        )
      },
      Case("generic-danger", { "پاک شود؟" }, danger = true, cancel = { Str.cancel }) { log ->
        ShopAlertDialog(
          danger = true,
          onDismissRequest = { log.events += "dismiss" },
          title = { Text("پاک شود؟") },
          confirmButton = { ShopTextButton(onClick = { log.events += "confirm" }) { Text("پاک کن") } },
          dismissButton = { ShopTextButton(onClick = { log.events += "dismiss" }) { Text(Str.cancel) } },
        )
      },
      Case("wipe-all", { StrSettings.wipeQ }, danger = true, cancel = { Str.cancel }) { log ->
        WipeAllDialog("دکانِ آزمون", onDismiss = { log.events += "dismiss" }, onConfirm = { log.events += "confirm" })
      },
      Case("reprint-reason", { StrSale.reprintTitle }, danger = false, cancel = { Str.cancel },
        prepare = { t -> t.type("کاغذ گیر کرد") }) { log ->
        ReprintReasonDialog(onDismiss = { log.events += "dismiss" }, onConfirm = { log.events += "confirm:$it" })
      },
    )
  }

  @get:Rule val rule = createComposeRule()
  private val log = Log()

  @Before fun up() { I18n.source = { Lang.FA }; ShopRole.remember(DesktopContext, ShopRole.OWNER) }

  private fun show() {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext, LocalDeskLayout provides true) {
        TohidTheme(ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Box(Modifier.fillMaxSize()) { case.content(log) } }
        }
      }
    }
    rule.waitUntil(10_000) { rule.onAllNodes(hasText(case.shown())).fetchSemanticsNodes().isNotEmpty() }
  }

  /** دلیلِ دیگر در کادرِ پنجره (پیش‌نیازِ «چاپِ دوباره») */
  internal fun type(text: String) {
    rule.onAllNodes(hasSetTextAction())[0].performTextInput(text)
    rule.waitForIdle()
  }

  private fun card() = rule.onNodeWithTag(SHOP_DIALOG_TAG)

  @Test fun escapeCancelsWithoutTheAction() {
    show()
    card().performSemanticsAction(SemanticsActions.RequestFocus)
    card().performKeyInput { pressKey(Key.Escape) }
    rule.waitForIdle()
    assertEquals(listOf("dismiss"), log.events.distinct())
  }

  @Test fun enterConfirmsANormalDialogAndCancelsADestructiveOne() {
    show()
    case.prepare(this)
    if (case.danger) {
      //  فوکوسِ آغاز روی «انصراف» ⇒ Enter همان «انصراف»
      val cancel = rule.onAllNodes(hasText(case.cancel()) and hasClickAction())[0]
      rule.waitUntil(5_000) { runCatching { cancel.assertIsFocused(); true }.getOrDefault(false) }
      cancel.performKeyInput { pressKey(Key.Enter) }
      rule.waitForIdle()
      assertEquals(listOf("dismiss"), log.events)
    } else {
      card().performSemanticsAction(SemanticsActions.RequestFocus)
      card().performKeyInput { pressKey(Key.Enter) }
      rule.waitForIdle()
      assertEquals(1, log.events.count { it.startsWith("confirm") })
      if (case.name == "reprint-reason") assertEquals(listOf("confirm:کاغذ گیر کرد"), log.events)
    }
  }
}
