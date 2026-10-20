import SwiftUI

/// A silent local illustration. It never records audio or sends keyboard input.
struct MessengerDemo: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var began = Date()
    var shortcut: String
    var touch = false
    var previewTime: Double? = nil
    private var ink: Color { QuillTheme.ink(scheme == .dark) }
    private var muted: Color { QuillTheme.muted(scheme == .dark) }
    private var surface: Color { QuillTheme.surface(scheme == .dark) }
    private var paper: Color { QuillTheme.paper(scheme == .dark) }
    private var line: Color { QuillTheme.line(scheme == .dark) }
    private var brand: Color { QuillTheme.brandInk(scheme == .dark) }
    private var soft: Color { QuillTheme.soft(scheme == .dark) }
    private func c(_ key: String) -> String { Copy.text(key) }
    private var frozenTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if args.contains("--ui-testing"), let index = args.firstIndex(of: "--ui-demo-time"), args.indices.contains(index + 1) { return Double(args[index + 1]) }
        if args.contains("--ui-testing") && !args.contains("--ui-animate-demo") { return 0 }
        #endif
        return previewTime
    }
    var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion || frozenTime != nil)) { context in
            let time = frozenTime ?? (reduceMotion ? 9.5 : context.date.timeIntervalSince(began).truncatingRemainder(dividingBy: 12))
            let listening = time >= 1.6 && time < 5.5
            let working = time >= 5.5 && time < 7.2
            let filled = time >= 7.2
            let pressed = (time >= 1.1 && time < 1.7) || (time >= 5.1 && time < 5.6)
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 6) {
                    ForEach(0..<3) { _ in Circle().fill(line).frame(width: 6, height: 6) }
                    Spacer()
                    Text(c("demo.conversation")).foregroundStyle(muted)
                }.font(.custom("IBMPlexSans", fixedSize: 12)).padding(.horizontal, 18).frame(height: 40)
                Rectangle().fill(line).frame(height: 1)
                VStack(alignment: .leading, spacing: 12) {
                    message(c("demo.hello"))
                    message(c("demo.question"))
                    ZStack(alignment: .topLeading) {
                        RoundedRectangle(cornerRadius: 8).fill(paper)
                        RoundedRectangle(cornerRadius: 8).stroke(line, lineWidth: 1)
                        (Text(filled ? c("demo.reply") : "") + Text("│").foregroundColor(brand))
                            .font(.custom("IBMPlexSans", fixedSize: 15)).lineSpacing(4).foregroundStyle(ink).padding(12)
                        if !touch {
                            DemoPointer().fill(ink).overlay(DemoPointer().stroke(surface, lineWidth: 1.2))
                                .frame(width: 17, height: 23).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomTrailing).padding(12)
                        }
                    }.frame(height: 92).padding(.top, 12)
                    HStack(spacing: 12) {
                        HStack(spacing: 6) {
                            if touch { Image(systemName: "mic.fill") }
                            Text(shortcut)
                        }.font(.custom("IBMPlexSans", fixedSize: 12)).foregroundStyle(pressed ? brand : muted)
                            .padding(.horizontal, 10).frame(minHeight: 30)
                            .background(pressed ? soft : paper, in: RoundedRectangle(cornerRadius: 5))
                            .overlay(RoundedRectangle(cornerRadius: 5).stroke(pressed ? brand : line, lineWidth: 1))
                        Spacer(minLength: 0)
                        Text(c(filled ? "demo.inserted" : working ? "demo.transcribing" : listening ? "demo.speak" : touch ? "demo.record" : "demo.shortcut"))
                            .font(.custom("IBMPlexSans", fixedSize: 12)).foregroundStyle(brand).padding(.trailing, touch ? 52 : 0)
                    }.frame(minHeight: touch ? 44 : 30).padding(.top, 4)
                }.padding(18)
            }.background(surface, in: RoundedRectangle(cornerRadius: 10))
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(line, lineWidth: 1))
                .overlay(alignment: touch ? .bottomTrailing : .trailing) {
                    if listening || working || touch {
                        ZStack {
                            Circle().fill(Color(red: 35 / 255, green: 77 / 255, blue: 206 / 255))
                            Circle().stroke(Color.white, lineWidth: 2).padding(2)
                            if listening || working {
                                HStack(spacing: working ? 4 : 3) {
                                    ForEach(0..<(working ? 3 : 5), id: \.self) { index in
                                        Capsule().fill(Color.white).frame(width: working ? 4 : 2.5, height: working ? 4 : 6 + abs(sin(time * 5 + Double(index) * 1.4)) * 18)
                                    }
                                }
                            } else { Image(systemName: "mic.fill").font(.system(size: 20)).foregroundStyle(Color.white) }
                        }.frame(width: 48, height: 48).padding(.trailing, 16).padding(.bottom, touch ? 12 : 0).offset(y: touch ? 0 : -25)
                    }
                }
        }.allowsHitTesting(false).accessibilityElement(children: .ignore)
            .accessibilityLabel(c("demo.accessibility")).accessibilityIdentifier("dictation.messenger-demo")
    }
    private func message(_ text: String) -> some View {
        Text(text).font(.custom("IBMPlexSans", fixedSize: 14)).foregroundStyle(ink)
            .padding(.horizontal, 12).padding(.vertical, 9).background(paper, in: RoundedRectangle(cornerRadius: 7))
    }
}

private struct DemoPointer: Shape {
    func path(in rect: CGRect) -> Path {
        Path { p in
            p.move(to: CGPoint(x: 1, y: 1)); p.addLines([CGPoint(x: 1, y: 18), CGPoint(x: 5, y: 14), CGPoint(x: 9, y: 22), CGPoint(x: 12, y: 20), CGPoint(x: 8, y: 12), CGPoint(x: 16, y: 12)]); p.closeSubpath()
        }
    }
}
