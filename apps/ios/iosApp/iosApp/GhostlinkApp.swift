import SwiftUI
import FirebaseCore
import FirebaseCrashlytics
import GhostlinkKit

@main
struct GhostlinkApp: App {
    init() {
        // Конфиг Firebase лежит в бандле, только если GoogleService-Info.plist был рядом с project.yml при сборке.
        let hasFirebase = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil
        // До первого обращения к Firebase из Kotlin: GitLive берёт уже настроенное приложение.
        if hasFirebase { FirebaseApp.configure() }
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        GhostlinkKt.start(
            demoMode: !hasFirebase,
            versionName: version,
            crashReporter: hasFirebase ? CrashlyticsReporter() : nil
        )
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

/**
 * Необработанное исключение Kotlin: Crashlytics сам видит только SIGABRT без кадров Kotlin,
 * поэтому до падения записываем исключение с адресами его стека — символы подставит dSYM.
 */
final class CrashlyticsReporter: CrashReporter {
    func recordFatal(name: String, reason: String, addresses: [KotlinLong]) {
        let model = ExceptionModel(name: name, reason: reason)
        model.stackTrace = addresses.map { StackFrame(address: UInt(bitPattern: Int($0.int64Value))) }
        Crashlytics.crashlytics().record(exceptionModel: model)
    }
}
