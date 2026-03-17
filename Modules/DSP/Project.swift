import ProjectDescription

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
            dependencies: [
                .xcframework(path: .relativeToRoot("External/aubio/Aubio.xcframework")),
                .sdk(name: "Accelerate", type: .framework)
            ]
        )
    ]
)
