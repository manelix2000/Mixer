import ProjectDescription

let project = Project(
    name: "MixerApp",
    targets: [
        .target(
            name: "MixerApp",
            destinations: .iOS,
            product: .app,
            bundleId: "dev.manelix.Mixer",
            deploymentTargets: .iOS("17.0"),
            infoPlist: .extendingDefault(
                with: [
                    "UILaunchStoryboardName": "LaunchScreen",
                    "UIAppFonts": [
                        "MicrogrammaDExtendedBold.otf",
                        "MicrogrammaDExtendedBold.ttf"
                    ],
                    "NSMicrophoneUsageDescription": "Mixer uses the microphone to detect external BPM.",
                    "UISupportedInterfaceOrientations": [
                        "UIInterfaceOrientationLandscapeLeft",
                        "UIInterfaceOrientationLandscapeRight"
                    ],
                    "UISupportedInterfaceOrientations~ipad": [
                        "UIInterfaceOrientationLandscapeLeft",
                        "UIInterfaceOrientationLandscapeRight"
                    ],
                    "UIRequiresFullScreen": true
                ]
            ),
            sources: ["Sources/**"],
            resources: [
                "Sample.mp3",
                "Resources/**"
            ],
            dependencies: [
                .project(target: "DeckFeature", path: "../../Modules/DeckFeature")
            ],
            settings: .settings(
                base: [
                    "CODE_SIGN_STYLE": "Manual",
                    "DEVELOPMENT_TEAM": "FX8D5XGPA8",
                    "DEVELOPMENT_TEAM[sdk=iphoneos*]": "FX8D5XGPA8",
                    "PROVISIONING_PROFILE_SPECIFIER": "Privalia Wildcard",
                    "PROVISIONING_PROFILE_SPECIFIER[sdk=iphoneos*]": "Privalia Wildcard",
                    // Temporary fallback: current aubio.framework does not contain arm64-simulator slices.
                    // Remove this when migrating to an aubio XCFramework with ios-arm64_x86_64-simulator.
                    "EXCLUDED_ARCHS[sdk=iphonesimulator*]": "arm64"
                ]
            )
        )
    ]
)
