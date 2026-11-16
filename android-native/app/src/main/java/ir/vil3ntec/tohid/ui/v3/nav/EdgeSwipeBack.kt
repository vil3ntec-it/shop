package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 *  **کشیدنِ راست به چپ ⇒ برگشت** (صاحب‌کار ۱۴۰۵/۰۸/۲۵: «وقتی صفحه رو از راست به چپ بکشم از اون بخش بیاد بیرون»؛ ۱۴۰۵/۰۸/۲۶:
 *  «از وسط یا آخرِ صفحه هم کشیده بشه») — فقط گوشی (`V3RouteHost`، یک جا برای همهٔ صفحه‌ها). دو راه، هر دو به چپ:
 *   • **لبه** ([EDGE] ِ کنارِ لبهٔ فیزیکیِ راست): پیش از فرزندان (گذرِ Initial) ربوده می‌شود — همیشه برگشت.
 *   • **هر جای دیگرِ صفحه**: پس از فرزندان (گذرِ Main) و فقط اگر هیچ فرزندی کشیدن را نخورده باشد — ردیفِ تراشه و کاروسلی
 *     که هنوز جا دارد، برگه‌های افقی (مثلِ «کالا/انبار» ِ همان کالا)، کشیدنِ ردیف (حذف)، لغزنده و فیلدِ متن کارِ خودشان را
 *     می‌کنند؛ و چون کشیدنِ کج نباید برگشت شود، افقی بودن باید روشن‌تر باشد ([MID_DOMINANCE]).
 *  کشیدنِ عمودی رها می‌شود. رها کردن پس از [COMMIT] ِ پهنا یا پرتابِ تند ⇒ همان برگشتِ پیکانِ سربرگ (`V3Nav.back`)؛ کمتر ⇒
 *  سرِ جا. [onProgress] صفحه را پا‌به‌پای انگشت کمی می‌لغزاند؛ «کاهشِ حرکت» ⇒ صدا زننده آن را نمی‌کشد.
 */
object EdgeSwipe {
  /** پهنای نوارِ لبه که کشیدن از آن همیشه برگشت است (پیش از فرزندان) */
  val EDGE = 24.dp
  /** بخشی از پهنا که پس از آن رها کردن یعنی برگشت */
  const val COMMIT = 0.3f
  /** پرتابِ تند به چپ (px/s) هم برگشت است، حتی کوتاه */
  const val FLING = 1_000f
  /** کشیدن از میانهٔ صفحه: جابه‌جاییِ افقی دست‌کم این‌چند برابرِ عمودی، تا پیمایشِ کج برگشت نشود */
  const val MID_DOMINANCE = 2f

  /** خالص: رها کردن پس از کشیدنِ [fraction] ِ پهنا با سرعتِ [velocityX] (منفی = به چپ) برگشت است؟ */
  fun commits(fraction: Float, velocityX: Float): Boolean = fraction >= COMMIT || velocityX <= -FLING

  /** خالص: جابه‌جاییِ [dx]،[dy] (منفی = به چپ/بالا) کشیدنِ برگشت را آغاز می‌کند؟ */
  fun starts(dx: Float, dy: Float, slop: Float, fromEdge: Boolean): Boolean =
    -dx > slop && -dx > (if (fromEdge) 1f else MID_DOMINANCE) * abs(dy)

  /** خالص: جابه‌جاییِ [dx]،[dy] یعنی پیمایشِ عمودی (کشیدن رها شود)؟ */
  fun vertical(dx: Float, dy: Float, slop: Float): Boolean = abs(dy) > slop && abs(dy) > abs(dx)
}

fun Modifier.edgeSwipeBack(enabled: Boolean, scope: CoroutineScope, onProgress: (Float) -> Unit, onBack: () -> Unit): Modifier =
  if (!enabled) this else pointerInput(Unit) {
    val edge = EdgeSwipe.EDGE.toPx()
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
      //  گذرِ نخست (Initial): پیش از فرزندان می‌بینیم، ولی تا کشیدن قطعی نشده چیزی نمی‌خوریم
      val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      val fromEdge = down.position.x >= size.width - edge
      //  لبه پیش از فرزندان؛ میانه پس از آن‌ها (کشیدنی که فرزند خورد مالِ همان فرزند است)
      val pass = if (fromEdge) PointerEventPass.Initial else PointerEventPass.Main
      val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
      var dx = 0f
      var dy = 0f
      var dragging = false
      while (true) {
        val change = awaitPointerEvent(pass).changes.firstOrNull { it.id == down.id } ?: break
        tracker.addPosition(change.uptimeMillis, change.position)
        if (!change.pressed) break
        val d = change.positionChangeIgnoreConsumed()
        //  فرزندی **جابه‌جایی** را خورد (پیمایش، برگه‌های افقی، کشیدنِ ردیف) ⇒ کشیدن مالِ اوست. «فرودِ» خورده‌شده نه: هر ردیفِ
        //  زدنی (`clickable`/`detectTapGestures`) فرود را می‌خورد و آن نباید جلوی برگشت را بگیرد
        if (!dragging && !fromEdge && change.isConsumed && d != Offset.Zero) return@awaitEachGesture
        dx += d.x
        dy += d.y
        if (!dragging) {
          if (EdgeSwipe.vertical(dx, dy, slop)) return@awaitEachGesture
          if (EdgeSwipe.starts(dx, dy, slop, fromEdge)) dragging = true
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
