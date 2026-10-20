package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 *  کلیدِ انیمیشن — همان «انیمیشن روشن / مثل تنظیم گوشی» صفحهٔ تنظیماتِ وب.
 *
 *  اندروید یک تنظیمِ سراسری دارد که کاربر می‌تواند برای ذخیرهٔ باتری یا
 *  کم‌کردنِ حرکت، همهٔ انیمیشن‌ها را خاموش کند. تا وقتی کاربر خودش
 *  «انیمیشن روشن» را انتخاب نکرده، ما هم به همان تنظیم احترام می‌گذاریم؛
 *  اگر انتخاب کرد، انیمیشن‌ها همیشه اجرا می‌شوند.
 *
 *  حالت در یک `mutableStateOf` نگه داشته می‌شود تا لحظه‌ای که کاربر کلید
 *  را می‌زند، کلِ صفحه بدون بازکردنِ دوباره عوض شود.
 */
object Motion {

  private const val PREFS = "tohid"
  private const val KEY = "motion"

  /** `full` یعنی همیشه روشن، `auto` یعنی مثل تنظیم گوشی */
  var choice by mutableStateOf("full")
    private set

  /** آیا همین حالا باید انیمیشن اجرا شود */
  var enabled by mutableStateOf(true)
    private set

  fun load(context: Context) {
    // پیش‌فرض روشن است، چون کاربر انیمیشن‌ها را خواسته — مثل نسخهٔ وب
    choice = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "full") ?: "full"
    apply(context)
  }

  fun set(context: Context, value: String) {
    choice = value
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    apply(context)
  }

  private fun apply(context: Context) {
    enabled = choice == "full" || !systemReducesMotion(context)
  }

  /** گوشی گفته حرکت کم شود یا انیمیشن‌ها خاموش‌اند؟ */
  private fun systemReducesMotion(context: Context): Boolean = runCatching {
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    scale == 0f
  }.getOrDefault(false)
}


/**
 *  فنرهای برنامه.
 *
 *  Compose‌ی این پروژه `MaterialExpressiveTheme` ندارد، ولی همان رفتار را
 *  می‌شود با فنر ساخت: هر حرکتی در برنامه یکی از این سه است، تا حرکت‌ها
 *  با هم بخوانند و هر صفحه فنرِ خودش را از خودش درنیاورد.
 *
 *  وقتی کاربر انیمیشن را خاموش کرده، همه‌ی این‌ها `snap` می‌شوند — یعنی
 *  همان حالتِ پایانی، بدون حرکت.
 */
object Springs {

  /** فشار و انتخاب — تند و کمی سرکش */
  val press: FiniteAnimationSpec<Float>
    get() = if (Motion.enabled) spring(dampingRatio = 0.55f, stiffness = 900f) else snap()

  /** ورودِ کارت و باز شدنِ بخش — نرم و بدون پرش */
  val enter: FiniteAnimationSpec<Float>
    get() = if (Motion.enabled) spring(dampingRatio = 0.80f, stiffness = 380f) else snap()

  /** رنگ و تم — تغییرِ روز و شب نباید ناگهانی باشد */
  val effect: FiniteAnimationSpec<Color>
    get() = if (Motion.enabled) tween(280, easing = FastOutSlowInEasing) else snap()

  /** شمارنده‌ی عددِ قهرمان */
  val counter: FiniteAnimationSpec<Int>
    get() = if (Motion.enabled) tween(MotionTokens.COUNT_UP, easing = FastOutSlowInEasing) else snap()

  /** باز و بسته شدنِ بخشِ جمع‌شده */
  val size: FiniteAnimationSpec<IntSize>
    get() = if (Motion.enabled) spring(dampingRatio = 0.80f, stiffness = 380f) else snap()

  /** نوارِ پیشرفت — با کمی مکث تا بعد از نشستنِ کارت راه بیفتد */
  val progress: FiniteAnimationSpec<Float>
    get() = if (Motion.enabled) tween(600, delayMillis = 120, easing = FastOutSlowInEasing) else snap()
}


/**
 *  واژه‌نامهٔ حرکتِ نسخهٔ ۳ (VILL3N، «در حدِ اپل»): هر زمان و هر منحنیِ برنامه
 *  یکی از این نام‌هاست — صفحه‌ها عددِ خودشان را درنمی‌آورند. زمان‌ها ۱۵۰ تا
 *  ۳۵۰ میلی‌ثانیه، و هر جا جسمی جابه‌جا می‌شود فنر، نه خطی.
 *
 *  همه با `Motion.enabled` صفر می‌شوند (`ms`، `MotionSpec.*`)؛ پیاده‌سازیِ
 *  جابه‌جاییِ صفحه‌ها، بزرگ‌شدنِ کارت به صفحه، ورودِ پلکانی، شمارندهٔ عدد و
 *  سربرگِ جمع‌شونده در `ui/v3/V3Motion.kt` است و فقط از همین‌ها می‌خواند.
 */
object MotionTokens {
  /** فشار، رنگِ لمس، نشانگرِ کوچک */
  const val SHORT = 150
  /** محو شدنِ صفحهٔ رفته، بازشدنِ منو */
  const val MEDIUM = 220
  /** آمدنِ صفحه (shared-axis)، باز شدنِ برگه */
  const val LONG = 280
  /** بزرگ‌شدنِ کارت به صفحه (container transform) */
  const val EMPHASIZED = 350
  /** فاصلهٔ ورودِ پلکانیِ ردیف‌ها، و سقفِ شمارشان */
  const val STAGGER_STEP = 30
  const val STAGGER_MAX = 10
  /** شمارندهٔ عددِ پول/آمار */
  const val COUNT_UP = 600

  /** شتاب‌دار و نرم‌نشین — آمدنِ چیزی به صحنه */
  val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
  /** رفتنِ چیزی از صحنه */
  val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
  /** جابه‌جاییِ معمولی */
  val Standard: Easing = FastOutSlowInEasing

  /** زمان، یا صفر وقتی حرکت خاموش است */
  fun ms(full: Int): Int = if (Motion.enabled) full else 0
}

/** مشخصه‌های نام‌دار — هر کدام با «کاهشِ حرکت» بی‌درنگ (`snap`) */
object MotionSpec {
  fun <T> tweenOf(duration: Int, easing: Easing = MotionTokens.Standard, delay: Int = 0): FiniteAnimationSpec<T> =
    if (Motion.enabled) tween(duration, delayMillis = delay, easing = easing) else snap()

  /** برگه و نوارِ پایین: فنرِ نرم و بی‌پرش */
  fun <T> sheet(): FiniteAnimationSpec<T> = if (Motion.enabled) spring(dampingRatio = 0.85f, stiffness = 380f) else snap()

  /** نشانگرِ تب و تراشهٔ انتخاب‌شده: تند، کمی سرکش */
  fun <T> indicator(): FiniteAnimationSpec<T> = if (Motion.enabled) spring(dampingRatio = 0.72f, stiffness = 650f) else snap()

  /** دکمهٔ شناور و هر شکلی که عوض می‌شود (morph) */
  fun <T> morph(): FiniteAnimationSpec<T> = if (Motion.enabled) spring(dampingRatio = 0.8f, stiffness = 500f) else snap()

  /** فشردنِ لمسی */
  fun <T> press(): FiniteAnimationSpec<T> = if (Motion.enabled) spring(dampingRatio = 0.6f, stiffness = 900f) else snap()

  /** جابه‌جاییِ صفحه (لغزش) */
  val pageOffset: FiniteAnimationSpec<IntOffset>
    get() = if (Motion.enabled) tween(MotionTokens.LONG, easing = MotionTokens.EmphasizedDecelerate) else snap()

  /** بلندیِ سربرگِ جمع‌شونده، وقتی خودش جا می‌افتد */
  val headerSettle: FiniteAnimationSpec<Float>
    get() = if (Motion.enabled) spring(dampingRatio = 0.9f, stiffness = 500f) else snap()

  /** پهنای دکمهٔ شناور (کامل ⇄ گرد) */
  val fabWidth: FiniteAnimationSpec<Dp>
    get() = if (Motion.enabled) spring(dampingRatio = 0.8f, stiffness = 500f) else snap()
}
