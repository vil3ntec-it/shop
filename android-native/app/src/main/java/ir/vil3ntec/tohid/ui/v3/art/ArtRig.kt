package ir.vil3ntec.tohid.ui.v3.art

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 *  اسکلتِ آدمک‌ها (دستهٔ ۶۴، خواستِ صاحب‌کار «زنده باشند، جذاب باشند»): سر، تنه و دست‌وپای دوتکه که هر کدام به دورِ
 *  مفصلِ خودش می‌چرخد؛ ژست در هر فریم از خطِ زمانیِ کلیددار ([keys]، نرمیِ smoothstep میانِ کلیدها) پر می‌شود، با
 *  پلک ([blinkAt]) و نفس. همه با حساب (سینوس/کسینوس) و بی ساختنِ شیءِ تازه: یک [Pose] در کشِ رسم، دوباره پر می‌شود.
 *  زاویه‌ها درجه، در صفحه: صفر = رو به پایین، ۹۰ = رو به راست، ۱۸۰ = رو به بالا؛ `lean` چرخشِ تنه به دورِ لگن (مثبت ⇒
 *  سر به راست). شخصیت‌ها طرحِ خودِ برنامه‌اند (رنگ از توکن‌های تم)، نه کپیِ نمونه‌ای.
 */

private const val RAD = (PI / 180.0).toFloat()
internal fun sinD(d: Float) = sin(d * RAD)
internal fun cosD(d: Float) = cos(d * RAD)

/** زمانِ درونِ چرخهٔ [sec] ثانیه‌ایِ صحنه (چرخهٔ ۲۴ ثانیه‌ایِ ساعت بخش‌پذیر بر آن است ⇒ بی پرش) */
internal fun loop(t: Float, sec: Float): Float {
  val x = t % sec
  return if (x < 0f) x + sec else x
}

/** خطِ زمانی: `[t0, v0, t1, v1, …]` — پیش از نخستین و پس از آخرین کلید همان مقدار؛ میانشان نرم (smoothstep) */
internal fun keys(lt: Float, k: FloatArray): Float {
  if (lt <= k[0]) return k[1]
  var i = 0
  while (i + 2 < k.size) {
    val t1 = k[i + 2]
    if (lt <= t1) {
      val f = (lt - k[i]) / (t1 - k[i])
      val e = f * f * (3f - 2f * f)
      return k[i + 1] + (k[i + 3] - k[i + 1]) * e
    }
    i += 2
  }
  return k[k.size - 1]
}

/** پلکِ کوتاه (۰ تا ۱ تا ۰ در ۰٫۱۸ ثانیه) در لحظهٔ [at] */
internal fun blinkAt(lt: Float, at: Float): Float = max(0f, 1f - abs(lt - at) / 0.09f)

/** نفسِ آرام (−۱ تا ۱) در هر چرخه */
internal fun breath(lt: Float, sec: Float): Float = sin(lt / sec * 2f * PI.toFloat())

/** مدلِ مو و کلاه */
internal object Hair {
  const val SHORT = 0
  const val BUN = 1
  const val CAP = 2
  const val HARDHAT = 3
  const val HELMET = 4
  const val BEANIE = 5
  const val CURLY = 6
  const val PONYTAIL = 7
}

/** ظاهرِ یک آدمک */
internal class Look(
  val skin: Color,
  val hair: Color,
  val top: Color,
  val bottom: Color,
  val accent: Color,
  val style: Int,
  val apron: Boolean = false,
  val glasses: Boolean = false,
  val sleeves: Boolean = true,
)

/** رنگ‌های بازیگران از پالتِ تم: پوست، مو و لباس — در شب همان آدم‌اند، فقط زمینه تیره است */
internal class Cast(p: V3ArtPalette) {
  private val peach = lerp(p.warm, p.coral, 0.3f)
  val skinLight = lerp(peach, Color.White, 0.58f)
  val skinMid = lerp(peach, Color.White, 0.28f)
  val skinDeep = lerp(lerp(p.warm, p.coral, 0.45f), Color.Black, 0.38f)
  val hairDark = lerp(lerp(p.warm, p.coral, 0.5f), Color.Black, 0.78f)
  val hairBrown = lerp(lerp(p.warm, p.coral, 0.5f), Color.Black, 0.5f)
  val hairAuburn = lerp(p.coral, Color.Black, 0.32f)
  val denim = lerp(p.sky, Color.Black, 0.42f)
  val charcoal = lerp(p.violet, Color.Black, 0.62f)
  val shoe = lerp(lerp(p.warm, p.coral, 0.5f), Color.Black, 0.7f)
  /** خطِ چهره روی پوست — همیشه تیره، در هر تم */
  val face = lerp(lerp(p.warm, p.coral, 0.5f), Color.Black, 0.8f)
  val blush = p.coral.copy(alpha = 0.3f)
  val white = Color.White

  val shopkeeper = Look(skinMid, hairDark, p.leaf, denim, p.brand, Hair.CAP, apron = true)
  val cashier = Look(skinLight, hairAuburn, p.brand, charcoal, p.warm, Hair.BUN)
  val lender = Look(skinDeep, hairDark, p.violet, denim, p.warm, Hair.SHORT, glasses = true)
  val courier = Look(skinMid, hairBrown, p.coral, charcoal, p.brand, Hair.HELMET)
  val worker = Look(skinDeep, hairDark, p.sky, charcoal, p.warm, Hair.HARDHAT, apron = true, sleeves = false)
  val seeker = Look(skinLight, hairBrown, p.warm, denim, p.brand, Hair.CURLY, glasses = true)
  val offline = Look(skinMid, hairDark, p.violet, charcoal, p.coral, Hair.BEANIE)
  val rainy = Look(skinLight, hairAuburn, p.warm, denim, p.sky, Hair.SHORT)
  val reader = Look(skinMid, hairBrown, p.coral, denim, p.leaf, Hair.PONYTAIL)
  val puzzled = Look(skinDeep, hairDark, p.sky, charcoal, p.violet, Hair.SHORT)
}

/** ژستِ یک فریم (یک شیء در کش؛ [reset] پیش از هر فریم) و جای دست‌ها و سر پس از رسم */
internal class Pose {
  var x = 50f
  var ground = 88f
  var bob = 0f
  var lean = 0f
  var sit = false
  /** پاها پیدا؟ (پشتِ پیشخوان نه) */
  var legs = true
  var tilt = 0f
  var look = 0f
  var lookY = 0f
  var blink = 0f
  var smile = 0.35f
  var happy = 0f
  var brow = 0f
  var open = 0f
  var breathe = 0f
  var shrug = 0f
  var lArm = -12f
  var lFore = 0f
  var rArm = 12f
  var rFore = 0f
  var lLeg = -3f
  var lKnee = 0f
  var rLeg = 3f
  var rKnee = 0f
  //  خروجی
  var lhX = 0f
  var lhY = 0f
  var rhX = 0f
  var rhY = 0f
  var headX = 0f
  var headY = 0f

  fun reset() {
    x = 50f; ground = 88f; bob = 0f; lean = 0f; sit = false; legs = true; tilt = 0f; look = 0f; lookY = 0f; blink = 0f
    smile = 0.35f; happy = 0f; brow = 0f; open = 0f; breathe = 0f; shrug = 0f
    lArm = -12f; lFore = 0f; rArm = 12f; rFore = 0f; lLeg = -3f; lKnee = 0f; rLeg = 3f; rKnee = 0f
  }
}

private const val THIGH = 8.6f
private const val SHIN = 8.2f
private const val UPPER = 7.8f
private const val FORE = 7.2f
private const val TORSO = 17.5f
internal const val HEAD_R = 7.2f

/** یک پا از مفصلِ (jx, jy) */
private fun DrawScope.leg(a: Art, look: Look, jx: Float, jy: Float, h: Float, k: Float, side: Float, shoe: Color) {
  val kx = jx + THIGH * sinD(h)
  val ky = jy + THIGH * cosD(h)
  val fx = kx + SHIN * sinD(h + k)
  val fy = ky + SHIN * cosD(h + k)
  drawLine(look.bottom, a.o(jx, jy), a.o(kx, ky), strokeWidth = 4.8f * a.u, cap = StrokeCap.Round)
  drawLine(look.bottom, a.o(kx, ky), a.o(fx, fy), strokeWidth = 4.3f * a.u, cap = StrokeCap.Round)
  drawOval(shoe, a.o(fx - 2.7f + side * 1.1f, fy - 1.3f), a.s(5.4f, 3f))
}

/**
 *  یک آدمک: پاها، تنه (با نفس و بالا انداختنِ شانه)، گردن و سر (با کج کردن)، و دو دست در آخر — تا دستی که سر را
 *  می‌خاراند یا چیزی جلوی تن نگه می‌دارد رو باشد. جای دست‌ها و سر در [Pose] می‌ماند برای چیزهایی که صحنه در دست می‌دهد.
 */
internal fun DrawScope.figure(a: Art, cast: Cast, look: Look, p: Pose) {
  val hipX = p.x
  val hipY = if (p.sit) p.ground - 3.2f + p.bob else p.ground - (THIGH + SHIN) + 0.8f + p.bob
  if (p.legs) {
    leg(a, look, hipX - 3f, hipY, p.lLeg, p.lKnee, -1f, cast.shoe)
    leg(a, look, hipX + 3f, hipY, p.rLeg, p.rKnee, 1f, cast.shoe)
  }

  val c = cosD(p.lean)
  val s = sinD(p.lean)
  val lift = p.shrug * 1.8f + p.breathe * 0.25f
  //  تنه
  withTransform({
    rotate(p.lean, a.o(hipX, hipY))
    scale(1f, 1f + 0.016f * p.breathe, a.o(hipX, hipY))
  }) {
    drawRoundRect(look.top, a.o(hipX - 7.2f, hipY - TORSO - 1.4f), a.s(14.4f, TORSO + 3.6f), a.r(5.6f))
    drawRoundRect(lerp(look.top, cast.white, 0.22f), a.o(hipX - 2.4f, hipY - TORSO - 1.2f), a.s(4.8f, 2.6f), a.r(1.3f))
    if (look.apron) {
      drawRoundRect(look.accent, a.o(hipX - 5.4f, hipY - 12.5f), a.s(10.8f, 14.5f), a.r(2.6f))
      drawRoundRect(lerp(look.accent, cast.white, 0.35f), a.o(hipX - 2.6f, hipY - 6.5f), a.s(5.2f, 3.4f), a.r(1f))
    }
  }
  fun rx(dx: Float, dy: Float) = hipX + dx * c - dy * s
  fun ry(dx: Float, dy: Float) = hipY + dx * s + dy * c

  //  گردن و سر
  val nx = rx(0f, -TORSO - 0.4f)
  val ny = ry(0f, -TORSO - 0.4f)
  drawLine(look.skin, a.o(nx, ny + 1f), a.o(nx, ny - 1.6f), strokeWidth = 3.4f * a.u, cap = StrokeCap.Round)
  val turn = p.lean + p.tilt
  val hx = nx + (HEAD_R + 0.8f) * sinD(turn)
  val hy = ny - (HEAD_R + 0.8f) * cosD(turn)
  p.headX = hx
  p.headY = hy
  withTransform({ rotate(turn, a.o(hx, hy)) }) { head(a, cast, look, p, hx, hy) }

  //  دست‌ها
  val sy = -TORSO + 2f - lift
  arm(a, cast, look, rx(-6.4f, sy), ry(-6.4f, sy), p.lArm - p.lean, p.lFore, p, left = true)
  arm(a, cast, look, rx(6.4f, sy), ry(6.4f, sy), p.rArm - p.lean, p.rFore, p, left = false)
}

private fun DrawScope.arm(a: Art, cast: Cast, look: Look, sx: Float, sy: Float, ang: Float, fore: Float, p: Pose, left: Boolean) {
  val ex = sx + UPPER * sinD(ang)
  val ey = sy + UPPER * cosD(ang)
  val hx = ex + FORE * sinD(ang + fore)
  val hy = ey + FORE * cosD(ang + fore)
  drawLine(look.top, a.o(sx, sy), a.o(ex, ey), strokeWidth = 4f * a.u, cap = StrokeCap.Round)
  drawLine(if (look.sleeves) look.top else look.skin, a.o(ex, ey), a.o(hx, hy), strokeWidth = 3.5f * a.u, cap = StrokeCap.Round)
  drawCircle(look.skin, 2.2f * a.u, a.o(hx, hy))
  drawCircle(cast.face.copy(alpha = 0.12f), 2.2f * a.u, a.o(hx, hy), style = Stroke(width = 0.5f * a.u))
  if (left) { p.lhX = hx; p.lhY = hy } else { p.rhX = hx; p.rhY = hy }
}

/** سر در (hx, hy): گوش، چهره، مو/کلاه، چشم (پلک، نگاه، شاد)، ابرو، گونه و دهان */
private fun DrawScope.head(a: Art, cast: Cast, look: Look, p: Pose, hx: Float, hy: Float) {
  val r = HEAD_R
  if (look.style == Hair.PONYTAIL) {
    drawOval(look.hair, a.o(hx + 5.2f, hy - 5f), a.s(4.6f, 10f))
    drawCircle(look.accent, 1.1f * a.u, a.o(hx + 6.6f, hy - 4.6f))
  }
  if (look.style == Hair.CURLY) for (i in 0..6) {
    val ang = -100f + i * 33f
    drawCircle(look.hair, 2.9f * a.u, a.o(hx + 6.4f * sinD(ang + 180f), hy - 2.4f + 6.4f * cosD(ang + 180f)))
  }
  drawCircle(look.skin, 1.8f * a.u, a.o(hx - r + 0.2f, hy + 0.8f))
  drawCircle(look.skin, 1.8f * a.u, a.o(hx + r - 0.2f, hy + 0.8f))
  drawCircle(look.skin, r * a.u, a.o(hx, hy))
  hairOn(a, cast, look, hx, hy)

  val dx = p.look * 1.7f
  val eyeY = hy + 0.3f + p.lookY * 1.1f
  val ink = cast.face
  var side = -1
  while (side <= 1) {
    val ex = hx + side * 2.6f + dx
    when {
      p.happy > 0.5f -> drawArc(ink, 200f, 140f, false, a.o(ex - 1.2f, eyeY - 0.6f), a.s(2.4f, 1.8f), style = Stroke(width = 0.75f * a.u, cap = StrokeCap.Round))
      p.blink > 0.5f -> drawLine(ink, a.o(ex - 1f, eyeY), a.o(ex + 1f, eyeY), strokeWidth = 0.7f * a.u, cap = StrokeCap.Round)
      else -> {
        drawCircle(ink, 1f * a.u, a.o(ex, eyeY))
        drawCircle(cast.white.copy(alpha = 0.85f), 0.32f * a.u, a.o(ex + 0.3f, eyeY - 0.35f))
      }
    }
    if (p.brow != 0f) {
      val by = eyeY - 2.3f - p.brow * 0.7f
      drawLine(ink.copy(alpha = 0.8f), a.o(ex - 1.1f, by + side * p.brow * 0.25f), a.o(ex + 1.1f, by - side * p.brow * 0.25f), strokeWidth = 0.6f * a.u, cap = StrokeCap.Round)
    }
    drawCircle(cast.blush, 1.3f * a.u, a.o(hx + side * 4.3f + dx * 0.5f, hy + 2.6f))
    side += 2
  }
  if (look.glasses) {
    val g = Stroke(width = 0.55f * a.u)
    drawCircle(ink.copy(alpha = 0.85f), 2.1f * a.u, a.o(hx - 2.6f + dx, eyeY), style = g)
    drawCircle(ink.copy(alpha = 0.85f), 2.1f * a.u, a.o(hx + 2.6f + dx, eyeY), style = g)
    drawLine(ink.copy(alpha = 0.85f), a.o(hx - 0.5f + dx, eyeY), a.o(hx + 0.5f + dx, eyeY), strokeWidth = 0.55f * a.u)
  }
  val mx = hx + dx * 0.8f
  val my = hy + 3.7f + p.lookY * 0.5f
  val line = Stroke(width = 0.75f * a.u, cap = StrokeCap.Round)
  when {
    p.open > 0.3f -> drawOval(ink, a.o(mx - 0.9f, my - 0.7f), a.s(1.8f, 1.2f + 1.2f * p.open))
    p.smile >= 0f -> drawArc(ink, 20f, 140f, false, a.o(mx - 2f, my - 1.2f - p.smile * 0.6f), a.s(4f, 1.6f + p.smile * 1.6f), style = line)
    else -> drawArc(ink, 205f, 130f, false, a.o(mx - 1.7f, my - 0.2f), a.s(3.4f, 1.4f - p.smile * 1f), style = line)
  }
}

private fun DrawScope.hairOn(a: Art, cast: Cast, look: Look, hx: Float, hy: Float) {
  val r = HEAD_R
  when (look.style) {
    Hair.SHORT, Hair.BUN, Hair.PONYTAIL -> {
      drawArc(look.hair, 180f, 180f, true, a.o(hx - r - 0.3f, hy - r - 0.4f), a.s(2f * r + 0.6f, 2f * r * 0.78f))
      drawOval(look.hair, a.o(hx - r + 0.6f, hy - r + 1.4f), a.s(r * 1.15f, 3.4f))
      if (look.style == Hair.BUN) drawCircle(look.hair, 3.1f * a.u, a.o(hx + 0.8f, hy - r - 1.6f))
    }
    Hair.CURLY -> drawArc(look.hair, 180f, 180f, true, a.o(hx - r, hy - r - 0.6f), a.s(2f * r, 2f * r * 0.7f))
    Hair.CAP -> {
      drawOval(look.hair, a.o(hx - r - 0.2f, hy - 3.2f), a.s(3.2f, 4f))
      drawArc(look.accent, 180f, 180f, true, a.o(hx - r - 0.4f, hy - r - 1f), a.s(2f * r + 0.8f, 2f * r * 0.8f))
      drawRoundRect(lerp(look.accent, cast.face, 0.25f), a.o(hx - 1.5f, hy - 3.9f), a.s(r + 3.8f, 2.2f), a.r(1.1f))
      drawCircle(lerp(look.accent, cast.white, 0.4f), 0.9f * a.u, a.o(hx, hy - r - 0.7f))
    }
    Hair.HARDHAT -> {
      drawArc(look.accent, 180f, 180f, true, a.o(hx - r - 0.9f, hy - r - 2f), a.s(2f * r + 1.8f, 2f * r * 0.92f))
      drawRoundRect(lerp(look.accent, cast.face, 0.18f), a.o(hx - r - 2.2f, hy - 3.6f), a.s(2f * r + 4.4f, 2.2f), a.r(1.1f))
      drawLine(lerp(look.accent, cast.white, 0.45f), a.o(hx, hy - r - 1.6f), a.o(hx, hy - 4f), strokeWidth = 1.1f * a.u, cap = StrokeCap.Round)
    }
    Hair.HELMET -> {
      drawArc(look.accent, 160f, 220f, true, a.o(hx - r - 1.2f, hy - r - 1.6f), a.s(2f * r + 2.4f, 2f * r + 1.6f))
      drawRoundRect(lerp(look.accent, cast.white, 0.55f).copy(alpha = 0.85f), a.o(hx - r + 0.4f, hy - 3.6f), a.s(2f * r - 0.8f, 2.6f), a.r(1.3f))
      drawCircle(look.skin, (r - 1.6f) * a.u, a.o(hx, hy + 1.6f))
    }
    Hair.BEANIE -> {
      drawArc(look.accent, 180f, 180f, true, a.o(hx - r - 0.5f, hy - r - 2.4f), a.s(2f * r + 1f, 2f * r * 0.95f))
      drawRoundRect(lerp(look.accent, cast.face, 0.22f), a.o(hx - r - 0.8f, hy - 4.4f), a.s(2f * r + 1.6f, 3f), a.r(1.4f))
      drawCircle(lerp(look.accent, cast.white, 0.35f), 2.1f * a.u, a.o(hx, hy - r - 2.6f))
    }
  }
}

/** علامتِ سؤالِ کشیده (کمان، ساقه و نقطه) — نوشته نیست، شکل است */
internal fun DrawScope.question(a: Art, x: Float, y: Float, k: Float, color: Color) {
  if (k <= 0.02f) return
  val s = 4.2f * k
  val st = Stroke(width = 1.5f * k * a.u, cap = StrokeCap.Round)
  drawArc(color, 200f, 250f, false, a.o(x - s, y - s * 1.6f), a.s(2f * s, 2f * s), style = st)
  drawLine(color, a.o(x + s * 0.15f, y + s * 0.35f), a.o(x, y + s * 0.95f), strokeWidth = 1.5f * k * a.u, cap = StrokeCap.Round)
  drawCircle(color, 1f * k * a.u, a.o(x, y + s * 1.6f))
}

/** حبابِ فکر با سه نقطه */
internal fun DrawScope.thought(a: Art, x: Float, y: Float, k: Float, fill: Color, dots: Color) {
  if (k <= 0.02f) return
  drawCircle(fill.copy(alpha = fill.alpha * k), 1.2f * a.u, a.o(x - 4.5f, y + 6f))
  drawCircle(fill.copy(alpha = fill.alpha * k), 1.9f * a.u, a.o(x - 2.6f, y + 3.4f))
  drawRoundRect(fill.copy(alpha = fill.alpha * k), a.o(x - 2f, y - 4f), a.s(13f * k, 7.4f), a.r(3.7f))
  for (i in 0..2) drawCircle(dots.copy(alpha = k), 0.9f * a.u, a.o(x + 1.8f + i * 3f * k, y - 0.3f))
}
