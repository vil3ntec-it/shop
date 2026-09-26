package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf

/** `LocalContext` — روی کامپیوتر همیشه همان `DesktopContext`ِ یگانه. */
val LocalContext = staticCompositionLocalOf<Context> { error("DesktopContext فراهم نشده") }

/**
 *  اندازهٔ پنجره به زبانِ اندروید. `Main.kt` با هر تغییرِ اندازهٔ پنجره
 *  تازه‌اش می‌کند، پس `Responsive.kt` پنجرهٔ پهن را «WIDE» می‌بیند —
 *  همان چیدمانِ تبلتِ بزرگ.
 */
class Configuration(
  @JvmField val screenWidthDp: Int,
  @JvmField val screenHeightDp: Int,
) {
  @JvmField val orientation: Int = if (screenWidthDp > screenHeightDp) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
  @JvmField val fontScale: Float = 1f
  @JvmField val smallestScreenWidthDp: Int = minOf(screenWidthDp, screenHeightDp)
  companion object {
    const val ORIENTATION_PORTRAIT = 1
    const val ORIENTATION_LANDSCAPE = 2
  }
}

val LocalConfiguration = compositionLocalOf { Configuration(1200, 800) }
