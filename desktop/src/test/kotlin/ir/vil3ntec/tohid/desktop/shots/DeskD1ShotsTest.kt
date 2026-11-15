package ir.vil3ntec.tohid.desktop.shots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.RouteUiHarness
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.AppRoot
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import org.junit.Assume
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

/**
 *  عکس‌های پیش/پسِ پوستهٔ کامپیوتر (`claude/e-desk-d1`): خودِ `AppRoot` (منوی کناری، سربرگ، نوارِ وضعیت و صفحه) همان‌طور که
 *  برنامهٔ ویندوز می‌کشد — پنجرهٔ ۱۳۶۶×۷۶۸ و ۱۹۲۰×۱۰۸۰ با چگالیِ ۱، فارسی و راست‌به‌چپ، روز و شب، روی دفترِ نمایشیِ [DeskDemo]:
 *  خانه، «همهٔ بخش‌ها» (`more`)، تنظیمات و فهرستِ کالاها.
 *  فقط با `D1_SHOTS=<پوشه>` کشیده می‌شود (آزمونِ سد نیست)؛ `D1_TAG` (`before`/`after`) پسوندِ نام است:
 *  `<پوشه>/<صفحه>-<پهنا>-<tag>-<light|dark>.png`. `D1_ONLY=home,more` فقط همان صفحه‌ها.
 */
/**
 *  هر شیءِ سراسریِ برنامه که وضعیتِ Compose دارد (`mutableStateOf` …) پیش از نخستین ترکیب ساخته می‌شود — برنامهٔ واقعی
 *  آن‌ها را هنگامِ آغاز می‌سازد؛ در آزمون اگر نخستین دست زدن درونِ ترکیب باشد، وضعیتِ ساخته‌شده در عکسِ لحظه‌ایِ ترکیب
 *  بیرونش خوانا نیست («Reading a state that was created after the snapshot was taken»).
 */
internal object GlobalsWarm {
  @Volatile private var done = false

  fun run() {
    if (done) return
    done = true
    val loader = GlobalsWarm::class.java.classLoader
    val state = Regex("""mutable(?:Int|Long|Float)?State(?:Of|MapOf|ListOf)""")
    val pkgRe = Regex("""^package ([\w.]+)""", RegexOption.MULTILINE)
    val objRe = Regex("""^(?:internal |private )?object (\w+)""", RegexOption.MULTILINE)
    for (dir in listOf("../android-native/app/src/main/java", "src/main/kotlin")) {
      java.io.File(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
        val text = f.readText()
        if (!state.containsMatchIn(text)) return@forEach
        val pkg = pkgRe.find(text)?.groupValues?.get(1) ?: return@forEach
        val names = objRe.findAll(text).map { it.groupValues[1] }.toList() + (f.nameWithoutExtension + "Kt")
        for (n in names) runCatching { Class.forName("$pkg.$n", true, loader) }
      }
    }
  }
}

/** مالکِ چرخهٔ عمرِ ثابت در حالتِ «ساخته‌شده» — `LifecycleEventEffect` ِ پوسته بی آن نمی‌نشیند */
private class ShotLifecycle : androidx.lifecycle.LifecycleOwner {
  override val lifecycle: androidx.lifecycle.LifecycleRegistry = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply {
    currentState = androidx.lifecycle.Lifecycle.State.CREATED
  }
}

@OptIn(ExperimentalTestApi::class)
class DeskD1ShotsTest {

  private val out: File? = System.getenv("D1_SHOTS")?.takeIf { it.isNotBlank() }?.let(::File)
  private val tag: String = System.getenv("D1_TAG")?.takeIf { it.isNotBlank() } ?: "after"
  private val only: Set<String>? = System.getenv("D1_ONLY")?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim() }?.toSet()

  private fun shot(name: String, route: String, w: Int, h: Int, dark: Boolean, data: ShopData) = runDesktopComposeUiTest(w, h, testTimeout = 4.minutes) {
    I18n.source = { Lang.FA }
    GlobalsWarm.run()
    mainClock.autoAdvance = false
    val store = loadedStore(data)
    AppNav.request.value = route.takeIf { it != "home" }
    setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(1f, 1f),
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(w, h),
        //  پنجرهٔ آزمون مالکِ چرخهٔ عمر ندارد (برنامهٔ واقعی را `Window` می‌دهد)؛ «ساخته‌شده» بس است
        androidx.lifecycle.compose.LocalLifecycleOwner provides remember { ShotLifecycle() },
      ) {
        val theme = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT
        TohidTheme(theme) {
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            AppRoot(store = store, theme = theme, onTheme = {})
          }
        }
      }
    }
    repeat(20) {
      mainClock.advanceTimeBy(250)
      Thread.sleep(50)
    }
    val img = captureToImage().toAwtImage()
    val dir = out!!.apply { mkdirs() }
    ImageIO.write(img, "png", File(dir, "$name-$w-$tag-${if (dark) "dark" else "light"}.png"))
  }

  /** دفترِ حافظه‌ایِ «خوانده‌شده» — بی آن پوسته اسکلت می‌کشد */
  private fun loadedStore(d: ShopData): ShopStore = RouteUiHarness.memoryStore(d).also { s ->
    @Suppress("UNCHECKED_CAST")
    (ShopStore::class.java.getDeclaredField("_loaded").apply { isAccessible = true }.get(s) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>).value = true
  }

  @Test
  fun shots() {
    Assume.assumeTrue("D1_SHOTS نیست", out != null)
    ShopRole.remember(DesktopContext, ShopRole.OWNER)
    val d = DeskDemo.full()
    val pages = listOf("home" to "home", "more" to "more", "settings" to "settings", "products" to "products")
    for ((name, route) in pages) {
      if (only != null && name !in only) continue
      for ((w, h) in listOf(1366 to 768, 1920 to 1080)) for (dark in listOf(false, true)) shot(name, route, w, h, dark, d)
    }
  }
}
