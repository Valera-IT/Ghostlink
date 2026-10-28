import SwiftUI
import BackgroundTasks
import UserNotifications
import FirebaseCore
import FirebaseCrashlytics
import GhostlinkKit

@main
struct GhostlinkApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

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
        .onChange(of: scenePhase) { phase in
            if phase == .background {
                BackgroundRefreshKt.appDidEnterBackground()
                BackgroundRefresh.schedule()
            }
        }
    }
}

/**
 * Фоновое обновление вместо push (этап 6.12): без Apple Developer APNs недоступен. Уходя в фон, просим iOS
 * разбудить приложение не раньше чем через 15 минут; когда именно и будить ли вообще, решает система.
 */
enum BackgroundRefresh {
    static let taskId = "io.github.valerait.ghostlink.refresh"

    /** До конца didFinishLaunching, иначе iOS не отдаст задачу приложению. */
    static func register() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskId, using: nil) { task in
            handle(task as! BGAppRefreshTask)
        }
    }

    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskId)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        // Не вышло (симулятор, выключено «Обновление контента») — значит, уведомлений в фоне не будет.
        try? BGTaskScheduler.shared.submit(request)
    }

    private static func handle(_ task: BGAppRefreshTask) {
        // Каждое пробуждение заказывает следующее.
        schedule()
        let check = Task {
            try? await BackgroundRefreshKt.checkNewMessages()
            task.setTaskCompleted(success: true)
        }
        task.expirationHandler = { check.cancel() }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        BackgroundRefresh.register()
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /** Нажатое уведомление открывает свой чат. */
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if let link = response.notification.request.content.userInfo[BackgroundRefreshKt.LINK_KEY] as? String {
            GhostlinkKt.openUrl(url: link)
        }
        completionHandler()
    }

    /** Приложение на экране: новое сообщение и так видно, баннер не нужен. */
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([])
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
