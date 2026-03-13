import ProjectDescription

let project = Project(
    name: "AudioEngine",
    targets: [
        .target(
            name: "AudioEngine",
            destinations: .iOS,
            product: .framework,
            bundleId: "dev.manelix.Mixer.AudioEngine",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            resources: [],
            dependencies: [
                .project(target: "DSP", path: "../DSP")
            ]
        )
    ]
)
