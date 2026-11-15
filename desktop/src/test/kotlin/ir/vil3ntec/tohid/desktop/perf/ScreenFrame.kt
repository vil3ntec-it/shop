package ir.vil3ntec.tohid.desktop.perf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.desktop.shots.V3RoutePreview
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Surface
import java.util.concurrent.Executors

/**
 *  PERF-SCREEN — «نخستین قاب» ِ یک مسیرِ v3 با دفترِ بزرگ، بی پنجره (رَسترِ Skia ِ همان Compose ِ کامپیوتر؛ همان پوشش‌های
 *  `ScreenRender`: `LocalContext`، `LocalConfiguration`، تم، راست‌به‌چپ و زبانِ `I18n`) و همان پوسته‌ای که برنامه می‌سازد
 *  ([V3RoutePreview] ⇐ `V3RouteHost`). زمان = `setContent` (ترکیبِ نخست) + نخستین `render` (چیدمان و کشیدن)؛ سپس چند قابِ
 *  ۱۶ میلی‌ثانیه‌ای تا کارهای پس از نخستین قاب (`LaunchedEffect`، `produceState`) هم دیده شوند.
 *
 *  صحنه روی **یک رشتهٔ جدا** ([worker]) با dispatcher ِ همان رشته ساخته و کشیده می‌شود: با `Dispatchers.Unconfined`
 *  (`ScreenRender`) ادامهٔ `delay` ِ پلکانِ v3 روی `DefaultExecutor` می‌دوید و با `render` بن‌بست می‌شد.
 *  پروندهٔ «نیازمندِ اشتراک» (`VipGate`) با نشستِ ساختگی ([signedIn]) باز است، همان پروندهٔ برنامه، نه برگهٔ قفل.
 *
 *  عددِ این‌جا JVM ِ کامپیوتر است، نه گوشی؛ گوشی و ART جدا سنجیده نشده‌اند (`docs/PERFORMANCE-fa.md`).
 */
@OptIn(InternalComposeUiApi::class)
internal object ScreenFrame {

  /** [first] = ترکیب + نخستین قاب؛ [next] = [frames] قابِ پسین، هر کدام جدا (نانوثانیه) */
  class Times(val first: Long, val next: List<Long>) {
    val nextMax: Long get() = next.maxOrNull() ?: 0L
    val total: Long get() = first + next.sum()
  }

  /** رشتهٔ صحنه — برای نمونه‌برداری ([StackSampler]) */
  @Volatile lateinit var worker: Thread
    private set

  private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "screen-frame").apply { isDaemon = true; worker = this } }.asCoroutineDispatcher()

  /** [block] با نشستِ ساختگی (`TokenStore.signedIn`) تا `VipGate` همان صفحه را نشان دهد؛ نشستِ پیشین پس از آن برمی‌گردد */
  fun <T> signedIn(block: () -> T): T {
    val tokens = ir.vil3ntec.tohid.data.repo.Backend.tokens(DesktopContext)
    val before = tokens.accessToken
    tokens.accessToken = "screen-speed"
    try { return block() } finally { tokens.accessToken = before }
  }

  /** دفترِ نمایشی فقط در حافظه — هیچ نوشتنی روی پوشهٔ دادهٔ آزمون (همان راهِ `NewScreensRenderTest`) */
  fun storeOf(d: ShopData): ShopStore = ShopStore(DesktopContext).also { s ->
    @Suppress("UNCHECKED_CAST")
    (ShopStore::class.java.getDeclaredField("_data").apply { isAccessible = true }.get(s) as MutableStateFlow<ShopData>).value = d
  }

  /** یک مسیر در قابِ گوشیِ [widthDp]×[heightDp] (چگالیِ ۲) — هر بار صحنهٔ تازه، مثلِ باز کردنِ صفحه از نو */
  fun route(path: String, store: ShopStore, widthDp: Int = 360, heightDp: Int = 760, frames: Int = 4): Times =
    measure(widthDp, heightDp, frames) { V3RoutePreview(path, store, dark = false) }

  fun measure(widthDp: Int, heightDp: Int, frames: Int, density: Float = 2f, content: @Composable () -> Unit): Times =
    runBlocking(dispatcher) { measureHere(widthDp, heightDp, frames, density, content) }

  private fun measureHere(widthDp: Int, heightDp: Int, frames: Int, density: Float, content: @Composable () -> Unit): Times {
    val w = (widthDp * density).toInt()
    val h = (heightDp * density).toInt()
    val info = object : WindowInfo {
      override val isWindowFocused: Boolean get() = true
      override val containerSize: IntSize get() = IntSize(w, h)
    }
    val platform = object : PlatformContext by PlatformContext.Empty {
      override val windowInfo: WindowInfo get() = info
    }
    val before = I18n.source
    I18n.source = { Lang.FA }
    val surface = Surface.makeRasterN32Premul(w, h)
    val scene = CanvasLayersComposeScene(Density(density), LayoutDirection.Rtl, IntSize(w, h), dispatcher, platform)
    try {
      val t0 = System.nanoTime()
      scene.setContent {
        CompositionLocalProvider(
          LocalContext provides DesktopContext,
          LocalView provides remember { android.view.View(DesktopContext) },
          LocalConfiguration provides Configuration(widthDp, heightDp),
          ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout provides false,
        ) {
          TohidTheme(ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
              Box(Modifier.fillMaxSize().background(Shop.colors.bg)) { content() }
            }
          }
        }
      }
      scene.render(surface.canvas.asComposeCanvas(), 0L)
      val first = System.nanoTime() - t0
      val next = ArrayList<Long>(frames)
      var t = 0L
      repeat(frames) {
        t += 16_000_000L
        val s = System.nanoTime()
        scene.render(surface.canvas.asComposeCanvas(), t)
        next += System.nanoTime() - s
      }
      return Times(first, next)
    } finally {
      scene.close()
      surface.close()
      I18n.source = before
    }
  }
}
