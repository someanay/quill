import UIKit
import Combine

// Quill custom keyboard — the iOS equivalent of Android's "dictate into any focused field": a keyboard
// the user switches to, with a mic button that runs the SAME DictationEngine and inserts the transcript
// via UITextDocumentProxy. Requires "Allow Full Access" (microphone + network) — enabled by the user in
// Settings › General › Keyboard › Keyboards › Quill.
final class KeyboardViewController: UIInputViewController {
    private let engine = DictationEngine()
    private let statusLabel = UILabel()
    private let micButton = UIButton(type: .system)
    private let nextKeyboardButton = UIButton(type: .system)
    private var cancellable: AnyCancellable?
    private var runTask: Task<Void, Never>?

    override func viewDidLoad() {
        super.viewDidLoad()
        buildUI()
        // Live status while listening/transcribing.
        cancellable = engine.$statusText.receive(on: RunLoop.main).sink { [weak self] in self?.statusLabel.text = $0 }
    }

    private func buildUI() {
        view.backgroundColor = UIColor.secondarySystemBackground

        nextKeyboardButton.setTitle("🌐", for: .normal)
        nextKeyboardButton.titleLabel?.font = .systemFont(ofSize: 22)
        nextKeyboardButton.addTarget(self, action: #selector(handleInputModeList(from:with:)), for: .allTouchEvents)

        micButton.setTitle("🎙️  Dictate", for: .normal)
        micButton.titleLabel?.font = .systemFont(ofSize: 20, weight: .semibold)
        micButton.backgroundColor = .systemBlue
        micButton.setTitleColor(.white, for: .normal)
        micButton.layer.cornerRadius = 12
        micButton.addTarget(self, action: #selector(micTapped), for: .touchUpInside)

        statusLabel.text = "Tap to dictate"
        statusLabel.font = .systemFont(ofSize: 13)
        statusLabel.textColor = .secondaryLabel
        statusLabel.textAlignment = .center

        let deleteButton = UIButton(type: .system)
        deleteButton.setTitle("⌫", for: .normal)
        deleteButton.titleLabel?.font = .systemFont(ofSize: 22)
        deleteButton.addTarget(self, action: #selector(deleteTapped), for: .touchUpInside)

        for v in [nextKeyboardButton, micButton, statusLabel, deleteButton] {
            v.translatesAutoresizingMaskIntoConstraints = false
            view.addSubview(v)
        }
        NSLayoutConstraint.activate([
            view.heightAnchor.constraint(equalToConstant: 216),
            micButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            micButton.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -8),
            micButton.widthAnchor.constraint(equalToConstant: 220),
            micButton.heightAnchor.constraint(equalToConstant: 56),
            statusLabel.topAnchor.constraint(equalTo: micButton.bottomAnchor, constant: 12),
            statusLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            nextKeyboardButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            nextKeyboardButton.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -12),
            deleteButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            deleteButton.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -12),
        ])
    }

    @objc private func micTapped() {
        guard hasFullAccess else {
            statusLabel.text = "Enable Full Access for Quill in Settings"
            return
        }
        let host = "Keyboard"
        runTask = Task { @MainActor in
            if let text = await engine.toggle(application: host) {
                textDocumentProxy.insertText(text)
            }
        }
    }

    @objc private func deleteTapped() { textDocumentProxy.deleteBackward() }

    deinit { runTask?.cancel() }
}
