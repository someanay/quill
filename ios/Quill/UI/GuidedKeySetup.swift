import SwiftUI
import QuillCore

/// The same guided setup appears in onboarding, speech selection and adding an extra saved key.
struct GuidedKeySetup: View {
    @EnvironmentObject private var model: AppModel
    var activateSpeechProvider = true
    var prefillSavedKey = false
    var initialProvider: Provider = .gemini
    var onSaved: () -> Void = {}
    var body: some View {
        GuidedKeyRows(model: model, activateSpeechProvider: activateSpeechProvider, prefillSavedKey: prefillSavedKey, initialProvider: initialProvider, onSaved: onSaved)
    }
}

private struct GuidedKeyRows: View {
    @ObservedObject var model: AppModel
    @StateObject private var setup: KeySetupController
    @State private var revealed = false
    @State private var canPaste = UIPasteboard.general.hasStrings
    @Environment(\.scenePhase) private var scenePhase
    @FocusState private var keyFocused: Bool
    let onSaved: () -> Void

    init(model: AppModel, activateSpeechProvider: Bool, prefillSavedKey: Bool, initialProvider: Provider, onSaved: @escaping () -> Void) {
        self.model = model; self.onSaved = onSaved
        _setup = StateObject(wrappedValue: KeySetupController(model: model, activateSpeechProvider: activateSpeechProvider, prefillSavedKey: prefillSavedKey, initialProvider: initialProvider))
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(model.c("onboarding.key.choose")).font(Q.sans(17)).fontWeight(.medium)
            QCard {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(KeySetupPolicy.guidedProviders) { provider in
                        if provider != .gemini { Divider().padding(.vertical, 4) }
                        Button { revealed = false; setup.toggle(provider) } label: {
                            HStack(spacing: 8) {
                                Text(model.c(KeySetupPolicy.titleKey(provider))).font(Q.sans(14)).multilineTextAlignment(.leading)
                                Spacer(minLength: 4)
                                Image(systemName: setup.expanded == provider ? "chevron.up" : "chevron.down")
                            }.foregroundStyle(Q.ink).frame(minHeight: 48).contentShape(Rectangle())
                        }.buttonStyle(.plain).disabled(setup.phase == .saving)
                            .accessibilityIdentifier("key.provider.\(provider.rawValue)")
                            .accessibilityValue(model.c(setup.expanded == provider ? "onboarding.key.expanded" : "onboarding.key.collapsed"))
                            .accessibilityAddTraits(setup.expanded == provider ? [.isSelected] : [])
                        if setup.expanded == provider {
                            expanded(provider).padding(.bottom, 12)
                        }
                    }
                }
            }
            if setup.storageError { Text(model.c("onboarding.key.storage_error")).font(Q.sans(13)).foregroundStyle(Q.danger) }
        }.onAppear { canPaste = UIPasteboard.general.hasStrings; setup.scheduleValidation() }
            .onDisappear { setup.stopValidation() }
            .onChange(of: scenePhase) { _, phase in if phase == .active { canPaste = UIPasteboard.general.hasStrings } }
            .onReceive(NotificationCenter.default.publisher(for: UIPasteboard.changedNotification)) { _ in canPaste = UIPasteboard.general.hasStrings }
    }
    @ViewBuilder private func expanded(_ provider: Provider) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ProviderInstructions(provider: provider).environmentObject(model)
            let binding = Binding<String>(get: { setup.drafts[provider] ?? "" }, set: { setup.update($0, provider: provider) })
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Image(systemName: "key").foregroundStyle(Q.muted).accessibilityHidden(true)
                    Group {
                        if revealed { TextField(KeySetupPolicy.mask(provider), text: binding) }
                        else { SecureField(KeySetupPolicy.mask(provider), text: binding) }
                    }.font(Q.sans(14)).textInputAutocapitalization(.never).autocorrectionDisabled().privacySensitive()
                        .focused($keyFocused).submitLabel(.done).onSubmit { keyFocused = false }
                        .accessibilityLabel(model.c("onboarding.key.field_label"))
                        .accessibilityIdentifier("key.candidate")
                    Button { if let text = UIPasteboard.general.string { setup.update(text, provider: provider) } } label: { Text(model.c("onboarding.key.paste")).font(Q.sans(13)).frame(minWidth: 44, minHeight: 48) }
                        .disabled(!canPaste || setup.phase == .saving).accessibilityIdentifier("key.paste")
                    Button { revealed.toggle() } label: { Image(systemName: revealed ? "eye.slash" : "eye").frame(width: 44, height: 48) }
                        .accessibilityLabel(model.c(revealed ? "a11y.hide_key" : "a11y.show_key"))
                }.padding(.leading, 10).padding(.trailing, 3)
                    .background(Q.surface, in: RoundedRectangle(cornerRadius: Q.fieldRadius))
                    .overlay(RoundedRectangle(cornerRadius: Q.fieldRadius).stroke(setup.phase == .rejected ? Q.danger : Q.line, lineWidth: 1.5))
                    .disabled(setup.phase == .saving)
            }
            if !binding.wrappedValue.isEmpty && !KeySetupPolicy.plausible(provider, binding.wrappedValue) {
                Text(model.c("onboarding.key.format_help", KeySetupPolicy.mask(provider))).font(Q.sans(12)).foregroundStyle(Q.muted)
            }
            if setup.phase == .checking {
                ProgressView().progressViewStyle(.linear).frame(maxWidth: .infinity, alignment: .leading)
                Text(model.c("onboarding.key.verifying")).font(Q.sans(12)).foregroundStyle(Q.muted)
            }
            if setup.phase == .verified {
                Label(model.c("onboarding.key.verified"), systemImage: "checkmark.circle.fill").font(Q.sans(13)).foregroundStyle(Q.success).accessibilityIdentifier("key.verified")
            }
            if setup.phase == .rejected { Text(model.c("key.error.invalid")).font(Q.sans(12)).foregroundStyle(Q.danger) }
            if setup.phase == .unverified {
                Text(model.c("onboarding.key.unverified")).font(Q.sans(12)).foregroundStyle(Q.danger)
                Button(model.c("onboarding.key.retry")) { setup.scheduleValidation() }.frame(minHeight: 48)
            }
            QButton(title: model.c("onboarding.key.save_provider", model.c(KeySetupPolicy.titleKey(provider)).components(separatedBy: " (").first ?? provider.title)) {
                Task { if await setup.save() { onSaved() } }
            }.disabled(!setup.canSave).opacity(setup.canSave ? 1 : 0.5).accessibilityIdentifier("onboarding.key.verify")
            if setup.phase == .saving { ProgressView().progressViewStyle(.linear) }
        }
    }
}

private struct ProviderInstructions: View {
    @EnvironmentObject private var model: AppModel
    let provider: Provider
    private var steps: [String] { model.c("onboarding.key.guide.\(provider.rawValue)_steps").components(separatedBy: .newlines).filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty } }
    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                HStack(alignment: .top, spacing: 4) {
                    Text("\(index + 1))")
                    if let token = step.split(separator: " ").first(where: { $0.hasPrefix("https://") }), let url = URL(string: String(token)) {
                        Link(step, destination: url).foregroundStyle(Q.brandInk)
                    } else { Text(step) }
                }.font(Q.sans(13)).fixedSize(horizontal: false, vertical: true)
            }
        }.padding(.bottom, 8)
    }
}
