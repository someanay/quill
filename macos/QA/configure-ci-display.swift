// Give the ephemeral Actions desktop enough room to test expanded window layouts.
import Foundation
import CoreGraphics

let display = CGMainDisplayID()
let modes = CGDisplayCopyAllDisplayModes(display, nil) as? [CGDisplayMode] ?? []
print("Available display modes:", modes.map { "\($0.width)x\($0.height)" }.joined(separator: ", "))
let candidates = modes.filter { $0.width >= 1440 && $0.height >= 900 && $0.width <= 2560 && $0.height <= 1600 }
guard let mode = candidates.min(by: {
    abs($0.width - 1600) + abs($0.height - 1200) < abs($1.width - 1600) + abs($1.height - 1200)
}) else { fatalError("No display mode large enough for expanded desktop UI coverage") }
var configuration: CGDisplayConfigRef?
guard CGBeginDisplayConfiguration(&configuration) == .success,
      CGConfigureDisplayWithDisplayMode(configuration, display, mode, nil) == .success,
      CGCompleteDisplayConfiguration(configuration, .forSession) == .success else {
    fatalError("Cannot persist the larger display mode for the CI login session")
}
print("CI desktop:", CGDisplayPixelsWide(display), "x", CGDisplayPixelsHigh(display))
