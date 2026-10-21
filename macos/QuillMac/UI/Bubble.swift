import SwiftUI
import AppKit
import Combine
import QuillCore

struct BubbleFace: View {
    var state: DictationState
    var diameter: Double = 56
    @ObservedObject var meter: AudioMeterState
    private var amplitude: Double { meter.level }
    var dark = false
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var pulse = false
    var body: some View {
        ZStack {
            if state == .recording { Circle().fill(QuillTheme.brand.opacity(0.18)).frame(width: diameter + (pulse && !reduced ? 20 : 12), height: diameter + (pulse && !reduced ? 20 : 12)) }
            Circle().fill(LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing))
                .overlay(Circle().strokeBorder(Color.white.opacity(0.92), lineWidth: 2.5)).frame(width: diameter, height: diameter)
                .shadow(color: .black.opacity(0.3), radius: 10, y: 6)
            if state == .recording {
                HStack(spacing: diameter * 0.065) { ForEach(0..<5, id: \.self) { index in
                    Capsule().fill(.white).frame(width: 2.2, height: diameter * (0.16 + (reduced ? 0.25 : 0.18 + amplitude * 0.65) * (1 - abs(Double(index - 2)) * 0.2)))
                } }
            }
            else if state == .transcribing && !reduced { ProgressView().controlSize(.small).colorScheme(.dark) }
            else { Image(systemName: state == .paste ? "doc.on.clipboard" : state == .error ? "exclamationmark" : state == .transcribing ? "ellipsis" : "mic.fill").font(.system(size: diameter * 0.44)).foregroundStyle(.white) }
        }.frame(width: diameter + 26, height: diameter + 26)
        .onAppear { updatePulse() }
        .onChange(of: state) { _ in updatePulse() }
        .onChange(of: reduced) { _ in updatePulse() }
        .onDisappear { pulse = false }
    }
    private func updatePulse() {
        if state == .recording && !reduced { withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true } }
        else { var transaction = Transaction(); transaction.disablesAnimations = true; withTransaction(transaction) { pulse = false } }
    }
    private var gradient: [Color] {
        if state == .error { return [Color(hex: 0xD6453D), Color(hex: 0xE8635B)] }
        if state == .transcribing { return [Color(hex: 0x3E5579), Color(hex: 0x526F9E)] }
        if dark && state != .recording { return [Color(hex: 0x202522), Color(hex: 0x343E37)] }
        return [Color(hex: 0x234DCE), Color(hex: 0x244FD8)]
    }
}

final class BubblePanel: NSPanel {
    override var canBecomeKey: Bool { false }
    override var canBecomeMain: Bool { false }
}
@MainActor final class BubbleHitView: NSHostingView<BubbleFace> {
    var clicked: (() -> Void)?
    var longPressed: (() -> Void)?
    var dragged: ((NSPoint) -> Void)?
    var dragEnded: (() -> Void)?
    private var origin = NSPoint.zero
    private var moved = false
    private var held = false
    private var holdTimer: Timer?
    override func hitTest(_ point: NSPoint) -> NSView? { bounds.contains(point) ? self : nil }
    override func mouseDown(with event: NSEvent) {
        origin = NSEvent.mouseLocation; moved = false; held = false
        holdTimer = Timer.scheduledTimer(withTimeInterval: 0.65, repeats: false) { [weak self] _ in Task { @MainActor in guard let self, !self.moved else { return }; self.held = true; self.longPressed?() } }
    }
    override func mouseDragged(with event: NSEvent) {
        let point = NSEvent.mouseLocation
        if hypot(point.x-origin.x, point.y-origin.y) > 5 { moved = true; holdTimer?.invalidate() }
        if moved { dragged?(point) }
    }
    override func mouseUp(with event: NSEvent) { holdTimer?.invalidate(); if moved { dragEnded?() } else if !held { clicked?() } }
    override func accessibilityPerformPress() -> Bool { clicked?(); return true }
}

@MainActor final class BubbleController {
    static func shouldShow(state: DictationState, enabled: Bool, visible: Bool) -> Bool { enabled && visible || state != .idle }
    private let settings: SettingsStore
    private let dictation: Dictation
    private var panel: BubblePanel?
    private var companion: BubblePanel?
    private var bin: BubblePanel?
    private var view: BubbleHitView?
    private var subscriptions = Set<AnyCancellable>()
    private var returnTask: Task<Void,Never>?
    private var hiddenByDrag = false
    init(settings: SettingsStore, dictation: Dictation) {
        self.settings = settings; self.dictation = dictation
        settings.$value.sink { [weak self] _ in DispatchQueue.main.async { self?.refresh() } }.store(in: &subscriptions)
        dictation.$state.sink { [weak self] _ in DispatchQueue.main.async { self?.refresh() } }.store(in: &subscriptions)
        NotificationCenter.default.publisher(for: .quillExplicitVisibility).sink { [weak self] _ in self?.hiddenByDrag = false; self?.returnTask?.cancel() }.store(in: &subscriptions)
    }
    func refresh() {
        guard !AppPaths.testMode else { return }
        if settings.value.bubbleVisible { hiddenByDrag = false; returnTask?.cancel() }
        guard Self.shouldShow(state: dictation.state, enabled: settings.value.bubbleActive, visible: settings.value.bubbleVisible) else { panel?.orderOut(nil); companion?.orderOut(nil); bin?.orderOut(nil); return }
        if panel == nil { makePanel() }
        let dark = settings.value.appTheme == "Dark" || (settings.value.appTheme == "Match system" && NSApp.effectiveAppearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua)
        view?.rootView = BubbleFace(state: dictation.state, diameter: settings.value.bubbleDiameter, meter: dictation.meter, dark: dark)
        view?.setAccessibilityLabel(Copy.text(dictation.state == .recording ? "mac.dictation.stop" : dictation.state == .paste ? "mac.dictation.paste" : "mac.dictation.start"))
        if panel?.isVisible == false { panel?.orderFrontRegardless() }
        if dictation.state == .recording || dictation.state == .transcribing || dictation.state == .error || dictation.state == .paste { showCompanion() } else { companion?.orderOut(nil) }
    }
    private func makePanel() {
        let panel = BubblePanel(contentRect: NSRect(x: 0, y: 0, width: 96, height: 96), styleMask: [.borderless, .nonactivatingPanel], backing: .buffered, defer: false)
        panel.isFloatingPanel = true; panel.level = .floating; panel.isOpaque = false; panel.backgroundColor = .clear; panel.hasShadow = false
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary]; panel.hidesOnDeactivate = false
        let view = BubbleHitView(rootView: BubbleFace(state: dictation.state, diameter: settings.value.bubbleDiameter, meter: dictation.meter))
        view.setAccessibilityRole(.button); view.setAccessibilityElement(true)
        view.clicked = { [weak self] in self?.dictation.toggle() }
        view.longPressed = { [weak self] in guard let self else { return }; if self.dictation.state == .recording { self.dictation.stop() } else { self.dictation.start(toClipboard: true) } }
        view.dragged = { [weak self] point in guard let self else { return }; self.panel?.setFrameOrigin(NSPoint(x: point.x - 48, y: point.y - 48)); self.positionCompanion(); self.showBin(at: point) }
        view.dragEnded = { [weak self] in self?.finishDrag() }
        panel.contentView = view; self.panel = panel; self.view = view
        if let screen = NSScreen.main { panel.setFrameOrigin(NSPoint(x: screen.visibleFrame.maxX - 100, y: screen.visibleFrame.minY + screen.visibleFrame.height * 0.6)) }
    }
    private func finishDrag() {
        bin?.orderOut(nil)
        guard let panel, let screen = NSScreen.screens.first(where: { $0.frame.contains(NSEvent.mouseLocation) }) ?? NSScreen.main else { return }
        let bottomDistance = panel.frame.midY - settings.value.bubbleDiameter / 2 - screen.visibleFrame.minY
        if BubbleDismissGeometry.proximity(bottomDistance: bottomDistance) == 1 {
            hiddenByDrag = true; settings.value.bubbleVisible = false
            returnTask?.cancel(); returnTask = Task { try? await Task.sleep(nanoseconds: 600_000_000_000); guard !Task.isCancelled, hiddenByDrag, settings.value.bubbleActive else { return }; settings.value.bubbleVisible = true }
        } else {
            let frame = screen.visibleFrame
            let x = panel.frame.midX < frame.midX ? frame.minX : frame.maxX - panel.frame.width
            panel.setFrameOrigin(NSPoint(x: x, y: min(frame.maxY - panel.frame.height, max(frame.minY, panel.frame.minY)))); positionCompanion()
        }
    }
    private func showBin(at point: NSPoint) {
        guard let panel, let screen = NSScreen.screens.first(where: { $0.frame.contains(point) }) ?? NSScreen.main else { return }
        let progress = BubbleDismissGeometry.proximity(bottomDistance: panel.frame.midY - settings.value.bubbleDiameter / 2 - screen.visibleFrame.minY)
        guard progress > 0 else { bin?.orderOut(nil); return }
        if bin == nil {
            let target = BubblePanel(contentRect: NSRect(x: 0, y: 0, width: 210, height: 125), styleMask: [.borderless, .nonactivatingPanel], backing: .buffered, defer: false)
            target.isOpaque = false; target.backgroundColor = .clear; target.level = .floating; target.ignoresMouseEvents = true; target.hidesOnDeactivate = false
            target.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]; bin = target
        }
        bin?.contentView = NSHostingView(rootView: BubbleBin(progress: progress))
        bin?.setFrameOrigin(NSPoint(x: screen.visibleFrame.midX - 105, y: screen.visibleFrame.minY + 22)); bin?.orderFrontRegardless()
    }
    private func showCompanion() {
        if companion == nil {
            let panel = BubblePanel(contentRect: NSRect(x: 0, y: 0, width: 290, height: 160), styleMask: [.borderless, .nonactivatingPanel], backing: .buffered, defer: false)
            panel.isOpaque = false; panel.backgroundColor = .clear; panel.level = .floating; panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]; panel.hidesOnDeactivate = false
            panel.contentView = NSHostingView(rootView: BubbleCompanion().environmentObject(dictation)); companion = panel
        }
        positionCompanion(); if companion?.isVisible == false { companion?.orderFrontRegardless() }
    }
    private func positionCompanion() {
        guard let panel, let companion, let screen = panel.screen ?? NSScreen.main else { return }
        let right = panel.frame.midX < screen.visibleFrame.midX
        companion.setFrameOrigin(NSPoint(x: right ? panel.frame.maxX : panel.frame.minX - companion.frame.width, y: min(screen.visibleFrame.maxY - companion.frame.height, max(screen.visibleFrame.minY, panel.frame.midY - companion.frame.height / 2))))
    }
}

struct BubbleBin: View {
    var progress: Double
    @Environment(\.accessibilityReduceMotion) private var reduced
    var body: some View {
        VStack(spacing: 9) {
            Image(systemName: "trash").font(.system(size: 26, weight: .medium)).frame(width: 60, height: 60)
                .background(progress == 1 ? QuillTheme.error : Color(hex: 0x202522), in: Circle()).overlay(Circle().strokeBorder(.white, lineWidth: 2))
            CopyText(progress == 1 ? "bubble.bin.label_active" : "bubble.bin.label").font(QuillTheme.sans(12.5)).fontWeight(.semibold).padding(.horizontal, 11).padding(.vertical, 5).background(Color(hex: 0x202522), in: Capsule())
        }.foregroundStyle(.white).shadow(color: .black.opacity(0.25), radius: 8, y: 3).opacity(progress)
        .scaleEffect(reduced ? 1 : 0.8 + progress * 0.2).padding(10).allowsHitTesting(false)
    }
}

struct BubbleCompanion: View {
    @EnvironmentObject var dictation: Dictation
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if dictation.state == .recording { LiveMeter(meter: dictation.meter, bars: 6, showTime: true) }
            if dictation.state == .transcribing { HStack { ProgressView().controlSize(.small); CopyText("mac.status.transcribing") } }
            if !dictation.message.isEmpty { CopyText(dictation.message).font(.caption).lineLimit(3) }
            HStack {
                Button(Copy.text("action.cancel")) { dictation.cancel() }; Spacer()
                if dictation.state == .error && dictation.canRetry { Button(Copy.text("mac.action.retry")) { dictation.retry() } }
                if dictation.state == .paste { Button(Copy.text("history.sheet.copy")) { dictation.copyTranscript() }; Button(Copy.text("mac.dictation.paste")) { dictation.paste() } }
            }
        }.padding(16).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16)).padding(6)
    }
}
