// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "QuillCore",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "QuillCore", targets: ["QuillCore"])],
    targets: [.target(name: "QuillCore"), .testTarget(name: "QuillCoreTests", dependencies: ["QuillCore"])])
