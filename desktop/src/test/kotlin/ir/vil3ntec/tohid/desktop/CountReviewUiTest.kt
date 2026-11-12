package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrCount
import ir.vil3ntec.tohid.data.CountReview
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import ir.vil3ntec.tohid.ui.screens.StockCountToolsSheet
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  INV-09 — صفحهٔ بازبینیِ شمارش (همان `StockCountToolsSheet` ِ «انبارگردانیِ تازه»، گوشی و کامپیوتر) با آزمونِ خودکارِ صفحه:
 *  با بارکدخوان (کادرِ اسکن + Enter) یکی منطبق و یکی مغایر شمرده می‌شود و سومی شمرده‌نشده می‌ماند ⇒ تراشه‌های شمرده‌نشده/منطبق/
 *  مغایر با اثرِ مالی و هشدارِ «شمرده‌نشده دست نمی‌خورد» دیده می‌شوند؛ «مستثنا» تراشهٔ خودش را دارد و از ثبت بیرون است؛ «شمارشِ دوباره»
 *  عددِ پیشینِ مغایر را کنار می‌گذارد تا دوباره شمرده شود؛ شمارشِ کور منطبق/مغایر و CSV را نشان نمی‌دهد.
 */
@OptIn(ExperimentalTestApi::class)
class CountReviewUiTest {

  @get:Rule val rule = createComposeRule()

  private val d = ShopData(
    products = listOf(
      Product(id = "pA", name = "برنج", unit = "عدد", purchasePrice = 10.0, barcodes = listOf("111")),
      Product(id = "pB", name = "روغن", unit = "عدد", purchasePrice = 50.0, barcodes = listOf("222")),
      Product(id = "pC", name = "چای", unit = "عدد", purchasePrice = 20.0, barcodes = listOf("333")),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "wA", productId = "pA", units = 1.0),
      WarehouseEntry(id = "wB", productId = "pB", units = 3.0),
      WarehouseEntry(id = "wC", productId = "pC", units = 5.0),
    ),
  )

  private val saves = ArrayList<Map<String, Double>>()

  private fun show(blind: Boolean) {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          StockCountToolsSheet(
            d = d, blind = blind, ownSheetId = "own", imported = emptyList(), notice = null,
            onDismiss = {}, onExport = {}, onShare = {}, onImport = {}, onRemoveSheet = {},
            onSave = { counts, _, _ -> saves += counts },
            now = { 1_000L },
          )
        }
      }
    }
  }

  private fun scan(code: String) {
    val box = rule.onNode(hasSetTextAction() and hasImeAction(ImeAction.Done))
    box.performTextInput(code)
    box.performKeyInput { pressKey(Key.Enter) }
    rule.waitForIdle()
  }

  @Test fun reviewShowsUncountedMatchedDifferingWithMoneyAndTheUntouchedWarning() {
    show(blind = false)
    scan("111")
    scan("222")
    val value = CountReview.of(d, d.products, mapOf("pA" to 1.0, "pB" to 1.0)).value
    rule.onNodeWithText(StrCount.uncounted(plain(1))).assertExists()
    rule.onNodeWithText(StrCount.matched(plain(1))).assertExists()
    rule.onNodeWithText(StrCount.differing(plain(1), money(value))).assertExists()
    rule.onNodeWithText(StrCount.untouched).assertExists()
    rule.onNodeWithText(StrCount.save).performClick()
    rule.waitForIdle()
    //  شمرده‌نشده (چای) در ثبت نیست ⇒ دست نمی‌خورد
    assertEquals(listOf(mapOf("pA" to 1.0, "pB" to 1.0)), saves)
  }

  @Test fun excludedRowGetsItsOwnChipAndIsNeverSaved() {
    show(blind = false)
    scan("111")
    scan("222")
    //  ردیفِ دوم = روغن (ترتیبِ فهرستِ کالا)
    rule.onAllNodesWithText(StrCount.exclude)[1].performClick()
    rule.waitForIdle()
    rule.onNodeWithText(StrCount.excludedChip(plain(1))).assertExists()
    rule.onNodeWithText(StrCount.excludedMark).assertExists()
    rule.onNodeWithText(StrCount.includeAgain).assertExists()
    rule.onNodeWithText(StrCount.save).performClick()
    rule.waitForIdle()
    assertEquals(listOf(mapOf("pA" to 1.0)), saves)
    //  برگرداندن ⇒ دوباره در ثبت
    rule.onNodeWithText(StrCount.includeAgain).performClick()
    rule.onNodeWithText(StrCount.save).performClick()
    rule.waitForIdle()
    assertEquals(mapOf("pA" to 1.0, "pB" to 1.0), saves.last())
  }

  @Test fun recountClearsTheDifferingCountUntilItIsCountedAgain() {
    show(blind = false)
    scan("111")
    scan("222")
    rule.onNodeWithText(StrCount.recountButton(plain(1))).performClick()
    rule.waitForIdle()
    rule.onNodeWithText(StrCount.recountStarted(plain(1))).assertExists()
    rule.onNodeWithText(StrCount.recountMark).assertExists()
    rule.onNodeWithText(StrCount.save).performClick()
    rule.waitForIdle()
    assertEquals(listOf(mapOf("pA" to 1.0)), saves)
    //  شمارشِ تازه: دو بار اسکن ⇒ ۲ (نه ۱ + ۲)
    scan("222")
    scan("222")
    rule.onNodeWithText(StrCount.save).performClick()
    rule.waitForIdle()
    assertEquals(mapOf("pA" to 1.0, "pB" to 2.0), saves.last())
  }

  @Test fun blindCountHidesMatchedDifferingRecountAndCsv() {
    show(blind = true)
    scan("111")
    scan("222")
    rule.onNodeWithText(StrCount.blindSubtitle).assertExists()
    rule.onNodeWithText(StrCount.uncounted(plain(1))).assertExists()
    //  تراشهٔ «شمرده» (به جای منطبق/مغایر) و خلاصهٔ پایین
    assertEquals(2, rule.onAllNodesWithText(StrCount.summaryCounted(plain(2))).fetchSemanticsNodes().size)
    assertTrue(rule.onAllNodesWithText(StrCount.matched(plain(1))).fetchSemanticsNodes().isEmpty())
    assertTrue(rule.onAllNodesWithText(StrCount.recountButton(plain(1))).fetchSemanticsNodes().isEmpty())
    assertTrue(rule.onAllNodesWithText(StrCount.csv).fetchSemanticsNodes().isEmpty())
    rule.onNodeWithText(StrCount.save).performClick()
    rule.waitForIdle()
    assertEquals(listOf(mapOf("pA" to 1.0, "pB" to 1.0)), saves)
  }
}
