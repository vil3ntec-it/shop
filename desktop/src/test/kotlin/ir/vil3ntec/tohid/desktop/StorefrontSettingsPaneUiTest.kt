package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.StorefrontSettingsDto
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.online.storefront.SettingsPane
import ir.vil3ntec.tohid.ui.v3.online.storefront.StorefrontSettingsModel
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/**
 *  WEB-01 — برگهٔ «تنظیمات» ِ ویترین (`SettingsPane`، گوشی و کامپیوتر) با آزمونِ خودکارِ صفحه: نشانیِ هر دکان (`….vill3n.top`) زیرِ
 *  کادر، نام و رنگ؛ رنگِ نادرست خطای همان کادر را دارد و ذخیره نمی‌شود؛ روشن/خاموش کردنِ ویترین همان کلید است و به بدنهٔ ذخیره
 *  می‌رسد؛ روشن بی نام ⇒ خطای نام؛ بی ارسال و بی دریافت ⇒ خطای «دست‌کم یکی».
 */
class StorefrontSettingsPaneUiTest {

  @get:Rule val rule = createComposeRule()

  private var form by mutableStateOf(
    StorefrontSettingsModel.from(StorefrontSettingsDto(slug = "dokan", title = "دکانِ من", accent = "#2c5ce6", enabled = true)),
  )

  private fun show() {
    I18n.source = { Lang.FA }
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          SettingsPane(form, StorefrontSettingsModel.build(form).errors, { form = it }, PaddingValues())
        }
      }
    }
  }

  private fun field(label: String) = rule.onNode(hasSetTextAction() and hasContentDescription(label))

  @Test fun addressNameAndColourOfThisShopAreShownAndSaved() {
    show()
    rule.onNodeWithText(StrOnline.sfSlugHelper("dokan.vill3n.top")).assertExists()
    field(StrOnline.sfSlug).performScrollTo().performTextReplacement("bazar-kabul")
    rule.waitForIdle()
    rule.onNodeWithText(StrOnline.sfSlugHelper("bazar-kabul.vill3n.top")).assertExists()
    val body = StorefrontSettingsModel.build(form).body!!
    assertEquals("bazar-kabul", body["slug"]!!.jsonPrimitive.content)
    assertEquals("دکانِ من", body["title"]!!.jsonPrimitive.content)
    assertEquals("#2c5ce6", body["accent"]!!.jsonPrimitive.content)
  }

  @Test fun wrongColourShowsTheFieldErrorAndNothingIsSaved() {
    show()
    field(StrOnline.sfAccent).performScrollTo().performTextReplacement("#12")
    rule.waitForIdle()
    field(StrOnline.sfAccent).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, StrOnline.sfAccentFormat))
    assertNull(StorefrontSettingsModel.build(form).body)
  }

  @Test fun theOnOffSwitchReachesTheSavedBodyAndOnNeedsAName() {
    show()
    val sw = rule.onNodeWithText(StrOnline.sfEnabled)
    sw.performScrollTo().assertIsOn().performClick()
    rule.waitForIdle()
    sw.assertIsOff()
    assertFalse(form.enabled)
    assertEquals("false", StorefrontSettingsModel.build(form).body!!["enabled"]!!.jsonPrimitive.content)
    //  روشن دوباره بی نام ⇒ خطای نام زیرِ کادر
    field(StrOnline.sfName).performScrollTo().performTextReplacement("")
    sw.performScrollTo().performClick()
    rule.waitForIdle()
    field(StrOnline.sfName).assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, StrOnline.sfTitleRequired))
  }

  @Test fun turningOffBothDeliveryAndPickupShowsTheAtLeastOneError() {
    show()
    rule.onNodeWithText(StrOnline.sfDeliveryToggle).performScrollTo().performClick()
    rule.onNodeWithText(StrOnline.sfPickupToggle).performScrollTo().performClick()
    rule.waitForIdle()
    rule.onNodeWithText(StrOnline.sfOneFulfilment).performScrollTo().assertExists()
    assertNull(StorefrontSettingsModel.build(form).body)
  }
}
