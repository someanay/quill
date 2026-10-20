import UIKit
import CoreText
import QuillCore

/// Native seams are injectable in the hosted test target; the extension uses its real system
/// proxy, permission and App Group boundaries when no environment is supplied.
struct KeyboardEnvironment {
    let proxy: () -> (any UITextDocumentProxy)?
    let fullAccess: () -> Bool
    let peek: () -> KeyboardHandoff?
    let consume: (UUID) throws -> String
    let prune: () -> Void
    var didConnect: () -> Void = {}
    var didInsert: (UUID) -> Void = { _ in }
    var session: () -> KeyboardRecordingSession? = { nil }
    var command: (KeyboardSessionCommand) throws -> Void = { _ in }
}

final class KeyboardViewController: UIInputViewController {
    var environment: KeyboardEnvironment?
    private var activeProxy: (any UITextDocumentProxy)? { if let environment { return environment.proxy() }; return textDocumentProxy }
    private var fullAccess: Bool { environment?.fullAccess() ?? hasFullAccess }
    private var fieldAllowed: Bool {
        guard let proxy = activeProxy else { return false }
        return proxy.isSecureTextEntry != true && proxy.keyboardType != .phonePad && proxy.keyboardType != .namePhonePad
    }
    private let brand = KeyboardViewController.adaptive(0x234DCE, 0x234DCE)
    private static func adaptive(_ light: UInt32, _ dark: UInt32) -> UIColor {
        UIColor { trait in
            let value = trait.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((value >> 16) & 255) / 255, green: CGFloat((value >> 8) & 255) / 255, blue: CGFloat(value & 255) / 255, alpha: 1)
        }
    }
    private let ink = KeyboardViewController.adaptive(0x202522, 0xE0E7F6)
    private let paper = KeyboardViewController.adaptive(0xF5F7F5, 0x11141A)
    private let surface = KeyboardViewController.adaptive(0xFFFFFF, 0x171B23)
    private let muted = KeyboardViewController.adaptive(0x5C665F, 0xB2B9C6)
    private let stack = UIStackView()
    private let status = UILabel()
    private let insertButton = UIButton(type: .system)
    private let recordButton = UIButton(type: .system)
    private let endButton = UIButton(type: .system)
    private let letters = UIStackView()
    private var timer: Timer?
    private var current: KeyboardHandoff?
    private var session: KeyboardRecordingSession?
    private var pendingCommand: KeyboardSessionCommand?
    private var requestedCapture: UUID?
    private var anchor: KeyboardInsertionAnchor?
    private var insertedSessionTransfers: Set<UUID> = []
    private var inserting = false
    private var uppercase = true
    private var numbers = false
    private var connected = false
    private var globe: UIButton?
    private var minimumHeight: NSLayoutConstraint?
    private var globeHeight: NSLayoutConstraint?
    private var insertHeight: NSLayoutConstraint?
    private var endHeight: NSLayoutConstraint?
    private var compactKeys = false
    private var copy = CopyCatalog(strings: [:])
    private func c(_ key: String) -> String { copy.text(key) }

    override func viewDidLoad() {
        super.viewDidLoad()
        let url = Bundle.main.url(forResource: "strings.default", withExtension: "json", subdirectory: "copy") ?? Bundle.main.url(forResource: "strings.default", withExtension: "json")
        if let url, let data = try? Data(contentsOf: url), let catalog = try? CopyCatalog(data: data) { copy = catalog }
        for name in ["ibm_plex_sans", "ibm_plex_serif"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        }
        view.backgroundColor = paper
        stack.axis = .vertical; stack.spacing = 6; stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([stack.topAnchor.constraint(equalTo: view.topAnchor, constant: 8), stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -5), stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 5), stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -5)])
        let height = view.heightAnchor.constraint(greaterThanOrEqualToConstant: 350); height.priority = .defaultHigh; height.isActive = true; minimumHeight = height
        let toolbar = UIStackView(); toolbar.axis = .horizontal; toolbar.spacing = 8
        let title = UILabel(); title.font = UIFont(name: "IBMPlexSans", size: 21) ?? .systemFont(ofSize: 21, weight: .semibold)
        let wordmark = NSMutableAttributedString(string: "quill.", attributes: [.foregroundColor: ink, .kern: -1.2])
        wordmark.addAttribute(.foregroundColor, value: Self.adaptive(0x234DCE, 0xB6C9FF), range: NSRange(location: 5, length: 1))
        title.attributedText = wordmark; title.accessibilityLabel = "Quill"; toolbar.addArrangedSubview(title)
        endButton.setTitle(c("ios.session.end_short"), for: .normal); endButton.setTitleColor(ink, for: .normal)
        endButton.titleLabel?.font = UIFont(name: "IBMPlexSans", size: 13) ?? .systemFont(ofSize: 13)
        endButton.addTarget(self, action: #selector(endSession), for: .touchUpInside)
        endButton.accessibilityIdentifier = "keyboard.session.end"; endButton.isHidden = true
        let endHeight = endButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44); endHeight.isActive = true; self.endHeight = endHeight
        toolbar.addArrangedSubview(endButton)
        let globe = key("🌐", action: #selector(nextKeyboard)); self.globe = globe; globe.accessibilityLabel = c("ios.keyboard.next")
        globe.setTitle(nil, for: .normal); globe.setImage(UIImage(systemName: "globe"), for: .normal)
        globeHeight = globe.constraints.first { $0.firstAttribute == .height }
        globe.removeTarget(self, action: #selector(nextKeyboard), for: .touchUpInside)
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        globe.widthAnchor.constraint(equalToConstant: 48).isActive = true; toolbar.addArrangedSubview(globe)
        stack.addArrangedSubview(toolbar)
        status.font = UIFont(name: "IBMPlexSans", size: 13) ?? .systemFont(ofSize: 13)
        status.numberOfLines = 3; status.textColor = muted; status.setContentCompressionResistancePriority(.required, for: .vertical)
        status.accessibilityIdentifier = "keyboard.status"; stack.addArrangedSubview(status)
        recordButton.backgroundColor = brand; recordButton.tintColor = .white; recordButton.setTitleColor(.white, for: .normal)
        recordButton.titleLabel?.font = UIFont(name: "IBMPlexSans", size: 15) ?? .systemFont(ofSize: 15)
        recordButton.layer.cornerRadius = 6; recordButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 44).isActive = true
        recordButton.addTarget(self, action: #selector(recordDictation), for: .touchUpInside)
        recordButton.accessibilityIdentifier = "keyboard.record"; recordButton.isHidden = true
        stack.addArrangedSubview(recordButton)
        insertButton.setTitle(c("ios.keyboard.insert"), for: .normal); insertButton.backgroundColor = brand; insertButton.tintColor = .white
        insertButton.titleLabel?.font = UIFont(name: "IBMPlexSans", size: 15) ?? .systemFont(ofSize: 15, weight: .medium)
        insertButton.layer.cornerRadius = 6; let insertHeight = insertButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 48); insertHeight.isActive = true; self.insertHeight = insertHeight
        insertButton.addTarget(self, action: #selector(insertDictation), for: .touchUpInside)
        insertButton.accessibilityIdentifier = "keyboard.insert"; stack.addArrangedSubview(insertButton)
        letters.axis = .vertical; letters.spacing = 5; stack.addArrangedSubview(letters)
        renderKeys(); refresh()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated); connected = false; refresh()
        globe?.isHidden = !needsInputModeSwitchKey
        timer?.invalidate(); timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in self?.refresh() }
    }
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        // A vanished keyboard cannot silently continue a take or later insert into another field.
        if let session, session.phase == .recording, session.captureID == requestedCapture { try? send(.stop, session: session) }
        timer?.invalidate(); timer = nil; current = nil; anchor = nil; requestedCapture = nil; pendingCommand = nil
    }
    override func viewWillLayoutSubviews() { super.viewWillLayoutSubviews(); updateKeyLayout() }
    override func traitCollectionDidChange(_ previousTraitCollection: UITraitCollection?) { super.traitCollectionDidChange(previousTraitCollection); updateKeyLayout() }
    private func updateKeyLayout() {
        guard isViewLoaded else { return }
        let compact = traitCollection.verticalSizeClass == .compact
        guard compact != compactKeys else { return }
        compactKeys = compact
        minimumHeight?.constant = compact ? 260 : 350
        globeHeight?.constant = compact ? 32 : 48
        insertHeight?.constant = compact ? 44 : 48
        endHeight?.constant = compact ? 32 : 44
        status.numberOfLines = compact ? 1 : 3
        renderKeys()
    }
    override func textDidChange(_ textInput: UITextInput?) { super.textDidChange(textInput); refresh() }
    private func refresh() {
        guard !inserting else { return }
        if fullAccess && !connected {
            if let environment { environment.didConnect() } else { KeyboardTransfer.markConnected() }
            connected = true
        }
        if !fullAccess { connected = false }
        current = fullAccess ? (environment != nil ? environment?.peek() : KeyboardTransfer.peek()) : nil
        if current?.isValid() != true { current = nil }
        if fullAccess { if let environment { environment.prune() } else { KeyboardTransfer.prune() } }
        session = fullAccess ? (environment != nil ? environment?.session() : KeyboardSessionBridge.state()) : nil
        if session?.isLive() != true { session = nil }
        if let pendingCommand, session?.acknowledgedCommandID == pendingCommand.id || Date().timeIntervalSince(pendingCommand.createdAt) > 4 { self.pendingCommand = nil }
        endButton.isHidden = session == nil
        recordButton.isHidden = session == nil
        insertButton.isHidden = session != nil && current == nil
        if let session {
            let title: String
            switch session.phase {
            case .recording: title = "ios.session.stop_dictation"
            case .processing: title = "ios.session.processing"
            case .failed: title = "ios.session.retry"
            default: title = "ios.session.dictate"
            }
            recordButton.setTitle(c(title), for: .normal)
            recordButton.isEnabled = fullAccess && fieldAllowed && pendingCommand == nil && session.phase != .processing && current == nil && session.transferID == nil
            recordButton.alpha = recordButton.isEnabled ? 1 : 0.45
            endButton.isEnabled = pendingCommand == nil
            if session.phase == .ready, let transfer = session.transferID, transfer == current?.id,
               session.captureID == requestedCapture, !insertedSessionTransfers.contains(transfer), fieldAllowed,
               let proxy = activeProxy, anchor?.matches(documentID: proxy.documentIdentifier,
                   before: proxy.documentContextBeforeInput, after: proxy.documentContextAfterInput, selection: proxy.selectedText) == true {
                insertedSessionTransfers.insert(transfer)
                insertDictation(); return
            }
        }
        insertButton.isEnabled = current != nil && fieldAllowed
        insertButton.alpha = insertButton.isEnabled ? 1 : 0.45
        if !fieldAllowed { status.text = c("ios.keyboard.field_unavailable") }
        else if !fullAccess { status.text = c("ios.keyboard.full_access_needed") }
        else if let current { status.text = String(current.text.prefix(140)) }
        else if let session {
            switch session.phase {
            case .recording: status.text = c("ios.session.listening")
            case .processing: status.text = c("ios.session.processing_body")
            case .failed: status.text = c("ios.session.failed")
            default: status.text = c("ios.session.keyboard_ready")
            }
        } else { status.text = c("ios.keyboard.record_help") }
    }
    @objc private func insertDictation() {
        guard fullAccess, fieldAllowed, let proxy = activeProxy, let current else { return }
        insertedSessionTransfers.insert(current.id)
        inserting = true
        defer { inserting = false; refresh() }
        do {
            let text = try environment?.consume(current.id) ?? KeyboardTransfer.consume(current.id)
            let insertion = InsertionComposer.compose(text, before: proxy.documentContextBeforeInput, after: proxy.documentContextAfterInput, replacingSelection: !(proxy.selectedText ?? "").isEmpty)
            proxy.insertText(insertion)
            if let environment { environment.didInsert(current.id) } else { KeyboardTransfer.markInserted(current.id) }
            self.current = nil; anchor = nil; requestedCapture = nil
        } catch { status.text = c("ios.keyboard.transfer_error") }
    }
    @discardableResult private func send(_ kind: KeyboardSessionCommand.Kind, session: KeyboardRecordingSession) throws -> KeyboardSessionCommand {
        let command = KeyboardSessionCommand(sessionID: session.id, kind: kind, captureID: kind == .start ? nil : session.captureID)
        if let environment { try environment.command(command) } else { try KeyboardSessionBridge.send(command) }
        pendingCommand = command; return command
    }
    @objc private func recordDictation() {
        guard fullAccess, fieldAllowed, let proxy = activeProxy, let session, session.isLive(), pendingCommand == nil else { return }
        do {
            switch session.phase {
            case .ready:
                guard current == nil else { return }
                let command = try send(.start, session: session)
                requestedCapture = command.id
                anchor = KeyboardInsertionAnchor(documentID: proxy.documentIdentifier, before: proxy.documentContextBeforeInput,
                    after: proxy.documentContextAfterInput, selection: proxy.selectedText)
            case .recording: try send(.stop, session: session)
            case .failed: try send(.retry, session: session)
            default: return
            }
            refresh()
        } catch { pendingCommand = nil; status.text = c("ios.session.unavailable") }
    }
    @objc private func endSession() {
        guard fullAccess, let session, session.isLive(), pendingCommand == nil else { return }
        do { try send(.end, session: session); anchor = nil; requestedCapture = nil; refresh() }
        catch { status.text = c("ios.session.unavailable") }
    }
    private func key(_ title: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal); button.titleLabel?.font = UIFont(name: "IBMPlexSans", size: 19) ?? .systemFont(ofSize: 19)
        button.tintColor = ink; button.backgroundColor = surface; button.layer.cornerRadius = 4
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: compactKeys ? 32 : 48).isActive = true
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }
    private func renderKeys() {
        for view in letters.arrangedSubviews { letters.removeArrangedSubview(view); view.removeFromSuperview() }
        let rows = numbers ? ["1234567890", "-/:;()$&@\"", ".,?!'[]{}"] : ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
        for (index, chars) in rows.enumerated() {
            let row = UIStackView(); row.axis = .horizontal; row.spacing = 4; row.distribution = .fillEqually
            if index == 2 && !numbers { let shift = key(uppercase ? "⇧" : "↑", action: #selector(shift)); shift.accessibilityLabel = c("ios.keyboard.shift"); row.addArrangedSubview(shift) }
            for character in chars {
                let text = uppercase && !numbers ? String(character).uppercased() : String(character)
                row.addArrangedSubview(key(text, action: #selector(typeKey(_:))))
            }
            if index == 2 { let delete = key("⌫", action: #selector(backspace)); delete.accessibilityLabel = c("ios.keyboard.delete"); row.addArrangedSubview(delete) }
            letters.addArrangedSubview(row)
        }
        let bottom = UIStackView(); bottom.axis = .horizontal; bottom.spacing = 5
        let number = key(numbers ? "ABC" : "123", action: #selector(toggleNumbers)); number.widthAnchor.constraint(equalToConstant: 56).isActive = true
        bottom.addArrangedSubview(number)
        let space = key(c("ios.keyboard.space"), action: #selector(space)); bottom.addArrangedSubview(space)
        let enter = key("↵", action: #selector(enter)); enter.accessibilityLabel = c("ios.keyboard.return"); enter.widthAnchor.constraint(equalToConstant: 60).isActive = true; bottom.addArrangedSubview(enter)
        letters.addArrangedSubview(bottom)
    }
    @objc private func typeKey(_ button: UIButton) { guard fieldAllowed, let text = button.currentTitle else { return }; activeProxy?.insertText(text); if uppercase && !numbers { uppercase = false; renderKeys() } }
    @objc private func nextKeyboard() { advanceToNextInputMode() }
    @objc private func shift() { uppercase.toggle(); renderKeys() }
    @objc private func toggleNumbers() { numbers.toggle(); renderKeys() }
    @objc private func backspace() { if fieldAllowed { activeProxy?.deleteBackward() } }
    @objc private func space() { if fieldAllowed { activeProxy?.insertText(" ") } }
    @objc private func enter() { if fieldAllowed { activeProxy?.insertText("\n"); uppercase = true; renderKeys() } }
}
