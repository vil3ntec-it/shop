package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.sin

/*
 *  صحنه‌های طبیعت: درختِ پیش‌فرض و جعبهٔ کالا با گیاه. هر گروهِ شاخه به دورِ پای خودش با [wind] می‌چرخد (فاز جدا،
 *  دامنهٔ جدا) و درونِ چرخشِ نرمِ تنه است؛ برگ‌های لبه تندتر می‌لرزند و دو برگِ رها در باد می‌روند.
 */

/** یک خوشهٔ برگ: سایهٔ پشت، رنگِ اصلی و نورِ بالا-چپ */
internal fun DrawScope.blob(a: Art, x: Float, y: Float, r: Float) {
  val p = a.p
  drawCircle(p.leafDeep, r * a.u, a.o(x + 0.9f, y + 1.3f))
  drawCircle(p.leaf, r * a.u, a.o(x, y))
  drawCircle(p.leafSoft, r * 0.42f * a.u, a.o(x - r * 0.32f, y - r * 0.36f))
}

/** شاخه از (px, py) تا (tx, ty) با ضخامتِ [w] */
internal fun DrawScope.limb(a: Art, px: Float, py: Float, tx: Float, ty: Float, w: Float) {
  drawLine(a.p.trunk, a.o(px, py), a.o(tx, ty), strokeWidth = w * a.u, cap = StrokeCap.Round)
}

/** درختِ پیش‌فرض: سه گروهِ شاخه که هر کدام جدا در باد تاب می‌خورند */
internal fun DrawScope.treeScene(a: Art) {
  val p = a.p
  val t = a.t
  val lift = 1.2f
  stage(a, lift, groundW = 52f)
  translate(a.parallax(0.5f) * a.u, a.float(lift) * a.u) { treeBody(a) }
  //  دو برگِ رها: از تاجِ درخت با باد پایین و بیرون می‌روند (هر کدام ۱۲ ثانیه)، در آغاز و پایان کم‌رنگ
  for (i in 0..1) {
    val f = frac(t, 2, i * 0.5f)
    val x = 50f + 38f * f
    val y = 34f + 36f * f + 4f * wave(t, 9, i * 1.3f)
    val alpha = sin(PI.toFloat() * f)
    leaf(a, x, y, 540f * f + i * 70f, 4.4f, p.leafSoft.copy(alpha = p.leafSoft.alpha * alpha))
  }
}

/** درختِ تاب‌خور (تنه، سه گروهِ شاخه و برگ‌های لرزان) بی زمینه — صحنهٔ درخت و صحنهٔ کتاب‌خوان زیرِ درخت */
internal fun DrawScope.treeBody(a: Art) {
  val p = a.p
  val t = a.t
  turn(a, 1.4f * wind(t, 0f), 50f, 88f) {
    softShadow(a) { c -> drawPath(a.shapes.trunk, c) }
    drawPath(a.shapes.trunk, p.trunk)
    //  علفِ پای درخت
    leaf(a, 45f, 88.5f, -68f + 6f * wind(t, 2.2f), 6f, p.leafDeep)
    leaf(a, 55f, 88.5f, 66f + 6f * wind(t, 3.1f), 5.5f, p.leafDeep)
    leaf(a, 50.5f, 88.8f, 8f + 9f * wind(t, 1.4f), 5f, p.leaf)
    //  شاخهٔ چپ
    turn(a, 6.5f * wind(t, 0.0f), 49.5f, 68f) {
      limb(a, 49.5f, 68f, 34f, 55f, 2.8f)
      softShadow(a) { c -> drawCircle(c, 10f * a.u, a.o(31f, 49f)) }
      blob(a, 26f, 54f, 7f)
      blob(a, 31f, 48f, 10f)
      blob(a, 39f, 42f, 7.5f)
      leaf(a, 20f, 50f, -80f + 18f * wave(t, 13, 0.4f), 4.6f, p.leaf)
    }
    //  شاخهٔ راست
    turn(a, 6f * wind(t, 1.7f), 50.5f, 63f) {
      limb(a, 50.5f, 63f, 66f, 50f, 2.6f)
      softShadow(a) { c -> drawCircle(c, 10f * a.u, a.o(69f, 44f)) }
      blob(a, 75f, 50f, 7f)
      blob(a, 69f, 44f, 10f)
      blob(a, 61f, 38f, 7.5f)
      leaf(a, 81f, 46f, 78f + 18f * wave(t, 13, 1.9f), 4.6f, p.leaf)
    }
    //  سرِ درخت
    turn(a, 4.2f * wind(t, 3.1f), 50f, 55f) {
      limb(a, 50f, 55f, 50f, 36f, 2.4f)
      softShadow(a) { c -> drawCircle(c, 13f * a.u, a.o(50f, 29f)) }
      blob(a, 42f, 33f, 8.5f)
      blob(a, 58f, 32f, 8.5f)
      blob(a, 50f, 27f, 12.5f)
      leaf(a, 50f, 14.5f, 4f + 22f * wave(t, 13, 2.7f), 4.8f, p.leafSoft)
      leaf(a, 37f, 24f, -42f + 20f * wave(t, 13, 0.9f), 4.2f, p.leaf)
    }
  }
}

/** جعبهٔ کالا: دهانهٔ باز، درهای تاب‌خور و گیاهی که از آن روییده و در باد است */
internal fun DrawScope.productsScene(a: Art) {
  val p = a.p
  val t = a.t
  val lift = 1.4f
  stage(a, lift, groundW = 64f)
  translate(a.parallax(0.5f) * a.u, a.float(lift) * a.u) {
    softShadow(a) { c ->
      drawRect(c, a.o(22f, 58f), a.s(46f, 30f))
      drawPath(a.shapes.boxSide, c)
    }
    turn(a, 2.4f * wind(t, 2.4f), 57f, 52f) { drawPath(a.shapes.flapBack, p.kraftSide) }
    drawPath(a.shapes.boxMouth, lerp(p.kraftSide, p.ink, 0.32f))
    sprout(a, 46f, 56.5f, 30f, 0.4f, 1.15f)
    sprout(a, 57f, 56.5f, 17f, 2.3f, 0.75f)
    turn(a, -3f * wind(t, 1.1f), 28f, 55f) { drawPath(a.shapes.flapLeft, p.kraft) }
    turn(a, 3f * wind(t, 3.6f), 74f, 55f) { drawPath(a.shapes.flapRight, p.kraftSide) }
    drawRect(p.kraft, a.o(22f, 58f), a.s(46f, 30f))
    drawPath(a.shapes.boxSide, p.kraftSide)
    drawPath(a.shapes.boxSide, p.line.copy(alpha = 0.18f), style = Stroke(width = 0.8f * a.u))
    drawRect(p.line.copy(alpha = 0.18f), a.o(22f, 58f), a.s(46f, 30f), style = Stroke(width = 0.8f * a.u))
    //  نوارِ چسب و برچسب
    drawRect(p.brandSoft.copy(alpha = 0.85f), a.o(41.5f, 58f), a.s(7f, 11f))
    drawRoundRect(p.paper, a.o(27f, 73f), a.s(16f, 9f), a.r(1.2f))
    drawLine(p.muted.copy(alpha = 0.45f), a.o(29.5f, 76f), a.o(40f, 76f), strokeWidth = 1.1f * a.u, cap = StrokeCap.Round)
    drawLine(p.muted.copy(alpha = 0.3f), a.o(29.5f, 79f), a.o(36f, 79f), strokeWidth = 1.1f * a.u, cap = StrokeCap.Round)
    //  پیکانِ «این رو بالا»
    drawLine(p.line.copy(alpha = 0.35f), a.o(58f, 82f), a.o(58f, 74f), strokeWidth = 1f * a.u, cap = StrokeCap.Round)
    drawLine(p.line.copy(alpha = 0.35f), a.o(55.5f, 76.5f), a.o(58f, 74f), strokeWidth = 1f * a.u, cap = StrokeCap.Round)
    drawLine(p.line.copy(alpha = 0.35f), a.o(60.5f, 76.5f), a.o(58f, 74f), strokeWidth = 1f * a.u, cap = StrokeCap.Round)
  }
  spark(a, 80f, 30f, maxOf(0f, wave(a.t, 6, 0.8f)), p.warm)
  spark(a, 18f, 36f, maxOf(0f, wave(a.t, 6, 2.9f)), p.brand)
}
