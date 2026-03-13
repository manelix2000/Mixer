import ProjectDescription
import Foundation

let candidateAubioXCFrameworkPaths = [
    "../../External/aubio/Aubio.xcframework",
    "../../External/aubio/aubio.xcframework"
]
let candidateAubioFrameworkPaths = [
    "../../External/aubio/Aubio.framework",
    "../../External/aubio/aubio.framework"
]
let aubioDependencies: [TargetDependency] = {
    let manifestDirectory = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
    for candidatePath in candidateAubioXCFrameworkPaths {
        let resolvedPath = URL(fileURLWithPath: candidatePath, relativeTo: manifestDirectory)
            .standardizedFileURL
            .path
        if FileManager.default.fileExists(atPath: resolvedPath) {
            return [
                .xcframework(path: .relativeToManifest(candidatePath))
            ]
        }
    }
    for candidatePath in candidateAubioFrameworkPaths {
        let resolvedPath = URL(fileURLWithPath: candidatePath, relativeTo: manifestDirectory)
            .standardizedFileURL
            .path
        if FileManager.default.fileExists(atPath: resolvedPath) {
            return [
                .framework(path: .relativeToManifest(candidatePath))
            ]
        }
    }
    return []
}()

let project = Project(
    name: "DSP",
    targets: [
        .target(
            name: "DSP",
            destinations: .iOS,
            product: .framework,
            bundleId: "dev.manelix.Mixer.DSP",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            resources: [],
            dependencies: aubioDependencies + [
                .sdk(name: "Accelerate", type: .framework)
            ]
        )
    ]
)
