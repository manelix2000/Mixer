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
                    "CFBundleDisplayName": "DJ companion",
                    "UILaunchStoryboardName": "LaunchScreenMixer",
                    "UIAppFonts": [
                        "MicrogrammaDExtendedBold.otf",
                        "MicrogrammaDExtendedBold.ttf"
                    ],
                    "NSMicrophoneUsageDescription": "Mixer uses the microphone to detect external BPM.",
                    "NSCameraUsageDescription": "Mixer uses the camera to detect external BPM.",
                    "UIBackgroundModes": [
                        "audio"
                    ],
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
                    "CURRENT_PROJECT_VERSION": "2",
                    "MARKETING_VERSION": "1.0"
                ],
                configurations: [
                    .debug(
                        name: "Debug",
                        settings: [
                            "CODE_SIGN_IDENTITY": "iPhone Developer: Manuel Mateos Ramirez (N9A2P3JC94)",
                            "CODE_SIGN_STYLE": "Manual",
                            "DEVELOPMENT_TEAM": "A7GL585WAC",
                            "DEVELOPMENT_TEAM[sdk=iphoneos*]": "U84GD972G4",
                            "PROVISIONING_PROFILE_SPECIFIER": "DJ companion dev",
                            "PROVISIONING_PROFILE_SPECIFIER[sdk=iphoneos*]": "DJ companion dev"
                        ]
                    ),
                    .release(
                        name: "Release",
                        settings: [
                            "CODE_SIGN_IDENTITY": "iPhone Distribution: Flacotech SL (U84GD972G4)",
                            "CODE_SIGN_STYLE": "Manual",
                            "DEVELOPMENT_TEAM": "U84GD972G4",
                            "DEVELOPMENT_TEAM[sdk=iphoneos*]": "U84GD972G4",
                            "PROVISIONING_PROFILE_SPECIFIER": "DJ companion dist",
                            "PROVISIONING_PROFILE_SPECIFIER[sdk=iphoneos*]": "DJ companion dist"
                        ]
                    )
                ]
            )
        )
    ]
)
