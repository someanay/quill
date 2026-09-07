import SwiftUI
import AppKit
import Combine

struct BubbleFace: View {
    var state: DictationState
    var diameter: Double = 56
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var pulse = false
    var body: some View {
        ZStack {
            if state == .recording { Circle().fill(QuillTheme.brand.opacity(0.18)).frame(width: diameter + (pulse && !reduced ? 20 : 12), height: diameter + (pulse && !reduced ? 20 : 12)) }
            Circle().fill((state == .error ? QuillTheme.error : state == .transcribing ? Color(hex: 0x3E5579) : QuillTheme.brand).gradient)
                .overlay(Circle().strokeBorder(Color.white.opacity(0.92), lineWidth: 2.5)).frame(width: diameter, height: diameter)
                .shadow(color: .black.opacity(0.3), radius: 10, y: 6)
            if state == .transcribing && !reduced { ProgressView().controlSize(.small).colorScheme(.dark) }
            else { Image(systemName: state == .paste ? "doc.on.clipboard" : state == .error ? "exclamationmark" : state == .transcribing ? "ellipsis" : "mic.fill").font(.system(size: diameter * 0.44)).foregroundStyle(.white) }
        }.frame(width: diameter + 26, height: diameter + 26)
        .onAppear { if !reduced { withAnimation(.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) { pulse = true } } }
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
    private let settings: SettingsStore
    private let dictation: Dictation
    private var panel: BubblePanel?
    private var companion: BubblePanel?
    private var view: BubbleHitView?
    private var subscriptions = Set<AnyCancellable>()
    private var returnTask: Task<Void,Never>?
    private var hiddenByDrag = false
    init(settings: SettingsStore, dictation: Dictation) {
        self.settings = settings; self.dictation = dictation
        settings.$value.sink { [weak self] _ in DispatchQueue.main.async { self?.refresh() } }.store(in: &subscriptions)
        dictation.$state.sink { [weak self] _ in DispatchQueue.main.async { self?.refresh() } }.store(in: &subscriptions)
    }
    func refresh() {
        guard !AppPaths.testMode else { return }
        if settings.value.bubbleVisible { hiddenByDrag = false; returnTask?.cancel() }
        guard settings.value.bubbleActive && settings.value.bubbleVisible else { panel?.orderOut(nil); companion?.orderOut(nil); return }
        if panel == nil { makePanel() }
        view?.rootView = BubbleFace(state: dictation.state, diameter: settings.value.bubbleDiameter)
        view?.setAccessibilityLabel(Copy.text(dictation.state == .recording ? "mac.dictation.stop" : dictation.state == .paste ? "mac.dictation.paste" : "mac.dictation.start"))
        panel?.orderFrontRegardless()
        if dictation.state == .recording || dictation.state == .transcribing || dictation.state == .error || dictation.state == .paste { showCompanion() } else { companion?.orderOut(nil) }
    }
    private func makePanel() {
        let panel = BubblePanel(contentRect: NSRect(x: 0, y: 0, width: 96, height: 96), styleMask: [.borderless, .nonactivatingPanel], backing: .buffered, defer: false)
        panel.isFloatingPanel = true; panel.level = .floating; panel.isOpaque = false; panel.backgroundColor = .clear; panel.hasShadow = false
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary]; panel.hidesOnDeactivate = false
        let view = BubbleHitView(rootView: BubbleFace(state: dictation.state, diameter: settings.value.bubbleDiameter))
        view.setAccessibilityRole(.button); view.setAccessibilityElement(true)
        view.clicked = { [weak self] in self?.dictation.toggle() }
        view.longPressed = { [weak self] in self?.dictation.start(toClipboard: true) }
        view.dragged = { [weak self] point in guard let self else { return }; self.panel?.setFrameOrigin(NSPoint(x: point.x - 48, y: point.y - 48)); self.positionCompanion() }
        view.dragEnded = { [weak self] in self?.finishDrag() }
        panel.contentView = view; self.panel = panel; self.view = view
        if let screen = NSScreen.main { panel.setFrameOrigin(NSPoint(x: screen.visibleFrame.maxX - 100, y: screen.visibleFrame.minY + screen.visibleFrame.height * 0.6)) }
    }
    private func finishDrag() {
        guard let panel, let screen = NSScreen.screens.first(where: { $0.frame.contains(NSEvent.mouseLocation) }) ?? NSScreen.main else { return }
        if NSEvent.mouseLocation.y < screen.visibleFrame.minY + 72 {
            dictation.cancel(); hiddenByDrag = true; settings.value.bubbleVisible = false
            returnTask?.cancel(); returnTask = Task { try? await Task.sleep(nanoseconds: 600_000_000_000); guard !Task.isCancelled, hiddenByDrag, settings.value.bubbleActive else { return }; settings.value.bubbleVisible = true }
        } else {
            let frame = screen.visibleFrame
            let x = panel.frame.midX < frame.midX ? frame.minX : frame.maxX - panel.frame.width
            panel.setFrameOrigin(NSPoint(x: x, y: min(frame.maxY - panel.frame.height, max(frame.minY, panel.frame.minY)))); positionCompanion()
        }
    }
    private func showCompanion() {
        if companion == nil {
            let panel = BubblePanel(contentRect: NSRect(x: 0, y: 0, width: 290, height: 160), styleMask: [.borderless, .nonactivatingPanel], backing: .buffered, defer: false)
            panel.isOpaque = false; panel.backgroundColor = .clear; panel.level = .floating; panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary]; panel.hidesOnDeactivate = false
            panel.contentView = NSHostingView(rootView: BubbleCompanion().environmentObject(dictation)); companion = panel
        }
        positionCompanion(); companion?.orderFrontRegardless()
    }
    private func positionCompanion() {
        guard let panel, let companion, let screen = panel.screen ?? NSScreen.main else { return }
        let right = panel.frame.midX < screen.visibleFrame.midX
        companion.setFrameOrigin(NSPoint(x: right ? panel.frame.maxX : panel.frame.minX - companion.frame.width, y: min(screen.visibleFrame.maxY - companion.frame.height, max(screen.visibleFrame.minY, panel.frame.midY - companion.frame.height / 2))))
    }
}

struct BubbleCompanion: View {
    @EnvironmentObject var dictation: Dictation
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if dictation.state == .recording { HStack { InkMeter(amplitude: dictation.level, bars: 6).frame(height: 34); Text(formatDuration(dictation.elapsed)).monospacedDigit(); Spacer() }; if dictation.limitWarning { CopyText("mac.audio.warning").font(.caption) } }
            if dictation.state == .transcribing { HStack { ProgressView().controlSize(.small); CopyText("mac.status.transcribing") } }
            if !dictation.message.isEmpty { CopyText(dictation.message).font(.caption).lineLimit(3) }
            HStack {
                Button(Copy.text("action.cancel")) { dictation.cancel() }; Spacer()
                if dictation.state == .error { Button(Copy.text("mac.action.retry")) { dictation.retry() } }
                if dictation.state == .paste { Button(Copy.text("history.sheet.copy")) { dictation.copyTranscript() }; Button(Copy.text("mac.dictation.paste")) { dictation.paste() } }
            }
        }.padding(16).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16)).padding(6)
    }
}
