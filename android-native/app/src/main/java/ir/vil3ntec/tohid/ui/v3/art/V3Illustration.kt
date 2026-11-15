package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.screens.Motion
import ir.vil3ntec.tohid.ui.v3.art.platform.rememberResumed
import ir.vil3ntec.tohid.ui.v3.V3Depth
import kotlin.math.min

/** فقط آزمون: گونهٔ تصویر و این‌که ساعتِ حرکتش زنده است — صفحه‌خوان هیچ‌کدام را نمی‌خواند (تصویر تزئینی است) */
val V3ArtKindKey = SemanticsPropertyKey<V3ArtKind>("V3ArtKind")
val V3ArtAnimatedKey = SemanticsPropertyKey<Boolean>("V3ArtAnimated")
val V3ArtCharacterKey = SemanticsPropertyKey<Boolean>("V3ArtCharacter")
private var SemanticsPropertyReceiver.artKind by V3ArtKindKey
private var SemanticsPropertyReceiver.artAnimated by V3ArtAnimatedKey
private var SemanticsPropertyReceiver.artCharacter by V3ArtCharacterKey

/** لایهٔ روی‌همِ محتوا: صفحه ۰، هر برگهٔ `V3Sheet` یکی بالاتر — تصویرِ زیرِ برگهٔ باز نمی‌جنبد */
val LocalArtLayer = compositionLocalOf { 0 }

/** کمترین بلندی‌ای که صحنهٔ آدمک‌دار می‌گیرد؛ کوچک‌تر ⇒ نشانِ مربعِ همان صحنه (چهره و چیزِ داستان) */
val CHARACTER_MIN = 72.dp

/**
 *  تصویرِ یک زمینه با بلندیِ [size]: از [CHARACTER_MIN] به بالا صحنهٔ پهنِ ۲:۱ (پهنا = ۲ × بلندی)، کوچک‌تر نشانِ مربعِ
 *  همان صحنه. برای حالت‌های تمام‌صفحه [V3SceneArt] (پهنا از جای خالی).
 */
@Composable
fun V3Illustration(kind: V3ArtKind, modifier: Modifier = Modifier, size: Dp = 112.dp) {
  val character = size >= CHARACTER_MIN
  ArtCanvas(kind, if (character) modifier.size(width = size * 2, height = size) else modifier.size(size), character)
}

/** صحنهٔ پهنِ آدمک‌دار به اندازهٔ [modifier] (۲:۱ — مثلاً `widthIn(max = 360.dp).fillMaxWidth().aspectRatio(2f)`) */
@Composable
fun V3SceneArt(kind: V3ArtKind, modifier: Modifier = Modifier) = ArtCanvas(kind, modifier, character = true)

/**
 *  یک بوم برای یک صحنه (`ArtScene`): زمینِ نازک، سنگ و بوته، و آدمکی که بازی می‌کند. مسیرها یک بار ساخته می‌شوند؛
 *  در هر قاب فقط زمان (در رسم) خوانده می‌شود. ایستادن: «کاهشِ حرکت» ⇒ ژستِ ایستای گویای همان صحنه؛ بیرون از دید،
 *  برنامهٔ نه‌RESUMED یا زیرِ برگه ⇒ همان ژستِ آخر، بی هیچ قابِ خواسته. تزئینی ([clearAndSetSemantics] بی نام).
 */
@Composable
private fun ArtCanvas(kind: V3ArtKind, modifier: Modifier, character: Boolean) {
  val palette = rememberV3ArtPalette()
  val scene = remember(kind) { ArtScenes.of(kind) }
  var onScreen by remember { mutableStateOf(true) }
  val resumed = rememberResumed()
  val uncovered = V3Depth.sheets.intValue <= LocalArtLayer.current
  val moving = Motion.enabled
  val running = moving && onScreen && resumed && uncovered
  val elapsed = rememberArtElapsed(running)
  Spacer(
    modifier
      .onGloballyPositioned { c ->
        val b = c.boundsInWindow()
        val seen = b.width > 0f && b.height > 0f
        if (seen != onScreen) onScreen = seen
      }
      .clearAndSetSemantics {
        artKind = kind
        artAnimated = running
        artCharacter = character
      }
      .drawWithCache {
        //  کادرِ دیدِ صحنه: پهن ⇒ ۳۴۰×۱۷۰ ِ میانی؛ نشان ⇒ مربعِ `badge` ِ همان صحنه — در میانهٔ بوم، بی کشیدگی
        val vx = if (character) ART_VIEW_X else scene.badge[0]
        val vy = if (character) ART_VIEW_Y else scene.badge[1]
        val vw = if (character) ART_VIEW_W else scene.badge[2]
        val vh = if (character) ART_VIEW_H else scene.badge[2]
        val s = min(size.width / vw, size.height / vh)
        val dx = (size.width - vw * s) / 2f
        val dy = (size.height - vh * s) / 2f
        onDrawBehind {
          val t = scene.timeAt(if (moving) elapsed.floatValue else null)
          withTransform({
            translate(dx, dy)
            scale(s, s, Offset.Zero)
            translate(-vx, -vy)
            clipRect(vx, vy, vx + vw, vy + vh)
          }) { scene.draw(this, t, palette) }
        }
      },
  )
}

/** صحنهٔ هر زمینه — یک بار خوانده و ساخته می‌شود (دادهٔ `ArtData*.kt`) */
internal object ArtScenes {
  private val tree by lazy { ArtScene.parse(ART_TREE) }
  private val products by lazy { ArtScene.parse(ART_PRODUCTS) }
  private val sales by lazy { ArtScene.parse(ART_SALES) }
  private val people by lazy { ArtScene.parse(ART_PEOPLE) }
  private val orders by lazy { ArtScene.parse(ART_ORDERS) }
  private val stock by lazy { ArtScene.parse(ART_STOCK) }
  private val search by lazy { ArtScene.parse(ART_SEARCH) }
  private val cloud by lazy { ArtScene.parse(ART_ERROR_CLOUD) }
  private val cable by lazy { ArtScene.parse(ART_ERROR_CABLE) }
  private val mark by lazy { ArtScene.parse(ART_ERROR_MARK) }

  fun of(kind: V3ArtKind): ArtScene = when (kind) {
    V3ArtKind.TREE -> tree
    V3ArtKind.PRODUCTS -> products
    V3ArtKind.SALES -> sales
    V3ArtKind.PEOPLE -> people
    V3ArtKind.ORDERS -> orders
    V3ArtKind.STOCK -> stock
    V3ArtKind.SEARCH -> search
    V3ArtKind.ERROR_CLOUD -> cloud
    V3ArtKind.ERROR_CABLE -> cable
    V3ArtKind.ERROR_MARK -> mark
  }
}
