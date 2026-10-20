import SwiftUI
import QuillCore

enum Q {
    static func adaptive(_ light: UInt32, _ dark: UInt32) -> Color {
        Color(uiColor: UIColor { trait in
            let value = trait.userInterfaceStyle == .dark ? dark : light
            return UIColor(red: CGFloat((value >> 16) & 255) / 255, green: CGFloat((value >> 8) & 255) / 255, blue: CGFloat(value & 255) / 255, alpha: 1)
        })
    }
    static let brand = adaptive(0x234DCE, 0x234DCE)
    static let brandInk = adaptive(0x1A3FAF, 0xB6C9FF)
    static let brandMark = adaptive(0x234DCE, 0xB6C9FF)
    static let brandSoft = adaptive(0xE4EBF5, 0x202D47)
    static let paper = adaptive(0xF5F7F5, 0x11141A)
    static let surface = adaptive(0xFFFFFF, 0x171B23)
    static let sunken = adaptive(0xEDF0ED, 0x1E232D)
    static let ink = adaptive(0x202522, 0xE0E7F6)
    static let muted = adaptive(0x5C665F, 0xB2B9C6)
    static let line = adaptive(0xD9DED9, 0x353C48)
    static let danger = adaptive(0xB93631, 0xE8635B)
    static let dangerFill = adaptive(0xB93631, 0xB93631)
    static let success = adaptive(0x23744F, 0x43B585)
    static let fieldRadius: CGFloat = 6
    static let cardRadius: CGFloat = 8
    static let controlRadius: CGFloat = 6
    static let largeRadius: CGFloat = 12
    static let dialogRadius: CGFloat = 16
    static func sans(_ size: CGFloat = 16, relativeTo style: Font.TextStyle = .body) -> Font { .custom("IBMPlexSans", size: size, relativeTo: style) }
    static func serif(_ size: CGFloat = 30) -> Font { .custom("IBMPlexSerifVar-Regular", size: size, relativeTo: .title) }
}

struct Wordmark: View {
    var body: some View {
        (Text("quill").foregroundStyle(Q.ink) + Text(".").foregroundStyle(Q.brandMark))
            .font(Q.sans(34, relativeTo: .title)).fontWeight(.semibold).tracking(-2).accessibilityLabel("Quill")
    }
}

struct QCard<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View { content.padding(16).frame(maxWidth: .infinity, alignment: .leading).fixedSize(horizontal: false, vertical: true).background(Q.surface, in: RoundedRectangle(cornerRadius: Q.cardRadius)).overlay(RoundedRectangle(cornerRadius: Q.cardRadius).stroke(Q.line, lineWidth: 1)) }
}
struct QButton: View {
    @Environment(\.horizontalSizeClass) private var sizeClass
    let title: String
    var icon: String? = nil
    var secondary = false
    var destructive = false
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) { if let icon { Image(systemName: icon) }; Text(title).multilineTextAlignment(.center).fixedSize(horizontal: false, vertical: true) }
                .font(Q.sans(15)).fontWeight(.medium).padding(.horizontal, 20).padding(.vertical, 14).frame(maxWidth: sizeClass == .regular ? 320 : .infinity, minHeight: 50)
                .foregroundStyle(secondary ? Q.brandInk : .white)
                .background(secondary ? Q.brandSoft : (destructive ? Q.dangerFill : Q.brand), in: RoundedRectangle(cornerRadius: Q.controlRadius))
        }.buttonStyle(.plain).frame(maxWidth: .infinity, alignment: .leading)
    }
}
struct QBanner: View {
    var title: String? = nil
    let text: String
    var icon = "info.circle"
    var error = false
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: icon).font(.system(size: 16)).frame(width: 20).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 5) {
                if let title { Text(title).fontWeight(.medium).fixedSize(horizontal: false, vertical: true) }
                Text(text).fixedSize(horizontal: false, vertical: true)
            }
        }.font(Q.sans(13)).multilineTextAlignment(.leading).foregroundStyle(error ? Q.danger : Q.brandInk)
            .padding(14).frame(maxWidth: .infinity, alignment: .leading).fixedSize(horizontal: false, vertical: true)
            .background(error ? Q.danger.opacity(0.09) : Q.brandSoft, in: RoundedRectangle(cornerRadius: Q.largeRadius))
    }
}
struct QHeading: View {
    let title: String
    var body: some View { Text(title).font(Q.serif()).foregroundStyle(Q.ink).fixedSize(horizontal: false, vertical: true).frame(maxWidth: .infinity, alignment: .leading).padding(.top, 10).accessibilityAddTraits(.isHeader) }
}
struct QSection: View {
    let title: String
    var body: some View { Text(title).font(Q.sans(14)).fontWeight(.medium).foregroundStyle(Q.muted).fixedSize(horizontal: false, vertical: true).frame(maxWidth: .infinity, alignment: .leading).padding(.top, 18).accessibilityAddTraits(.isHeader) }
}
struct QRow: View {
    let title: String
    var subtitle: String? = nil
    var icon: String = "circle"
    var value: String? = nil
    var chevron = true
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon).font(.system(size: 19)).foregroundStyle(Q.brandInk).frame(width: 40, height: 40).background(Q.brandSoft, in: RoundedRectangle(cornerRadius: Q.cardRadius)).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(Q.sans(16)).foregroundStyle(Q.ink).fixedSize(horizontal: false, vertical: true)
                if let subtitle { Text(subtitle).font(Q.sans(13)).foregroundStyle(Q.muted).fixedSize(horizontal: false, vertical: true) }
            }.multilineTextAlignment(.leading).frame(maxWidth: .infinity, alignment: .leading)
            if let value { Text(value).font(Q.sans(13)).foregroundStyle(Q.muted) }
            if chevron { Image(systemName: "chevron.right").font(.system(size: 12)).foregroundStyle(Q.muted).accessibilityHidden(true) }
        }.padding(.vertical, 5).frame(minHeight: 48).fixedSize(horizontal: false, vertical: true)
    }
}
struct QField: View {
    let title: String
    @Binding var text: String
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title).font(Q.sans(12)).foregroundStyle(Q.muted)
            TextField(title, text: $text, axis: .vertical).textInputAutocapitalization(.never).autocorrectionDisabled()
                .modifier(QInputSurface())
                .accessibilityLabel(title)
        }
    }
}
struct QInputSurface: ViewModifier {
    func body(content: Content) -> some View {
        content.textFieldStyle(.plain).font(Q.sans()).padding(14).frame(minHeight: 52)
            .background(Q.surface, in: RoundedRectangle(cornerRadius: Q.fieldRadius))
            .overlay(RoundedRectangle(cornerRadius: Q.fieldRadius).stroke(Q.line, lineWidth: 1.5))
    }
}
struct PermissionIllustration: View {
    let icon: String
    var body: some View {
        ZStack {
            Circle().fill(Q.brandSoft.opacity(0.5))
            Circle().stroke(Q.line, style: StrokeStyle(lineWidth: 1.5, dash: [6, 6])).padding(5)
            Image(systemName: icon).font(.system(size: 30)).foregroundStyle(.white).frame(width: 60, height: 60).background(Q.brand, in: RoundedRectangle(cornerRadius: Q.largeRadius))
        }.frame(width: 118, height: 118).accessibilityHidden(true)
    }
}
struct InkMeter: View {
    var amplitude: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        HStack(spacing: 4) {
            ForEach(0..<13, id: \.self) { index in
                let factor = 0.3 + 0.7 * abs(sin(Double(index) * 1.73 + 0.9))
                RoundedRectangle(cornerRadius: 3).fill(Q.brand).frame(width: 5, height: max(5, 64 * (reduceMotion ? min(amplitude, 0.3) : amplitude) * factor))
            }
        }.frame(height: 64).animation(reduceMotion ? nil : .easeOut(duration: 0.1), value: amplitude).accessibilityHidden(true)
    }
}
struct RecordingBubble: View {
    var phase: DictationPhase = .idle
    var size: CGFloat = 72
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @State private var pulse = false
    var body: some View {
        ZStack {
            if phase == .recording { Circle().fill(Q.brand.opacity(pulse ? 0.10 : 0.22)).frame(width: size + (pulse ? 26 : 14), height: size + (pulse ? 26 : 14)) }
            Circle().fill(phase == .failed ? Q.dangerFill : Q.brand).frame(width: size, height: size)
                .overlay(Circle().stroke(.white.opacity(0.92), lineWidth: 2.5)).shadow(color: .black.opacity(0.24), radius: 12, y: 6)
            if [.transcribing, .cleaning].contains(phase) {
                if reducedMotion { Image(systemName: "ellipsis").foregroundStyle(.white) } else { ProgressView().tint(.white) }
            } else { Image(systemName: phase == .recording ? "stop.fill" : (phase == .failed ? "exclamationmark" : "mic.fill")).font(.system(size: size * 0.4)).foregroundStyle(.white) }
        }.frame(width: size + 26, height: size + 26)
            .onAppear { if !reducedMotion { withAnimation(.easeInOut(duration: 1.8).repeatForever(autoreverses: true)) { pulse = true } } }
    }
}
struct QScrollClearance: ViewModifier {
    @Environment(\.horizontalSizeClass) private var sizeClass
    func body(content: Content) -> some View { content.padding(.bottom, clearance) }
    private var clearance: CGFloat {
        if #available(iOS 26, *), UIDevice.current.userInterfaceIdiom == .phone || sizeClass == .compact { return 80 }
        return 0
    }
}
struct QPage<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        GeometryReader { geometry in
            ScrollView {
                VStack(alignment: .leading, spacing: 18) { content }
                    .padding(geometry.size.width >= 700 ? 28 : 20)
                    .modifier(QScrollClearance())
                    .frame(maxWidth: 980).frame(maxWidth: .infinity)
            }.scrollDismissesKeyboard(.interactively)
        }.background(Q.paper).foregroundStyle(Q.ink).font(Q.sans())
    }
}
