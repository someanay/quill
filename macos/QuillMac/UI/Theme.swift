import SwiftUI
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
    static let error = Color(hex: 0xD6453D)
    static let success = Color(hex: 0x2E9E6B)
    static func paper(_ dark: Bool) -> Color { Color(hex: dark ? 0x11141A : 0xF5F7F5) }
    static func surface(_ dark: Bool) -> Color { Color(hex: dark ? 0x171B23 : 0xFFFFFF) }
    static func line(_ dark: Bool) -> Color { Color(hex: dark ? 0x353C48 : 0xD9DED9) }
    static func soft(_ dark: Bool) -> Color { Color(hex: dark ? 0x202D47 : 0xE4EBF5) }
    static func ink(_ dark: Bool) -> Color { Color(hex: dark ? 0xE0E7F6 : 0x202522) }
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
    var body: some View { (Text("quill") + Text(".").foregroundColor(scheme == .dark ? Color(hex: 0xB6C9FF) : QuillTheme.brand)).font(QuillTheme.sans(34)).fontWeight(.semibold).tracking(-2).accessibilityLabel("Quill") }
}
struct QuillCard<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    @ViewBuilder var content: Content
    var body: some View { VStack(alignment: .leading, spacing: 14) { content }.padding(18).frame(maxWidth: .infinity, alignment: .leading).background(QuillTheme.surface(scheme == .dark), in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(QuillTheme.line(scheme == .dark), lineWidth: 1)) }
}
struct QuillButton: View {
    let key: String
    var primary = true
    var action: () -> Void
    var body: some View {
        Button(action: action) { CopyText(key).font(QuillTheme.sans(15)).fontWeight(.medium).frame(maxWidth: .infinity).frame(minHeight: 48).contentShape(Capsule()) }
        .buttonStyle(.plain).foregroundStyle(primary ? Color.white : QuillTheme.brand)
        .background(primary ? QuillTheme.brand : QuillTheme.brand.opacity(0.1), in: Capsule()).accessibilityIdentifier(key)
    }
}
struct SectionLabel: View {
    let key: String
    var body: some View { CopyText(key).font(QuillTheme.sans(13)).fontWeight(.medium).foregroundStyle(.secondary).padding(.top, 8) }
}
struct HelpText: View {
    let key: String
    var body: some View { CopyText(key).font(QuillTheme.sans(13)).foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true) }
}
struct InkMeter: View {
    var amplitude: Double
    var bars = 13
    @Environment(\.accessibilityReduceMotion) private var reduced
    var body: some View {
        HStack(spacing: 4) { ForEach(0..<bars, id: \.self) { index in
            Capsule().fill(QuillTheme.brand.gradient).frame(width: 5, height: max(5, (reduced ? 0.18 : amplitude) * (28 + 30 * abs(sin(Double(index) * 1.7)))))
        } }.frame(height: 64).animation(reduced ? nil : .easeOut(duration: 0.1), value: amplitude).accessibilityLabel(Copy.text("mac.meter.level"))
    }
}
struct PermissionArt: View {
    let symbol: String
    var body: some View { ZStack { Circle().fill(QuillTheme.brand.opacity(0.08)).frame(width: 118, height: 118); Circle().strokeBorder(QuillTheme.brand.opacity(0.25), style: StrokeStyle(lineWidth: 1.5, dash: [4,5])).frame(width: 108, height: 108); Image(systemName: symbol).font(.system(size: 30)).foregroundStyle(.white).frame(width: 64, height: 64).background(QuillTheme.brand.gradient, in: RoundedRectangle(cornerRadius: 18)) }.accessibilityHidden(true) }
}
