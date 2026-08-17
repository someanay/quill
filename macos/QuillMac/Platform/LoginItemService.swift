import Foundation
import ServiceManagement

// Start-at-login via the modern SMAppService API (macOS 13+). Mirrors the Windows StartupService.
enum LoginItemService {
    static func setEnabled(_ enabled: Bool) {
        do {
            if enabled { try SMAppService.mainApp.register() }
            else { try SMAppService.mainApp.unregister() }
        } catch {
            NSLog("Quill: could not update the login item: \(error.localizedDescription)")
        }
    }

    static var isEnabled: Bool { SMAppService.mainApp.status == .enabled }
}
