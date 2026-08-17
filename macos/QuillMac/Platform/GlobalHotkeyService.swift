import AppKit
import Carbon.HIToolbox

// System-wide hotkey via Carbon RegisterEventHotKey — the macOS equivalent of the Windows
// RegisterHotKey path. Parses the same "Alt+Q" shortcut string the settings share with Windows
// (Alt → Option, Win/Cmd → Command).
final class GlobalHotkeyService {
    private var hotKeyRef: EventHotKeyRef?
    private var handlerRef: EventHandlerRef?
    var onPressed: (() -> Void)?
    private(set) var registrationFailureReason = ""

    @discardableResult
    func register(_ shortcut: String) -> Bool {
        registrationFailureReason = ""
        unregister()
        guard let (mods, key) = Self.parse(shortcut) else {
            registrationFailureReason = "Invalid shortcut format."
            return false
        }
        var eventType = EventTypeSpec(eventClass: OSType(kEventClassKeyboard), eventKind: UInt32(kEventHotKeyPressed))
        let selfPtr = Unmanaged.passUnretained(self).toOpaque()
        InstallEventHandler(GetApplicationEventTarget(), { _, _, userData in
            guard let userData else { return noErr }
            Unmanaged<GlobalHotkeyService>.fromOpaque(userData).takeUnretainedValue().onPressed?()
            return noErr
        }, 1, &eventType, selfPtr, &handlerRef)

        var ref: EventHotKeyRef?
        let id = EventHotKeyID(signature: OSType(0x5155494C), id: 1) // 'QUIL'
        let status = RegisterEventHotKey(key, mods, id, GetApplicationEventTarget(), 0, &ref)
        if status == noErr { hotKeyRef = ref; return true }
        registrationFailureReason = "The system rejected this shortcut (error \(status)); it may be taken by another app."
        return false
    }

    func unregister() {
        if let hotKeyRef { UnregisterEventHotKey(hotKeyRef); self.hotKeyRef = nil }
        if let handlerRef { RemoveEventHandler(handlerRef); self.handlerRef = nil }
    }

    static func parse(_ value: String) -> (UInt32, UInt32)? {
        var mods: UInt32 = 0
        var key: UInt32?
        for raw in value.split(separator: "+") {
            let part = raw.trimmingCharacters(in: .whitespaces)
            switch part.lowercased() {
            case "ctrl", "control": mods |= UInt32(controlKey)
            case "alt", "option", "opt": mods |= UInt32(optionKey)
            case "shift": mods |= UInt32(shiftKey)
            case "win", "cmd", "command": mods |= UInt32(cmdKey)
            case "space": key = UInt32(kVK_Space)
            default: if let k = keyCode(for: part) { key = k } else { return nil }
            }
        }
        guard mods != 0, let key else { return nil }
        return (mods, key)
    }

    private static func keyCode(for part: String) -> UInt32? {
        if part.count == 1, let c = part.uppercased().first { return letterOrDigit(c) }
        if part.count > 1, part.first?.uppercased() == "F", let n = Int(part.dropFirst()), (1...12).contains(n) {
            let f = [1: kVK_F1, 2: kVK_F2, 3: kVK_F3, 4: kVK_F4, 5: kVK_F5, 6: kVK_F6,
                     7: kVK_F7, 8: kVK_F8, 9: kVK_F9, 10: kVK_F10, 11: kVK_F11, 12: kVK_F12]
            if let vk = f[n] { return UInt32(vk) }
        }
        return nil
    }

    private static func letterOrDigit(_ c: Character) -> UInt32? {
        let map: [Character: Int] = [
            "A": kVK_ANSI_A, "B": kVK_ANSI_B, "C": kVK_ANSI_C, "D": kVK_ANSI_D, "E": kVK_ANSI_E,
            "F": kVK_ANSI_F, "G": kVK_ANSI_G, "H": kVK_ANSI_H, "I": kVK_ANSI_I, "J": kVK_ANSI_J,
            "K": kVK_ANSI_K, "L": kVK_ANSI_L, "M": kVK_ANSI_M, "N": kVK_ANSI_N, "O": kVK_ANSI_O,
            "P": kVK_ANSI_P, "Q": kVK_ANSI_Q, "R": kVK_ANSI_R, "S": kVK_ANSI_S, "T": kVK_ANSI_T,
            "U": kVK_ANSI_U, "V": kVK_ANSI_V, "W": kVK_ANSI_W, "X": kVK_ANSI_X, "Y": kVK_ANSI_Y, "Z": kVK_ANSI_Z,
            "0": kVK_ANSI_0, "1": kVK_ANSI_1, "2": kVK_ANSI_2, "3": kVK_ANSI_3, "4": kVK_ANSI_4,
            "5": kVK_ANSI_5, "6": kVK_ANSI_6, "7": kVK_ANSI_7, "8": kVK_ANSI_8, "9": kVK_ANSI_9]
        return map[c].map { UInt32($0) }
    }

    // For the settings UI: normalize a parsed shortcut back to canonical "Ctrl+Alt+Q" form.
    static func normalize(_ value: String) -> String? {
        guard parse(value) != nil else { return nil }
        var parts: [String] = []
        let lower = value.lowercased()
        if lower.contains("ctrl") || lower.contains("control") { parts.append("Ctrl") }
        if lower.contains("alt") || lower.contains("option") { parts.append("Alt") }
        if lower.contains("shift") { parts.append("Shift") }
        if lower.contains("cmd") || lower.contains("command") || lower.contains("win") { parts.append("Cmd") }
        if let last = value.split(separator: "+").last { parts.append(last.trimmingCharacters(in: .whitespaces).uppercased()) }
        return parts.joined(separator: "+")
    }
}
