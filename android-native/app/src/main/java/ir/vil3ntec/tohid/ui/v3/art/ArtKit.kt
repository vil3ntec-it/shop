package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform

/*
 *  ابزارِ مشترکِ صحنه‌ها: هر صحنه در دستگاهِ مختصاتِ ۰ تا ۱۰۰ کشیده می‌شود (`u` = یک صدمِ ضلعِ کوچک‌ترِ بوم). مسیرهای
 *  ثابت یک بار در `drawWithCache` ساخته می‌شوند ([ArtShapes])؛ هر فریم فقط چرخش و جابه‌جایی است، بی ساختنِ چیزِ تازه.
 */

/** بستِ رسم: اندازه، رنگ‌ها و مسیرهای ثابت (یک بار در `drawWithCache`) و زمانِ همین فریم */
internal class Art(val u: Float, val p: V3ArtPalette, val shapes: ArtShapes, var t: Float) {
  fun o(x: Float, y: Float) = Offset(x * u, y * u)
  fun s(w: Float, h: Float) = Size(w * u, h * u)
  fun r(v: Float) = CornerRadius(v * u, v * u)
}

/** مسیرهای ثابتِ یک اندازه؛ هر کدام فقط اگر صحنه‌اش کشیده شود ساخته می‌شود */
internal class ArtShapes(private val u: Float, private val p: V3ArtPalette) {
  private fun path(block: Path.() -> Unit): Lazy<Path> = lazy(LazyThreadSafetyMode.NONE) { Path().apply(block) }
  private fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
  private fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
  private fun Path.c(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = cubicTo(x1 * u, y1 * u, x2 * u, y2 * u, x * u, y * u)
  private fun Path.q(x1: Float, y1: Float, x: Float, y: Float) = quadraticTo(x1 * u, y1 * u, x * u, y * u)

  /** هالهٔ پشت، دورِ (۵۰، ۵۰) — جابه‌جایی‌اش با `translate`، بی ساختنِ قلمِ تازه در هر فریم */
  val halo: Brush by lazy(LazyThreadSafetyMode.NONE) {
    Brush.radialGradient(listOf(p.halo, p.halo, p.halo.copy(alpha = 0f)), center = Offset(50f * u, 50f * u), radius = 47f * u)
  }

  /** برگِ یکه: نوک به بالا، درازای ۱ (بی `u`؛ با `scale` کشیده می‌شود) */
  val leaf: Path by lazy(LazyThreadSafetyMode.NONE) {
    Path().apply {
      moveTo(0f, 0f)
      cubicTo(-0.52f, -0.28f, -0.42f, -0.82f, 0f, -1f)
      cubicTo(0.42f, -0.82f, 0.52f, -0.28f, 0f, 0f)
      close()
    }
  }

  /** تنهٔ درخت: پهن در پا، باریک در سر */
  val trunk: Path by path { m(45.5f, 88f); q(48f, 70f, 48.4f, 52f); l(51.6f, 52f); q(52f, 70f, 54.5f, 88f); close() }

  /** رسید با لبهٔ دندانه‌دارِ پایین */
  val receipt: Path by path {
    m(31f, 16f); l(69f, 16f); l(69f, 78f)
    val teeth = 7
    val w = 38f / teeth
    for (i in 0 until teeth) {
      val x = 69f - i * w
      l(x - w / 2f, 82f); l(x - w, 78f)
    }
    close()
  }

  /** بدنهٔ سبد */
  val basket: Path by path { m(21f, 58f); l(79f, 58f); l(71f, 87f); l(29f, 87f); close() }

  /** ابر */
  val cloud: Path by path {
    m(30f, 60f)
    c(21f, 60f, 20f, 46f, 31f, 45f)
    c(31f, 34f, 44f, 30f, 50f, 37f)
    c(55f, 27f, 72f, 30f, 70f, 43f)
    c(81f, 43f, 82f, 60f, 71f, 60f)
    close()
  }

  /** آذرخشِ کوچک */
  val bolt: Path by path { m(54f, 61f); l(46f, 73f); l(51f, 73f); l(47f, 84f); l(58f, 69f); l(52.5f, 69f); l(56f, 61f); close() }

  /** دمِ بادکنکِ «!» */
  val tail: Path by path { m(38f, 62f); l(33f, 74f); l(46f, 66f); close() }

  /** گلدان */
  val pot: Path by path { m(61f, 38f); l(73f, 38f); l(71.5f, 46f); l(62.5f, 46f); close() }

  /** پهلو، دهانه و درِ عقبِ جعبهٔ کالا */
  val boxSide: Path by path { m(68f, 58f); l(80f, 52f); l(80f, 82f); l(68f, 88f); close() }
  val boxMouth: Path by path { m(22f, 58f); l(34f, 52f); l(80f, 52f); l(68f, 58f); close() }
  val flapBack: Path by path { m(34f, 52f); l(80f, 52f); l(77f, 41f); l(37f, 41f); close() }
  val flapLeft: Path by path { m(22f, 58f); l(34f, 52f); l(25f, 43f); l(11f, 50f); close() }
  val flapRight: Path by path { m(68f, 58f); l(80f, 52f); l(91f, 57f); l(79f, 64f); close() }

  /** بدنهٔ دو نفر: سرِ گرد، پای صاف */
  val bodyBack: Path by path { addRoundRect(round(25f, 58f, 51f, 88f, 13f, 2f)) }
  val bodyFront: Path by path { addRoundRect(round(46f, 58f, 76f, 90f, 15f, 2f)) }

  private fun round(l: Float, t: Float, r: Float, b: Float, top: Float, bottom: Float) = RoundRect(
    l * u, t * u, r * u, b * u,
    topLeftCornerRadius = CornerRadius(top * u), topRightCornerRadius = CornerRadius(top * u),
    bottomRightCornerRadius = CornerRadius(bottom * u), bottomLeftCornerRadius = CornerRadius(bottom * u),
  )

  /** بازیگرانِ صحنه‌های آدمک‌دار و ژستِ هر فریم (یک شیء، دوباره پر می‌شود) */
  val cast: Cast by lazy(LazyThreadSafetyMode.NONE) { Cast(p) }
  val pose = Pose()

  /** مسیرهای خمیدهٔ هر فریم (ساقه، سیم): همان چند شیء از نو پر می‌شوند، بی ساختنِ تازه */
  val stem = Path()
  val cableA = Path()
  val cableB = Path()
}

/* ------------------------------- صحنه ------------------------------- */

/** شناوری: چیز تا [lift] واحد از زمین بالا می‌رود (۶ ثانیه)، سایه هم‌پایش کوچک و کم‌رنگ می‌شود */
internal fun Art.float(lift: Float): Float = -lift * (0.5f + 0.5f * wave(t, 4, 0.4f))

/** پارالاکس: لایهٔ دور خلافِ چرخشِ سه‌بعدی، لایهٔ نزدیک هم‌سویش ([V3Illustration] همان موج را می‌چرخاند) */
internal fun Art.parallax(depth: Float): Float = depth * 1.6f * wave(t, 3)

/**
 *  زمینهٔ هر صحنه: هالهٔ نرمِ پشت (دور، خلافِ پارالاکس) و سایهٔ بیضیِ زمین که با بالا رفتنِ چیز کوچک می‌شود.
 *  @param lift همان عددِ [float] ِ صحنه
 */
internal fun DrawScope.stage(a: Art, lift: Float, groundY: Float = 89f, groundW: Float = 58f) {
  val p = a.p
  val back = a.parallax(-0.6f) * a.u
  translate(back, 0f) {
    drawCircle(a.shapes.halo, radius = 47f * a.u, center = a.o(50f, 50f))
    drawCircle(p.haloRim, radius = 41f * a.u, center = a.o(50f, 50f), style = Stroke(width = 0.8f * a.u))
  }
  val up = if (lift > 0f) -a.float(lift) / lift else 0f
  val w = groundW * (1f - 0.12f * up)
  drawOval(p.shadow.copy(alpha = p.shadow.alpha * (1f - 0.35f * up)), topLeft = a.o(50f - w / 2f, groundY - 3.2f), size = a.s(w, 6.4f))
}

/** سایهٔ نرمِ لایه‌لایه زیرِ یک شکل: دو بار با جابه‌جایی و شفافیتِ کم‌شونده (به‌جای blur) */
internal inline fun DrawScope.softShadow(a: Art, block: DrawScope.(Color) -> Unit) {
  val c = a.p.shadow
  translate(1.6f * a.u, 2.4f * a.u) { block(c.copy(alpha = c.alpha * 0.45f)) }
  translate(0.8f * a.u, 1.2f * a.u) { block(c.copy(alpha = c.alpha * 0.55f)) }
}

/** برگ با پا در (x, y)، زاویه (درجه، صفر = رو به بالا) و درازای [len] */
internal fun DrawScope.leaf(a: Art, x: Float, y: Float, deg: Float, len: Float, color: Color, vein: Color? = null) {
  withTransform({
    translate(x * a.u, y * a.u)
    rotate(deg, Offset.Zero)
    scale(len * a.u, len * a.u, Offset.Zero)
  }) {
    drawPath(a.shapes.leaf, color)
    if (vein != null) drawLine(vein, Offset(0f, -0.08f), Offset(0f, -0.82f), strokeWidth = 0.06f, cap = StrokeCap.Round)
  }
}

/** ستارهٔ چهارپرِ جرقه با اندازهٔ [k] (۰ ⇒ ناپیدا) */
internal fun DrawScope.spark(a: Art, x: Float, y: Float, k: Float, color: Color) {
  if (k <= 0.02f) return
  val r = 3.2f * k
  val w = 0.9f * a.u
  drawLine(color, a.o(x - r, y), a.o(x + r, y), strokeWidth = w, cap = StrokeCap.Round)
  drawLine(color, a.o(x, y - r), a.o(x, y + r), strokeWidth = w, cap = StrokeCap.Round)
}

/** چرخشِ یک گروه به دورِ (x, y) */
internal inline fun DrawScope.turn(a: Art, deg: Float, x: Float, y: Float, block: DrawScope.() -> Unit) {
  withTransform({ rotate(deg, a.o(x, y)) }, block)
}

/** گیاهِ کوچکِ تاب‌خور: ساقه از (x, y) تا بلندیِ [h]، دو برگِ کنار و یک برگِ سر — جعبهٔ کالا و گلدانِ قفسه */
internal fun DrawScope.sprout(a: Art, x: Float, y: Float, h: Float, phase: Float, scale: Float = 1f) {
  val p = a.p
  val sway = wind(a.t, phase)
  val tipX = x + 5.5f * scale * sway
  val tipY = y - h
  val midX = x + 1.2f * scale * sway
  val stem = a.shapes.stem
  stem.reset()
  stem.moveTo(x * a.u, y * a.u)
  stem.quadraticTo(midX * a.u, (y - h * 0.55f) * a.u, tipX * a.u, tipY * a.u)
  drawPath(stem, p.leafDeep, style = Stroke(width = 1.5f * scale * a.u, cap = StrokeCap.Round))
  //  برگ‌ها در جایِ همان منحنی (s = ۰٫۴۵ و ۰٫۷)، هر کدام با لرزشِ خودش
  fun at(s: Float): Offset {
    val q = 1f - s
    return Offset(q * q * x + 2f * q * s * midX + s * s * tipX, q * q * y + 2f * q * s * (y - h * 0.55f) + s * s * tipY)
  }
  val l1 = at(0.42f)
  val l2 = at(0.68f)
  val flutter = 10f * wave(a.t, 11, phase * 2.3f)
  leaf(a, l1.x, l1.y, -62f + 14f * sway + flutter, 11f * scale, p.leaf, p.leafDeep)
  leaf(a, l2.x, l2.y, 58f + 16f * sway - flutter, 10f * scale, p.leafSoft, p.leafDeep)
  leaf(a, tipX, tipY + 0.5f, 6f + 20f * sway, 9f * scale, p.leaf, p.leafDeep)
}
