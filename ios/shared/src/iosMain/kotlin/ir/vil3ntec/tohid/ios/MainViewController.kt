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
  //  پوشه‌هایی که اندروید از پشتیبان کنار می‌گذارد (`sync-shadow/`، `sync-v3/`) بیرون از پشتیبانِ iCloud، نصبِ پیشین هم (‎ios/backup/IosNoBackupDirs.kt‎)
  ir.vil3ntec.tohid.ios.backup.IosNoBackupDirs.ensure()
  //  پیش از هر چیز: آیفونی که از پشتیبانِ آیفونِ دیگر بالا آمده شناسهٔ خودش را می‌گیرد (‎ios/identity/IosDeviceIdentity.kt‎)
  ir.vil3ntec.tohid.ios.identity.IosDeviceIdentity.check()
  //  «ورود با اپل» فقط این‌جا گذاشته می‌شود (گامِ ۶)؛ اندروید و کامپیوتر ندارند ⇒ دکمه فقط در آیفون
  ir.vil3ntec.tohid.data.ExternalSignIn.apple = AppleSignInIos
  IosWork.install()
  IosBackground.register()
  IosNotifications.install()
  IosLifecycle.install()
  IosPush.register()
}

/** `.onOpenURL` ِ SwiftUI: پیوندِ `vill3n://app/…` (‎Info.plist‎: CFBundleURLTypes) */
fun openUrl(url: String) = IosLinks.open(url)
