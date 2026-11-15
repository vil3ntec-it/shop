package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/*
 *  صحنه‌های آدمک‌دارِ دکان (کالا، فروش، قرض‌دار، سفارش): هر کدام چرخهٔ چندثانیه‌ای با مکثِ طبیعی، پلک و نفس. ژستِ
 *  «کاهشِ حرکت» همان لحظهٔ [ART_REST] است که برای هر صحنه گویاترین لحظه گذاشته شده (شانه بالا انداختن، ضرب گرفتن…).
 */

/* ------------------------------- کالا: جعبهٔ خالی ------------------------------- */

private val P_LID = floatArrayOf(0f, 0f, 0.35f, 0f, 0.75f, 118f, 3.55f, 118f, 3.95f, 0f)
private val P_RARM = floatArrayOf(0f, 14f, 0.35f, 80f, 0.75f, 112f, 1.05f, 40f, 1.9f, 40f, 2.2f, 14f, 2.5f, 58f, 3.2f, 58f, 3.6f, 14f)
private val P_RFORE = floatArrayOf(0f, 0f, 0.35f, -10f, 0.75f, -32f, 1.05f, 78f, 1.9f, 78f, 2.2f, 0f, 2.5f, -18f, 3.2f, -18f, 3.6f, 0f)
private val P_LARM = floatArrayOf(0f, -14f, 0.9f, -14f, 1.05f, -40f, 1.9f, -40f, 2.2f, -14f)
private val P_LFORE = floatArrayOf(0f, 0f, 0.9f, 0f, 1.05f, -78f, 1.9f, -78f, 2.2f, 0f)
private val P_SHRUG = floatArrayOf(0f, 0f, 0.95f, 0f, 1.15f, 1f, 1.75f, 1f, 2f, 0f)
private val P_TILT = floatArrayOf(0f, 0f, 1.05f, 0f, 1.25f, 9f, 1.8f, 9f, 2.1f, 0f, 2.5f, 4f, 3.2f, 4f, 3.5f, 0f)
private val P_SMILE = floatArrayOf(0f, 0.35f, 1f, 0.35f, 1.2f, -0.25f, 1.85f, -0.25f, 2.1f, 0.35f, 2.6f, 0.05f, 3.2f, 0.05f, 3.5f, 0.4f)
private val P_BROW = floatArrayOf(0f, 0f, 1.05f, 0f, 1.2f, 1f, 1.85f, 1f, 2.05f, 0f)
private val P_LEAN = floatArrayOf(0f, 0f, 0.3f, 7f, 0.75f, 9f, 1f, 0f, 2.3f, 0f, 2.7f, 15f, 3.2f, 15f, 3.6f, 0f)
private val P_LOOK = floatArrayOf(0f, 0.4f, 0.3f, 1f, 1f, 1f, 1.2f, 0f, 2.3f, 0f, 2.6f, 1f, 3.3f, 1f, 3.6f, 0.4f)
private val P_LOOKY = floatArrayOf(0f, 0f, 2.5f, 0f, 2.7f, 0.9f, 3.2f, 0.9f, 3.5f, 0f)
private val P_THINK = floatArrayOf(0f, 0f, 1.15f, 0f, 1.3f, 1f, 1.8f, 1f, 1.95f, 0f)
private val P_DUST = floatArrayOf(0f, 0f, 2.75f, 0f, 3f, 1f, 3.4f, 0f)

/** فروشنده درِ جعبهٔ کالا را باز می‌کند، شانه بالا می‌اندازد («خالی است!») و سرک می‌کشد */
internal fun DrawScope.productsCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 4f)
  stage(a, 0f, groundW = 76f)
  //  جعبه (همان مسیرهای جعبهٔ کالا، ۱۰ واحد به چپ تا فروشنده جا شود)
  withTransform({ translate(-10f * a.u, 0f) }) {
    drawPath(a.shapes.flapBack, p.kraftSide)
    drawPath(a.shapes.boxMouth, lerp(p.kraftSide, p.ink, 0.35f))
    val dust = keys(lt, P_DUST)
    if (dust > 0f) for (i in 0..2) drawCircle(p.muted.copy(alpha = 0.35f * dust), (1.2f + i * 0.5f) * a.u, a.o(40f + i * 7f, 50f - dust * (4f + i * 2f)))
    softShadow(a) { c -> drawRect(c, a.o(22f, 58f), a.s(46f, 30f)) }
    drawRect(p.kraft, a.o(22f, 58f), a.s(46f, 30f))
    drawPath(a.shapes.boxSide, p.kraftSide)
    drawRect(p.brandSoft.copy(alpha = 0.85f), a.o(41.5f, 58f), a.s(7f, 10f))
    drawRoundRect(p.paper, a.o(27f, 74f), a.s(15f, 8f), a.r(1.2f))
    //  درِ جلو، لولا در گوشهٔ نزدیکِ فروشنده
    withTransform({ rotate(keys(lt, P_LID), a.o(68f, 58f)) }) {
      drawRoundRect(p.kraft, a.o(40f, 56.6f), a.s(28f, 2.8f), a.r(1f))
      drawRoundRect(lerp(p.kraft, p.ink, 0.12f), a.o(40f, 58.4f), a.s(28f, 1f))
    }
  }
  //  فروشنده
  val pose = a.shapes.pose.apply {
    reset()
    x = 80f; ground = 89f
    breathe = breath(lt, 4f)
    lean = -keys(lt, P_LEAN)
    look = -keys(lt, P_LOOK); lookY = keys(lt, P_LOOKY)
    tilt = -keys(lt, P_TILT)
    smile = keys(lt, P_SMILE); brow = keys(lt, P_BROW)
    shrug = keys(lt, P_SHRUG)
    blink = max(blinkAt(lt, 0.15f), max(blinkAt(lt, 2.25f), blinkAt(lt, 3.75f)))
    lArm = -keys(lt, P_RARM); lFore = -keys(lt, P_RFORE)
    rArm = -keys(lt, P_LARM); rFore = -keys(lt, P_LFORE)
  }
  figure(a, a.shapes.cast, a.shapes.cast.shopkeeper, pose)
  thought(a, pose.headX - 15f, pose.headY - 12f, keys(lt, P_THINK), p.paper, p.muted)
}

/* ------------------------------- فروش: صندوق‌دارِ منتظر ------------------------------- */

private val S_LOOK = floatArrayOf(0f, 0f, 0.3f, -1f, 1.1f, -1f, 1.4f, 0f, 2.9f, 0f, 3.1f, -1f, 3.6f, -1f, 3.9f, 0f)
private val S_TILT = floatArrayOf(0f, 0f, 0.3f, -6f, 1.1f, -6f, 1.4f, 0f, 2.9f, 0f, 3.1f, -4f, 3.6f, -4f, 3.9f, 0f)
private val S_DRUM = floatArrayOf(0f, 0f, 1.3f, 0f, 1.5f, 1f, 2.8f, 1f, 3f, 0f)
private val S_SMILE = floatArrayOf(0f, 0.3f, 1.4f, 0.3f, 1.8f, -0.15f, 2.8f, -0.15f, 3.1f, 0.3f)
private val S_BROW = floatArrayOf(0f, 0f, 0.3f, 0.7f, 1.1f, 0.7f, 1.3f, 0f)

/** صندوق‌دار پشتِ پیشخوان با انگشت ضرب می‌گیرد و چشمش به در است؛ سکه روی پیشخوان می‌چرخد */
internal fun DrawScope.salesCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 4f)
  stage(a, 0f, groundW = 80f)
  //  در با زنگوله
  drawRoundRect(p.side, a.o(6f, 34f), a.s(16f, 55f), a.r(1.5f))
  drawRoundRect(p.face, a.o(8f, 36f), a.s(12f, 52f), a.r(1f))
  drawRoundRect(p.brandSoft.copy(alpha = 0.7f), a.o(10f, 39f), a.s(8f, 14f), a.r(1f))
  drawCircle(p.warm, 1f * a.u, a.o(18f, 64f))
  val ring = keys(lt, S_LOOK)
  withTransform({ rotate(14f * sin(lt * 18f) * max(0f, -ring) * 0.5f, a.o(24f, 33f)) }) {
    drawLine(p.line.copy(alpha = 0.5f), a.o(24f, 33f), a.o(24f, 37f), strokeWidth = 0.6f * a.u)
    drawArc(p.warm, 180f, 180f, true, a.o(21.8f, 36f), a.s(4.4f, 5f))
  }
  //  صندوق‌دار (پایین‌تنه پشتِ پیشخوان)
  val drum = keys(lt, S_DRUM)
  val pose = a.shapes.pose.apply {
    reset()
    x = 52f; ground = 90f; legs = false
    breathe = breath(lt, 4f) * (1f + 0.6f * drum)
    look = keys(lt, S_LOOK); tilt = keys(lt, S_TILT); lean = 2.5f * keys(lt, S_LOOK)
    smile = keys(lt, S_SMILE); brow = keys(lt, S_BROW)
    blink = max(blinkAt(lt, 1.25f), blinkAt(lt, 2.4f))
    lArm = -18f; lFore = 100f
    rArm = 16f; rFore = -96f + 6f * drum * sin(lt * 22f)
  }
  figure(a, a.shapes.cast, a.shapes.cast.cashier, pose)
  //  انگشت‌های ضرب‌گیر
  for (i in 0..2) {
    val up = drum * max(0f, sin(lt * 2f * PI.toFloat() * 3f - i * 0.9f)) * 1.3f
    drawCircle(a.shapes.cast.cashier.skin, 0.95f * a.u, a.o(pose.rhX - 1.2f + i * 1.3f, pose.rhY - 1.6f - up))
  }
  //  پیشخوان، صندوق و سکه
  softShadow(a) { c -> drawRoundRect(c, a.o(28f, 66f), a.s(62f, 22f), a.r(2f)) }
  drawRoundRect(lerp(p.side, p.ink, 0.12f), a.o(26f, 64f), a.s(66f, 4f), a.r(1.5f))
  drawRoundRect(p.face, a.o(28f, 68f), a.s(62f, 20f), a.r(1.5f))
  drawRoundRect(p.brandSoft.copy(alpha = 0.6f), a.o(28f, 72f), a.s(62f, 2.4f), a.r(1f))
  drawRoundRect(p.side, a.o(72f, 52f), a.s(15f, 12f), a.r(2f))
  drawRoundRect(p.brandSoft, a.o(74f, 54f), a.s(11f, 4f), a.r(1f))
  for (k in 0..5) drawCircle(p.line.copy(alpha = 0.35f), 0.7f * a.u, a.o(75.5f + (k % 3) * 3.5f, 60.5f + (k / 3) * 2f))
  coin(a, 64f, 60f, 3.6f)
}

/* ------------------------------- قرض‌داران: دفترِ خالی ------------------------------- */

private val D_LOOKY = floatArrayOf(0f, 0.8f, 2.2f, 0.8f, 2.45f, -0.1f, 3.5f, -0.1f, 3.8f, 0.8f)
private val D_HAPPY = floatArrayOf(0f, 0f, 2.4f, 0f, 2.5f, 1f, 3.4f, 1f, 3.5f, 0f)
private val D_SMILE = floatArrayOf(0f, 0.2f, 2.3f, 0.2f, 2.5f, 1f, 3.4f, 1f, 3.7f, 0.2f)
private val D_TILT = floatArrayOf(0f, 3f, 2.3f, 3f, 2.5f, -8f, 3.4f, -8f, 3.7f, 3f)
private val D_SPARK = floatArrayOf(0f, 0f, 2.45f, 0f, 2.7f, 1f, 3.3f, 1f, 3.5f, 0f)

/** کسی دفترِ حساب را ورق می‌زند، چیزی ندارد و لبخند می‌زند («هیچ‌کس بدهکار نیست») */
internal fun DrawScope.peopleCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 4f)
  stage(a, 0f, groundW = 50f)
  val pose = a.shapes.pose.apply {
    reset()
    x = 50f; ground = 89f
    breathe = breath(lt, 4f)
    lookY = keys(lt, D_LOOKY); happy = keys(lt, D_HAPPY); smile = keys(lt, D_SMILE); tilt = keys(lt, D_TILT)
    blink = max(blinkAt(lt, 0.2f), blinkAt(lt, 3.85f))
    lArm = -28f; lFore = 58f; rArm = 28f; rFore = -58f
  }
  figure(a, a.shapes.cast, a.shapes.cast.lender, pose)
  //  دفترِ باز میانِ دو دست
  val cx = (pose.lhX + pose.rhX) / 2f
  val cy = (pose.lhY + pose.rhY) / 2f - 4f
  drawRoundRect(lerp(p.violet, p.ink, 0.25f), a.o(cx - 12.5f, cy - 7.5f), a.s(25f, 14f), a.r(1.6f))
  drawRoundRect(p.paper, a.o(cx - 11.5f, cy - 8f), a.s(11.3f, 13f), a.r(1f))
  drawRoundRect(p.paper, a.o(cx + 0.2f, cy - 8f), a.s(11.3f, 13f), a.r(1f))
  for (i in 0..2) {
    drawLine(p.muted.copy(alpha = 0.22f), a.o(cx - 9.5f, cy - 4f + i * 3f), a.o(cx - 2f, cy - 4f + i * 3f), strokeWidth = 0.6f * a.u)
    drawLine(p.muted.copy(alpha = 0.22f), a.o(cx + 2f, cy - 4f + i * 3f), a.o(cx + 9.5f, cy - 4f + i * 3f), strokeWidth = 0.6f * a.u)
  }
  //  ورقِ در حالِ برگشتن (سه بار)
  val f = when {
    lt in 0.3f..0.75f -> (lt - 0.3f) / 0.45f
    lt in 0.95f..1.4f -> (lt - 0.95f) / 0.45f
    lt in 1.6f..2.05f -> (lt - 1.6f) / 0.45f
    else -> -1f
  }
  if (f >= 0f) {
    val e = f * f * (3f - 2f * f)
    val sx = kotlin.math.cos(e * PI.toFloat())
    withTransform({
      translate(0f, -2.2f * sin(e * PI.toFloat()) * a.u)
      scale(if (kotlin.math.abs(sx) < 0.04f) 0.04f else sx, 1f, a.o(cx, cy))
    }) {
      drawRoundRect(lerp(p.paper, p.muted, 0.12f), a.o(cx + 0.2f, cy - 8f), a.s(11.3f, 13f), a.r(1f))
    }
  }
  drawLine(p.line.copy(alpha = 0.4f), a.o(cx, cy - 8f), a.o(cx, cy + 5f), strokeWidth = 0.6f * a.u)
  //  دست‌ها روی لبهٔ دفتر
  drawCircle(a.shapes.cast.lender.skin, 2.2f * a.u, a.o(pose.lhX, pose.lhY))
  drawCircle(a.shapes.cast.lender.skin, 2.2f * a.u, a.o(pose.rhX, pose.rhY))
  val k = keys(lt, D_SPARK)
  spark(a, pose.headX + 12f, pose.headY - 8f, k, p.warm)
  spark(a, pose.headX - 12f, pose.headY - 4f, k * 0.8f, p.brand)
}

/* ------------------------------- سفارش: پیکِ منتظر ------------------------------- */

private val O_LOOK = floatArrayOf(0f, 0f, 0.4f, -1f, 1f, -1f, 1.3f, 1f, 1.9f, 1f, 2.2f, 0f)
private val O_TILT = floatArrayOf(0f, 0f, 0.4f, -5f, 1f, -5f, 1.3f, 5f, 1.9f, 5f, 2.2f, 0f)
private val O_TAP = floatArrayOf(0f, 0f, 2.2f, 0f, 2.3f, 1f, 2.85f, 1f, 2.95f, 0f)

/** پیک روی موتورِ روشن منتظرِ سفارش است: موتور می‌لرزد، چرخ‌ها کمی تاب دارند، دود بیرون می‌زند، پیک این‌سو و آن‌سو را می‌پاید */
internal fun DrawScope.ordersCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 3f)
  stage(a, 0f, groundW = 80f)
  val hum = 0.35f * sin(lt * 2f * PI.toFloat() * 8f)
  //  دود
  for (i in 0..2) {
    val f = frac(a.t, 8, i / 3f)
    drawCircle(p.muted.copy(alpha = 0.28f * (1f - f)), (1.4f + 3f * f) * a.u, a.o(17f - 10f * f, 79f - 6f * f))
  }
  //  چرخ‌ها (تابِ کوچک)
  for (w in 0..1) {
    val wx = if (w == 0) 30f else 72f
    val wob = 1f + 0.035f * sin(lt * 2f * PI.toFloat() * 4f + wx)
    withTransform({ scale(1f / wob, wob, a.o(wx, 88f)) }) {
      drawCircle(lerp(p.ink, p.side, 0.3f), 6.4f * a.u, a.o(wx, 82f))
      drawCircle(p.side, 3f * a.u, a.o(wx, 82f))
      drawCircle(p.warm, 1f * a.u, a.o(wx, 82f))
    }
  }
  withTransform({ translate(0f, hum * a.u) }) {
    //  جعبهٔ سفارش و بدنه
    softShadow(a) { c -> drawRoundRect(c, a.o(14f, 44f), a.s(18f, 16f), a.r(2f)) }
    drawRoundRect(p.warm, a.o(14f, 44f), a.s(18f, 16f), a.r(2f))
    drawRoundRect(p.brand, a.o(14f, 50f), a.s(18f, 3f))
    drawRoundRect(p.brand, a.o(20f, 62f), a.s(28f, 16f), a.r(7f))
    drawRoundRect(p.brand, a.o(30f, 74f), a.s(36f, 6f), a.r(3f))
    drawRoundRect(lerp(p.ink, p.side, 0.25f), a.o(28f, 60f), a.s(22f, 4.5f), a.r(2.2f))
    drawLine(p.brand, a.o(64f, 77f), a.o(68f, 50f), strokeWidth = 4f * a.u, cap = StrokeCap.Round)
    drawLine(lerp(p.ink, p.side, 0.25f), a.o(63f, 49f), a.o(73f, 49.5f), strokeWidth = 2f * a.u, cap = StrokeCap.Round)
    drawCircle(p.warm, 2.2f * a.u, a.o(70.5f, 55f))
    drawCircle(p.paper.copy(alpha = 0.8f), 1f * a.u, a.o(71f, 54.5f))
    //  پیک
    val tap = keys(lt, O_TAP)
    val pose = a.shapes.pose.apply {
      reset()
      x = 41f; ground = 66f; sit = true
      breathe = breath(lt, 3f)
      look = keys(lt, O_LOOK); tilt = keys(lt, O_TILT)
      lean = 10f
      blink = blinkAt(lt, 1.15f)
      lLeg = 72f; lKnee = -66f
      rLeg = 66f; rKnee = -60f + 14f * tap * max(0f, sin(lt * 2f * PI.toFloat() * 5f))
      lArm = 88f; lFore = -6f
      rArm = 98f; rFore = -16f
    }
    figure(a, a.shapes.cast, a.shapes.cast.courier, pose)
  }
}
