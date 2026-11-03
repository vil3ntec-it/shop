package androidx.compose.ui.platform

import android.content.Context
import androidx.compose.runtime.Composable

/**
 *  shimِ iOS (گامِ ۹) — نمای Compose درونِ نمای اندروید، فقط برای `Presentation` ِ نمایشگرِ دومِ مشتری
 *  (`PayDevice.CustomerScreen`) که در آیفون هرگز ساخته نمی‌شود. ریشهٔ آیفون `ComposeUIViewController` است (‎ios/MainViewController.kt‎).
 */
internal class ComposeView(context: Context) : android.view.View(context) {
  fun setContent(content: @Composable () -> Unit) {
    throw UnsupportedOperationException("iOS: ComposeView ِ اندروید نیست")
  }
}
