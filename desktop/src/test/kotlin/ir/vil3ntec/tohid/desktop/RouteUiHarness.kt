package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.net.TestHttpServer
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.flow.MutableStateFlow

/**
 *  ابزارِ آزمونِ صفحه برای مسیرهای نسخهٔ ۳ (`RouteScope` با `ShellContext` ِ واقعی): مسیر درست مثلِ برنامه باز می‌شود
 *  (`V3RoutePreview`: پشتهٔ پدرها، پوسته، `ShellContext`، نوار)، `Backend.api` به سرورِ ساختگیِ 127.0.0.1 ([TestBackend]) و
 *  دفترِ دکان فقط در حافظه (هیچ نوشتنی روی پوشهٔ دادهٔ آزمون مگر صفحه خودش در دفتر بنویسد).
 *
 *  کاربرد: `RouteUiHarness(rule).use { h -> h.server.on(...); h.show("purchasing/orders", data); h.await("…"); h.click("…") }`.
 *  کلیک با کنشِ معناییِ `OnClick` است (نه لمسِ مختصات) تا ردیفی که پایینِ صفحهٔ پیمایشی است هم زده شود.
 */
@OptIn(ExperimentalTestApi::class)
internal class RouteUiHarness(private val rule: ComposeContentTestRule) : AutoCloseable {
  val backend = TestBackend()
  val server: TestHttpServer get() = backend.server
  /** دفترِ حافظه‌ایِ آخرین [show] — برای سنجیدنِ آنچه صفحه نوشت */
  var store: ShopStore? = null
    private set

  fun show(path: String, data: ShopData) = show(path, memoryStore(data))

  /** با دفترِ دلخواه — مثلاً دفترِ بارشدهٔ روی دیسک (`ShopStore.load`) وقتی صفحه بیرون از برگه در دفتر می‌نویسد (`update`) */
  fun show(path: String, store: ShopStore) {
    I18n.source = { Lang.FA }
    this.store = store
    rule.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(ThemeChoice.LIGHT) { V3RoutePreview(path, store, dark = false) }
      }
    }
  }

  fun has(text: String, substring: Boolean = false): Boolean =
    rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

  fun await(text: String, substring: Boolean = false, ms: Long = 15_000) = awaitThat(ms) { has(text, substring) }

  fun awaitThat(ms: Long = 15_000, what: () -> Boolean) = rule.waitUntil(ms) { what() }

  /** نخستین گرهِ کلیک‌پذیر با همین نوشته */
  fun click(text: String, substring: Boolean = false) {
    rule.onAllNodes(hasText(text, substring = substring) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
    rule.waitForIdle()
  }

  /** کادرِ نوشتن با همین برچسب (`V3TextField`: توضیحِ محتوا؛ کادرِ Material: نوشتهٔ برچسب) */
  fun type(label: String, value: String) {
    val field: SemanticsMatcher = hasSetTextAction() and (hasContentDescription(label) or hasText(label))
    rule.onAllNodes(field)[0].performTextInput(value)
    rule.waitForIdle()
  }

  override fun close() = backend.close()

  companion object {
    /** دفترِ نمایشی فقط در حافظه (همان راهِ `NewScreensRenderTest`) */
    fun memoryStore(d: ShopData): ShopStore = ShopStore(DesktopContext).also { s ->
      @Suppress("UNCHECKED_CAST")
      (ShopStore::class.java.getDeclaredField("_data").apply { isAccessible = true }.get(s) as MutableStateFlow<ShopData>).value = d
    }
  }
}
