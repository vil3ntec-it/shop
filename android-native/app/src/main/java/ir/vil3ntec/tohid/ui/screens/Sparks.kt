package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 *  نورهای زنده‌ی کناره‌ها — تا وقتی انگشت روی آن است.
 *
 *  ── این چه چیزی است ───────────────────────────────────────────────
 *  انگشت که روی کارت می‌نشیند، از دورِ تا دورِ لبه‌ها نور بیرون
 *  می‌زند: بیست‌وشش نقطه که پشتِ سرِ هم می‌زایند، بیرون می‌روند و محو
 *  می‌شوند و جایشان تازه‌ها می‌آیند. تا انگشت هست، جریان هست. انگشت
 *  که برداشته شد، نیم‌ثانیه‌ای آرام خاموش می‌شود.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── دو چیزی که جایش را گرفت ───────────────────────────────────────
 *   • **خطِ نور**: نواری که بی‌وقفه روی کارت‌ها می‌لغزید. چیزی
 *     نمی‌گفت، تمام نمی‌شد، و باتری را برای تزئین می‌سوزاند.
 *   • **جرقه‌ی یک‌بارمصرف**: نسخه‌ی اولِ همین فایل با هر کلیک دوازده
 *     نقطه می‌پاشید و تمام. کم بود و بی‌جان: می‌آمد و همان لحظه گم
 *     می‌شد، و کاربر چیزی را که «روی آن است» نمی‌دید.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── دو نکته‌ی فنی ─────────────────────────────────────────────────
 *   • **پیش از `clip` بیاید.** نقطه‌ها بیرونِ کادر کشیده می‌شوند و بعد
 *     از `clip` همان‌جا بریده می‌شوند.
 *   • **در سکون هیچ چیز نمی‌چرخد.** حلقه‌ی فریم فقط وقتی راه می‌افتد
 *     که چیزی روشن باشد؛ نه ساعتی، نه محاسبه‌ای، نه کشیدنی.
 */

/** چند نقطه هم‌زمان در جریان‌اند */
private const val COUNT = 26

/** عمرِ هر نقطه، به ثانیه — از زادن تا محو شدن */
private const val CYCLE = 1.05f

/** روشن شدنِ جریان تند است و خاموش شدنش آرام */
private const val FADE_IN_MS = 120
private const val FADE_OUT_MS = 520

/**
 *  @param active تا وقتی درست است، نور می‌آید — همان حالتِ «انگشت روی
 *    آن است» که از `MutableInteractionSource.collectIsPressedAsState()`
 *    می‌آید.
 *  @param tint رنگِ نورها؛ مغزِ هر نقطه سفیدِ داغ است و هاله‌اش همین رنگ.
 */
@Composable
fun Modifier.edgeSparks(active: Boolean, tint: Color): Modifier {
  //  شدتِ جریان: ۰ خاموش، ۱ کاملاً روشن
  val life = remember { Animatable(0f) }
  //  ساعتِ خودمان، به ثانیه — فقط وقتی چیزی روشن است جلو می‌رود
  val clock = remember { mutableFloatStateOf(0f) }

  LaunchedEffect(active) {
    if (active) life.animateTo(1f, tween(if (Motion.enabled) FADE_IN_MS else 0))
    else life.animateTo(0f, tween(if (Motion.enabled) FADE_OUT_MS else 0))
  }

  /*
   *  حلقه‌ی فریم تا وقتی می‌چرخد که یا انگشت روی کارت باشد یا جریان
   *  در حالِ خاموش شدن. `withFrameNanos` ساعتِ خودِ کامپوز است، پس با
   *  نرخِ صفحه‌ی همان گوشی جلو می‌رود — روی ۱۶۵ هرتز هم نرم است.
   */
  //  `isRunning` خوانده می‌شود نه `value`: با دومی، صفحه در تمامِ
  //  نیم‌ثانیهٔ محو شدن فریم‌به‌فریم از نو ساخته می‌شد
  val running = active || life.isRunning
  LaunchedEffect(running) {
    if (!running || !Motion.enabled) return@LaunchedEffect
    var start = 0L
    while (true) {
      withFrameNanos { nanos ->
        if (start == 0L) start = nanos
        clock.floatValue = (nanos - start) / 1_000_000_000f
      }
    }
  }

  return drawWithContent {
    drawContent()
    val glow = life.value
    if (glow <= 0.001f || !Motion.enabled) return@drawWithContent

    val t = clock.floatValue
    if (size.width <= 0f || size.height <= 0f) return@drawWithContent

    for (i in 0 until COUNT) {
      //  جای تولد روی محیط، و اندازه و سرعت — همه از خودِ شماره حساب
      //  می‌شوند (نسبتِ طلایی، تا کنارِ هم جمع نشوند) و هر بار همان‌اند
      val seat = frac(i * 0.6180339f)
      val vary = frac(i * 0.3819660f + 0.17f)

      val p = frac(t / (CYCLE * (0.72f + vary * 0.55f)) + seat + vary)
      //  کندشدنِ آخرِ راه: نور از لبه تند بیرون می‌زند و بعد آرام می‌گیرد
      val eased = 1f - (1f - p) * (1f - p)

      val reach = (13f + vary * 13f).dp.toPx() * eased
      val (at, dir) = pointOn(seat, size.width, size.height)
      val center = Offset(at.x + dir.x * reach, at.y + dir.y * reach)

      //  روشن و محو، نرم — نه ظاهر شدنِ ناگهانی
      val alpha = glow * sin(PI.toFloat() * p)
      if (alpha <= 0.02f) continue
      val radius = (1.7f + vary * 2.1f).dp.toPx() * (1f - 0.4f * p)

      drawCircle(color = tint.copy(alpha = alpha * 0.16f), radius = radius * 3.2f, center = center)
      drawCircle(color = tint.copy(alpha = alpha * 0.85f), radius = radius, center = center)
      drawCircle(color = Color.White.copy(alpha = alpha * 0.75f), radius = radius * 0.42f, center = center)
    }
  }
}

/** بخشِ کسریِ یک عدد — همیشه در بازه‌ی [۰، ۱) */
private fun frac(v: Float): Float = v - kotlin.math.floor(v)

/**
 *  نقطه‌ای روی محیطِ کادر، و جهتِ بیرون‌رفتن از همان‌جا.
 *
 *  @param t جای نقطه روی محیط، از ۰ تا ۱ — از گوشه‌ی بالا شروع و
 *    ساعت‌گرد. با پراکنده بودنِ نقطه‌ها، نور از **همه‌ی** کناره‌ها و
 *    کنج‌ها می‌آید، نه از چند جای معلوم.
 */
private fun pointOn(t: Float, w: Float, h: Float): Pair<Offset, Offset> {
  var d = frac(t) * 2f * (w + h)
  if (d < w) return Offset(d, 0f) to Offset(0f, -1f)
  d -= w
  if (d < h) return Offset(w, d) to Offset(1f, 0f)
  d -= h
  if (d < w) return Offset(w - d, h) to Offset(0f, 1f)
  d -= w
  return Offset(0f, (h - d).coerceIn(0f, h)) to Offset(-1f, 0f)
}
