package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.abs
import kotlin.math.max

/*
 *  صحنه‌های دکان: رسید (فروش)، دو نفر (قرض‌دار و مشتری)، سبد (سفارش) و قفسه (موجودی). هر چیزِ آویزان یا ایستاده با
 *  همان باد و فنرِ [wind] کمی تاب می‌خورد؛ چیزهای کوچک با فازِ جدا بالا و پایین می‌روند تا صحنه زنده باشد ولی آرام.
 */

/** نوارِ گرد (خطِ نوشتهٔ رسید) */
private fun DrawScope.bar(a: Art, x1: Float, x2: Float, y: Float, h: Float, color: Color) {
  drawRoundRect(color, a.o(x1, y), a.s(x2 - x1, h), a.r(h / 2f))
}

/** سکهٔ چرخان: پهنا با کسینوس کم و زیاد می‌شود، لبه وقتی رو به ماست پیدا */
internal fun DrawScope.coin(a: Art, x: Float, y: Float, r: Float, phase: Float = 0f) {
  val p = a.p
  val spin = wave(a.t, 2, phase + 1.5708f)
  val sx = max(abs(spin), 0.14f)
  val deep = lerp(p.warm, p.ink, 0.28f)
  softShadow(a) { c -> drawCircle(c, r * a.u, a.o(x, y)) }
  withTransform({ scale(sx, 1f, a.o(x, y)) }) {
    drawCircle(deep, r * a.u, a.o(x + 0.7f / sx, y + 0.5f))
    drawCircle(p.warm, r * a.u, a.o(x, y))
    drawCircle(lerp(p.warm, p.paper, 0.45f), r * 0.68f * a.u, a.o(x, y), style = Stroke(width = 0.9f * a.u))
  }
  if (sx > 0.45f) spark(a, x - r * 0.25f * sx, y - r * 0.2f, 0.55f * sx, p.paper)
}

/** رسیدِ آویزان با سکه */
internal fun DrawScope.salesScene(a: Art) {
  val p = a.p
  val t = a.t
  val lift = 1.8f
  stage(a, lift, groundW = 46f)
  translate(a.parallax(0.5f) * a.u, a.float(lift) * a.u) {
    turn(a, 2.8f * wind(t, 0.3f), 50f, 14f) {
      softShadow(a) { c -> drawPath(a.shapes.receipt, c) }
      drawPath(a.shapes.receipt, p.paper)
      drawPath(a.shapes.receipt, p.line.copy(alpha = 0.22f), style = Stroke(width = 0.9f * a.u))
      drawCircle(p.brandSoft, 4.8f * a.u, a.o(50f, 25.5f))
      drawCircle(p.brand, 2.3f * a.u, a.o(50f, 25.5f))
      bar(a, 39f, 61f, 33.5f, 2.6f, p.muted.copy(alpha = 0.5f))
      for (i in 0..2) {
        val y = 41f + i * 6f
        bar(a, 36f, 51f - i * 2f, y, 2.2f, p.muted.copy(alpha = 0.32f))
        bar(a, 56f, 64f, y, 2.2f, p.muted.copy(alpha = 0.46f))
      }
      for (j in 0..7) drawLine(p.muted.copy(alpha = 0.35f), a.o(36f + j * 3.6f, 60.5f), a.o(37.8f + j * 3.6f, 60.5f), strokeWidth = 0.8f * a.u)
      bar(a, 36f, 47f, 65f, 3.4f, p.ink.copy(alpha = 0.55f))
      bar(a, 52f, 64f, 65f, 3.4f, p.brand)
      for (k in 0..9) {
        val w = if (k % 3 == 0) 1.1f else 0.6f
        drawLine(p.ink.copy(alpha = 0.32f), a.o(39f + k * 2.3f, 71f), a.o(39f + k * 2.3f, 75f), strokeWidth = w * a.u)
      }
    }
  }
  coin(a, 73f, 72f + 2.2f * wave(t, 4, 1.3f), 7.5f)
}

/** یک نفر: نفس کشیدن (کشیدگیِ نرمِ تن از پا) و سرِ تکان‌خورنده */
private fun DrawScope.person(a: Art, cx: Float, base: Float, top: Float, headR: Float, body: Color, skin: Color, phase: Float, front: Boolean) {
  val p = a.p
  val breathe = 1f + 0.022f * wave(a.t, 5, phase)
  val shape = if (front) a.shapes.bodyFront else a.shapes.bodyBack
  withTransform({ scale(1f, breathe, a.o(cx, base)) }) {
    softShadow(a) { c -> drawPath(shape, c) }
    drawPath(shape, body)
    //  یقه
    drawCircle(lerp(body, p.paper, 0.3f), 3.2f * a.u, a.o(cx, top + 1.2f))
  }
  val headY = base - (base - top) * breathe - headR * 0.82f
  turn(a, 5f * wind(a.t, phase), cx, headY + headR) {
    drawCircle(skin, headR * a.u, a.o(cx, headY))
    val hair = lerp(p.ink, p.trunk, 0.35f)
    drawArc(hair, 180f, 180f, true, a.o(cx - headR, headY - headR), a.s(2f * headR, 2f * headR * 0.92f))
    drawCircle(p.ink.copy(alpha = 0.72f), 0.95f * a.u, a.o(cx - headR * 0.34f, headY + headR * 0.12f))
    drawCircle(p.ink.copy(alpha = 0.72f), 0.95f * a.u, a.o(cx + headR * 0.34f, headY + headR * 0.12f))
    drawArc(
      p.ink.copy(alpha = 0.55f), 25f, 130f, false,
      a.o(cx - headR * 0.32f, headY + headR * 0.12f), a.s(headR * 0.64f, headR * 0.5f),
      style = Stroke(width = 0.8f * a.u, cap = StrokeCap.Round),
    )
  }
}

/** دو نفر با سکهٔ شناورِ میانشان — قرض‌داران، مشتریان، کارمندان */
internal fun DrawScope.peopleScene(a: Art) {
  val p = a.p
  val t = a.t
  val lift = 1.1f
  stage(a, lift, groundW = 66f)
  translate(a.parallax(0.5f) * a.u, a.float(lift) * a.u) {
    person(a, 38f, 88f, 58f, 8.5f, lerp(p.sky, p.paper, 0.32f), lerp(p.warm, p.paper, 0.62f), 0f, front = false)
    person(a, 61f, 90f, 58f, 9.5f, p.brand, lerp(p.coral, p.paper, 0.6f), 1.9f, front = true)
  }
  val cy = 22f + 2.6f * wave(t, 4, 2.2f)
  drawCircle(p.warm.copy(alpha = 0.16f + 0.08f * wave(t, 8)), 10.5f * a.u, a.o(50f, cy))
  coin(a, 50f, cy, 6.4f, 0.9f)
}

/** سبدِ خرید با بطری، سیب و سبزی که هر کدام با فازِ خودش بالا و پایین می‌رود */
internal fun DrawScope.ordersScene(a: Art) {
  val p = a.p
  val t = a.t
  val lift = 1.4f
  stage(a, lift, groundW = 64f)
  translate(a.parallax(0.5f) * a.u, a.float(lift) * a.u) {
    turn(a, 3f * wind(t, 0.8f), 50f, 58f) {
      drawArc(p.line.copy(alpha = 0.8f), 180f, 180f, false, a.o(29f, 30f), a.s(42f, 56f), style = Stroke(width = 3.2f * a.u, cap = StrokeCap.Round))
    }
    val b1 = 1.8f * wave(t, 5, 0f)
    val b2 = 1.8f * wave(t, 5, 1.4f)
    val b3 = 1.8f * wave(t, 5, 2.9f)
    //  بطری
    turn(a, -6f + 3f * wind(t, 0.2f), 41f, 60f) {
      drawRoundRect(p.brand, a.o(36f, 40f + b1), a.s(10f, 22f), a.r(3f))
      drawRoundRect(p.brand, a.o(39.2f, 33f + b1), a.s(3.6f, 8f), a.r(1f))
      drawRoundRect(p.warm, a.o(38.8f, 31f + b1), a.s(4.4f, 2.6f), a.r(0.8f))
      drawRoundRect(p.paper.copy(alpha = 0.8f), a.o(37.5f, 47f + b1), a.s(7f, 7f), a.r(1f))
    }
    //  سبزی
    leaf(a, 67f, 59f + b3, -24f + 8f * wind(t, 1.1f), 16f, p.leafDeep)
    leaf(a, 70f, 59f + b3, 14f + 8f * wind(t, 2.0f), 14f, p.leaf, p.leafDeep)
    leaf(a, 64f, 59f + b3, -48f + 8f * wind(t, 2.7f), 12f, p.leafSoft, p.leafDeep)
    //  سیب
    drawCircle(lerp(p.coral, p.ink, 0.15f), 8.2f * a.u, a.o(56.7f, 51.4f + b2))
    drawCircle(p.coral, 8f * a.u, a.o(56f, 51f + b2))
    drawCircle(lerp(p.coral, p.paper, 0.55f), 2.2f * a.u, a.o(53f, 48f + b2))
    leaf(a, 56.5f, 43.5f + b2, 38f + 10f * wind(t, 0.5f), 5f, p.leaf)
    //  بدنه، بافت و لبه
    softShadow(a) { c -> drawPath(a.shapes.basket, c) }
    drawPath(a.shapes.basket, p.kraft)
    val weave = p.kraftSide.copy(alpha = 0.9f)
    for (y in floatArrayOf(66f, 73f, 80f)) {
      val inset = (y - 58f) * (8f / 29f)
      drawLine(weave, a.o(21f + inset + 1.5f, y), a.o(79f - inset - 1.5f, y), strokeWidth = 1.1f * a.u)
    }
    for (i in 1..6) {
      val xt = 21f + i * (58f / 7f)
      val xb = 29f + i * (42f / 7f)
      drawLine(weave, a.o(xt, 59f), a.o(xb, 86f), strokeWidth = 1.1f * a.u)
    }
    drawRoundRect(p.kraftSide, a.o(18.5f, 55f), a.s(63f, 6f), a.r(3f))
    drawRoundRect(p.line.copy(alpha = 0.2f), a.o(18.5f, 55f), a.s(63f, 6f), a.r(3f), style = Stroke(width = 0.7f * a.u))
  }
}

/** جعبهٔ قفسه که گاهی روی لبه‌اش کمی بلند می‌شود و برمی‌گردد */
private fun DrawScope.crate(a: Art, x: Float, y: Float, w: Float, h: Float, color: Color, phase: Float) {
  val hop = max(0f, wave(a.t, 7, phase))
  turn(a, -2.6f * hop * hop * hop, x + w, y + h) {
    drawRoundRect(color, a.o(x, y), a.s(w, h), a.r(1.2f))
    drawLine(lerp(color, a.p.paper, 0.4f), a.o(x + 1f, y + 1f), a.o(x + w - 1f, y + 1f), strokeWidth = 0.8f * a.u, cap = StrokeCap.Round)
    if (w > 11f) drawRoundRect(a.p.paper.copy(alpha = 0.85f), a.o(x + w / 2f - 3f, y + h / 2f - 2f), a.s(6f, 4f), a.r(0.6f))
  }
}

/** قفسهٔ سه‌طبقه با جعبه‌ها، شیشه و گلدانی که برگ‌هایش در باد است */
internal fun DrawScope.stockScene(a: Art) {
  val p = a.p
  val lift = 1f
  stage(a, lift, groundW = 68f)
  translate(a.parallax(0.5f) * a.u, a.float(lift) * a.u) {
    softShadow(a) { c -> drawRoundRect(c, a.o(19f, 26f), a.s(62f, 63f), a.r(2f)) }
    drawRoundRect(p.face, a.o(21f, 27f), a.s(58f, 61f), a.r(2f))
    drawRoundRect(p.side, a.o(18.5f, 25f), a.s(4f, 64f), a.r(1.5f))
    drawRoundRect(p.side, a.o(77.5f, 25f), a.s(4f, 64f), a.r(1.5f))
    val board = lerp(p.side, p.ink, 0.18f)
    for (y in floatArrayOf(46f, 66f, 86f)) drawRoundRect(board, a.o(17.5f, y), a.s(65f, 3f), a.r(1.2f))
    //  طبقهٔ بالا
    crate(a, 26f, 33f, 15f, 13f, p.kraft, 0f)
    crate(a, 44f, 38f, 10f, 8f, lerp(p.sky, p.paper, 0.25f), 1.3f)
    drawPath(a.shapes.pot, p.coral)
    drawRoundRect(lerp(p.coral, p.ink, 0.2f), a.o(60.2f, 36.6f), a.s(13.6f, 2.4f), a.r(1f))
    sprout(a, 67f, 37f, 13f, 1.2f, 0.62f)
    //  طبقهٔ میانه
    crate(a, 26f, 54f, 11f, 12f, lerp(p.violet, p.paper, 0.3f), 2.4f)
    crate(a, 40f, 51f, 17f, 15f, p.kraftSide, 3.7f)
    drawRoundRect(p.brandSoft, a.o(61f, 55f), a.s(11f, 11f), a.r(3f))
    drawRoundRect(p.brand, a.o(62.5f, 52.6f), a.s(8f, 3f), a.r(1f))
    //  طبقهٔ پایین
    crate(a, 26f, 71f, 21f, 15f, p.kraft, 5.1f)
    crate(a, 50f, 75f, 18f, 11f, p.leafSoft, 0.7f)
  }
  val k = max(0f, wave(a.t, 6, 1.2f))
  spark(a, 86f, 32f, k, p.warm)
}
