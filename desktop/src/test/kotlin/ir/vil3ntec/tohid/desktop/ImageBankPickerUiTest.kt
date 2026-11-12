package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrDevices
import ir.vil3ntec.tohid.core.i18n.StrImagePages
import ir.vil3ntec.tohid.ui.screens.ImageBankPicker
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 *  IMG-BANK-10 — «انتخاب از بانک» (`ImageBankPicker`؛ کامپیوتر همان صفحهٔ گوشی را می‌سازد) در برابرِ سرورِ ساختگی:
 *  جست‌وجوی بی نتیجه ⇒ پیامِ «چیزی پیدا نشد» و دکمهٔ «عکسِ خودم را می‌گذارم» (هیچ کالای نامرتبطی «نتیجه» نمی‌شود)؛
 *  با نتیجه ⇒ همان نتیجه، بی آن دکمه؛ بی `onUploadOwn` ⇒ بی دکمه؛ خطای سرور ⇒ «دوباره»، نه «بی نتیجه».
 *
 *  سرورِ ساختگی بی درنگ پاسخ می‌دهد — همان حالتی که باگ را نشان داد: جست‌وجوی نخست پیش از ساخته شدنِ پنجره شروع می‌شد و
 *  پاسخی که پیش از آن می‌رسید حالت را می‌نوشت ولی پنجره «در حالِ جست‌وجو…» می‌ماند (پیش از رفع ۴ از ۴ سرخ؛ با پاسخِ
 *  ۱٫۵ ثانیه‌ای سبز). اکنون جست‌وجو از درونِ خودِ پنجره شروع می‌شود.
 */
@OptIn(ExperimentalTestApi::class)
class ImageBankPickerUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var backend: TestBackend
  private var uploads = 0

  private val empty = """{"items":[],"next_cursor":null,"bank_rev":3}"""
  private val oneHit = """{"items":[{"product":{"id":"cp1","name":"شیرِ پاک","brand":"پاک","size_value":"1","size_unit":"L"},
    "match":{"certain":false,"kind":"name_words","reasons":[]},"images":[]}],"next_cursor":null,"bank_rev":3}"""

  @Before fun up() {
    I18n.source = { Lang.FA }
    //  کشِ بی‌اینترنتیِ بانک (IMG-BANK-11) روی دیسکِ آزمون از اجرای پیشین نماند
    File(DesktopContext.cacheDir, "imgbank-search.json").delete()
    backend = TestBackend()
  }

  @After fun down() = backend.close()

  private fun reply(status: Int, body: String) = backend.server.on("/api/v1/shop/images/search") { status to body }

  private fun show(uploadOwn: Boolean) {
    rule.setContent {
      CompositionLocalProvider(LocalContext provides DesktopContext) {
        TohidTheme(ThemeChoice.LIGHT) {
          ImageBankPicker(
            productId = "p1", name = "شیر", barcode = "", hasPhoto = false, onDismiss = {},
            onUploadOwn = if (uploadOwn) ({ uploads++ }) else null, onChosen = {},
          )
        }
      }
    }
  }

  private fun settled(text: String) {
    rule.waitUntil(15_000) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    assertTrue(rule.onAllNodesWithText(StrDevices.searching).fetchSemanticsNodes().isEmpty())
  }

  @Test fun noResultSaysSoAndOffersOwnPhotoWithoutAnyUnrelatedHit() {
    reply(200, empty)
    show(uploadOwn = true)
    settled(StrImagePages.nothingFound)
    assertEquals(1, backend.server.hits("/api/v1/shop/images/search"))
    rule.onNodeWithText(StrImagePages.useOwnPhoto).performClick()
    rule.waitForIdle()
    assertEquals(1, uploads)
    assertTrue(rule.onAllNodesWithText("شیرِ پاک", substring = true).fetchSemanticsNodes().isEmpty())
  }

  @Test fun aHitIsShownAndThereIsNoOwnPhotoButton() {
    reply(200, oneHit)
    show(uploadOwn = true)
    settled(StrImagePages.resultsCount(1))
    rule.onAllNodesWithText("شیرِ پاک", substring = true).fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    assertTrue(rule.onAllNodesWithText(StrImagePages.useOwnPhoto).fetchSemanticsNodes().isEmpty())
  }

  @Test fun withoutAnUploadPathThereIsNoButtonEvenWhenEmpty() {
    reply(200, empty)
    show(uploadOwn = false)
    settled(StrImagePages.nothingFound)
    assertTrue(rule.onAllNodesWithText(StrImagePages.useOwnPhoto).fetchSemanticsNodes().isEmpty())
  }

  @Test fun aServerErrorIsAnErrorWithRetryNotAnEmptyResult() {
    reply(400, """{"error":"bad_query","message":"نادرست"}""")
    show(uploadOwn = true)
    settled(StrImagePages.serverError(""))
    assertTrue(rule.onAllNodesWithText(StrImagePages.nothingFound).fetchSemanticsNodes().isEmpty())
    assertTrue(rule.onAllNodesWithText(StrImagePages.useOwnPhoto).fetchSemanticsNodes().isEmpty())
    rule.onNodeWithText(ir.vil3ntec.tohid.core.i18n.Str.again).assertExists()
  }
}
