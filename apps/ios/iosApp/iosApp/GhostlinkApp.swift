import SwiftUI
import FirebaseCore
import GhostlinkKit

@main
struct GhostlinkApp: App {
    init() {
        // Конфиг Firebase лежит в бандле, только если GoogleService-Info.plist был рядом с project.yml при сборке.
        let hasFirebase = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
        // До первого обращения к Firebase из Kotlin: GitLive берёт уже настроенное приложение.
        if hasFirebase { FirebaseApp.configure() }
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        GhostlinkKt.start(demoMode: !hasFirebase, versionName: version)
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .onOpenURL { url in GhostlinkKt.openUrl(url: url.absoluteString) }
        }
    }
}

/** Весь UI — Compose Multiplatform из GhostlinkKit. */
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        GhostlinkKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
