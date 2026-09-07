import UIKit
import CoreText
import QuillCore

final class KeyboardViewController: UIInputViewController {
    private let brand = UIColor(red: 35/255, green: 77/255, blue: 206/255, alpha: 1)
    private let stack = UIStackView()
    private let status = UILabel()
    private let insertButton = UIButton(type: .system)
    private let letters = UIStackView()
    private var timer: Timer?
    private var current: KeyboardHandoff?
    private var uppercase = true
    private var numbers = false
    private var copy = CopyCatalog(strings: [:])
    private func c(_ key: String) -> String { copy.text(key) }

    override func viewDidLoad() {
        super.viewDidLoad()
        let url = Bundle.main.url(forResource: "strings.default", withExtension: "json", subdirectory: "copy") ?? Bundle.main.url(forResource: "strings.default", withExtension: "json")
        if let url, let data = try? Data(contentsOf: url), let catalog = try? CopyCatalog(data: data) { copy = catalog }
        for name in ["ibm_plex_sans", "ibm_plex_serif"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        }
        view.backgroundColor = .systemGroupedBackground
        stack.axis = .vertical; stack.spacing = 6; stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([stack.topAnchor.constraint(equalTo: view.topAnchor, constant: 8), stack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -5), stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 5), stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -5)])
        let height = view.heightAnchor.constraint(greaterThanOrEqualToConstant: 350); height.priority = .defaultHigh; height.isActive = true
        let toolbar = UIStackView(); toolbar.axis = .horizontal; toolbar.spacing = 8
        let title = UILabel(); title.text = "Quill"; title.font = UIFont(name: "IBMPlexSans", size: 21) ?? .systemFont(ofSize: 21, weight: .medium)
        title.textColor = brand; toolbar.addArrangedSubview(title)
        let globe = key("🌐", action: #selector(nextKeyboard)); globe.accessibilityLabel = c("ios.keyboard.next")
        globe.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)
        globe.widthAnchor.constraint(equalToConstant: 48).isActive = true; toolbar.addArrangedSubview(globe)
        stack.addArrangedSubview(toolbar)
        status.font = UIFont(name: "IBMPlexSans", size: 13) ?? .systemFont(ofSize: 13)
        status.numberOfLines = 3; status.textColor = .secondaryLabel; status.setContentCompressionResistancePriority(.required, for: .vertical)
        status.accessibilityIdentifier = "keyboard.status"; stack.addArrangedSubview(status)
        insertButton.setTitle(c("ios.keyboard.insert"), for: .normal); insertButton.backgroundColor = brand; insertButton.tintColor = .white
        insertButton.titleLabel?.font = UIFont(name: "IBMPlexSans", size: 15) ?? .systemFont(ofSize: 15, weight: .medium)
        insertButton.layer.cornerRadius = 12; insertButton.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
        insertButton.addTarget(self, action: #selector(insertDictation), for: .touchUpInside)
        insertButton.accessibilityIdentifier = "keyboard.insert"; stack.addArrangedSubview(insertButton)
        letters.axis = .vertical; letters.spacing = 5; stack.addArrangedSubview(letters)
        renderKeys(); refresh()
    }
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated); refresh()
        timer?.invalidate(); timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in self?.refresh() }
    }
    override func viewDidDisappear(_ animated: Bool) { super.viewDidDisappear(animated); timer?.invalidate(); timer = nil; current = nil }
    override func textDidChange(_ textInput: UITextInput?) { super.textDidChange(textInput); refresh() }
    private func refresh() {
        current = hasFullAccess ? KeyboardTransfer.peek() : nil
        if hasFullAccess { KeyboardTransfer.prune() }
        let selected = !(textDocumentProxy.selectedText ?? "").isEmpty
        insertButton.isEnabled = current != nil && !selected
        insertButton.alpha = insertButton.isEnabled ? 1 : 0.45
        if selected { status.text = c("ios.keyboard.selection") }
        else if !hasFullAccess { status.text = c("ios.keyboard.full_access_needed") }
        else if let current { status.text = String(current.text.prefix(140)) }
        else { status.text = c("ios.keyboard.record_help") }
    }
    @objc private func insertDictation() {
        guard hasFullAccess, (textDocumentProxy.selectedText ?? "").isEmpty, let current else { return }
        do {
            let text = try KeyboardTransfer.consume(current.id)
            let insertion = InsertionComposer.compose(text, before: textDocumentProxy.documentContextBeforeInput, after: textDocumentProxy.documentContextAfterInput)
            textDocumentProxy.insertText(insertion)
            self.current = nil; refresh()
        } catch { status.text = c("ios.keyboard.transfer_error"); refresh() }
    }
    private func key(_ title: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal); button.titleLabel?.font = UIFont(name: "IBMPlexSans", size: 19) ?? .systemFont(ofSize: 19)
        button.tintColor = .label; button.backgroundColor = .secondarySystemGroupedBackground; button.layer.cornerRadius = 6
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: 48).isActive = true
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
    @objc private func typeKey(_ button: UIButton) { guard let text = button.currentTitle else { return }; textDocumentProxy.insertText(text); if uppercase && !numbers { uppercase = false; renderKeys() } }
    @objc private func nextKeyboard() { advanceToNextInputMode() }
    @objc private func shift() { uppercase.toggle(); renderKeys() }
    @objc private func toggleNumbers() { numbers.toggle(); renderKeys() }
    @objc private func backspace() { textDocumentProxy.deleteBackward() }
    @objc private func space() { textDocumentProxy.insertText(" ") }
    @objc private func enter() { textDocumentProxy.insertText("\n"); uppercase = true; renderKeys() }
}
