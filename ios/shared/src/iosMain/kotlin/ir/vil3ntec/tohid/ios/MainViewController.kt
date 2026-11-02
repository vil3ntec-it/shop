package ir.vil3ntec.tohid.ios

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** دروازهٔ SwiftUI (`ios/iosApp`): `MainViewControllerKt.MainViewController()` */
fun MainViewController(): UIViewController = ComposeUIViewController { IosRoot() }

/**
 *  `iOSApp.init` ِ SwiftUI، پیش از پایانِ راه‌اندازیِ برنامه (قاعدهٔ اپل برای BGTaskScheduler و نمایندهٔ اعلان):
 *  کارگرهای WorkManager، کارِ پس‌زمینهٔ همگام‌سازی، اعلانِ محلی، رخدادهای پیش‌زمینه/پس‌زمینه و ثبتِ APNs (گامِ ۴).
 */
fun appDidLaunch() {
  IosWork.install()
  IosBackground.register()
  IosNotifications.install()
  IosLifecycle.install()
  IosPush.register()
}

/** `.onOpenURL` ِ SwiftUI: پیوندِ `vill3n://app/…` (‎Info.plist‎: CFBundleURLTypes) */
fun openUrl(url: String) = IosLinks.open(url)
