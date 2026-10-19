import SwiftUI
import FirebaseCore
import Shared

@main
struct iOSApp: App {
    init() {
        // До первого обращения к Firebase из Kotlin (GitLive берёт уже настроенное приложение).
        FirebaseApp.configure()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .onOpenURL { url in MainViewControllerKt.openUrl(url: url.absoluteString) }
        }
    }
}

/** Весь UI — Compose Multiplatform из модуля shared. */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
