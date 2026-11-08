import Foundation
import SwiftUI
import UIKit
import TohidCustomer

// میزبانِ SwiftUI ِ برنامهٔ خریدِ مشتری — هیچ صفحه‌ای این‌جا نیست؛ همه از همان صفحه‌های Compose ِ `customer-android`
// (چارچوبِ TohidCustomer از `customer-ios/shared`). امضا و انتشار با اجازهٔ صاحب‌کار (docs/IOS-PLAN-fa.md §۰.۶).
@main
struct iOSApp: SwiftUI.App {
    @SwiftUI.Environment(\.scenePhase) private var phase

    var body: some SwiftUI.Scene {
        SwiftUI.WindowGroup {
            ContentView()
                // پیوندِ tohidshop://store/… (همان `openLink` ِ اندروید)
                .onOpenURL { url in MainViewControllerKt.openUrl(url: url.absoluteString) }
        }
        .onChange(of: phase) { now in
            // جلوی چشم ⇒ «خواندم» ِ گفت‌وگوی باز؛ inactive/background ⇒ فقط «رسید» (CHAT-04)
            if now == .active { MainViewControllerKt.resumed() } else { MainViewControllerKt.paused() }
        }
    }
}
