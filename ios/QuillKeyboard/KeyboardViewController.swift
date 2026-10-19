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
}

final class KeyboardViewController: UIInputViewController {
    var environment: KeyboardEnvironment?
    private var activeProxy: (any UITextDocumentProxy)? { if let environment { return environment.proxy() }; return textDocumentProxy }
    private var fullAccess: Bool { environment?.fullAccess() ?? hasFullAccess }
    private var fieldAllowed: Bool {
        guard let proxy = activeProxy else { return false }
        return proxy.isSecureTextEntry != true && proxy.keyboardType != .phonePad && proxy.keyboardType != .namePhonePad
    }
    private let brand = UIColor(red: 35/255, green: 77/255, blue: 206/255, alpha: 1)
    private static func adaptive(_ light: UInt32, _ dark: UInt32) -> UIColor {
        UIColor { trait in
            let value = trait.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((value >> 16) & 255) / 255, green: CGFloat((value >> 8) & 255) / 255, blue: CGFloat(value & 255) / 255, alpha: 1)
        }
    }
    private let ink = KeyboardViewController.adaptive(0x202522, 0xE0E7F6)
    private let paper = KeyboardViewController.adaptive(0xF5F7F5, 0x11141A)
    private let surface = KeyboardViewController.adaptive(0xFFFFFF, 0x171B23)
    private let stack = UIStackView()
    private let status = UILabel()
    private let insertButton = UIButton(type: .system)
    private let letters = UIStackView()
    private var timer: Timer?
    private var current: KeyboardHandoff?
    private var uppercase = true
    private var numbers = false
    private var connected = false
    private var globe: UIButton?
    private var minimumHeight: NSLayoutConstraint?
    private var globeHeight: NSLayoutConstraint?
    private var insertHeight: NSLayoutConstraint?
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
        let globe = key("🌐", action: #selector(nextKeyboard)); self.globe = globe; globe.accessibilityLabel = c("ios.keyboard.next")
        globe.setTitle(nil, for: .normal); globe.setImage(UIImage(systemName: "globe"), for: .normal)
        globeHeight = globe.constraints.first { $0.firstAttribute == .height }
        globe.removeTarget(self, action: #selector(nextKeyboard), for: .touchUpInside)
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        globe.widthAnchor.constraint(equalToConstant: 48).isActive = true; toolbar.addArrangedSubview(globe)
        stack.addArrangedSubview(toolbar)
        status.font = UIFont(name: "IBMPlexSans", size: 13) ?? .systemFont(ofSize: 13)
        status.numberOfLines = 3; status.textColor = .secondaryLabel; status.setContentCompressionResistancePriority(.required, for: .vertical)
        status.accessibilityIdentifier = "keyboard.status"; stack.addArrangedSubview(status)
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
    override func viewDidDisappear(_ animated: Bool) { super.viewDidDisappear(animated); timer?.invalidate(); timer = nil; current = nil }
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
        status.numberOfLines = compact ? 1 : 3
        renderKeys()
    }
    override func textDidChange(_ textInput: UITextInput?) { super.textDidChange(textInput); refresh() }
    private func refresh() {
        if fullAccess && !connected {
            if let environment { environment.didConnect() } else { KeyboardTransfer.markConnected() }
            connected = true
        }
        if !fullAccess { connected = false }
        current = fullAccess ? (environment != nil ? environment?.peek() : KeyboardTransfer.peek()) : nil
        if current?.isValid() != true { current = nil }
        if fullAccess { if let environment { environment.prune() } else { KeyboardTransfer.prune() } }
        insertButton.isEnabled = current != nil && fieldAllowed
        insertButton.alpha = insertButton.isEnabled ? 1 : 0.45
        if !fieldAllowed { status.text = c("ios.keyboard.field_unavailable") }
        else if !fullAccess { status.text = c("ios.keyboard.full_access_needed") }
        else if let current { status.text = String(current.text.prefix(140)) }
        else { status.text = c("ios.keyboard.record_help") }
    }
    @objc private func insertDictation() {
        guard fullAccess, fieldAllowed, let proxy = activeProxy, let current else { return }
        do {
            let text = try environment?.consume(current.id) ?? KeyboardTransfer.consume(current.id)
            let insertion = InsertionComposer.compose(text, before: proxy.documentContextBeforeInput, after: proxy.documentContextAfterInput, replacingSelection: !(proxy.selectedText ?? "").isEmpty)
            proxy.insertText(insertion)
            if let environment { environment.didInsert(current.id) } else { KeyboardTransfer.markInserted(current.id) }
            self.current = nil; refresh()
        } catch { refresh(); status.text = c("ios.keyboard.transfer_error") }
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
