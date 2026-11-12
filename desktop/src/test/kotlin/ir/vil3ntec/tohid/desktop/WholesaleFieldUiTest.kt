package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrGoods
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrPriceCheck
import ir.vil3ntec.tohid.core.i18n.StrProducts
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductDraftRules
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.products.FieldHooks
import ir.vil3ntec.tohid.ui.v3.products.ProductFieldGroups
import ir.vil3ntec.tohid.ui.v3.products.ProductFormModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 *  PRD-12 — کادرِ «قیمتِ عمده» ِ فرمِ کالا (`ProductFieldGroups`، مشترکِ «کالای تازه» و «ویرایش کالا» ِ گوشی و کامپیوتر) با آزمونِ
 *  خودکارِ صفحه: فقط با «لیستِ قیمت در صندوق» دیده می‌شود؛ عددِ نوشته‌شده همان `wholesalePrice` ِ پیش‌نویس است؛ منفی خطای کادر
 *  و ردِ قاعده (`StrGoods.wholesaleBad`) را دارد؛ قیمتِ عمدهٔ پیشین در ویرایش در کادر می‌آید.
 */
class WholesaleFieldUiTest {

  @get:Rule val rule = createComposeRule()

  private val p1 = Product(id = "p1", name = "برنج", unit = "عدد", purchasePrice = 100.0, salePrice = 120.0, wholesalePrice = 110.0)
  private var row by mutableStateOf(ProductFormModel.of(p1))
  private val focus = ProductFormModel.Field.entries.associateWith { FocusRequester() }

  private fun show(wholesale: Boolean) {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          Column {
            ProductFieldGroups(
              row, ProductFormModel.errors(row, ProductFormModel.Mode.EDIT), emptyList(), listOf("عدد"),
              FieldHooks(onChange = { f -> row = f(row) }, onBlur = {}, onScan = {}, onNewCategory = {}, focus = { focus.getValue(it) }),
              { title, content -> Column { Text(title); content() } },
              ProductFormModel.Mode.EDIT,
              wholesale = wholesale,
            )
          }
        }
      }
    }
  }

  private fun box() = rule.onNode(hasSetTextAction() and hasContentDescription(StrPriceCheck.wholesale))

  @Test fun wholesaleBoxIsShownOnlyWithPriceListsAndCarriesTheTypedPrice() {
    show(wholesale = true)
    rule.onNodeWithText(StrProducts.wholesaleHelper).assertExists()
    //  ویرایش: قیمتِ عمدهٔ پیشین در کادر
    assertEquals("110", row.wholesale)
    box().performTextReplacement("95")
    rule.waitForIdle()
    assertEquals("95", row.wholesale)
    assertEquals(95.0, ProductFormModel.draftEdit(row).wholesalePrice!!, 0.0)
    assertTrue(ProductFormModel.errors(row, ProductFormModel.Mode.EDIT).isEmpty())
  }

  @Test fun minusCannotBeTypedAndAWrongValueShowsTheFieldErrorAndTheRuleRejectsIt() {
    show(wholesale = true)
    //  کادرِ پول منفی نمی‌گیرد: «-5» ⇒ «5»
    box().performTextReplacement("-5")
    rule.waitForIdle()
    assertEquals("5", row.wholesale)
    //  عددِ نادرستی که از راهِ دیگر (چسباندن/دادهٔ کهنه) در فرم نشسته ⇒ خطای همان کادر و ردِ قاعده
    row = row.copy(wholesale = "-5")
    rule.waitForIdle()
    box().assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, StrKit.notANumber))
    assertEquals(StrGoods.wholesaleBad, ProductDraftRules.problem(ShopData(products = listOf(p1)), ProductFormModel.draftEdit(row), p1))
  }

  @Test fun withoutPriceListsThereIsNoWholesaleBox() {
    show(wholesale = false)
    assertTrue(rule.onAllNodes(hasContentDescription(StrPriceCheck.wholesale)).fetchSemanticsNodes().isEmpty())
    assertTrue(rule.onAllNodesWithText(StrProducts.wholesaleHelper).fetchSemanticsNodes().isEmpty())
    rule.onNodeWithText(StrProducts.salePrice).assertExists()
  }
}
