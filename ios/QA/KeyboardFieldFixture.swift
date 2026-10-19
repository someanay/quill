import UIKit

/// Standalone QA host with no App Group, network transport, Quill imports or persistence.
@main @MainActor final class KeyboardFieldFixture: UIResponder, UIApplicationDelegate {
    var window: UIWindow?
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = KeyboardFieldViewController()
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}

@MainActor private final class KeyboardFieldViewController: UIViewController {
    private let editor = UITextView()
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        let title = UILabel()
        title.text = "Quill keyboard integration fixture"
        title.font = .preferredFont(forTextStyle: .headline)
        title.numberOfLines = 0
        title.translatesAutoresizingMaskIntoConstraints = false
        editor.text = "Before"
        editor.font = .preferredFont(forTextStyle: .body)
        editor.autocorrectionType = .no
        editor.spellCheckingType = .no
        editor.smartQuotesType = .no
        editor.smartDashesType = .no
        editor.accessibilityIdentifier = "quill.qa.editor"
        editor.accessibilityLabel = "Synthetic integration text"
        editor.layer.borderWidth = 1
        editor.layer.borderColor = UIColor.separator.cgColor
        editor.translatesAutoresizingMaskIntoConstraints = false
        let protected = UITextField(); protected.isSecureTextEntry = true; protected.placeholder = "Protected field"; protected.borderStyle = .roundedRect; protected.accessibilityIdentifier = "quill.qa.secure"
        let phone = UITextField(); phone.keyboardType = .phonePad; phone.placeholder = "Phone number"; phone.borderStyle = .roundedRect; phone.accessibilityIdentifier = "quill.qa.phone"
        let fields = UIStackView(arrangedSubviews: [protected, phone]); fields.axis = .horizontal; fields.spacing = 12; fields.distribution = .fillEqually; fields.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(title)
        view.addSubview(fields)
        view.addSubview(editor)
        NSLayoutConstraint.activate([
            title.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            title.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            title.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            fields.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 16),
            fields.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            fields.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            fields.heightAnchor.constraint(equalToConstant: 44),
            editor.topAnchor.constraint(equalTo: fields.bottomAnchor, constant: 16),
            editor.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            editor.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            editor.bottomAnchor.constraint(equalTo: view.keyboardLayoutGuide.topAnchor, constant: -16)
        ])
    }
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        editor.becomeFirstResponder()
        editor.selectedRange = NSRange(location: editor.text.utf16.count, length: 0)
    }
}
