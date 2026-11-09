package ir.vil3ntec.tohid.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 *  OPS-02 — `FLAG_SECURE` تا [on] است (`Platform.SecureWindow`، فقط اندروید: در ساختِ کامپیوتر و آیفون نمی‌آید —
 *  `replaced` ِ ‎desktop/build.gradle.kts‎ و ‎ios/shared/ios-exclude.txt‎).
 *
 *  روی پنجرهٔ خودِ برگه (`DialogWindowProvider`) **و** پنجرهٔ Activity: برگهٔ پایینِ Compose سیاستِ «Inherit» دارد و با هر
 *  بازکشیدن پرچم را از Activity می‌خواند؛ فقط یکی ⇒ دیگری پاکش می‌کرد. `on = false` یا بیرون رفتن ⇒ فقط همان که این‌جا
 *  گذاشته شد برداشته می‌شود (پرچمی که پیش‌تر بود دست نمی‌خورد).
 */
@Composable
internal fun secureWindowAndroid(on: Boolean) {
  val view = LocalView.current
  DisposableEffect(on, view) {
    val windows = if (!on) emptyList() else listOfNotNull(
      (view.parent as? DialogWindowProvider)?.window,
      view.context.activity()?.window,
    ).distinct()
    val set = windows.filter { w -> (w.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) == 0 }
    set.forEach { runCatching { it.addFlags(WindowManager.LayoutParams.FLAG_SECURE) } }
    onDispose { set.forEach { w -> runCatching { w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) } } }
  }
}

private tailrec fun Context.activity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.activity()
  else -> null
}
