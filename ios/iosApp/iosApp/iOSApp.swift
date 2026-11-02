import SwiftUI

// میزبانِ SwiftUI ِ برنامهٔ آیفون — هیچ صفحه‌ای این‌جا نیست؛ همه از رابطِ مشترکِ Compose
// (`ios/shared`، چارچوبِ TohidShared) می‌آید. شرح: docs/IOS-PLAN-fa.md.
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
