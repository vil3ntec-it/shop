package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/**
 *  نشانِ برنامه، همان که روی صفحهٔ گوشی است.
 *
 *  چرا کشیده می‌شود و از فایلِ آیکن خوانده نمی‌شود: آیکنِ لانچر برایِ
 *  اندازه‌های مشخصی ساخته شده و کشیدنش داخلِ برنامه در هر اندازه‌ای
 *  تار می‌شد. اینجا همان هندسه است — همان اعدادی که آیکن‌ها از رویشان
 *  ساخته شده‌اند — پس آیکنِ روی صفحهٔ گوشی و لوگوی داخلِ برنامه دو چیز
 *  نیستند.
 *
 *  دستگاهِ مختصات ۱۰۰×۱۰۰ است و هر اندازه‌ای که بدهید، همان نسبت را
 *  نگه می‌دارد.
 */

private val INK = Color(0xFF141C27)
private val BLUE_TOP = Color(0xFF2D9BFF)
private val BLUE_BOTTOM = Color(0xFF0A4AD6)

@Composable
fun TohidMark(modifier: Modifier = Modifier) {
  Canvas(modifier) {
    val k = size.minDimension / 100f
    fun p(x: Float, y: Float) = Offset(x * k, y * k)

    /** چندضلعیِ بسته */
    fun poly(vararg points: Pair<Float, Float>) = Path().apply {
      points.forEachIndexed { i, (x, y) ->
        if (i == 0) moveTo(p(x, y).x, p(x, y).y) else lineTo(p(x, y).x, p(x, y).y)
      }
      close()
    }

    //  گرادیانِ آبی روی کلِ نشان کشیده می‌شود، نه روی هر تکه جدا — وگرنه
    //  دو تکهٔ آبی دو رنگِ متفاوت می‌شدند و درزشان دیده می‌شد
    val blue = Brush.verticalGradient(
      listOf(BLUE_TOP, BLUE_BOTTOM),
      startY = 13f * k,
      endY = 87f * k,
    )

    // بازوی چپ
    drawPath(poly(6f to 23.69f, 27.23f to 23.69f, 56.02f to 80.32f, 46.58f to 86.69f), INK)

    // بازوی راست
    drawPath(poly(70.17f to 14.26f, 94f to 13.31f, 56.02f to 54.84f, 45.4f to 39.5f), blue)

    /*
     *  تیغهٔ پایین — دو کمانِ درجه‌دو.
     *
     *  با `cubicTo` نوشته شده‌اند، نه `quadraticTo`: تبدیلِ یک کمانِ
     *  درجه‌دو به درجه‌سه دقیق است (نقطه‌های راهنما در دو سومِ راه) و
     *  `cubicTo` در هر نسخه‌ای از Compose هست.
     */
    fun quad(from: Pair<Float, Float>, ctrl: Pair<Float, Float>, to: Pair<Float, Float>, path: Path) {
      val c1 = Pair(from.first + 2f / 3f * (ctrl.first - from.first), from.second + 2f / 3f * (ctrl.second - from.second))
      val c2 = Pair(to.first + 2f / 3f * (ctrl.first - to.first), to.second + 2f / 3f * (ctrl.second - to.second))
      path.cubicTo(p(c1.first, c1.second).x, p(c1.first, c1.second).y,
        p(c2.first, c2.second).x, p(c2.first, c2.second).y,
        p(to.first, to.second).x, p(to.first, to.second).y)
    }

    val tip = 76.31f to 46.11f
    val foot = 49.41f to 85.03f
    val blade = Path().apply {
      moveTo(p(tip.first, tip.second).x, p(tip.first, tip.second).y)
      quad(tip, 78.9f to 70.17f, foot, this)
      quad(foot, 58.61f to 57.9f, tip, this)
      close()
    }
    drawPath(blade, blue)
  }
}
