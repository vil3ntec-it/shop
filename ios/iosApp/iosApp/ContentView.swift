import SwiftUI
import UIKit
import TohidShared

// همان ریشهٔ مشترک (`ir.vil3ntec.tohid.ios.IosRoot`) در یک UIViewController ِ Compose.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose خودش حاشیهٔ امن (بریدگی و نوارِ پایین) را می‌گیرد
        ComposeView().ignoresSafeArea()
    }
}
