package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ReturnSettings
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ ۶۵h برای صاحب‌کار — «تاریخچه فروش» (`sales`) روی گوشیِ ۳۶۰×۷۶۰dp (چگالیِ ۲)، فارسی و راست‌به‌چپ، روشن
 *  و تیره، با نوارِ شناورِ پایین؛ «برگشت و تعویض» روشن مثلِ گوشیِ صاحب‌کار. دو حالت: روزِ خالی (فقط ۵۰۰ ؋ قرضِ وصول‌شده) و
 *  روزِ پر ([HistoryDemo.busy]).
 *  فقط با `E65H_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست)؛ `E65H_TAG` = `before`/`after` (پیش‌فرض `after`). نام:
 *  `history-<empty|busy>-<before|after>-<light|dark>.png`، و همان با `-list` پس از ۵۰۰dp بالا کشیدنِ فهرست. فقط API ِ پایه ⇒ همین پرونده روی شاخهٔ پایه هم عکسِ «پیش» می‌گیرد.
 */
@OptIn(ExperimentalTestApi::class)
class E65hShotsTest {

  private val out: File? = System.getenv("E65H_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val tag: String = System.getenv("E65H_TAG")?.takeIf { it.isNotBlank() } ?: "after"

  private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) = runDesktopComposeUiTest(720, 1520, testTimeout = 3.minutes) {
    I18n.source = { Lang.FA }
    mainClock.autoAdvance = false
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(2f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(360, 760),
      ) {
        TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { content() }
        }
      }
    }
    //  دفترِ تاریخچه در پس‌زمینه ساخته می‌شود ⇒ چند گام ساعت و کمی صبر
    repeat(32) {
      mainClock.advanceTimeBy(250)
      Thread.sleep(80)
    }
    val dir = out!!.apply { mkdirs() }
    val theme = if (dark) "dark" else "light"
    ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "history-$name-$tag-$theme.png"))
    //  همان صفحه، فهرست بالا کشیده (۵۰۰dp) — ردیف‌های فاکتور و سرخطِ روز زیرِ کارت‌ها پیدا شوند
    val lists = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).fetchSemanticsNodes()
    val tallest = lists.indices.maxByOrNull { lists[it].boundsInRoot.height } ?: return@runDesktopComposeUiTest
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy))[tallest].performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 1000f) }
    repeat(12) {
      mainClock.advanceTimeBy(250)
      Thread.sleep(60)
    }
    ImageIO.write(captureToImage().toAwtImage(), "png", File(dir, "history-$name-$tag-$theme-list.png"))
  }

  private fun route(name: String, data: ShopData) {
    for (dark in listOf(false, true)) shot(name, dark) {
      val store = remember { loadedStore(data) }
      V3RoutePreview("sales", store, dark)
    }
  }

  /** دفترِ حافظه‌ایِ «خوانده‌شده» — بی آن صفحه اسکلت می‌کشد */
  private fun loadedStore(d: ShopData): ir.vil3ntec.tohid.data.ShopStore = RouteUiHarness.memoryStore(d).also { s ->
    @Suppress("UNCHECKED_CAST")
    (ir.vil3ntec.tohid.data.ShopStore::class.java.getDeclaredField("_loaded").apply { isAccessible = true }.get(s) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>).value = true
  }

  @Test
  fun shots() {
    Assume.assumeTrue("E65H_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val was = ReturnSettings.enabled(DesktopContext)
    ReturnSettings.setEnabled(DesktopContext, true)
    try {
      route("empty", HistoryDemo.empty())
      route("busy", HistoryDemo.busy())
    } finally {
      ReturnSettings.setEnabled(DesktopContext, was)
    }
  }
}
