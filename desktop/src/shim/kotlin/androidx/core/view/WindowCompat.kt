package androidx.core.view

import android.view.View
import android.view.Window

class WindowInsetsControllerCompat {
  var isAppearanceLightStatusBars: Boolean = false
  var isAppearanceLightNavigationBars: Boolean = false
}

object WindowCompat {
  fun getInsetsController(window: Window, view: View): WindowInsetsControllerCompat = WindowInsetsControllerCompat()
  fun setDecorFitsSystemWindows(window: Window, fits: Boolean) {}
}
