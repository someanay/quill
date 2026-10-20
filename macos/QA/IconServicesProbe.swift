import AppKit

// A CI desktop-health probe, independent of Quill and its permission adapters.
// Broken Intel macOS 26 images otherwise block indefinitely on this system call.
DispatchQueue.global().asyncAfter(deadline: .now() + 10) {
    fputs("IconServices did not return within 10 seconds.\n", stderr)
    exit(78)
}
let started = Date()
let image = NSWorkspace.shared.icon(forFileType: "txt")
guard let bytes = image.tiffRepresentation, !bytes.isEmpty else {
    fputs("AppKit did not return a usable file icon.\n", stderr)
    exit(1)
}
print("AppKit file icon returned in \(Date().timeIntervalSince(started)) seconds (\(bytes.count) bytes).")
