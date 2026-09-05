package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
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
    get() = if (Motion.enabled) tween(700, easing = FastOutSlowInEasing) else snap()

  /** باز و بسته شدنِ بخشِ جمع‌شده */
  val size: FiniteAnimationSpec<IntSize>
    get() = if (Motion.enabled) spring(dampingRatio = 0.80f, stiffness = 380f) else snap()

  /** نوارِ پیشرفت — با کمی مکث تا بعد از نشستنِ کارت راه بیفتد */
  val progress: FiniteAnimationSpec<Float>
    get() = if (Motion.enabled) tween(600, delayMillis = 120, easing = FastOutSlowInEasing) else snap()
}
