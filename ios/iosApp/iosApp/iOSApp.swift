import SwiftUI
import TohidShared

// میزبانِ SwiftUI ِ برنامهٔ آیفون — هیچ صفحه‌ای این‌جا نیست؛ همه از رابطِ مشترکِ Compose
// (`ios/shared`، چارچوبِ TohidShared) می‌آید. شرح: docs/IOS-PLAN-fa.md.
@main
struct iOSApp: App {
    init() {
        // پیش از پایانِ راه‌اندازی: کارِ پس‌زمینهٔ همگام‌سازی (BGTaskScheduler)، نمایندهٔ اعلان، چرخهٔ برنامه
        MainViewControllerKt.appDidLaunch()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                // پیوندِ vill3n://app/… (همان `handleLink` ِ اندروید)
                .onOpenURL { url in MainViewControllerKt.openUrl(url: url.absoluteString) }
        }
    }
}
