package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrInvoiceOcr
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.shots.MoneyDemo
import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import ir.vil3ntec.tohid.ui.screens.LocalInvoicePhotoPicker
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  PUR-12 / AI-10 روی «ثبت خرید» ِ گوشیِ نسخهٔ ۳ (`purchasing/new`، `V3Sheet`): دکمه با پرچمِ روشن، بازبینی، و فقط ردیفِ
 *  تأییدشده در کادرهای همان فرم؛ دفتر تا «ثبت» دست نمی‌خورد.
 */
class InvoiceOcrPhoneUiTest {

  @get:Rule val rule = createComposeRule()
  private lateinit var backend: TestBackend

  @Before fun up() { I18n.source = { Lang.FA }; ShopRole.remember(DesktopContext, ShopRole.OWNER); backend = TestBackend() }
  @After fun down() { backend.close() }

  private val picker: @Composable (Boolean, () -> Unit, (ByteArray?) -> Unit) -> Unit = { open, dismiss, bytes ->
    LaunchedEffect(open) { if (open) { dismiss(); bytes(byteArrayOf(-1, -40, -1, -32, 9)) } }
  }

  private fun has(t: String) = rule.onAllNodes(hasText(t)).fetchSemanticsNodes().isNotEmpty()
  private fun tap(t: String) {
    rule.waitUntil(15_000) { rule.onAllNodes(hasText(t) and hasClickAction()).fetchSemanticsNodes().isNotEmpty() }
    rule.onAllNodes(hasText(t) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }
  private fun fieldTexts(): List<String> = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
    .mapNotNull { it.config.getOrNull(SemanticsProperties.EditableText)?.text }

  @Test fun onlyTheConfirmedRowFillsThePhoneForm() {
    backend.server.on("/api/v1/shop/purchase-ocr/config") { 200 to """{"enabled":true,"permitted":true,"paid":true,"available":true,"missing":[]}""" }
    backend.server.on("/api/v1/shop/purchase-ocr") {
      200 to """{"lines":[{"text":"ردیف شرح کالا تعداد فی مبلغ","conf":90},{"text":"۱ برنج سیلا ۵ کیلویی ۱۰ ۹۵۰ ۹٬۵۰۰","conf":90},{"text":"۲ چای سبز ۶ ۴۲۰ ۲۵۲۰","conf":90}]}"""
    }
    val store = RouteUiHarness.loadedStore(MoneyDemo.full())
    val before = store.data.value
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
        LocalInvoicePhotoPicker provides picker,
      ) { TohidTheme(ThemeChoice.LIGHT) { V3RoutePreview("purchasing/new", store, dark = false) } }
    }
    tap(StrInvoiceOcr.button)
    rule.waitUntil(15_000) { has(StrInvoiceOcr.notice) }
    assertEquals(1, backend.server.hits("/api/v1/shop/purchase-ocr"))
    tap(StrInvoiceOcr.confirm)                         //  فقط برنج؛ چای تأیید نمی‌شود
    tap(StrInvoiceOcr.addRows(1))
    rule.waitUntil(10_000) { !has(StrInvoiceOcr.notice) }
    rule.waitUntil(10_000) { has("برنج سیلا ۵ کیلویی") }
    val texts = fieldTexts()
    assertTrue(texts.toString(), "10" in texts && "950" in texts)
    assertTrue("چای تأییدنشده به فرم نیامد", "6" !in texts && "420" !in texts)
    assertEquals("هیچ ثبتی پیش از «ثبت خرید»", before.purchases.size, store.data.value.purchases.size)
  }
}
