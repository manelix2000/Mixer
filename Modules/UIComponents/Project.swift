import ProjectDescription

let project = Project(
    name: "UIComponents",
    targets: [
        .target(
            name: "UIComponents",
            destinations: .iOS,
            product: .framework,
            bundleId: "dev.manelix.Mixer.UIComponents",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .default,
            sources: ["Sources/**"],
            resources: []
        )
    ]
)
