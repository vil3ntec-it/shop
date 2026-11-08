import SwiftUI
import UIKit
import TohidCustomer

// همان ریشهٔ مشترک (`CustomerApp`) در یک UIViewController ِ Compose. نام‌های SwiftUI با نامِ ماژول (مثلِ برنامهٔ دکان).
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
