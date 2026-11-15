package ir.vil3ntec.tohid.desktop.perf

import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.ui.AppRoot
import ir.vil3ntec.tohid.ui.ProvideLang
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.v3.ProvideV3Foreground
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Surface
import java.util.concurrent.Executors

/**
 *  PERF-DESK — پنجرهٔ برنامهٔ کامپیوتر بی پنجره: همان درختِ `desktop/Main.kt` (`ProvideV3Foreground` ⇐ `TohidTheme` ⇐
 *  `ProvideLang` ⇐ `AppRoot` با سربرگ، منوی کناری، نوارِ وضعیت و `V3RouteHost`) روی رَسترِ Skia ِ خودِ Compose ِ کامپیوتر،
 *  در اندازهٔ لپ‌تاپِ معمولی (۱۳۶۶ نقطه، بزرگ‌نماییِ ۱۰۰٪ ویندوز، پنجرهٔ بزرگ‌شده زیرِ نوارِ وظیفه).
 *
 *  - **قاب** همان قابِ پنجره است: `render` = ترکیبِ دوباره + چیدمان + کشیدن؛ فقط وقتی صحنه چیزی برای کشیدن دارد
 *    ([ComposeScene.hasInvalidations]) — همان کاری که Skiko با vsync می‌کند. زمان = ساعتِ واقعی.
 *  - **شمارِ ترکیب** با ردیابِ رسمیِ Compose ([Composer.setTracer]): هر بدنهٔ `@Composable` که واقعاً دوباره اجرا شد.
 *  - کلید و چرخِ ماوس همان رویدادهای AWT ِ پنجره‌اند (`toComposeEvent`، `PointerEventType.Scroll`).
 *  - دفتر فقط در حافظه ([ScreenFrame.storeOf] + `loaded`)، چرخهٔ عمر RESUMED (پنجرهٔ جلوی چشم — بدترین حالت برای چیزهای زنده).
 *
 *  همه‌چیز روی **یک رشتهٔ جدا** ([run]) — همان رشتهٔ dispatcher ِ صحنه، مثلِ رشتهٔ AWT ِ برنامه.
 */
@OptIn(InternalComposeUiApi::class, InternalComposeTracingApi::class, ExperimentalComposeUiApi::class)
internal class DeskShell(
  val store: ShopStore,
  val widthDp: Int = LAPTOP_W,
  val heightDp: Int = LAPTOP_H,
  val density: Float = 1f,
) : AutoCloseable {

  companion object {
    /** لپ‌تاپِ ۱۳۶۶×۷۶۸ با بزرگ‌نماییِ ۱۰۰٪: پنجرهٔ بزرگ‌شده منهای نوارِ وظیفه (۴۰) و سرِ پنجره (۲۳) */
    const val LAPTOP_W = 1366
    const val LAPTOP_H = 705

    /** دفترِ حافظه‌ای که برنامه «بارشده» می‌بیند (`AppRoot` بی `loaded` فقط اسکلت نشان می‌دهد) */
    fun loadedStore(d: ShopData): ShopStore = ScreenFrame.storeOf(d).also { s ->
      @Suppress("UNCHECKED_CAST")
      (ShopStore::class.java.getDeclaredField("_loaded").apply { isAccessible = true }.get(s) as MutableStateFlow<Boolean>).value = true
    }
  }

  /** شمارِ اجرای بدنهٔ composableها (نامِ تابع ⇐ بار) — فقط وقتی [on] */
  object Recomps : CompositionTracer {
    @Volatile var on = false
    var total = 0
      private set
    private val byName = HashMap<String, Int>()
    fun reset() { total = 0; byName.clear() }
    fun top(n: Int = 12): List<Pair<String, Int>> = byName.entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }
    override fun isTraceInProgress(): Boolean = on
    override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
      total++
      //  نام و جای تابع («ir…UpdateChip (UpdateBanner.kt:60)») — نامِ کوتاه + پرونده:خط
      val name = info.substringBefore(" (").substringAfterLast('.') + " " + info.substringAfter(" (", "").removeSuffix(")")
      byName[name] = (byName[name] ?: 0) + 1
    }
    override fun traceEventEnd() {}
  }

  /** رشتهٔ صحنه — برای نمونه‌برداری ([StackSampler]) */
  @Volatile lateinit var thread: Thread
    private set
  private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "desk-shell").apply { isDaemon = true; thread = this } }
  private val dispatcher = executor.asCoroutineDispatcher()
  private val w = (widthDp * density).toInt()
  private val h = (heightDp * density).toInt()
  private val surface = Surface.makeRasterN32Premul(w, h)
  private val lifecycle = object : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registry
  }.also { it.registry.currentState = Lifecycle.State.RESUMED }
  private val awtSource = object : java.awt.Component() {}
  private val langBefore = I18n.source
  lateinit var scene: ComposeScene
    private set
  /** قاب‌هایی که صحنه خواست (`invalidate`) */
  @Volatile var invalidations = 0
    private set

  fun <T> run(block: suspend DeskShell.() -> T): T = runBlocking(dispatcher) { block() }

  /** پنجره فوکوس دارد (RESUMED) یا کاربر در برنامهٔ دیگری است (STARTED) — همان چرخهٔ عمرِ پنجرهٔ Compose ِ کامپیوتر */
  fun focus(on: Boolean) { lifecycle.registry.currentState = if (on) Lifecycle.State.RESUMED else Lifecycle.State.STARTED }

  /** ترکیبِ نخست + نخستین قاب (نانوثانیه) */
  fun open(): Long = run {
    I18n.source = { Lang.FA }
    //  همان گرم‌کردن‌های `main` ِ برنامه پیش از نخستین ترکیب — وضعِ سراسریِ Compose (`LicenseGuard.changes`، …) روی همین
    //  رشته ساخته می‌شود، نه روی رشتهٔ پس‌زمینه‌ای وسطِ ترکیب (#1028)
    val ctx = DesktopContext
    ir.vil3ntec.tohid.ui.components.keys.KeyHelpPref.load(ctx)
    ir.vil3ntec.tohid.sync.LicenseGuard.warm()
    ir.vil3ntec.tohid.ui.screens.Motion.load(ctx)
    ir.vil3ntec.tohid.LocaleChoice.load(ctx)
    ir.vil3ntec.tohid.ShopDayChoice.load(ctx)
    ir.vil3ntec.tohid.TillSynonyms.load(ctx)
    ir.vil3ntec.tohid.data.ExpiryPref.load(ctx)
    ir.vil3ntec.tohid.ui.theme.ThemeStore.load(ctx)
    Composer.setTracer(Recomps)
    val info = object : WindowInfo {
      override val isWindowFocused: Boolean get() = true
      override val containerSize: IntSize get() = IntSize(w, h)
    }
    val platform = object : PlatformContext by PlatformContext.Empty {
      override val windowInfo: WindowInfo get() = info
    }
    scene = CanvasLayersComposeScene(Density(density), LayoutDirection.Rtl, IntSize(w, h), dispatcher, platform, invalidate = { invalidations++ })
    val t0 = System.nanoTime()
    scene.setContent {
      CompositionLocalProvider(
        LocalContext provides DesktopContext,
        LocalView provides remember { android.view.View(DesktopContext) },
        LocalConfiguration provides Configuration(widthDp, heightDp),
        LocalLifecycleOwner provides lifecycle,
      ) {
        //  همان ریشهٔ `desktop/Main.kt`
        ProvideV3Foreground {
          TohidTheme(ThemeChoice.LIGHT) {
            ProvideLang {
              AppRoot(store = store, theme = ThemeChoice.LIGHT, onTheme = {})
              ir.vil3ntec.tohid.ui.components.keys.KeyLabelsOverlay()
            }
          }
        }
      }
    }
    frame()
    System.nanoTime() - t0
  }

  /** یک قاب (ترکیب + چیدمان + کشیدن) با ساعتِ واقعی — نانوثانیه */
  fun frame(): Long {
    Snapshot.sendApplyNotifications()
    val t = System.nanoTime()
    scene.render(surface.canvas.asComposeCanvas(), t)
    return System.nanoTime() - t
  }

  fun dirty(): Boolean {
    Snapshot.sendApplyNotifications()
    return scene.hasInvalidations()
  }

  /** قاب‌های پشتِ هم تا [quietMs] بی هیچ کار یا تا [maxMs] — مثلِ حلقهٔ vsync ِ پنجره (هر ۱۶ میلی‌ثانیه نگاه) */
  class Run(val wallNs: Long, val frames: Int, val busyNs: Long, val firstNs: Long, val maxNs: Long, val settled: Boolean)

  suspend fun settle(maxMs: Long = 3_000, quietMs: Long = 200): Run {
    val t0 = System.nanoTime()
    var frames = 0; var busy = 0L; var first = 0L; var max = 0L
    var lastWork = t0
    while (true) {
      val now = System.nanoTime()
      if (now - t0 > maxMs * 1_000_000) return Run(lastWork - t0, frames, busy, first, max, settled = false)
      if (dirty()) {
        val f = frame()
        if (frames == 0) first = f
        frames++; busy += f; if (f > max) max = f
        lastWork = System.nanoTime()
      } else if (now - lastWork > quietMs * 1_000_000) return Run(lastWork - t0, frames, busy, first, max, settled = true)
      delay(16)
    }
  }

  /** پنجرهٔ زمانیِ ثابت ([ms] میلی‌ثانیهٔ واقعی) با [poke] پیش از هر نگاهِ ۱۶ms (مثلاً یک «تیک» ِ چرخ هر چند نگاه)؛ مدتِ هر قاب */
  suspend fun frames(ms: Long, poke: (Int) -> Unit = {}): List<Long> {
    val end = System.nanoTime() + ms * 1_000_000
    val out = ArrayList<Long>()
    var i = 0
    while (System.nanoTime() < end) {
      poke(i++)
      if (dirty()) out += frame()
      delay(16)
    }
    return out
  }

  /** بی‌کاری: [ms] میلی‌ثانیهٔ واقعی بی هیچ ورودی — قاب‌ها و ترکیب‌هایی که با این حال رخ دادند */
  class Idle(val ms: Long, val frames: Int, val busyNs: Long, val recompositions: Int, val top: List<Pair<String, Int>>) {
    val framesPerSec: Double get() = frames * 1000.0 / ms
    val recompPerSec: Double get() = recompositions * 1000.0 / ms
  }

  suspend fun idle(ms: Long): Idle {
    Recomps.reset(); Recomps.on = true
    val end = System.nanoTime() + ms * 1_000_000
    var frames = 0; var busy = 0L
    while (System.nanoTime() < end) {
      if (dirty()) { busy += frame(); frames++ }
      delay(16)
    }
    Recomps.on = false
    return Idle(ms, frames, busy, Recomps.total, Recomps.top())
  }

  /** رویدادِ AWT ⇒ رویدادِ Compose، همان نگاشتِ پنجره (`toComposeEvent` ِ درونیِ Compose: کد و جای کلید، نوع، نویسه، خودِ رویداد) */
  private fun compose(e: java.awt.event.KeyEvent): KeyEvent = KeyEvent(
    Key(e.keyCode, java.awt.event.KeyEvent.KEY_LOCATION_STANDARD),
    when (e.id) {
      java.awt.event.KeyEvent.KEY_PRESSED -> KeyEventType.KeyDown
      java.awt.event.KeyEvent.KEY_RELEASED -> KeyEventType.KeyUp
      else -> KeyEventType.Unknown
    },
    e.keyChar.code, false, false, false, false, e,
  )

  /** یک نویسه همان‌طور که پنجرهٔ AWT می‌فرستد: KEY_PRESSED، KEY_TYPED، KEY_RELEASED */
  fun type(ch: Char) {
    val now = System.currentTimeMillis()
    val code = java.awt.event.KeyEvent.VK_UNDEFINED
    val undefined = java.awt.event.KeyEvent.CHAR_UNDEFINED
    scene.sendKeyEvent(java.awt.event.KeyEvent(awtSource, java.awt.event.KeyEvent.KEY_PRESSED, now, 0, code, undefined).let(::compose))
    scene.sendKeyEvent(java.awt.event.KeyEvent(awtSource, java.awt.event.KeyEvent.KEY_TYPED, now, 0, code, ch).let(::compose))
    scene.sendKeyEvent(java.awt.event.KeyEvent(awtSource, java.awt.event.KeyEvent.KEY_RELEASED, now, 0, code, undefined).let(::compose))
  }

  /** یک کلیدِ بی‌نویسه (Backspace، Escape…) */
  fun key(vk: Int) {
    val now = System.currentTimeMillis()
    val undefined = java.awt.event.KeyEvent.CHAR_UNDEFINED
    scene.sendKeyEvent(java.awt.event.KeyEvent(awtSource, java.awt.event.KeyEvent.KEY_PRESSED, now, 0, vk, undefined).let(::compose))
    scene.sendKeyEvent(java.awt.event.KeyEvent(awtSource, java.awt.event.KeyEvent.KEY_RELEASED, now, 0, vk, undefined).let(::compose))
  }

  /** یک «تیک» ِ چرخِ ماوس روی نقطهٔ ([xDp], [yDp]) — [lines] مثبت = پایین */
  fun wheel(xDp: Float, yDp: Float, lines: Float) {
    val p = Offset(xDp * density, yDp * density)
    scene.sendPointerEvent(PointerEventType.Move, p)
    scene.sendPointerEvent(PointerEventType.Scroll, p, scrollDelta = Offset(0f, lines))
  }

  /** حرکتِ ماوس (بی کلیک) — برای سنجیدنِ hover */
  fun hover(xDp: Float, yDp: Float) {
    scene.sendPointerEvent(PointerEventType.Move, Offset(xDp * density, yDp * density))
  }

  override fun close() {
    runCatching { run { scene.close() } }
    surface.close()
    executor.shutdownNow()
    Composer.setTracer(null)
    I18n.source = langBefore
  }
}
