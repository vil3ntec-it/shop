package ir.vil3ntec.tohid.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import ir.vil3ntec.tohid.ui.theme.TohidTheme
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Celebrate
import ir.vil3ntec.tohid.ui.v3.V3EmptyState
import ir.vil3ntec.tohid.ui.v3.V3ErrorState
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3SnackbarHost
import ir.vil3ntec.tohid.ui.v3.V3SuccessCheck
import ir.vil3ntec.tohid.ui.v3.art.V3ArtKind
import ir.vil3ntec.tohid.ui.v3.art.V3Illustration
import ir.vil3ntec.tohid.ui.v3.art.LocalArtLayer
import ir.vil3ntec.tohid.ui.v3.V3Depth
import ir.vil3ntec.tohid.ui.v3.V3ErrorText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Surface
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 *  دستهٔ ۶۳ (e-rows-63i)، صحنه‌های تختِ دستهٔ ۶۵ (e-rows-65a) — عکسِ هر تصویر و هر حالت با کشیدنِ بی پنجرهٔ Skia
 *  (همان رَسترِ `ScreenRender`)، با زمانِ صریحِ قاب:
 *   ۱) هر صحنه (پهنِ ۲:۱) چیزی می‌کشد و با حرکتِ روشن دو قابِ دور از هم فرق دارند؛ نشانِ کوچک هم؛
 *   ۲) چرخه بی درز (قابِ ۱ و ۲۵ ثانیه یکی‌اند)؛ «کاهشِ حرکت» ⇒ دو قاب نقطه‌به‌نقطه یکی؛ جشنِ موفقیت بی‌درنگ پایانی؛
 *   ۳) قاعدهٔ «فقط وقتی دیده می‌شود»: زیرِ برگه یا با برنامهٔ نه‌RESUMED هیچ قابی خواسته نمی‌شود (`hasInvalidations`)،
 *      و مرزِ مقابل — دیده و RESUMED ⇒ قاب خواسته می‌شود (سنجه کور نیست)؛
 *   ۴) `TOHID_ART_OUT=<پوشه>` ⇒ عکس‌ها (صحنه‌ها روز و شب، صفحهٔ کاملِ خالی برای هر صحنه ۳۶۰×۷۶۰، قاب‌های چرخه برای GIF).
 *  کنار رفتن فقط وقتی ران‌تایم و ui ِ Compose دو نسخهٔ فرعیِ جدا دارند (همان دروازهٔ `RenderReport`؛ کشِ آفلاینِ ناجور).
 */
@OptIn(InternalComposeUiApi::class)
class ArtShotsTest {

  private val out: File? = System.getenv("TOHID_ART_OUT")?.takeIf { it.isNotBlank() }?.let { File(it).apply { mkdirs() } }

  private fun jarMinor(cls: String): String? = runCatching {
    Class.forName(cls, false, javaClass.classLoader).protectionDomain?.codeSource?.location?.path
      ?.let { Regex("""-(\d+\.\d+)\.[^/]*\.jar$""").find(it)?.groupValues?.get(1) }
  }.getOrNull()

  @Before fun setUp() {
    val rt = jarMinor("androidx.compose.runtime.Composer")
    val ui = jarMinor("androidx.compose.ui.Modifier")
    Assume.assumeTrue("ران‌تایمِ Compose $rt با ui ِ $ui یکی نیست", rt != null && rt == ui)
    I18n.source = { Lang.FA }
  }

  @After fun tearDown() {
    Motion.reducedForTest = null
    Motion.load(DesktopContext)
    V3Celebrate.reset()
  }

  private fun motion(on: Boolean) {
    Motion.reducedForTest = !on
    Motion.load(DesktopContext)
  }

  /** [content] در قابِ [wDp]×[hDp]؛ برای هر زمان (میلی‌ثانیه) یک PNG (پس از چند قابِ گرم شدن) */
  private fun frames(wDp: Int, hDp: Int, dark: Boolean, timesMs: List<Long>, content: @Composable () -> Unit): List<ByteArray> =
    render(wDp, hDp, dark, timesMs, content).first

  /** همان، به‌علاوهٔ این‌که پس از قابِ آخر صحنه هنوز قابِ تازه می‌خواهد یا نه (`hasInvalidations`) */
  private fun render(wDp: Int, hDp: Int, dark: Boolean, timesMs: List<Long>, content: @Composable () -> Unit): Pair<List<ByteArray>, Boolean> {
    val density = 2f
    val w = (wDp * density).toInt()
    val h = (hDp * density).toInt()
    val scene = CanvasLayersComposeScene(Density(density), LayoutDirection.Rtl, IntSize(w, h), Dispatchers.Unconfined, PlatformContext.Empty)
    val surface = Surface.makeRasterN32Premul(w, h)
    try {
      scene.setContent {
        CompositionLocalProvider(LocalContext provides DesktopContext) {
          TohidTheme(if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
              Box(Modifier.fillMaxSize().background(Shop.colors.bg), contentAlignment = Alignment.Center) { content() }
            }
          }
        }
      }
      fun draw(ns: Long) { surface.canvas.clear(0); scene.render(surface.canvas.asComposeCanvas(), ns) }
      for (i in 0..3) draw(i * 16_000_000L)
      val shots = timesMs.map { ms ->
        draw(ms * 1_000_000L)
        draw(ms * 1_000_000L)
        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
      }
      return shots to scene.hasInvalidations()
    } finally {
      scene.close()
      surface.close()
    }
  }

  /** چند رنگِ جدا در شبکه‌ای از نقطه‌ها — تصویرِ خالی یک رنگ دارد */
  private fun colors(png: ByteArray): Int {
    val bmp = Bitmap.makeFromImage(Image.makeFromEncoded(png))
    val seen = HashSet<Int>()
    for (y in 0 until bmp.height step 6) for (x in 0 until bmp.width step 6) seen += bmp.getColor(x, y)
    return seen.size
  }

  /**
   *  سهمِ نقطه‌هایی که دست‌کم یک کانالشان بیش از [tol] فرق دارد. ساعتِ Compose زمانِ قاب را با `Float` می‌شمارد ⇒ قابِ
   *  ۲۵ ثانیه چند صد نانوثانیه با قابِ ۱ ثانیه فرق دارد و لبهٔ نرمِ چند نقطه یکی دو پله عوض می‌شود؛ درز یعنی پرشِ دیدنی.
   */
  private fun diff(a: ByteArray, b: ByteArray, tol: Int = 24): Double {
    val x = Bitmap.makeFromImage(Image.makeFromEncoded(a))
    val y = Bitmap.makeFromImage(Image.makeFromEncoded(b))
    var bad = 0
    for (j in 0 until x.height) for (i in 0 until x.width) {
      val p = x.getColor(i, j)
      val q = y.getColor(i, j)
      var worst = 0
      var sh = 0
      while (sh <= 24) { worst = maxOf(worst, kotlin.math.abs(((p ushr sh) and 0xFF) - ((q ushr sh) and 0xFF))); sh += 8 }
      if (worst > tol) bad++
    }
    return bad.toDouble() / (x.width * x.height)
  }

  private fun save(name: String, png: ByteArray) { out?.let { File(it, "$name.png").writeBytes(png) } }

  @Test fun everyIllustrationDrawsAndMovesWithMotion() {
    motion(on = true)
    for (kind in V3ArtKind.entries) for (dark in listOf(false, true)) {
      //  بلندیِ ۱۸۰ ⇒ صحنهٔ پهنِ ۳۶۰×۱۸۰
      val (a, b) = frames(380, 200, dark, listOf(1_000L, 2_300L)) { V3Illustration(kind, size = 180.dp) }
      assertTrue("$kind چیزی نکشید", colors(a) > 12)
      assertTrue("$kind با حرکتِ روشن ایستاده ماند", diff(a, b) > 0.005)
      save("art-${kind.name.lowercase()}-${if (dark) "dark" else "light"}", a)
    }
  }

  @Test fun everyCharacterLoopIsSeamlessAndTheSmallBadgeStillDraws() {
    motion(on = true)
    for (kind in V3ArtKind.entries) {
      //  ساعتِ ۲۴ ثانیه‌ای (چرخهٔ هر صحنه ۳/۴/۶ ثانیه): قابِ ۱ ثانیه و ۲۵ ثانیه یکی‌اند — پایانِ چرخه بی پرش به آغازش می‌چسبد
      val (a, b) = frames(380, 200, false, listOf(1_000L, 25_000L)) { V3Illustration(kind, size = 180.dp) }
      assertTrue("$kind: چرخه درز دارد (${diff(a, b)})", diff(a, b) < 0.002)
      //  نشانِ کوچک (کمتر از CHARACTER_MIN): چهره و چیزِ داستانِ همان صحنه، می‌کشد و می‌جنبد
      val (c, d) = frames(60, 60, false, listOf(1_000L, 2_300L)) { V3Illustration(kind, size = 48.dp) }
      assertTrue("$kind: نشانِ کوچک چیزی نکشید", colors(c) > 6)
      assertTrue("$kind: نشانِ کوچک ایستاده ماند", diff(c, d, tol = 8) > 0.002)
    }
  }

  /** چرخهٔ هر صحنه (میلی‌ثانیه) — قاب‌های GIF ِ نمایشی (همان `loop` ِ tools/art/scenes) */
  private fun loopOf(kind: V3ArtKind): Long = when (kind) {
    V3ArtKind.SALES, V3ArtKind.ORDERS, V3ArtKind.ERROR_CLOUD, V3ArtKind.ERROR_MARK -> 4_000L
    else -> 6_000L
  }

  @Test fun characterFrameSequencesForTheOwner() {
    val dir = out ?: return
    motion(on = true)
    for (kind in V3ArtKind.entries) for (dark in listOf(false, true)) {
      val loop = loopOf(kind)
      //  ۱۵ قاب در ثانیه، از ۱۰ ثانیه به بعد (پس از گرم شدن) تا یک چرخهٔ کامل
      val times = (0 until (loop / 1000L * 15L).toInt()).map { 10_000L + it * 1000L / 15L }
      val pngs = frames(380, 200, dark, times) { V3Illustration(kind, size = 180.dp) }
      val sub = File(dir, "frames/${kind.name.lowercase()}-${if (dark) "dark" else "light"}").apply { mkdirs() }
      pngs.forEachIndexed { i, png -> File(sub, "f%03d.png".format(i)).writeBytes(png) }
      assertTrue("$kind: قاب‌های چرخه همه یکی‌اند", pngs.distinctBy { it.contentHashCode() }.size > pngs.size / 3)
    }
  }

  /** صاحبِ چرخهٔ عمرِ ساختگی برای آزمون — RESUMED یا پس‌زمینه */
  private fun owner(state: Lifecycle.State) = object : LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = state }
  }

  @Test fun aCoveredOrBackgroundedSceneRequestsZeroFrames() {
    motion(on = true)
    val empty: @Composable () -> Unit = { V3EmptyState(Icons.Outlined.Inventory2, "هنوز کالایی ثبت نشده") }
    //  مرزِ مقابل: دیده، برنامه RESUMED ⇒ ساعت زنده است و قابِ بعدی را می‌خواهد
    val (live, wantsMore) = render(360, 760, false, listOf(1_000L, 1_400L)) {
      CompositionLocalProvider(LocalLifecycleOwner provides owner(Lifecycle.State.RESUMED)) { empty() }
    }
    assertTrue("با برنامهٔ RESUMED صحنه باید بجنبد", wantsMore)
    assertFalse(live[0].contentEquals(live[1]))
    //  برنامه در پس‌زمینه (STARTED/CREATED) ⇒ هیچ قابی خواسته نمی‌شود و تصویر می‌ایستد
    for (state in listOf(Lifecycle.State.STARTED, Lifecycle.State.CREATED)) {
      val (still, wants) = render(360, 760, false, listOf(1_000L, 1_400L)) {
        CompositionLocalProvider(LocalLifecycleOwner provides owner(state)) { empty() }
      }
      assertFalse("برنامهٔ $state هنوز قاب می‌خواهد", wants)
      assertTrue("برنامهٔ $state: تصویر جابه‌جا شد", still[0].contentEquals(still[1]))
    }
    //  زیرِ برگه (برگه‌ای روی صفحه باز است) ⇒ همان
    V3Depth.sheets.intValue = 1
    try {
      val (covered, wants) = render(360, 760, false, listOf(1_000L, 1_400L)) { empty() }
      assertFalse("صحنهٔ زیرِ برگه هنوز قاب می‌خواهد", wants)
      assertTrue(covered[0].contentEquals(covered[1]))
      //  تصویرِ درونِ همان برگه می‌جنبد
      val (inSheet, sheetWants) = render(360, 760, false, listOf(1_000L, 1_400L)) {
        CompositionLocalProvider(LocalArtLayer provides 1) { empty() }
      }
      assertTrue("تصویرِ درونِ برگه باید بجنبد", sheetWants)
      assertFalse(inSheet[0].contentEquals(inSheet[1]))
    } finally {
      V3Depth.sheets.intValue = 0
    }
  }

  @Test fun reducedMotionDrawsTheSameStillFrame() {
    motion(on = false)
    assertFalse(Motion.enabled)
    for (kind in V3ArtKind.entries) {
      val (a, b) = frames(200, 200, false, listOf(1_000L, 2_300L)) { V3Illustration(kind, size = 180.dp) }
      assertTrue("$kind چیزی نکشید", colors(a) > 12)
      assertTrue("$kind زیرِ «کاهشِ حرکت» جابه‌جا شد", a.contentEquals(b))
    }
    val (p, q) = frames(160, 160, false, listOf(60L, 900L)) { V3SuccessCheck(visible = true, size = 96.dp, haptic = false) }
    assertTrue("جشنِ موفقیت زیرِ «کاهشِ حرکت» بی‌درنگ همان حالتِ پایانی است", p.contentEquals(q))
  }

  @Test fun statesAndSuccessShots() {
    motion(on = true)
    //  جشن: قاب‌های پیاپی ⇒ هر کدام فرق دارد و پایان ایستاست
    val burst = frames(160, 160, false, listOf(90L, 230L, 380L, 560L, 1_200L, 1_600L)) { V3SuccessCheck(visible = true, size = 96.dp, haptic = false) }
    assertFalse(burst[0].contentEquals(burst[2]))
    assertTrue("پس از جشن چیزی نمی‌جنبد", burst[4].contentEquals(burst[5]))
    listOf(90, 230, 380, 560, 1_200).forEachIndexed { i, ms -> save("success-burst-${ms}ms", burst[i]) }

    for (dark in listOf(false, true)) {
      val suffix = if (dark) "dark" else "light"
      //  صفحهٔ کاملِ گوشی (۳۶۰×۷۶۰dp): بلوکِ صحنه + عنوان + خط + کار در میانه
      for ((kind, text) in SCREENS) {
        val (title, body, action) = text
        save("screen-${kind.name.lowercase()}-$suffix", frames(360, 760, dark, listOf(1_200L)) {
          if (kind.isError) V3ErrorState(title, body = body, onRetry = {}, kind = V3ErrorText.kind(kind))
          else V3EmptyState(Icons.Outlined.Inventory2, title, body = body, action = action, onAction = {}, art = kind)
        }.single())
      }
      save("state-error-offline-$suffix", frames(360, 760, dark, listOf(1_200L)) {
        V3ErrorState("java.net.UnknownHostException: api.vil3ntec.ir", onRetry = {})
      }.single())
      save("state-inline-error-$suffix", frames(360, 200, dark, listOf(1_200L)) {
        Column(Modifier.padding(16.dp)) { V3Message(V3Tone.ERROR, "موجودیِ این کالا کافی نیست", action = StrKit.retry, onAction = {}) }
      }.single())
      save("toast-celebration-$suffix", frames(360, 140, dark, listOf(300L)) {
        val host = remember { SnackbarHostState() }
        LaunchedEffect(Unit) { V3Celebrate.fire(haptic = false); host.showSnackbar(StrPos.saleRecorded("۱٬۲۵۰")) }
        Box(Modifier.fillMaxSize()) { V3SnackbarHost(host, Modifier.align(Alignment.TopCenter)) }
      }.single())
    }
  }

  /** متنِ هر صفحهٔ نمونه (فارسی، همان لحنِ برنامه) */
  private val SCREENS = listOf(
    V3ArtKind.PRODUCTS to Triple("هنوز کالایی ثبت نشده", "نخستین کالا را بیفزایید تا فروش آغاز شود.", "افزودنِ کالا"),
    V3ArtKind.SALES to Triple("امروز هنوز فروشی نیست", "نخستین فروشِ امروز همین‌جا دیده می‌شود.", "فروشِ تازه"),
    V3ArtKind.PEOPLE to Triple("دفترِ قرض خالی است", "کسی از شما قرض‌دار نیست.", "قرض‌دارِ تازه"),
    V3ArtKind.ORDERS to Triple("سفارشی نرسیده", "سفارش‌های تازهٔ فروشگاهِ اینترنتی این‌جا می‌آیند.", null),
    V3ArtKind.STOCK to Triple("در این انبار چیزی نیست", "با «ورودِ کالا» موجودی را بیفزایید.", "ورودِ کالا"),
    V3ArtKind.SEARCH to Triple("چیزی پیدا نشد", "واژهٔ دیگری بنویسید یا پالایه‌ها را بردارید.", "نمایشِ همه"),
    V3ArtKind.TREE to Triple("این‌جا هنوز خالی است", "چیزی که بیفزایید همین‌جا دیده می‌شود.", null),
    V3ArtKind.ERROR_CABLE to Triple("اینترنت وصل نیست", "اتصال را بررسی کنید و «دوباره» را بزنید.", null),
    V3ArtKind.ERROR_CLOUD to Triple("ارتباط با سرور برقرار نشد", "چند لحظهٔ دیگر «دوباره» را بزنید.", null),
    V3ArtKind.ERROR_MARK to Triple("کاری که خواستید انجام نشد", "دوباره امتحان کنید.", null),
  )
}
