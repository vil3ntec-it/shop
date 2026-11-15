package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import ir.vil3ntec.tohid.ui.theme.LightColors
import ir.vil3ntec.tohid.ui.theme.Brand
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.ui.theme.Shop
import ir.vil3ntec.tohid.ui.theme.ThemeChoice
import kotlin.math.cos
import kotlin.math.sin

/*
 *  پس‌زمینه و نشانِ صفحهٔ ورود.
 *
 *  ⛔ صاحب‌کار ۱۴۰۵/۰۸/۲۴: صفحهٔ ورودِ قدیمی (سه خط و دایرهٔ چرخان) — بی اجازهٔ صاحب‌کار دست نخورد. همان پروندهٔ
 *  `ui/screens/WelcomeDecor.kt` که در #1041 «کدِ مرده» برداشته شد، تقریباً حرف‌به‌حرف برگشت؛ فقط سه چیز عوض شد:
 *  بسته (package)، ساعت (پایین: [rememberWelcomeClock] جای `rememberInfiniteTransition` — همان زمان‌ها، فقط وقتی کسی
 *  روی همین صفحه است)، و برچسبِ آزمون. شکل، رنگ، سه خط، حباب‌ها، حلقه و سرعت‌ها همان. نگهبان: `LookGuardTest`.
 *
 *  همه‌چیز اینجا **کشیده** می‌شود، نه اینکه عکسی بارگذاری شود: چند مسیر و
 *  چند دایره روی یک `Canvas`. یک عکسِ پس‌زمینه‌ی تمام‌صفحه برای هر تراکمِ
 *  نمایشگر یک فایل می‌خواهد، چند مگابایت به برنامه اضافه می‌کند و هنگامِ
 *  باز شدن باید از حافظه رد شود. این‌ها هیچ‌کدام.
 *
 *  حرکتشان هم عمداً بسیار کند است — یک دورِ کامل در نیم دقیقه — تا صفحه
 *  زنده به نظر برسد ولی چشم را به خودش نکشد.
 */

/** زمینهٔ این صفحه — روشن، تمیز، نه نئون؛ از ۱۴۰۵/۰۷/۲۸ همان سه سطحِ روشنِ VILL3N */
private val SKY_TOP = LightColors.surfaceElevated
private val SKY_MID = LightColors.background
private val SKY_LOW = LightColors.backgroundSubtle

/*
 *  موج‌ها.
 *
 *  رنگِ قبلی آن‌قدر کم‌رنگ بود که موج از زمینه جدا نمی‌شد و فقط «یک جای
 *  کمی روشن‌تر» دیده می‌شد. حالا آبیِ سیرتری دارند و داخلشان یک بازتابِ
 *  سفید می‌افتد — همان چیزی که به سطح، حجم می‌دهد.
 */
//  VILL3N: موج‌ها تهِ سبزآبیِ برندند (پیش‌تر آبی) — آمیزهٔ سفید و برند، نه عددِ تازه
private val WAVE_SOFT = lerp(Color.White, Brand.primary, 0.22f)
private val WAVE_DEEP = lerp(Color.White, Brand.primary, 0.38f)
private val WAVE_EDGE = lerp(Color.White, Brand.primary, 0.52f)

/** بازتابِ نورِ سفید روی موج */
private val SHEEN = Color.White

/** لهجهٔ گرم — فقط چند نقطه، به‌اندازه‌ای که چشم را گرم کند */
private val ACCENT_AMBER = Brand.orange
private val ACCENT_BLUE = Brand.primary

/*
 *  رنگِ حباب‌ها.
 *
 *  همه یک آبی بودند و از دور مثلِ لکه دیده می‌شدند. حالا هر کدام رنگِ
 *  خودش را دارد — آبی، فیروزه‌ای، بنفشِ ملایم، طلایی — و دامنهٔ حرکتشان
 *  هم چند برابر شده تا واقعاً «شناور» باشند، نه ساکن.
 */
private val BUBBLE_TINTS = listOf(
  Brand.primary,   // سبزآبیِ خودِ برنامه
  Brand.blue,      // آبی
  Brand.purple,    // بنفشِ پشتیبان
  Brand.orange,    // نارنجی
  Brand.teal,      // فیروزه‌ای
)

/** برچسب‌های آزمون (`WelcomeLookUiTest`) */
object WelcomeTags {
  const val BACKGROUND = "welcome-background"
  const val MARK = "welcome-mark"
}

/**
 *  پس‌زمینهٔ صفحهٔ ورود.
 *
 *  در تمِ تاریک همان شکل‌ها کشیده می‌شوند ولی روی زمینهٔ خودِ برنامه و با
 *  رنگِ بسیار کم‌رنگ — طرحِ روشن روی تمِ تاریک، مثلِ یک وصله دیده می‌شود.
 */
@Composable
fun WelcomeBackground(theme: ThemeChoice? = null, modifier: Modifier = Modifier) {
  val colors = Shop.colors
  val dark = colors.bg.luminanceIsDark()

  //  همان دو حرکتِ پیشین: «flow» خطی ۰ تا ۱ در ۳۰ ثانیه (از نو)، «breathe» ۰٫۸۵ تا ۱٫۱۲ در ۹ ثانیه (رفت و برگشت، سینوسی)
  val clock = rememberWelcomeClock()
  val flow by WelcomeTime.derived(clock) { WelcomeTime.restart(it, 30_000) }
  val breathe by WelcomeTime.derived(clock) { WelcomeTime.reverse(it, 9_000, 0.85f, 1.12f) }

  Canvas(modifier.fillMaxSize().testTag(WelcomeTags.BACKGROUND)) {
    val w = size.width
    val h = size.height

    // ۱) زمینه — شیبِ عمودیِ بسیار ملایم
    drawRect(
      brush = if (dark) {
        Brush.verticalGradient(listOf(colors.bg, colors.surface, colors.bg))
      } else {
        Brush.verticalGradient(listOf(SKY_TOP, SKY_MID, SKY_LOW))
      },
      size = size,
    )

    val soft = if (dark) colors.primary.copy(alpha = 0.13f) else WAVE_SOFT
    val deep = if (dark) colors.primary.copy(alpha = 0.20f) else WAVE_DEEP
    val edge = if (dark) colors.primary.copy(alpha = 0.28f) else WAVE_EDGE
    val sheen = if (dark) Color.White.copy(alpha = 0.10f) else SHEEN.copy(alpha = 0.85f)

    /*
     *  ۲) دو موجِ بزرگ.
     *
     *  هر موج دو بار کشیده می‌شود: یک بار خودِ سطح با شیبِ آبی، و یک بار
     *  یک بازتابِ سفید که فقط بالای همان سطح می‌افتد. سطحِ تک‌رنگ تخت
     *  دیده می‌شود؛ چیزی که به آن حجم می‌دهد همین بازتاب است.
     */
    val top = topWave(w, h, flow)
    drawPath(
      path = top,
      brush = Brush.linearGradient(
        colors = listOf(edge, deep, soft),
        start = Offset(w, 0f),
        end = Offset(w * 0.1f, h * 0.55f),
      ),
    )
    drawPath(
      path = top,
      brush = Brush.linearGradient(
        colors = listOf(sheen, sheen.copy(alpha = 0f)),
        start = Offset(w * 0.72f, 0f),
        end = Offset(w * 0.42f, h * 0.30f),
      ),
    )

    val bottom = bottomWave(w, h, flow)
    drawPath(
      path = bottom,
      brush = Brush.linearGradient(
        colors = listOf(deep, soft, soft.copy(alpha = 0f)),
        start = Offset(0f, h),
        end = Offset(w * 0.9f, h * 0.45f),
      ),
    )
    drawPath(
      path = bottom,
      brush = Brush.linearGradient(
        colors = listOf(sheen.copy(alpha = sheen.alpha * 0.7f), sheen.copy(alpha = 0f)),
        start = Offset(w * 0.10f, h * 0.98f),
        end = Offset(w * 0.55f, h * 0.74f),
      ),
    )

    // ۳) خطوطِ نازکِ سفید — سفیدِ کامل، وگرنه روی موجِ آبی دیده نمی‌شوند
    val line = if (dark) Color.White.copy(alpha = 0.16f) else Color.White
    listOf(0f, 0.06f, 0.12f).forEachIndexed { index, shift ->
      drawPath(
        path = curveLine(w, h, flow, shift),
        color = line.copy(alpha = line.alpha * (1f - index * 0.22f)),
        style = Stroke(width = (2.0f - index * 0.35f).dp.toPx(), cap = StrokeCap.Round),
      )
    }

    /*
     *  ۴) حباب‌های شناور.
     *
     *  هر کدام رنگ و سرعتِ خودش را دارد و روی یک بیضی می‌چرخد — نه یک
     *  دایره، وگرنه همه یک‌جور می‌چرخند و حرکت مصنوعی می‌شود. دامنهٔ
     *  حرکت هم چند برابرِ قبل است؛ پیش از این آن‌قدر کم بود که تکان
     *  خوردنشان اصلاً دیده نمی‌شد.
     */
    dots.forEachIndexed { index, dot ->
      val angle = (flow * dot.speed + dot.phase) * 6.2832f
      val x = w * dot.x + cos(angle) * w * dot.sway
      val y = h * dot.y + sin(angle * 1.3f) * h * dot.sway * 0.75f
      val tint = BUBBLE_TINTS[index % BUBBLE_TINTS.size]
      val pulse = 0.85f + 0.3f * sin(angle * 0.7f)

      // هالهٔ نرمِ دورِ حباب — لبهٔ تیزِ یک دایرهٔ تنها، چسبانده به نظر می‌رسد
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(tint.copy(alpha = dot.alpha * 0.30f), tint.copy(alpha = 0f)),
          center = Offset(x, y),
          radius = dot.radius.dp.toPx() * 2.6f * breathe,
        ),
        radius = dot.radius.dp.toPx() * 2.6f * breathe,
        center = Offset(x, y),
      )
      drawCircle(
        color = tint.copy(alpha = dot.alpha * (if (dark) 0.75f else 1f)),
        radius = dot.radius.dp.toPx() * breathe * pulse,
        center = Offset(x, y),
      )
    }
  }
}

/* --------------------------- شکلِ موج‌ها --------------------------- */

/*
 *  هر موج یک مسیرِ بسته است با دو خمِ درجه‌سه. عددها نسبتِ پهنا و بلندی
 *  هستند نه نقطهٔ ثابت، پس روی هر اندازهٔ صفحه‌ای همان شکل درمی‌آید.
 */

private fun topWave(w: Float, h: Float, flow: Float): Path {
  val sway = sin(flow * 6.2832f) * h * 0.012f
  return Path().apply {
        moveTo(w * 1.02f, -h * 0.02f)
        cubicTo(
          w * 0.62f, h * 0.06f + sway,
          w * 0.74f, h * 0.34f - sway,
          w * 0.30f, h * 0.44f + sway,
        )
        cubicTo(
          w * 0.02f, h * 0.51f,
          w * -0.06f, h * 0.20f,
          w * 0.06f, -h * 0.04f,
        )
        close()
      }
}

private fun bottomWave(w: Float, h: Float, flow: Float): Path {
  val sway = cos(flow * 6.2832f) * h * 0.014f
  return Path().apply {
        moveTo(-w * 0.05f, h * 1.02f)
        cubicTo(
          w * 0.18f, h * 0.86f + sway,
          w * 0.34f, h * 0.92f - sway,
          w * 0.66f, h * 0.74f + sway,
        )
        cubicTo(
          w * 0.92f, h * 0.60f,
          w * 1.04f, h * 0.86f,
          w * 1.05f, h * 1.05f,
        )
        close()
      }
}

private fun curveLine(w: Float, h: Float, flow: Float, shift: Float): Path {
  val sway = sin((flow + shift) * 6.2832f) * h * 0.01f
  return Path().apply {
        moveTo(w * 1.05f, h * (0.10f + shift))
        cubicTo(
          w * 0.66f, h * (0.20f + shift) + sway,
          w * 0.70f, h * (0.42f + shift) - sway,
          w * 0.24f, h * (0.52f + shift) + sway,
        )
        cubicTo(
          w * 0.06f, h * (0.56f + shift),
          w * -0.02f, h * (0.44f + shift),
          w * -0.05f, h * (0.36f + shift),
        )
      }
}

/** نقطه‌های ریزِ شناور — جایشان دستی چیده شده تا پخش باشند، نه تصادفی */
private data class Dot(
  val x: Float,
  val y: Float,
  val radius: Float,
  val alpha: Float,
  val phase: Float,
  val sway: Float,
  /** ضریبِ سرعت — همه با یک تندی بچرخند، حرکت مصنوعی می‌شود */
  val speed: Float = 1f,
)

private val dots = listOf(
  Dot(x = 0.78f, y = 0.09f, radius = 5.5f, alpha = 0.85f, phase = 0.00f, sway = 0.045f, speed = 1.0f),
  Dot(x = 0.24f, y = 0.17f, radius = 4.0f, alpha = 0.80f, phase = 0.30f, sway = 0.055f, speed = 1.4f),
  Dot(x = 0.69f, y = 0.26f, radius = 3.5f, alpha = 0.70f, phase = 0.55f, sway = 0.038f, speed = 0.8f),
  Dot(x = 0.18f, y = 0.38f, radius = 4.5f, alpha = 0.75f, phase = 0.72f, sway = 0.050f, speed = 1.7f),
  Dot(x = 0.83f, y = 0.40f, radius = 6.0f, alpha = 0.80f, phase = 0.18f, sway = 0.042f, speed = 1.1f),
  Dot(x = 0.10f, y = 0.44f, radius = 3.0f, alpha = 0.55f, phase = 0.62f, sway = 0.060f, speed = 2.1f),
  Dot(x = 0.94f, y = 0.41f, radius = 3.0f, alpha = 0.50f, phase = 0.88f, sway = 0.048f, speed = 1.3f),
)

/* ------------------------------ نشان ------------------------------ */

/**
 *  نشانِ برنامه در قابِ گرد.
 *
 *  یک قرصِ سفید با سایهٔ آبیِ نرم، و دورش یک حلقهٔ نازک که بیشترش آبی است
 *  و یک تکه‌اش طلایی. حلقه آرام می‌چرخد؛ چون دور کامل است، جایی که
 *  برمی‌گردد دیده نمی‌شود.
 */
@Composable
fun WelcomeMark(size: androidx.compose.ui.unit.Dp = 118.dp) {
  val colors = Shop.colors
  //  همان دو حرکتِ پیشین: «spin» ۰ تا ۳۶۰ درجه در ۱۴ ثانیه (خطی، از نو)، «glow» ۰٫۳۵ تا ۰٫۶ در ۳ ثانیه (رفت و برگشت)
  val clock = rememberWelcomeClock(onScreenOnly = true)
  val spin by WelcomeTime.derived(clock) { WelcomeTime.restart(it, 14_000) * 360f }
  val glow by WelcomeTime.derived(clock) { WelcomeTime.reverse(it, 3_000, 0.35f, 0.6f) }

  Box(Modifier.size(size).testTag(WelcomeTags.MARK).then(clock.area), contentAlignment = Alignment.Center) {
    // هالهٔ آبی، بیرونِ حلقه
    Canvas(Modifier.fillMaxSize()) {
      val r = this.size.minDimension / 2f
      drawCircle(
        brush = Brush.radialGradient(
          colors = listOf(
            ACCENT_BLUE.copy(alpha = 0.16f * glow),
            ACCENT_BLUE.copy(alpha = 0f),
          ),
          center = center,
          radius = r,
        ),
        radius = r,
      )

      // حلقهٔ نازک: بیشترِ دور آبی، یک کمانِ کوتاه طلایی
      rotate(spin) {
        drawCircle(
          color = ACCENT_BLUE.copy(alpha = 0.30f),
          radius = r * 0.80f,
          style = Stroke(width = 2.2.dp.toPx()),
        )
        drawArc(
          color = ACCENT_AMBER,
          startAngle = -140f,
          sweepAngle = 78f,
          useCenter = false,
          topLeft = Offset(center.x - r * 0.80f, center.y - r * 0.80f),
          size = Size(r * 1.60f, r * 1.60f),
          style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawArc(
          color = ACCENT_BLUE,
          startAngle = 40f,
          sweepAngle = 96f,
          useCenter = false,
          topLeft = Offset(center.x - r * 0.80f, center.y - r * 0.80f),
          size = Size(r * 1.60f, r * 1.60f),
          style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
        )
      }
    }

    // قرصِ سفیدِ وسط
    Box(
      Modifier
        .size(size * 0.52f)
        .shadow(10.dp, CircleShape, ambientColor = ACCENT_BLUE, spotColor = ACCENT_BLUE)
        .clip(CircleShape)
        .background(if (colors.bg.luminanceIsDark()) colors.surface else Color.White),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        Icons.Filled.Storefront,
        contentDescription = null,
        tint = ACCENT_BLUE,
        modifier = Modifier.size(size * 0.24f),
      )
    }
  }
}

/** آیا این رنگ تیره است — برای اینکه طرحِ روشن روی تمِ تاریک وصله نشود */
private fun Color.luminanceIsDark(): Boolean =
  (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f

/* ------------------------------ ساعت ------------------------------ */

/**
 *  زمانِ دو حرکتِ بالا (میلی‌ثانیه) ⇒ همان مقدارهای `rememberInfiniteTransition` ِ پیشین: `tween` ِ خطیِ «از نو» و
 *  `tween(EaseInOutSine)` ِ «رفت و برگشت» — ریاضیِ خالص (آزمون: `WelcomeLookUiTest`).
 */
internal object WelcomeTime {
  /** ۰ تا ۱ در هر [periodMs]، از نو (نقطهٔ ۱ همان نقطهٔ ۰ است پس پرشی ندارد) */
  fun restart(ms: Long, periodMs: Int): Float = (ms % periodMs).toFloat() / periodMs

  /** [from] تا [to] در [periodMs] و برگشت در همان‌قدر، با `EaseInOutSine` (همان `RepeatMode.Reverse`) */
  fun reverse(ms: Long, periodMs: Int, from: Float, to: Float): Float {
    val p = ms % (2L * periodMs)
    val f = if (p < periodMs) p.toFloat() / periodMs else (2L * periodMs - p).toFloat() / periodMs
    return from + (to - from) * EaseInOutSine.transform(f)
  }

  /** مقدارِ برگرفته از ساعت که فقط در مرحلهٔ رسم خوانده می‌شود (هیچ بازترکیبی در هر قاب نیست) */
  @Composable
  fun derived(clock: WelcomeClock, f: (Long) -> Float) =
    remember(clock) { derivedStateOf { f(clock.ms) } }
}
