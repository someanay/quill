import SwiftUI
import AppKit
import CoreText
import QuillCore

enum Copy {
    static let catalog: CopyCatalog = {
        let url = Bundle.main.url(forResource: "strings.default", withExtension: "json", subdirectory: "copy") ?? Bundle.main.url(forResource: "strings.default", withExtension: "json")
        guard let url, let data = try? Data(contentsOf: url), let copy = try? CopyCatalog(data: data) else { fatalError("Bundled copy catalog is missing") }
        return copy
    }()
    static func text(_ key: String, _ args: String...) -> String { catalog.resolve(key, arguments: args) }
}
enum QuillTheme {
    static let brand = Color(hex: 0x234DCE)
    static func brandInk(_ dark: Bool) -> Color { Color(hex: dark ? 0xB6C9FF : 0x1A3FAF) }
    static func liveInk(_ dark: Bool) -> Color { Color(hex: dark ? 0xD3DFFF : 0x1A3FAF) }
    static let error = statusColor(light: 0xB93631, dark: 0xE8635B)
    static let success = statusColor(light: 0x23744F, dark: 0x43B585)
    private static func statusColor(light: UInt32, dark: UInt32) -> Color {
        Color(nsColor: NSColor(name: nil) { appearance in
            let value = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua ? dark : light
            return NSColor(srgbRed: CGFloat((value >> 16) & 255) / 255, green: CGFloat((value >> 8) & 255) / 255, blue: CGFloat(value & 255) / 255, alpha: 1)
        })
    }
    static func paper(_ dark: Bool) -> Color { Color(hex: dark ? 0x11141A : 0xF5F7F5) }
    static func surface(_ dark: Bool) -> Color { Color(hex: dark ? 0x171B23 : 0xFFFFFF) }
    static func line(_ dark: Bool) -> Color { Color(hex: dark ? 0x353C48 : 0xD9DED9) }
    static func soft(_ dark: Bool) -> Color { Color(hex: dark ? 0x202D47 : 0xE4EBF5) }
    static func ink(_ dark: Bool) -> Color { Color(hex: dark ? 0xE0E7F6 : 0x202522) }
    static func text2(_ dark: Bool) -> Color { Color(hex: dark ? 0xC2C6CF : 0x454E48) }
    static func muted(_ dark: Bool) -> Color { Color(hex: dark ? 0xB2B9C6 : 0x5C665F) }
    static func sans(_ size: CGFloat = 14) -> Font { .custom("IBMPlexSans", size: size, relativeTo: .body) }
    static func serif(_ size: CGFloat = 30) -> Font { .custom("IBMPlexSerifVar-Regular", size: size, relativeTo: .title) }
    static func registerFonts() {
        for name in ["ibm_plex_sans", "ibm_plex_serif"] {
            if let url = Bundle.main.url(forResource: name, withExtension: "ttf", subdirectory: "fonts") ?? Bundle.main.url(forResource: name, withExtension: "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        }
    }
}
extension Color {
    init(hex: UInt32) { self.init(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1) }
}
struct CopyText: View {
    let key: String
    init(_ key: String) { self.key = key }
    var body: some View { Text(Copy.text(key)) }
}
struct BrandMark: View {
    var size: CGFloat = 36
    var body: some View { Image(systemName: "pencil.tip.crop.circle.fill").font(.system(size: size * 0.62)).foregroundStyle(.white).frame(width: size, height: size).background(QuillTheme.brand.gradient, in: RoundedRectangle(cornerRadius: size * 0.24)).accessibilityHidden(true) }
}
struct Wordmark: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View { (Text("quill") + Text(".").foregroundColor(QuillTheme.brandInk(scheme == .dark))).font(QuillTheme.sans(34)).fontWeight(.semibold).tracking(-2).accessibilityLabel("Quill") }
}
struct QuillCard<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    @ViewBuilder var content: Content
    var body: some View { VStack(alignment: .leading, spacing: 14) { content }.padding(18).frame(maxWidth: .infinity, alignment: .leading).background(QuillTheme.surface(scheme == .dark), in: RoundedRectangle(cornerRadius: 8)).overlay(RoundedRectangle(cornerRadius: 8).stroke(QuillTheme.line(scheme == .dark), lineWidth: 1).allowsHitTesting(false)) }
}
struct QuillButton: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.isEnabled) private var enabled
    let key: String
    var primary = true
    var arguments: [String] = []
    var action: () -> Void
    var body: some View {
        Button(action: action) { Text(Copy.catalog.resolve(key, arguments: arguments)).font(QuillTheme.sans(15)).fontWeight(.medium).padding(.horizontal, 22).frame(minHeight: 44).contentShape(RoundedRectangle(cornerRadius: 6)) }
        .buttonStyle(.plain).foregroundStyle(primary ? Color.white : QuillTheme.brandInk(scheme == .dark))
        .background(primary ? QuillTheme.brand : Color.clear, in: RoundedRectangle(cornerRadius: 6))
        .opacity(enabled ? 1 : 0.45).accessibilityIdentifier(key).frame(maxWidth: .infinity, alignment: .leading)
    }
}
struct QuillFormSection<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View { VStack(alignment: .leading, spacing: 20) { content }.padding(.vertical, 8).frame(maxWidth: .infinity, alignment: .leading) }
}
struct ShortcutKeycaps: View {
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        HStack(spacing: 10) {
            ForEach(["⌥", "⌘", "Space"], id: \.self) { key in
                Text(key).font(QuillTheme.sans(20)).padding(.horizontal, 18).frame(minHeight: 52)
                    .background(QuillTheme.surface(scheme == .dark), in: RoundedRectangle(cornerRadius: 7))
                    .overlay(RoundedRectangle(cornerRadius: 7).stroke(QuillTheme.line(scheme == .dark), lineWidth: 1))
            }
        }.accessibilityElement(children: .ignore).accessibilityLabel(Copy.text("mac.hotkey.label"))
    }
}
struct SectionLabel: View {
    let key: String
    var body: some View { CopyText(key).font(QuillTheme.sans(13)).fontWeight(.medium).foregroundStyle(.secondary).padding(.top, 8) }
}
struct HelpText: View {
    @Environment(\.colorScheme) private var scheme
    let key: String
    var size: CGFloat = 12
    var bad = false
    var body: some View { CopyText(key).font(QuillTheme.sans(size)).foregroundStyle(bad ? QuillTheme.error : QuillTheme.muted(scheme == .dark)).fixedSize(horizontal: false, vertical: true) }
}
struct BodyCopy: View {
    @Environment(\.colorScheme) private var scheme
    let key: String
    var body: some View { CopyText(key).font(QuillTheme.sans(16)).foregroundStyle(QuillTheme.text2(scheme == .dark)).lineSpacing(5).fixedSize(horizontal: false, vertical: true) }
}
struct InkMeter: View {
    var amplitude: Double
    var bars = 13
    @Environment(\.accessibilityReduceMotion) private var reduced
    var body: some View {
        HStack(spacing: 4) { ForEach(0..<bars, id: \.self) { index in
            let t = bars <= 1 ? 0.5 : Double(index) / Double(bars - 1)
            let weight = 0.45 + 0.55 * (1 - abs(t - 0.5) * 2)
            let target = reduced ? 0.18 : min(1, max(0, amplitude))
            let active = target * weight > 0.06
            Capsule().fill(LinearGradient(colors: active ? [QuillTheme.brand, Color(hex: 0x1A3FAF)] : [Color(hex: 0xCBD2CC), Color(hex: 0xCBD2CC)], startPoint: .top, endPoint: .bottom)).frame(width: 5, height: active ? 64 * (0.16 + 0.84 * target * weight) : 5)
        } }.frame(height: 64).animation(reduced ? nil : .easeOut(duration: 0.1), value: amplitude).accessibilityLabel(Copy.text("mac.meter.level"))
    }
}
struct PermissionArt: View {
    let symbol: String
    var body: some View { ZStack { Circle().fill(QuillTheme.brand.opacity(0.08)).frame(width: 118, height: 118); Circle().strokeBorder(QuillTheme.brand.opacity(0.25), style: StrokeStyle(lineWidth: 1.5, dash: [4,5])).frame(width: 108, height: 108); Image(systemName: symbol).font(.system(size: 30)).foregroundStyle(.white).frame(width: 64, height: 64).background(QuillTheme.brand.gradient, in: RoundedRectangle(cornerRadius: 18)) }.accessibilityHidden(true) }
}
