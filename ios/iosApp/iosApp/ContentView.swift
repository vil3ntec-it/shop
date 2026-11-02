import SwiftUI
import UIKit
import TohidShared

// همان ریشهٔ مشترک (`ir.vil3ntec.tohid.ios.IosRoot`) در یک UIViewController ِ Compose.
// ⚠️ چارچوبِ TohidShared رده‌هایی هم‌نامِ SwiftUI دارد (`View` و `Context` ِ shimهای اندروید)؛ پس نام‌های
// SwiftUI همه با نامِ ماژول نوشته می‌شوند (گامِ ۴: «'View' is ambiguous» در CI ِ گامِ ۳).
struct ComposeView: UIViewControllerRepresentable {
    typealias UIViewControllerType = UIViewController

    func makeUIViewController(context: UIViewControllerRepresentableContext<ComposeView>) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: UIViewControllerRepresentableContext<ComposeView>) {}
}

struct ContentView: SwiftUI.View {
    var body: some SwiftUI.View {
        // Compose خودش حاشیهٔ امن (بریدگی و نوارِ پایین) را می‌گیرد
        ComposeView().ignoresSafeArea()
    }
}
