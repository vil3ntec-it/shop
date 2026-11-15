package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/*
 *  صحنه‌های آدمک‌دارِ موجودی، جست‌وجو، خطاها و پیش‌فرض: کارگری که گاریِ خالی را از کنارِ قفسه‌ها می‌برد، جوینده با
 *  ذره‌بینِ بزرگ، کسی که دوشاخهٔ کشیده در دست سرش را می‌خاراند، کسی که زیرِ ابرِ آذرخش‌دار چتر گرفته، کتاب‌خوانِ زیرِ
 *  درختِ تاب‌خور و کسی که دست به چانه به «!» نگاه می‌کند.
 */

private const val TAU = (2.0 * PI).toFloat()

/* ------------------------------- موجودی: گاریِ خالی ------------------------------- */

/** کارگر در جا راه می‌رود و گاریِ خالی را هل می‌دهد؛ قفسه‌ها و خطِ زمین از کنارش می‌گذرند (چرخهٔ بی‌درز) */
internal fun DrawScope.stockCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 4f)
  stage(a, 0f, groundW = 84f)
  val shift = -(lt / 4f) * 32f
  for (i in -1..3) {
    val x0 = 6f + i * 32f + shift
    val back = p.side.copy(alpha = 0.55f)
    drawRoundRect(back, a.o(x0, 30f), a.s(2.4f, 58f), a.r(1f))
    drawRoundRect(back, a.o(x0 + 26f, 30f), a.s(2.4f, 58f), a.r(1f))
    drawRoundRect(back, a.o(x0, 46f), a.s(28.4f, 2f), a.r(1f))
    drawRoundRect(back, a.o(x0, 64f), a.s(28.4f, 2f), a.r(1f))
    drawRoundRect(p.kraft.copy(alpha = 0.6f), a.o(x0 + 4f, 37f), a.s(9f, 9f), a.r(1f))
    drawRoundRect(p.brandSoft.copy(alpha = 0.6f), a.o(x0 + 15f, 40f), a.s(7f, 6f), a.r(1f))
    drawRoundRect(lerp(p.violet, p.paper, 0.4f).copy(alpha = 0.55f), a.o(x0 + 5f, 56f), a.s(7f, 8f), a.r(1f))
  }
  for (i in 0..8) {
    val x = 4f + i * 12f + (shift * 1.5f) % 12f
    drawLine(p.line.copy(alpha = 0.18f), a.o(x, 90.5f), a.o(x + 5f, 90.5f), strokeWidth = 0.9f * a.u, cap = StrokeCap.Round)
  }
  val ph = lt * TAU
  val pose = a.shapes.pose.apply {
    reset()
    x = 36f; ground = 89f
    bob = 0.7f * abs(sin(ph))
    breathe = breath(lt, 2f)
    lean = 9f
    look = 0.6f; smile = 0.5f
    blink = blinkAt(lt, 1.6f)
    lLeg = 26f * sin(ph); lKnee = -34f * max(0f, sin(ph))
    rLeg = -26f * sin(ph); rKnee = -34f * max(0f, -sin(ph))
    lArm = 74f; lFore = 12f
    rArm = 84f; rFore = 4f
  }
  figure(a, a.shapes.cast, a.shapes.cast.worker, pose)
  //  گاری
  val wire = Stroke(width = 1.1f * a.u, cap = StrokeCap.Round)
  drawLine(p.line, a.o(pose.rhX, pose.rhY), a.o(62f, 64f), strokeWidth = 1.6f * a.u, cap = StrokeCap.Round)
  drawLine(p.line, a.o(pose.lhX, pose.lhY), a.o(pose.rhX, pose.rhY), strokeWidth = 1.6f * a.u, cap = StrokeCap.Round)
  softShadow(a) { c -> drawRoundRect(c, a.o(62f, 60f), a.s(26f, 18f), a.r(2f)) }
  drawRoundRect(p.paper.copy(alpha = 0.6f), a.o(62f, 60f), a.s(26f, 18f), a.r(2f))
  drawRoundRect(p.line, a.o(62f, 60f), a.s(26f, 18f), a.r(2f), style = wire)
  for (k in 1..4) drawLine(p.line.copy(alpha = 0.55f), a.o(62f + k * 5.2f, 60f), a.o(62f + k * 5.2f, 78f), strokeWidth = 0.6f * a.u)
  drawLine(p.line.copy(alpha = 0.55f), a.o(62f, 69f), a.o(88f, 69f), strokeWidth = 0.6f * a.u)
  drawLine(p.line, a.o(66f, 78f), a.o(66f, 82f), strokeWidth = 1.2f * a.u)
  drawLine(p.line, a.o(84f, 78f), a.o(84f, 82f), strokeWidth = 1.2f * a.u)
  for (w in 0..1) {
    val wx = if (w == 0) 66f else 84f
    drawCircle(lerp(p.ink, p.side, 0.3f), 3.4f * a.u, a.o(wx, 85f))
    turn(a, -lt * 360f, wx, 85f) { drawLine(p.side, a.o(wx - 2.2f, 85f), a.o(wx + 2.2f, 85f), strokeWidth = 0.8f * a.u) }
  }
}

/* ------------------------------- جست‌وجو: ذره‌بین ------------------------------- */

private val Q_LEAN = floatArrayOf(0f, 0f, 0.5f, 0f, 1f, -7f, 1.9f, -7f, 2.4f, 7f, 2.9f, 7f, 3.2f, 0f)
private val Q_LOOK = floatArrayOf(0f, 0f, 0.5f, 0f, 0.9f, -1f, 1.9f, -1f, 2.3f, 1f, 2.9f, 1f, 3.1f, 0f)
private val Q_RARM = floatArrayOf(0f, 30f, 0.5f, 30f, 1f, -60f, 1.9f, -60f, 2.4f, 60f, 2.9f, 60f, 3.2f, 30f)
private val Q_RFORE = floatArrayOf(0f, 110f, 0.5f, 110f, 1f, -60f, 1.9f, -60f, 2.4f, 80f, 2.9f, 80f, 3.2f, 110f)
private val Q_LARM = floatArrayOf(0f, -12f, 3.15f, -12f, 3.3f, -40f, 3.75f, -40f, 3.95f, -12f)
private val Q_LFORE = floatArrayOf(0f, 0f, 3.15f, 0f, 3.3f, -78f, 3.75f, -78f, 3.95f, 0f)
private val Q_SHRUG = floatArrayOf(0f, 0f, 3.2f, 0f, 3.35f, 1f, 3.7f, 1f, 3.9f, 0f)
private val Q_SMILE = floatArrayOf(0f, 0.3f, 3.2f, 0.3f, 3.35f, -0.2f, 3.75f, -0.2f, 3.95f, 0.3f)
private val Q_BROW = floatArrayOf(0f, 0f, 0.9f, 0.5f, 3.1f, 0.5f, 3.3f, 1f, 3.75f, 1f, 3.9f, 0f)
private val Q_MARK = floatArrayOf(0f, 0f, 3.25f, 0f, 3.4f, 1f, 3.75f, 1f, 3.9f, 0f)

/** جوینده با ذره‌بینِ بزرگ چپ و راست را می‌گردد و آخر شانه بالا می‌اندازد («پیدا نشد») */
internal fun DrawScope.searchCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 4f)
  stage(a, 0f, groundW = 52f)
  val pose = a.shapes.pose.apply {
    reset()
    x = 48f; ground = 89f
    breathe = breath(lt, 4f)
    lean = keys(lt, Q_LEAN); look = keys(lt, Q_LOOK); tilt = 0.6f * keys(lt, Q_LEAN)
    rArm = keys(lt, Q_RARM); rFore = keys(lt, Q_RFORE)
    lArm = keys(lt, Q_LARM); lFore = keys(lt, Q_LFORE)
    shrug = keys(lt, Q_SHRUG); smile = keys(lt, Q_SMILE); brow = keys(lt, Q_BROW)
    blink = max(blinkAt(lt, 0.25f), blinkAt(lt, 2.15f))
  }
  figure(a, a.shapes.cast, a.shapes.cast.seeker, pose)
  //  ذره‌بین در دستِ راست، در امتدادِ ساعد (عدسی هرگز جلوی چهره نمی‌افتد)
  val fa = pose.rArm + pose.rFore - pose.lean
  val lx = pose.rhX + 8.5f * sinD(fa)
  val ly = pose.rhY + 8.5f * cosD(fa)
  drawLine(p.line, a.o(pose.rhX, pose.rhY), a.o(pose.rhX + 2.6f * sinD(fa), pose.rhY + 2.6f * cosD(fa)), strokeWidth = 2.4f * a.u, cap = StrokeCap.Round)
  drawCircle(p.paper.copy(alpha = 0.88f), 6.6f * a.u, a.o(lx, ly))
  drawCircle(p.brand.copy(alpha = 0.10f), 6.6f * a.u, a.o(lx, ly))
  drawCircle(p.brand, 6.6f * a.u, a.o(lx, ly), style = Stroke(width = 2.2f * a.u))
  drawArc(p.paper, 200f, 60f, false, a.o(lx - 4.6f, ly - 4.6f), a.s(9.2f, 9.2f), style = Stroke(width = 1.1f * a.u, cap = StrokeCap.Round))
  question(a, pose.headX + 10f, pose.headY - 12f, keys(lt, Q_MARK), p.brand)
  spark(a, 18f, 30f, max(0f, wave(a.t, 6, 0f)), p.warm)
  spark(a, 82f, 36f, max(0f, wave(a.t, 6, 2.1f)), p.sky)
}

/* ------------------------------- بی اینترنت: دوشاخهٔ کشیده ------------------------------- */

private val C_RARM = floatArrayOf(0f, 25f, 0.3f, 75f, 1f, 75f, 1.3f, 40f, 3.4f, 40f, 3.7f, 25f)
private val C_RFORE = floatArrayOf(0f, 40f, 0.3f, 95f, 1f, 95f, 1.3f, 90f, 3.4f, 90f, 3.7f, 40f)
private val C_TILT = floatArrayOf(0f, 0f, 0.3f, 8f, 1f, 8f, 1.3f, -6f, 2.6f, -6f, 2.9f, 0f)
private val C_LOOK = floatArrayOf(0f, 0.3f, 0.3f, 1f, 1f, 1f, 1.3f, 0f, 2.6f, 0f, 2.9f, 1f, 3.4f, 1f, 3.7f, 0.3f)
private val C_LOOKY = floatArrayOf(0f, 0f, 0.3f, 0.4f, 1f, 0.4f, 1.3f, -0.4f, 2.6f, -0.4f, 2.9f, 0f)
private val C_LARM = floatArrayOf(0f, -12f, 1.2f, -12f, 1.45f, -170f, 2.6f, -170f, 2.85f, -12f)
private val C_LFORE = floatArrayOf(0f, 0f, 1.2f, 0f, 1.45f, -30f, 2.6f, -30f, 2.85f, 0f)
private val C_SCRATCH = floatArrayOf(0f, 0f, 1.45f, 0f, 1.55f, 1f, 2.5f, 1f, 2.6f, 0f)
private val C_SMILE = floatArrayOf(0f, 0.2f, 1.3f, 0.2f, 1.5f, -0.3f, 2.6f, -0.3f, 2.9f, 0.2f)
private val C_BROW = floatArrayOf(0f, 0.3f, 1.3f, 0.3f, 1.5f, 1f, 2.6f, 1f, 2.9f, 0.3f)
private val C_MARK = floatArrayOf(0f, 0f, 1.4f, 0f, 1.6f, 1f, 2.5f, 1f, 2.7f, 0f)

/** کسی دوشاخهٔ کشیده را در دست دارد، نگاهش می‌کند و سرش را می‌خاراند؛ پریز آن‌سوتر — بی اینترنت */
internal fun DrawScope.offlineCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 4f)
  stage(a, 0f, groundW = 70f)
  //  پریزِ دیوار
  softShadow(a) { c -> drawRoundRect(c, a.o(74f, 46f), a.s(14f, 18f), a.r(3f)) }
  drawRoundRect(p.face, a.o(74f, 46f), a.s(14f, 18f), a.r(3f))
  drawRoundRect(p.line.copy(alpha = 0.3f), a.o(74f, 46f), a.s(14f, 18f), a.r(3f), style = Stroke(width = 0.8f * a.u))
  drawRoundRect(p.ink.copy(alpha = 0.5f), a.o(77.5f, 51f), a.s(2f, 6.5f), a.r(0.8f))
  drawRoundRect(p.ink.copy(alpha = 0.5f), a.o(82.5f, 51f), a.s(2f, 6.5f), a.r(0.8f))
  val scratch = keys(lt, C_SCRATCH) * 14f * sin(lt * TAU * 5f)
  val pose = a.shapes.pose.apply {
    reset()
    x = 42f; ground = 89f
    breathe = breath(lt, 4f)
    tilt = keys(lt, C_TILT); look = keys(lt, C_LOOK); lookY = keys(lt, C_LOOKY)
    rArm = keys(lt, C_RARM); rFore = keys(lt, C_RFORE)
    lArm = keys(lt, C_LARM); lFore = keys(lt, C_LFORE) + scratch
    smile = keys(lt, C_SMILE); brow = keys(lt, C_BROW)
    blink = max(blinkAt(lt, 1.15f), blinkAt(lt, 3.2f))
  }
  //  سیم: از دوشاخه پایین تا زمین و بیرون
  val cable = a.shapes.cableA
  cable.reset()
  cable.moveTo(pose.rhX * a.u, (pose.rhY + 1f) * a.u)
  cable.cubicTo((pose.rhX - 2f) * a.u, 84f * a.u, 30f * a.u, 90f * a.u, 6f * a.u, 87f * a.u)
  figure(a, a.shapes.cast, a.shapes.cast.offline, pose)
  drawPath(cable, p.line.copy(alpha = 0.8f), style = Stroke(width = 1.8f * a.u, cap = StrokeCap.Round))
  //  دوشاخه در دست
  drawRoundRect(p.brand, a.o(pose.rhX - 2.6f, pose.rhY - 6f), a.s(5.2f, 6.4f), a.r(1.4f))
  drawRoundRect(p.line, a.o(pose.rhX - 2f, pose.rhY - 9f), a.s(1.2f, 3.2f), a.r(0.5f))
  drawRoundRect(p.line, a.o(pose.rhX + 0.8f, pose.rhY - 9f), a.s(1.2f, 3.2f), a.r(0.5f))
  drawCircle(a.shapes.cast.offline.skin, 2.2f * a.u, a.o(pose.rhX, pose.rhY))
  val zap = max(0f, wave(a.t, 6, 0.7f))
  if (zap > 0.6f) spark(a, pose.rhX + 0.2f, pose.rhY - 11f, (zap - 0.6f) * 2.5f, p.warm)
  question(a, pose.headX + 11f, pose.headY - 11f, keys(lt, C_MARK), p.brand)
}

/* ------------------------------- سرور: ابر و چتر ------------------------------- */

private val U_LOOK = floatArrayOf(0f, 0f, 0.4f, -0.8f, 1.9f, -0.8f, 2.05f, 0f, 2.9f, 0f, 3.2f, -0.5f, 3.8f, -0.5f, 4f, 0f)
private val U_LOOKY = floatArrayOf(0f, 0f, 0.4f, -0.7f, 1.9f, -0.7f, 2.05f, 0f, 2.9f, 0f, 3.2f, -0.6f, 3.8f, -0.6f, 4f, 0f)
private val U_SHRUG = floatArrayOf(0f, 0f, 1.95f, 0f, 2.05f, 1f, 2.5f, 1f, 2.8f, 0f)
private val U_LEAN = floatArrayOf(0f, 0f, 1.95f, 0f, 2.05f, 6f, 2.5f, 6f, 2.8f, 0f)
private val U_OPEN = floatArrayOf(0f, 0f, 2.4f, 0f, 2.5f, 0.9f, 2.8f, 0.9f, 2.95f, 0f)

/** ابرِ سرور با آذرخشِ کوچک می‌بارد؛ کسی زیرِ چتر منتظر است، با برقِ آذرخش جا می‌خورد و دوباره بالا را نگاه می‌کند */
internal fun DrawScope.serverCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 4f)
  stage(a, 0f, groundW = 60f)
  val flash = max(max(0f, 1f - abs(lt - 2f) / 0.12f), 0.6f * max(0f, 1f - abs(lt - 2.22f) / 0.08f))
  //  باران از ابر تا زمین؛ زیرِ سایبانِ چتر خشک است
  val tipX0 = 53f
  val tipY0 = 28f
  for (i in 0..9) {
    val f = frac(a.t, 12, i / 10f)
    val x = 14f + i * 7.6f - 3f * f
    val y = 34f + 52f * f
    if (x in (tipX0 - 17f)..(tipX0 + 17f) && y > tipY0 - 6f) continue
    drawLine(p.sky.copy(alpha = 0.55f * (1f - f * 0.6f)), a.o(x, y), a.o(x - 0.8f, y + 3.4f), strokeWidth = 1.2f * a.u, cap = StrokeCap.Round)
  }
  drawOval(p.sky.copy(alpha = 0.18f), a.o(12f, 87f), a.s(26f, 3.4f))
  drawOval(p.sky.copy(alpha = 0.14f), a.o(76f, 87.5f), a.s(14f, 2.6f))
  //  ابر و آذرخش (همان مسیرها، کوچک‌تر و بالا-چپ)
  withTransform({
    translate(-16f * a.u, -18f * a.u)
    scale(0.74f, 0.74f, a.o(50f, 45f))
  }) {
    if (flash > 0.05f) drawCircle(p.warm.copy(alpha = 0.3f * flash), 16f * a.u, a.o(52f, 70f))
    drawPath(a.shapes.bolt, lerp(lerp(p.warm, p.paper, 0.3f), p.warm, flash))
    softShadow(a) { c -> drawPath(a.shapes.cloud, c) }
    drawPath(a.shapes.cloud, lerp(p.paper, p.sky, 0.14f + 0.2f * flash))
    drawPath(a.shapes.cloud, p.line.copy(alpha = 0.22f), style = Stroke(width = 1.1f * a.u))
    val eye = Stroke(width = 1.4f * a.u, cap = StrokeCap.Round)
    drawArc(p.line, 20f, 140f, false, a.o(40f, 45f), a.s(6f, 4f), style = eye)
    drawArc(p.line, 20f, 140f, false, a.o(54f, 45f), a.s(6f, 4f), style = eye)
  }
  val pose = a.shapes.pose.apply {
    reset()
    x = 62f; ground = 89f
    breathe = breath(lt, 4f)
    look = keys(lt, U_LOOK); lookY = keys(lt, U_LOOKY)
    shrug = keys(lt, U_SHRUG); lean = keys(lt, U_LEAN); open = keys(lt, U_OPEN)
    blink = if (lt in 1.98f..2.4f) 1f else blinkAt(lt, 0.9f)
    lArm = -60f; lFore = 230f
    rArm = 14f; rFore = 0f
    smile = 0.1f
  }
  figure(a, a.shapes.cast, a.shapes.cast.rainy, pose)
  //  چتر: دسته در دستِ چپ، سایبان بالای سر و کمی رو به ابر
  val tipX = tipX0 + 0.8f * wind(a.t, 0.4f)
  val tipY = tipY0
  drawLine(p.line, a.o(pose.lhX, pose.lhY + 2.6f), a.o(tipX, tipY), strokeWidth = 1.3f * a.u, cap = StrokeCap.Round)
  drawArc(p.line, 0f, 180f, false, a.o(pose.lhX - 0.2f, pose.lhY + 1.6f), a.s(3.2f, 3.2f), style = Stroke(width = 1.2f * a.u, cap = StrokeCap.Round))
  drawCircle(a.shapes.cast.rainy.skin, 2.2f * a.u, a.o(pose.lhX, pose.lhY))
  turn(a, -10f + 4f * wind(a.t, 0.4f), tipX, tipY) {
    drawArc(p.coral, 180f, 180f, true, a.o(tipX - 17f, tipY - 9f), a.s(34f, 18f))
    drawArc(lerp(p.coral, p.paper, 0.35f), 180f, 180f, true, a.o(tipX - 5.5f, tipY - 9f), a.s(11f, 18f))
    for (k in 0..3) drawArc(p.coral, 0f, 180f, true, a.o(tipX - 17f + k * 8.5f, tipY - 2.2f), a.s(8.5f, 4.4f))
    drawCircle(p.line, 0.9f * a.u, a.o(tipX, tipY - 9.4f))
  }
}

/* ------------------------------- پیش‌فرض: کتاب‌خوان زیرِ درخت ------------------------------- */

/** کسی پشت به تنهٔ درختِ تاب‌خور نشسته و کتاب می‌خواند؛ ورق می‌زند و برگی از درخت روی کتاب می‌افتد */
internal fun DrawScope.readerCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 6f)
  stage(a, 0f, groundW = 78f)
  withTransform({
    translate(-18f * a.u, 0f)
    scale(0.86f, 0.86f, a.o(50f, 88f))
  }) { treeBody(a) }
  val pose = a.shapes.pose.apply {
    reset()
    x = 46f; ground = 89f; sit = true
    breathe = breath(lt, 3f)
    lean = -10f
    tilt = 8f; lookY = 0.85f; look = 0.25f + 0.3f * sin(lt / 1.5f * TAU)
    smile = 0.45f
    blink = max(blinkAt(lt, 1.2f), blinkAt(lt, 4.4f))
    lLeg = 84f; lKnee = -4f
    rLeg = 78f; rKnee = 8f
    lArm = 40f; lFore = 70f
    rArm = 22f; rFore = 100f
  }
  figure(a, a.shapes.cast, a.shapes.cast.reader, pose)
  //  کتاب روی دست‌ها
  val cx = (pose.lhX + pose.rhX) / 2f + 1f
  val cy = (pose.lhY + pose.rhY) / 2f - 3f
  turn(a, -12f, cx, cy) {
    drawRoundRect(lerp(p.leaf, p.ink, 0.3f), a.o(cx - 9f, cy - 6f), a.s(18f, 11f), a.r(1.2f))
    drawRoundRect(p.paper, a.o(cx - 8.3f, cy - 6.4f), a.s(8.1f, 10f), a.r(0.8f))
    drawRoundRect(p.paper, a.o(cx + 0.2f, cy - 6.4f), a.s(8.1f, 10f), a.r(0.8f))
    for (i in 0..2) drawLine(p.muted.copy(alpha = 0.3f), a.o(cx - 6.5f, cy - 3.5f + i * 2.6f), a.o(cx - 1.5f, cy - 3.5f + i * 2.6f), strokeWidth = 0.5f * a.u)
    val f = when {
      lt in 2.6f..3f -> (lt - 2.6f) / 0.4f
      lt in 5.2f..5.6f -> (lt - 5.2f) / 0.4f
      else -> -1f
    }
    if (f >= 0f) {
      val sx = kotlin.math.cos(f * PI.toFloat())
      withTransform({ scale(if (abs(sx) < 0.05f) 0.05f else sx, 1f, a.o(cx, cy)) }) {
        drawRoundRect(lerp(p.paper, p.muted, 0.12f), a.o(cx + 0.2f, cy - 6.4f), a.s(8.1f, 10f), a.r(0.8f))
      }
    }
  }
  drawCircle(a.shapes.cast.reader.skin, 2.2f * a.u, a.o(pose.lhX, pose.lhY))
  drawCircle(a.shapes.cast.reader.skin, 2.2f * a.u, a.o(pose.rhX, pose.rhY))
  //  برگی که از درخت روی کتاب می‌افتد (هر ۶ ثانیه)
  val f = frac(a.t, 4, 0.1f)
  val lx = 30f + (cx - 30f) * f + 4f * sin(f * TAU * 1.5f)
  val ly = 34f + (cy - 34f) * f
  leaf(a, lx, ly, 300f * f, 4f, p.leafSoft.copy(alpha = p.leafSoft.alpha * sin(PI.toFloat() * f)))
}

/* ------------------------------- خطای دیگر: «!» ------------------------------- */

private val M_SHRUG = floatArrayOf(0f, 0f, 2.6f, 0f, 2.8f, 1f, 3.3f, 1f, 3.55f, 0f)
private val M_LARM = floatArrayOf(0f, -10f, 2.6f, -10f, 2.8f, -40f, 3.3f, -40f, 3.55f, -10f)
private val M_LFORE = floatArrayOf(0f, 165f, 2.6f, 165f, 2.8f, -78f, 3.3f, -78f, 3.55f, 165f)
private val M_RARM = floatArrayOf(0f, 14f, 2.6f, 14f, 2.8f, 40f, 3.3f, 40f, 3.55f, 14f)
private val M_RFORE = floatArrayOf(0f, 0f, 2.6f, 0f, 2.8f, 78f, 3.3f, 78f, 3.55f, 0f)
private val M_TILT = floatArrayOf(0f, -8f, 2.6f, -8f, 2.8f, 6f, 3.3f, 6f, 3.55f, -8f)

/** کسی دست به چانه به بادکنکِ «!» نگاه می‌کند و سپس شانه بالا می‌اندازد */
internal fun DrawScope.markCast(a: Art) {
  val p = a.p
  val lt = loop(a.t, 4f)
  stage(a, 0f, groundW = 56f)
  val by = 34f + 2f * sin(lt / 4f * TAU * 2f)
  turn(a, 6f * wind(a.t, 0.7f), 28f, by + 12f) {
    val fill = lerp(p.danger, p.paper, 0.82f)
    softShadow(a) { c -> drawCircle(c, 10f * a.u, a.o(28f, by)) }
    drawCircle(fill, 10f * a.u, a.o(28f, by))
    drawCircle(p.danger.copy(alpha = 0.45f), 10f * a.u, a.o(28f, by), style = Stroke(width = 1f * a.u))
    drawRoundRect(p.danger, a.o(26.8f, by - 6f), a.s(2.4f, 7.5f), a.r(1.2f))
    drawCircle(p.danger, 1.4f * a.u, a.o(28f, by + 4f))
  }
  val pose = a.shapes.pose.apply {
    reset()
    x = 60f; ground = 89f
    breathe = breath(lt, 4f)
    tilt = keys(lt, M_TILT); look = -1f; lookY = -0.4f
    shrug = keys(lt, M_SHRUG)
    lArm = keys(lt, M_LARM); lFore = keys(lt, M_LFORE)
    rArm = keys(lt, M_RARM); rFore = keys(lt, M_RFORE)
    smile = if (shrug > 0.5f) -0.2f else 0f; brow = 0.6f + 0.4f * shrug
    blink = max(blinkAt(lt, 0.8f), blinkAt(lt, 2.2f))
  }
  figure(a, a.shapes.cast, a.shapes.cast.puzzled, pose)
}
