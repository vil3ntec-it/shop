package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import kotlin.math.max

/*
 *  صحنه‌های نشانه: ذره‌بینِ جست‌وجو و سه تصویرِ خطا. خطا هیچ‌وقت ترسناک نیست: ابرِ خواب‌آلود با آذرخشِ کوچکی که گاهی
 *  می‌درخشد، دوشاخه‌ای که از پریز جدا مانده و جرقهٔ کوچکِ میانشان، و بادکنکِ «!» که آرام تاب می‌خورد.
 */

/** درخششِ گاه‌به‌گاه: نزدیکِ صفر، هر ۶ ثانیه یک برقِ کوتاه (و پژواکِ کم‌رنگ‌ترش) */
private fun flash(t: Float): Float {
  val w1 = max(0f, wave(t, 4, 0.2f))
  val w2 = max(0f, wave(t, 4, 0.75f))
  val a = w1 * w1 * w1 * w1 * w1 * w1 * w1 * w1
  val b = w2 * w2 * w2 * w2 * w2 * w2 * w2 * w2 * w2 * w2
  return max(a, 0.6f * b)
}

/** ذره‌بینی که با مسیرِ هشت‌مانند می‌گردد؛ سه نقطهٔ «در حالِ گشتن» درونِ عدسی */
internal fun DrawScope.searchScene(a: Art) {
  val p = a.p
  val t = a.t
  val lift = 3f
  stage(a, lift, groundW = 44f)
  val cx = 46f + 6f * wave(t, 2)
  val cy = 43f + 3f * wave(t, 4, 0.7f) + a.float(lift)
  translate(a.parallax(0.6f) * a.u, 0f) {
    turn(a, -4f + 7f * wave(t, 3, 0.3f), cx, cy) {
      softShadow(a) { c ->
        drawCircle(c, 19f * a.u, a.o(cx, cy))
        drawLine(c, a.o(cx + 12.5f, cy + 12.5f), a.o(cx + 27f, cy + 27f), strokeWidth = 6.5f * a.u, cap = StrokeCap.Round)
      }
      drawLine(p.line, a.o(cx + 12.5f, cy + 12.5f), a.o(cx + 27f, cy + 27f), strokeWidth = 6.5f * a.u, cap = StrokeCap.Round)
      drawLine(lerp(p.line, p.paper, 0.4f), a.o(cx + 19.5f, cy + 19.5f), a.o(cx + 25.5f, cy + 25.5f), strokeWidth = 2.4f * a.u, cap = StrokeCap.Round)
      drawCircle(p.paper.copy(alpha = 0.94f), 17f * a.u, a.o(cx, cy))
      drawCircle(p.brand.copy(alpha = 0.10f), 17f * a.u, a.o(cx, cy))
      drawCircle(p.brand, 17f * a.u, a.o(cx, cy), style = Stroke(width = 4.4f * a.u))
      drawArc(p.paper, 200f, 55f, false, a.o(cx - 12f, cy - 12f), a.s(24f, 24f), style = Stroke(width = 2.2f * a.u, cap = StrokeCap.Round))
      for (i in 0..2) {
        val k = 0.5f + 0.5f * wave(t, 8, -i * 0.9f)
        drawCircle(p.muted.copy(alpha = 0.25f + 0.45f * k), (1.3f + 0.5f * k) * a.u, a.o(cx - 5f + 5f * i, cy + 1f))
      }
    }
  }
  spark(a, 22f, 30f, max(0f, wave(t, 6, 0f)), p.warm)
  spark(a, 79f, 25f, max(0f, wave(t, 6, 2.1f)), p.brand)
  spark(a, 81f, 62f, max(0f, wave(t, 6, 4.2f)), p.sky)
}

/** ابرِ خواب‌آلود با آذرخشِ کوچک و دو قطره — سرور جواب نداد */
internal fun DrawScope.errorCloudScene(a: Art) {
  val p = a.p
  val t = a.t
  val lift = 3f
  stage(a, lift, groundW = 46f)
  translate(a.parallax(0.6f) * a.u, a.float(lift) * a.u) {
    val f = flash(t)
    if (f > 0.04f) drawCircle(p.warm.copy(alpha = 0.22f * f), 13f * a.u, a.o(52f, 72f))
    turn(a, 4f * wave(t, 3, 1f), 52f, 62f) {
      softShadow(a) { c -> drawPath(a.shapes.bolt, c) }
      drawPath(a.shapes.bolt, lerp(lerp(p.warm, p.paper, 0.25f), p.warm, f))
    }
    for (i in 0..1) {
      val d = frac(t, 4, i * 0.5f)
      val x = 37f + 27f * i
      val y = 62f + 22f * d
      drawLine(p.sky.copy(alpha = 0.7f * (1f - d)), a.o(x, y), a.o(x, y + 3f), strokeWidth = 1.7f * a.u, cap = StrokeCap.Round)
    }
    softShadow(a) { c -> drawPath(a.shapes.cloud, c) }
    drawPath(a.shapes.cloud, lerp(p.paper, p.sky, 0.10f))
    clipPath(a.shapes.cloud) { drawOval(lerp(p.paper, p.sky, 0.26f), a.o(24f, 53f), a.s(54f, 14f)) }
    drawPath(a.shapes.cloud, p.line.copy(alpha = 0.22f), style = Stroke(width = 0.9f * a.u))
    val eye = Stroke(width = 1.2f * a.u, cap = StrokeCap.Round)
    drawArc(p.line, 20f, 140f, false, a.o(40f, 45f), a.s(6f, 4f), style = eye)
    drawArc(p.line, 20f, 140f, false, a.o(54f, 45f), a.s(6f, 4f), style = eye)
    drawCircle(p.line.copy(alpha = 0.75f), 1.5f * a.u, a.o(50f, 53.5f), style = Stroke(width = 1f * a.u))
  }
}

/** دوشاخه‌ای که از پریز جدا مانده و آرام جلو و عقب می‌رود، با جرقهٔ کوچکِ میانشان — بی اینترنت */
internal fun DrawScope.errorCableScene(a: Art) {
  val p = a.p
  val t = a.t
  val lift = 2f
  stage(a, lift, groundW = 66f)
  translate(a.parallax(0.5f) * a.u, a.float(lift) * a.u) {
    val cord = Stroke(width = 2.8f * a.u, cap = StrokeCap.Round)
    //  پریز و سیمش
    val b = a.shapes.cableB
    b.reset()
    b.moveTo(80f * a.u, 52f * a.u)
    b.quadraticTo(90f * a.u, 54f * a.u, 95f * a.u, 82f * a.u)
    drawPath(b, p.line.copy(alpha = 0.75f), style = cord)
    softShadow(a) { c -> drawRoundRect(c, a.o(60f, 38f), a.s(21f, 27f), a.r(4f)) }
    drawRoundRect(p.face, a.o(60f, 38f), a.s(21f, 27f), a.r(4f))
    drawRoundRect(p.line.copy(alpha = 0.3f), a.o(60f, 38f), a.s(21f, 27f), a.r(4f), style = Stroke(width = 0.9f * a.u))
    drawRoundRect(p.ink.copy(alpha = 0.5f), a.o(64.5f, 46f), a.s(2.6f, 9f), a.r(1f))
    drawRoundRect(p.ink.copy(alpha = 0.5f), a.o(73.5f, 46f), a.s(2.6f, 9f), a.r(1f))
    //  دوشاخه و سیمش
    val plugX = 43f + 2.2f * wave(t, 3, 0.4f)
    val sway = 5f * wind(t, 0.5f)
    val c = a.shapes.cableA
    c.reset()
    c.moveTo((plugX - 15f) * a.u, 51f * a.u)
    c.quadraticTo(10f * a.u, 52f * a.u, 6f * a.u, 82f * a.u)
    drawPath(c, p.line.copy(alpha = 0.75f), style = cord)
    turn(a, sway, plugX - 15f, 51f) {
      softShadow(a) { s -> drawRoundRect(s, a.o(plugX - 16f, 43f), a.s(16f, 16f), a.r(4f)) }
      drawRoundRect(p.brand, a.o(plugX - 16f, 43f), a.s(16f, 16f), a.r(4f))
      drawLine(lerp(p.brand, p.paper, 0.4f), a.o(plugX - 12f, 47f), a.o(plugX - 12f, 55f), strokeWidth = 1f * a.u, cap = StrokeCap.Round)
      drawLine(lerp(p.brand, p.paper, 0.4f), a.o(plugX - 9f, 47f), a.o(plugX - 9f, 55f), strokeWidth = 1f * a.u, cap = StrokeCap.Round)
      drawRoundRect(p.line, a.o(plugX, 46f), a.s(7f, 2.6f), a.r(1f))
      drawRoundRect(p.line, a.o(plugX, 53.4f), a.s(7f, 2.6f), a.r(1f))
    }
    //  جرقه در میانه
    val gap = (plugX + 7f + 60f) / 2f
    for (i in 0..2) {
      val k = max(0f, wave(t, 9, i * 2.1f))
      val e = k * k * k
      if (e < 0.05f) continue
      val dy = (i - 1) * 4.5f
      drawLine(p.warm.copy(alpha = e), a.o(gap - 1.6f * e, 51f + dy), a.o(gap + 1.6f * e, 51f + dy * 1.4f), strokeWidth = 1.2f * a.u, cap = StrokeCap.Round)
    }
  }
}

/** بادکنکِ «!» ِ تاب‌خور — هر خطای دیگر */
internal fun DrawScope.errorMarkScene(a: Art) {
  val p = a.p
  val t = a.t
  val lift = 2.6f
  stage(a, lift, groundW = 44f)
  translate(a.parallax(0.6f) * a.u, a.float(lift) * a.u) {
    turn(a, 6f * wind(t, 0.7f), 50f, 70f) {
      softShadow(a) { c ->
        drawCircle(c, 21f * a.u, a.o(50f, 44f))
        drawPath(a.shapes.tail, c)
      }
      val fill = lerp(p.danger, p.paper, 0.82f)
      drawPath(a.shapes.tail, fill)
      drawCircle(fill, 21f * a.u, a.o(50f, 44f))
      drawCircle(p.danger.copy(alpha = 0.42f), 21f * a.u, a.o(50f, 44f), style = Stroke(width = 1.4f * a.u))
      drawRoundRect(p.danger, a.o(47.6f, 31f), a.s(4.8f, 16f), a.r(2.4f))
      drawCircle(p.danger, 2.7f * a.u, a.o(50f, 53f))
    }
  }
  spark(a, 79f, 28f, max(0f, wave(t, 6, 0.5f)), p.warm)
  spark(a, 21f, 34f, max(0f, wave(t, 6, 2.8f)), p.violet)
}
