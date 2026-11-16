package ir.vil3ntec.tohid.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 *  نوارهای سیستم همرنگِ برنامه (صاحب‌کار ۱۴۰۵/۰۸/۲۶ با عکسِ حالتِ تیره: «نوارهای سامسونگ سفیده و تمامِ صفحه رو خراب نشون
 *  میده … یا پنهان بشه یا شبیهِ رنگِ برنامه بشه») — `Platform.SystemBars`، فقط اندروید (کامپیوتر و آیفون: بی‌اثر؛ `replaced` ِ
 *  ‎desktop/build.gradle.kts‎ و ‎ios/shared/ios-exclude.txt‎).
 *
 *  `enableEdgeToEdge()` ِ `MainActivity` رنگِ نشانه‌ها را از روز/شبِ **گوشی** می‌گرفت، نه از تمِ **برنامه**، و روی نوارِ سه‌دکمه‌ای
 *  پرده‌ای روشن برای «کنتراست» می‌کشید ⇒ برنامهٔ تیره روی گوشیِ روشن: نوارِ پایینِ سفید و ساعتِ کم‌رنگ. حالا هر دو نوار شفاف‌اند
 *  (پس‌زمینهٔ خودِ برنامه زیرشان است — پوسته تا لبه می‌کشد) و نشانه‌ها با تمِ برنامه روشن/تیره می‌شوند. اندرویدِ پیش از ۸ نشانهٔ
 *  تیره برای نوارِ پایین ندارد ⇒ در تمِ روشن همان پردهٔ نیم‌تیره می‌ماند تا دکمه‌ها دیده شوند.
 */
@Composable
internal fun systemBarsAndroid(dark: Boolean) {
  val view = LocalView.current
  DisposableEffect(dark, view) {
    val window = view.context.activityOf()?.window
    if (window != null) runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
      }
      @Suppress("DEPRECATION")
      window.statusBarColor = Color.TRANSPARENT
      @Suppress("DEPRECATION")
      window.navigationBarColor = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && !dark) OLD_LIGHT_SCRIM else Color.TRANSPARENT
      WindowCompat.getInsetsController(window, view).apply {
        isAppearanceLightStatusBars = !dark
        isAppearanceLightNavigationBars = !dark
      }
    }
    onDispose { }
  }
}

/** همان پردهٔ `enableEdgeToEdge` برای نوارِ پایینِ اندرویدِ پیش از ۸ در تمِ روشن (نشانهٔ تیره ندارد) */
private const val OLD_LIGHT_SCRIM = 0x801B1B1B.toInt()

private tailrec fun Context.activityOf(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.activityOf()
  else -> null
}
