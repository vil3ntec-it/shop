package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrTouch
import ir.vil3ntec.tohid.core.pos.TouchMode
import ir.vil3ntec.tohid.core.pos.SaleNotes
import ir.vil3ntec.tohid.ui.components.pos.PosNoteField
import ir.vil3ntec.tohid.ui.components.pos.PosTouchPrefs
import ir.vil3ntec.tohid.ui.screens.DebtorPicker
import ir.vil3ntec.tohid.ui.screens.FreeLineDialog
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  POS-31 (§۵.۳): «صفحه‌کلید روی‌صفحه … با فوکوس هر فیلد متنی در حالت لمسی باز می‌شود» — کادرهای دیگرِ صندوقِ
 *  کامپیوتر (نامِ قلمِ آزاد F4، یادداشتِ ردیف و فاکتور) و پیش‌فرضِ تنظیم از صفحهٔ لمسیِ تشخیص‌داده.
 */
@OptIn(ExperimentalTestApi::class)
class TouchTextKeysUiTest {

  @get:Rule val rule = createComposeRule()

  @After fun reset() {
    PosTouchPrefs.on.value = false
    PosTouchPrefs.active.value = true
  }

  private fun touch(on: Boolean) {
    I18n.source = { Lang.FA }
    PosTouchPrefs.on.value = on
    PosTouchPrefs.active.value = true
  }

  private fun type(word: String) = word.forEach { rule.onNodeWithContentDescription(it.toString()).performClick() }

  @Test fun freeItemNameOpensTheLetterKeysOnFocusAndTypes() {
    touch(true)
    var added: Triple<String, Double, Double>? = null
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          FreeLineDialog(onDismiss = {}, onConfirm = { l, p, q -> added = Triple(l, p, q) })
        }
      }
    }
    rule.waitForIdle()
    //  پیش از فوکوس بسته است؛ فقط کلیدِ «حروف» دیده می‌شود
    rule.onAllNodesWithContentDescription("ح").assertCountEquals(0)
    rule.onAllNodesWithContentDescription(StrTouch.lettersOpen).assertCountEquals(1)
    rule.onAllNodes(hasSetTextAction())[0].performClick()
    rule.waitForIdle()
    type("نان")
    rule.onNode(hasText("نان")).assertExists()
    //  کلیدِ «حروف» آن را می‌بندد
    rule.onNodeWithContentDescription(StrTouch.lettersClose).performClick()
    rule.waitForIdle()
    rule.onAllNodesWithContentDescription("ن").assertCountEquals(0)
    assertEquals(null, added)
  }

  @Test fun noteFieldTypesWithTheLetterKeysWithinTheNoteLimit() {
    touch(true)
    var note by mutableStateOf("x".repeat(SaleNotes.MAX - 1))
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { Column { PosNoteField(note, { note = it }, "یادداشت") } }
      }
    }
    rule.waitForIdle()
    rule.onAllNodes(hasSetTextAction())[0].performClick()
    rule.waitForIdle()
    type("بد")
    //  سقفِ یادداشت (`SaleNotes.MAX`) از صفحه‌کلید هم نمی‌گذرد
    assertEquals("x".repeat(SaleNotes.MAX - 1) + "ب", note)
  }

  @Test fun settingOffOrTogglePausedKeepsTodaysFieldsWithoutKeys() {
    for ((on, active) in listOf(false to true, true to false)) {
      PosTouchPrefs.on.value = on
      PosTouchPrefs.active.value = active
      assertEquals(false, PosTouchPrefs.extras())
    }
    touch(false)
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) { FreeLineDialog(onDismiss = {}, onConfirm = { _, _, _ -> }) }
      }
    }
    rule.waitForIdle()
    rule.onAllNodes(hasSetTextAction())[0].performClick()
    rule.waitForIdle()
    rule.onAllNodesWithContentDescription(StrTouch.lettersOpen).assertCountEquals(0)
    rule.onAllNodesWithContentDescription("ح").assertCountEquals(0)
  }

  @Test fun storedChoiceAndDetectedScreenDecideTheDefault() {
    val dir = kotlin.io.path.createTempDirectory("touch-prefs").toFile()
    try {
      val p = DesktopPrefs(File(dir, "tohid-ui.json"))
      //  هرگز انتخاب نشده: صفحهٔ لمسی ⇒ فقط کلیدِ نوار؛ بی صفحهٔ لمسی ⇒ همان امروز
      assertEquals(TouchMode.Choice(on = true, active = false), PosTouchPrefs.choice(p, detected = true))
      assertEquals(TouchMode.Choice(on = false, active = false), PosTouchPrefs.choice(p, detected = false))
      //  تنظیمِ خاموشِ ذخیره‌شده بر صفحهٔ لمسی برنده است
      p.edit().putBoolean("pos_touch", false).apply()
      assertEquals(false, PosTouchPrefs.choice(p, detected = true).on)
      //  روشن کردنِ صریح بی کلیدِ ذخیره‌شده ⇒ همان دم لمسی
      p.edit().putBoolean("pos_touch", true).apply()
      assertEquals(TouchMode.Choice(on = true, active = true), PosTouchPrefs.choice(p, detected = false))
      p.edit().putBoolean("pos_touch_active", false).apply()
      assertEquals(TouchMode.Choice(on = true, active = false), PosTouchPrefs.choice(p, detected = true))
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test fun probeNeverThrowsOnThisMachine() {
    //  هر خطای سیستم‌عامل ⇒ «نه» (همان صندوقِ امروز)، هرگز استثنا
    val first = ir.vil3ntec.tohid.ui.components.pos.TouchScreenProbe.detected
    assertEquals(first, ir.vil3ntec.tohid.ui.components.pos.TouchScreenProbe.detected)
  }

  @Test fun touchTillAddsANewCustomerWithTheOnScreenKeys() {
    touch(true)
    val d = ShopData(debtors = listOf(Debtor(id = "a", name = "احمد")))
    val added = mutableListOf<String>()
    var withAdd by mutableStateOf(true)
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          DebtorPicker(d = d, selected = null, onClose = {}, onPick = {}, touch = true, onAdd = if (withAdd) ({ n -> added += n; null }) else null)
        }
      }
    }
    rule.waitForIdle()
    rule.onNodeWithContentDescription(StrTouch.lettersOpen).performClick()
    rule.waitForIdle()
    //  نامِ موجود ⇒ دکمهٔ «مشتریِ تازه» نیست
    type("احمد")
    rule.onAllNodes(hasText(StrSale.newCustomerNamed("احمد"))).assertCountEquals(0)
    rule.onNodeWithContentDescription(StrTouch.clearAll).performClick()
    type("زهرا")
    rule.waitForIdle()
    rule.onNode(hasText(StrSale.newCustomerNamed("زهرا"))).performClick()
    assertEquals(listOf("زهرا"), added)
    //  بی `onAdd` (صندوقِ موشی) دکمه نیست
    withAdd = false
    rule.waitForIdle()
    rule.onAllNodes(hasText(StrSale.newCustomerNamed("زهرا"))).assertCountEquals(0)
  }

  @Test fun deskSaleGivesNewCustomerOnlyToTheTouchTillThroughTheStore() {
    val src = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/DeskSale.kt").readText()
    val call = src.substringAfter("DebtorPicker(d = d, selected = cartDebtorId").substringBefore("if (lineSellerOpen")
    assertEquals(true, call.contains("onAdd = if (!touchExtras) null else { name -> tillNewCustomer(d, name, store, scope,"))
    //  همان `DebtorEngine.add` و `store.save` ِ «مشتریان» با ساعتِ مشترک (`TillNewCustomer.kt`)
    val helper = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/TillNewCustomer.kt").readText()
    assertEquals(true, helper.contains("DebtorEngine.add(d, DebtorEngine.DebtorDraft(name = name), ReportBridge.nowMs(), ::newId)") && helper.contains("store.save(d, r.data)"))
  }

  @Test fun freeLineAndNotesUseTheSharedTouchKeys() {
    val src = File("../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt").readText()
      .substringAfter("fun FreeLineDialog(")
    assertEquals(true, src.contains(".touchKeysOnFocus(keys)") && src.contains("TouchTextKeysPanel(keys, label, { label = it })"))
  }
}
