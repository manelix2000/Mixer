# dev.manelix.Mixer --- Codex‑Ready Engineering Specification

## Overview

`dev.manelix.Mixer` is an iOS application that allows a user to load a
track and overlay it on top of music already playing from another app.
The workflow emulates a DJ turntable using **vinyl mode** (speed affects
BPM and pitch).

Primary goals:

-   Load a track from Files
-   Detect BPM using aubio
-   Adjust playback rate to match external music BPM
-   Route audio to left/right stereo channel
-   Control playback via a virtual vinyl turntable
-   Optional BPM detection from microphone

Target workflow: emulate the behavior of a professional turntable such
as the Technics SL‑1200.

------------------------------------------------------------------------

# 1. Platform

  Parameter        Value
  ---------------- ---------------
  Language         Swift 6
  UI               SwiftUI
  Architecture     MVVM
  Project System   Tuist
  Orientation      Landscape
  Devices          iPhone + iPad

------------------------------------------------------------------------

# 2. Core Interaction Model (Vinyl Mode)

Speed control simulates physical vinyl behavior.

    newBPM = originalBPM * playbackRate

Example:

    Original BPM: 120
    Pitch +5%

    playbackRate = 1.05
    Result BPM ≈ 126

Characteristics:

-   Pitch changes with BPM
-   No time‑stretch DSP
-   Very low latency
-   Authentic DJ feel

------------------------------------------------------------------------

# 3. Tuist Project Structure

    dev.manelix.Mixer
    │
    ├─ Tuist
    │   └─ Project.swift
    │
    ├─ Projects
    │   └─ MixerApp
    │
    ├─ Modules
    │   ├─ App
    │   ├─ DeckFeature
    │   ├─ AudioEngine
    │   ├─ Waveform
    │   ├─ DSP
    │   └─ UIComponents
    │
    └─ External
        └─ aubio

------------------------------------------------------------------------

# 4. Base Tuist Project.swift

``` swift
import ProjectDescription

let project = Project(
    name: "Mixer",
    targets: [
        Target(
            name: "MixerApp",
            platform: .iOS,
            product: .app,
            bundleId: "dev.manelix.Mixer",
            deploymentTarget: .iOS(targetVersion: "17.0", devices: .iphone),
            sources: ["Sources/**"],
            dependencies: [
                .project(target: "DeckFeature", path: "../Modules/DeckFeature")
            ]
        )
    ]
)
```

------------------------------------------------------------------------

# 5. MVVM Architecture

## Model

``` swift
struct Track {
    let url: URL
    let duration: TimeInterval
    let originalBPM: Double
}
```

------------------------------------------------------------------------

## ViewModel

``` swift
final class DeckViewModel: ObservableObject {

    @Published var bpm: Double
    @Published var playbackRate: Float
    @Published var currentTime: TimeInterval

    func loadTrack(url: URL)
    func play()
    func pause()
    func seek(time: TimeInterval)
    func adjustPitch(percent: Double)
}
```

------------------------------------------------------------------------

# 6. Audio Engine Design

Based on AVAudioEngine.

Audio graph:

    AVAudioEngine
         │
    AVAudioPlayerNode
         │
    AVAudioMixerNode
         │
    OutputNode

Main manager:

``` swift
final class AudioEngineManager {

    let engine = AVAudioEngine()
    let player = AVAudioPlayerNode()

    func start()
    func stop()

    func loadFile(url: URL)
    func setPlaybackRate(_ rate: Float)
}
```

------------------------------------------------------------------------

# 7. Vinyl Pitch Control

Pitch slider range:

    -8% to +8%

Rate conversion:

    playbackRate = targetBPM / trackBPM

Example:

    Track BPM: 120
    External BPM: 128

    rate = 1.066

------------------------------------------------------------------------

# 8. aubio Integration

aubio used for BPM detection.

C‑to‑Swift bridge required.

Wrapper example:

``` swift
final class BPMDetector {

    func detectBPM(samples: [Float]) -> Double {
        // aubio_tempo processing
    }
}
```

Functions used:

    aubio_tempo
    aubio_onset

------------------------------------------------------------------------

# 9. Waveform Engine

Waveform computed when loading track.

Pipeline:

    audio decode
     ↓
    downsample
     ↓
    amplitude envelope
     ↓
    waveform array

Swift interface:

``` swift
final class WaveformAnalyzer {

    func generateWaveform(url: URL) -> [Float]
}
```

------------------------------------------------------------------------

# 10. Turntable UI

SwiftUI component.

``` swift
struct TurntableView: View {

    @GestureState var rotation: Angle

}
```

Gestures:

    circular drag → scrubbing
    tap → play/pause
    pinch → waveform zoom

Visual behavior:

-   vinyl rotates during playback
-   rotation speed reflects playbackRate

------------------------------------------------------------------------

# 11. Stereo Channel Routing

Audio pan used for routing.

    player.pan = value

Range:

    -1 → left
    0  → center
    +1 → right

Allows cue monitoring while another app plays music.

------------------------------------------------------------------------

# 12. External BPM Detection (Microphone)

Optional feature.

Pipeline:

    mic input
     ↓
    audio buffer
     ↓
    aubio tempo detection
     ↓
    estimated BPM

Audio session configuration:

    .playAndRecord
    .mixWithOthers

Allows:

-   mic capture
-   external app playback simultaneously

------------------------------------------------------------------------

# 13. UI Layout

Landscape screen layout.

    +------------------------------------------------------------+
    | Controls |                  Deck                           |
    |          |-------------------------------------------------|
    | Load     | waveform                                        |
    | BPM      |                                                 |
    | BPM +/-  | vinyl platter                                   |
    | Time     |                                                 |
    | Volume   |                                                 |
    | Pan      |                                                 |
    +------------------------------------------------------------+

------------------------------------------------------------------------

# 14. Performance Targets

Audio latency target:

    < 20 ms

Buffer size:

    512 frames

UI:

    60 FPS

------------------------------------------------------------------------

# 15. Permissions

Required:

-   microphone (optional BPM detection)
-   file access

------------------------------------------------------------------------

# 16. Development Roadmap

Step 1 --- Create Tuist project

Step 2 --- Implement AudioEngineManager

Step 3 --- MP3 playback

Step 4 --- Waveform analysis

Step 5 --- Turntable UI

Step 6 --- Pitch slider

Step 7 --- aubio BPM detection

Step 8 --- External BPM sync

------------------------------------------------------------------------

# 17. Technical Risks

-   Bluetooth latency (\~150ms)
-   BPM detection stability
-   Smooth scrubbing implementation
-   CPU cost of waveform rendering

------------------------------------------------------------------------

# 18. Future Extensions

Possible upgrades:

-   second deck
-   beat grid
-   automatic sync
-   cue points
-   loops
-   FX (filter, delay)

------------------------------------------------------------------------

# End of Codex‑Ready Specification
