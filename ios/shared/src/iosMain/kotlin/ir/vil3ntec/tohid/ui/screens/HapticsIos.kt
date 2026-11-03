package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import ir.vil3ntec.tohid.ios.IosHost
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

/*
 *  جایگزینِ iOS ِ `ui/screens/Haptics.kt` (همان API). همان سه معنا با بازخوردِ لمسیِ خودِ iOS:
 *  «ثبت شد» ⇐ success، «تأیید» ⇐ ضربهٔ متوسط، «خطا» ⇐ error. iPad و آیفونِ بی Taptic بی‌صدا می‌گذرند؛ فرآیندِ بی بسته
 *  (آزمونِ شبیه‌ساز، بی UIApplication) هم (`IosHost`).
 */
object Haptics {
  fun success(context: Context) = notify(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)

  fun confirm(context: Context) {
    if (!IosHost.inApp) return
    UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium).impactOccurred()
  }

  fun error(context: Context) = notify(UINotificationFeedbackType.UINotificationFeedbackTypeError)

  private fun notify(type: UINotificationFeedbackType) {
    if (!IosHost.inApp) return
    UINotificationFeedbackGenerator().notificationOccurred(type)
  }
}
