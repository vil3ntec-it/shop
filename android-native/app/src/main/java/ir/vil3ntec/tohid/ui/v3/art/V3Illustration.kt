package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.Motion

/** فقط آزمون: گونهٔ تصویر و این‌که ساعتِ حرکتش زنده است — صفحه‌خوان هیچ‌کدام را نمی‌خواند (تصویر تزئینی است) */
val V3ArtKindKey = SemanticsPropertyKey<V3ArtKind>("V3ArtKind")
val V3ArtAnimatedKey = SemanticsPropertyKey<Boolean>("V3ArtAnimated")
private var SemanticsPropertyReceiver.artKind by V3ArtKindKey
private var SemanticsPropertyReceiver.artAnimated by V3ArtAnimatedKey

/**
 *  تصویرِ متحرکِ یک زمینه ([V3ArtKind]) در **یک** بوم:
 *
 *  - باد: هر گروهِ شاخه/برگ با موجِ فنرگونهٔ خودش ([wind]) و فازِ جدا می‌چرخد؛
 *  - عمقِ سه‌بعدی: چرخشِ نرمِ `rotationX`/`rotationY` در `graphicsLayer` و پارالاکسِ هم‌پای آن درونِ رسم (هالهٔ دور
 *    خلاف، چیزِ نزدیک هم‌سو)، سایه‌های نرمِ لایه‌لایه و شناوریِ آرام با سایهٔ زمینی که کوچک و بزرگ می‌شود؛
 *  - کارایی: ساعت فقط در `graphicsLayer {}` و رسم خوانده می‌شود (بی بازترکیب در هر فریم)، مسیرهای ثابت یک بار در
 *    `drawWithCache` ساخته می‌شوند، بی bitmap، بی Lottie؛ نخستین فریم فقط چند مسیرِ کوچک است؛
 *  - ایستادن: «کاهشِ حرکت» (`Motion.enabled`)، بیرون رفتن از دید (`boundsInWindow` تهی) یا رفتنِ صفحه ⇒ هیچ ساعتی نیست
 *    و تصویر در ژستِ آرام ثابت است؛ پس‌زمینه ⇒ ساعتِ قاب‌های Compose خودش می‌ایستد؛
 *  - دسترس‌پذیری: تزئینی ([clearAndSetSemantics] بی نام)؛ معنا در عنوانِ زیرِ آن است.
 *
 *  @param depth چرخشِ سه‌بعدی؛ در جاهای کوچک (کادرِ خطای درونِ فرم) خاموش تا چشم را نکشد
 */
@Composable
fun V3Illustration(kind: V3ArtKind, modifier: Modifier = Modifier, size: Dp = 112.dp, depth: Boolean = true) {
  val palette = rememberV3ArtPalette()
  var onScreen by remember { mutableStateOf(true) }
  val animated = Motion.enabled && onScreen
  val clock = rememberV3ArtClock(animated)
  Spacer(
    modifier
      .size(size)
      .onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        val seen = b.width > 0f && b.height > 0f
        if (seen != onScreen) onScreen = seen
      }
      .clearAndSetSemantics {
        artKind = kind
        artAnimated = animated
      }
      .graphicsLayer {
        if (depth && clock != null) {
          val t = clock.value
          rotationY = 7f * wave(t, 3)
          rotationX = 4f * wave(t, 2, 1.1f)
          cameraDistance = 14f * density
        }
      }
      .drawWithCache {
        val u = this.size.minDimension / 100f
        val shapes = ArtShapes(u, palette)
        //  بومِ مستطیل ⇒ صحنهٔ مربع در میانه
        val dx = (this.size.width - u * 100f) / 2f
        val dy = (this.size.height - u * 100f) / 2f
        val art = Art(u, palette, shapes, ART_REST)
        onDrawBehind {
          art.t = clock?.value ?: ART_REST
          translate(dx, dy) { drawArt(kind, art) }
        }
      },
  )
}

/** یک صحنه در زمانِ `art.t` — برای تصویر و آزمونِ عکس */
internal fun DrawScope.drawArt(kind: V3ArtKind, art: Art) = when (kind) {
  V3ArtKind.TREE -> treeScene(art)
  V3ArtKind.PRODUCTS -> productsScene(art)
  V3ArtKind.SALES -> salesScene(art)
  V3ArtKind.PEOPLE -> peopleScene(art)
  V3ArtKind.ORDERS -> ordersScene(art)
  V3ArtKind.STOCK -> stockScene(art)
  V3ArtKind.SEARCH -> searchScene(art)
  V3ArtKind.ERROR_CLOUD -> errorCloudScene(art)
  V3ArtKind.ERROR_CABLE -> errorCableScene(art)
  V3ArtKind.ERROR_MARK -> errorMarkScene(art)
}
