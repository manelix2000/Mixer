pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MixerAndroid"
include(
    ":app",
    ":feature:deck",
    ":core:audio",
    ":core:common",
    ":core:dsp",
    ":core:ui",
    ":core:waveform"
)
