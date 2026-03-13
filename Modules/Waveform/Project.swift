import ProjectDescription

let project = Project(
    name: "Waveform",
    targets: [
        .target(
            name: "Waveform",
            destinations: .iOS,
            product: .framework,
            bundleId: "dev.manelix.Mixer.Waveform",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            resources: []
        )
    ]
)
