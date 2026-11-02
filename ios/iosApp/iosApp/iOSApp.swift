import Foundation
import SwiftUI
import UIKit
import TohidShared

// میزبانِ SwiftUI ِ برنامهٔ آیفون — هیچ صفحه‌ای این‌جا نیست؛ همه از رابطِ مشترکِ Compose
// (`ios/shared`، چارچوبِ TohidShared) می‌آید. شرح: docs/IOS-PLAN-fa.md.
@main
struct iOSApp: SwiftUI.App {
    // توکنِ APNs (گامِ ۴) فقط از راهِ نمایندهٔ UIApplication می‌رسد
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        // پیش از پایانِ راه‌اندازی: کارِ پس‌زمینهٔ همگام‌سازی (BGTaskScheduler)، نمایندهٔ اعلان، چرخهٔ برنامه
        MainViewControllerKt.appDidLaunch()
    }

    var body: some SwiftUI.Scene {
        SwiftUI.WindowGroup {
            ContentView()
                // پیوندِ vill3n://app/… (همان `handleLink` ِ اندروید)
                .onOpenURL { url in MainViewControllerKt.openUrl(url: url.absoluteString) }
        }
    }
}

// اعلانِ سرور (APNs): توکنِ دستگاه ⇐ `IosPush` (ثبت در `POST /api/me/push` با provider = apns). بی قابلیتِ
// «Push Notifications» در امضا (حسابِ اپل، docs/IOS-PLAN-fa.md §۰.۶) iOS خطا می‌دهد و فقط دلیلش نگه داشته می‌شود.
final class AppDelegate: NSObject, UIKit.UIApplicationDelegate {
    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Foundation.Data) {
        IosPushKt.apnsToken(token: deviceToken)
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Swift.Error) {
        IosPushKt.apnsFailed(message: error.localizedDescription)
    }
}
