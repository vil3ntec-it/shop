package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 *  **کشیدن از لبهٔ راست ⇒ برگشت** (صاحب‌کار ۱۴۰۵/۰۸/۲۵: «وقتی صفحه رو از راست به چپ بکشم از اون بخش بیاد بیرون») — فقط گوشی
 *  (`V3RouteHost`، یک جا برای همهٔ صفحه‌ها). لبهٔ فیزیکیِ راست در هر زبان (گفتهٔ صاحب‌کار)؛ فقط کشیدنی که در [EDGE] ِ کنارِ لبه
 *  آغاز شود و افقی به چپ برود ربوده می‌شود — ردیفِ تراشه، کاروسل و کشیدنِ ردیف (حذف) که از میانهٔ صفحه آغاز می‌شوند دست
 *  نمی‌خورند، و کشیدنِ عمودی از لبه هم رها می‌شود. رها کردن پس از [COMMIT] ِ پهنا یا پرتابِ تند ⇒ همان برگشتِ پیکانِ سربرگ
 *  (`V3Nav.back`: صفحهٔ درونی ⇒ پدر؛ ریشهٔ تبی جز خانه ⇒ خانه، مثلِ دکمهٔ برگشتِ گوشی)؛ کمتر ⇒ سرِ جا.
 *  [onProgress] صفحه را پا‌به‌پای انگشت کمی می‌لغزاند؛ «کاهشِ حرکت» ⇒ صدا زننده آن را نمی‌کشد.
 */
object EdgeSwipe {
  /** پهنای نوارِ لبه که کشیدن باید در آن آغاز شود */
  val EDGE = 24.dp
  /** بخشی از پهنا که پس از آن رها کردن یعنی برگشت */
  const val COMMIT = 0.3f
  /** پرتابِ تند به چپ (px/s) هم برگشت است، حتی کوتاه */
  const val FLING = 1_000f

  /** خالص: رها کردن پس از کشیدنِ [fraction] ِ پهنا با سرعتِ [velocityX] (منفی = به چپ) برگشت است؟ */
  fun commits(fraction: Float, velocityX: Float): Boolean = fraction >= COMMIT || velocityX <= -FLING
}

fun Modifier.edgeSwipeBack(enabled: Boolean, scope: CoroutineScope, onProgress: (Float) -> Unit, onBack: () -> Unit): Modifier =
  if (!enabled) this else pointerInput(Unit) {
    val edge = EdgeSwipe.EDGE.toPx()
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
      //  گذرِ نخست (Initial): پیش از فرزندان می‌بینیم، ولی تا کشیدنِ لبه قطعی نشده چیزی نمی‌خوریم
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      if (down.position.x < size.width - edge) return@awaitEachGesture
      val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
      var dx = 0f
      var dy = 0f
      var dragging = false
      while (true) {
        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
        tracker.addPosition(change.uptimeMillis, change.position)
        if (!change.pressed) break
        val d = change.positionChange()
        dx += d.x
        dy += d.y
        if (!dragging) {
          if (abs(dy) > slop && abs(dy) > abs(dx)) return@awaitEachGesture
          if (-dx > slop && -dx > abs(dy)) dragging = true
        }
        if (dragging) {
          change.consume()
          onProgress((-dx / size.width).coerceIn(0f, 1f))
        }
      }
      if (!dragging) return@awaitEachGesture
      val fraction = (-dx / size.width).coerceIn(0f, 1f)
      if (EdgeSwipe.commits(fraction, tracker.calculateVelocity().x)) {
        onProgress(0f)
        onBack()
      } else {
        scope.launch { animate(fraction, 0f, animationSpec = tween(160)) { v, _ -> onProgress(v) } }
      }
    }
  }
