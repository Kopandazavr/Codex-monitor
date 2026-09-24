// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "CodexMonitorCore",
    platforms: [
        .iOS("26.0"),
    ],
    products: [
        .library(
            name: "CodexMonitorCore",
            targets: ["CodexMonitorCore"]
        ),
    ],
    targets: [
        .target(name: "CodexMonitorCore"),
        .testTarget(
            name: "CodexMonitorCoreTests",
            dependencies: ["CodexMonitorCore"],
            resources: [.process("Fixtures")]
        ),
    ],
    swiftLanguageModes: [.v6]
)
