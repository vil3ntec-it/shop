package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/*
 *  shimِ iOS ِ دو CompositionLocal ِ فقط-اندروید که کدِ مشترک می‌خواند (همان `AndroidLocals.kt` ِ
 *  کامپیوتر): `LocalContext` و `LocalConfiguration` (اندازهٔ پنجره به dp). ریشهٔ آیفون
 *  (`ir.vil3ntec.tohid.ios.IosRoot`) هر دو را از پنجرهٔ واقعی فراهم می‌کند.
 */
val LocalContext = staticCompositionLocalOf<Context> { android.content.IosContext }

class Configuration(
  val screenWidthDp: Int,
  val screenHeightDp: Int,
) {
  val orientation: Int = if (screenWidthDp > screenHeightDp) ORIENTATION_LANDSCAPE else ORIENTATION_PORTRAIT
  val fontScale: Float = 1f
  val smallestScreenWidthDp: Int = minOf(screenWidthDp, screenHeightDp)

  companion object {
    const val ORIENTATION_PORTRAIT = 1
    const val ORIENTATION_LANDSCAPE = 2
  }
}

/** پیش‌فرض: آیفونِ ۶٫۱ اینچ؛ `IosRoot` مقدارِ واقعی را می‌دهد */
val LocalConfiguration = compositionLocalOf { Configuration(390, 844) }
