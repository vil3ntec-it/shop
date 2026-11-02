package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/*
 *  جایگزینِ iOS ِ `ui/screens/Haptics.kt` (همان API). همان سه معنا با بازخوردِ لمسیِ خودِ iOS:
 *  «ثبت شد» ⇐ success، «تأیید» ⇐ ضربهٔ متوسط، «خطا» ⇐ error. iPad و آیفونِ بی Taptic بی‌صدا می‌گذرند.
 */
object Haptics {
  fun success(context: Context) = notify(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)

  fun confirm(context: Context) {
    UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
  }

  fun error(context: Context) = notify(UINotificationFeedbackType.UINotificationFeedbackTypeError)

  private fun notify(type: UINotificationFeedbackType) {
    UINotificationFeedbackGenerator().notificationOccurred(type)
  }
}
