import ProjectDescription

let project = Project(
    name: "DeckFeature",
    targets: [
        .target(
            name: "DeckFeature",
            destinations: .iOS,
            product: .framework,
            bundleId: "dev.manelix.Mixer.DeckFeature",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            resources: [],
            dependencies: [
                .project(target: "AudioEngine", path: "../AudioEngine"),
                .project(target: "DSP", path: "../DSP"),
                .project(target: "Waveform", path: "../Waveform"),
                .project(target: "UIComponents", path: "../UIComponents")
            ]
        )
    ]
)
