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
import ir.vil3ntec.tohid.core.text.PlainError
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
 *  دستهٔ ۶۳ (e-rows-63i) — عکسِ هر تصویر و هر حالت با کشیدنِ بی پنجرهٔ Skia (همان رَسترِ `ScreenRender`)، با زمانِ صریحِ قاب:
 *   ۱) هر تصویر چیزی می‌کشد و با حرکتِ روشن دو قابِ دور از هم فرق دارند (باد، شناوری، چرخشِ سه‌بعدی)؛
 *   ۲) «کاهشِ حرکت» ⇒ همان دو قاب نقطه‌به‌نقطه یکی‌اند (هیچ ساعتی نمی‌دود)؛ جشنِ موفقیت هم بی‌درنگ همان حالتِ پایانی؛
 *   ۳) `TOHID_ART_OUT=<پوشه>` ⇒ عکس‌ها (روز و شب، حالت‌ها، قاب‌های جشن) در همان پوشه برای دیدن.
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
  private fun frames(wDp: Int, hDp: Int, dark: Boolean, timesMs: List<Long>, content: @Composable () -> Unit): List<ByteArray> {
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
      return timesMs.map { ms ->
        draw(ms * 1_000_000L)
        draw(ms * 1_000_000L)
        surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)!!.bytes
      }
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

  private fun save(name: String, png: ByteArray) { out?.let { File(it, "$name.png").writeBytes(png) } }

  @Test fun everyIllustrationDrawsAndMovesWithMotion() {
    motion(on = true)
    for (kind in V3ArtKind.entries) for (dark in listOf(false, true)) {
      val (a, b) = frames(200, 200, dark, listOf(1_000L, 2_300L)) { V3Illustration(kind, size = 180.dp) }
      assertTrue("$kind چیزی نکشید", colors(a) > 12)
      assertFalse("$kind با حرکتِ روشن ایستاده ماند", a.contentEquals(b))
      save("art-${kind.name.lowercase()}-${if (dark) "dark" else "light"}", a)
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
      save("state-empty-products-$suffix", frames(360, 420, dark, listOf(1_200L)) {
        V3EmptyState(Icons.Outlined.Inventory2, "هنوز کالایی ثبت نشده", body = "نخستین کالا را بیفزایید تا فروش آغاز شود.", action = "افزودنِ کالا", onAction = {})
      }.single())
      save("state-error-offline-$suffix", frames(360, 420, dark, listOf(1_200L)) {
        V3ErrorState("java.net.UnknownHostException: api.vil3ntec.ir", onRetry = {})
      }.single())
      save("state-error-server-$suffix", frames(360, 420, dark, listOf(1_200L)) {
        V3ErrorState("ارتباط با سرور برقرار نشد", body = "HTTP 503 Service Unavailable", onRetry = {}, kind = PlainError.Kind.SERVER)
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
}
