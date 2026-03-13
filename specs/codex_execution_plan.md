# dev.manelix.Mixer --- Codex Execution Plan

## Purpose

This document provides a **step‑by‑step execution plan for OpenAI
Codex** to generate the project incrementally without breaking
compilation.

Each phase should: - compile successfully - keep code minimal - avoid
introducing incomplete subsystems

Target stack:

-   Swift 6
-   SwiftUI
-   Tuist
-   AVFoundation
-   aubio integration later

------------------------------------------------------------------------

# Phase 1 --- Create Base Tuist Project

Goal: Create the root project structure.

Codex tasks:

1.  Initialize Tuist project
2.  Create iOS app target
3.  Configure bundle id

Structure:

    dev.manelix.Mixer
    ├─ Tuist
    │  └─ Project.swift
    ├─ Projects
    │  └─ MixerApp
    └─ Modules

Expected result:

-   `tuist generate` succeeds
-   App launches with blank SwiftUI view

------------------------------------------------------------------------

# Phase 2 --- Basic SwiftUI App Shell

Goal: Add minimal SwiftUI interface.

Tasks:

-   Create `MixerApp.swift`
-   Add root `ContentView`
-   Landscape orientation configuration

UI placeholder:

    struct ContentView: View {
        var body: some View {
            Text("Mixer")
        }
    }

Expected result:

-   app runs
-   UI loads

------------------------------------------------------------------------

# Phase 3 --- Introduce Module Structure

Goal: Create internal modules.

Modules:

    Modules
    ├─ DeckFeature
    ├─ AudioEngine
    ├─ DSP
    ├─ Waveform
    └─ UIComponents

Tasks:

-   create Tuist targets for each module
-   wire dependencies

Dependency flow:

    App
     ↓
    DeckFeature
     ↓
    AudioEngine
     ↓
    DSP

------------------------------------------------------------------------

# Phase 4 --- Audio Engine Skeleton

Goal: Introduce AVAudioEngine wrapper.

Create:

    AudioEngineManager

Responsibilities:

-   initialize engine
-   create player node
-   connect mixer

Example skeleton:

``` swift
final class AudioEngineManager {

    let engine = AVAudioEngine()
    let player = AVAudioPlayerNode()

    func start() throws {
        engine.attach(player)
        engine.connect(player, to: engine.mainMixerNode, format: nil)
        try engine.start()
    }
}
```

Expected result:

-   project builds
-   audio engine starts

------------------------------------------------------------------------

# Phase 5 --- File Loading

Goal: Load audio files from Files.

Tasks:

-   add document picker
-   load URL
-   create AVAudioFile

UI addition:

    Load MP3 button

Expected result:

-   user selects file
-   file URL available

------------------------------------------------------------------------

# Phase 6 --- Basic Playback

Goal: Play selected file.

Steps:

1.  create AVAudioFile
2.  schedule in player node
3.  start playback

Example:

``` swift
player.scheduleFile(file, at: nil)
player.play()
```

Expected result:

-   audio plays

------------------------------------------------------------------------

# Phase 7 --- Waveform Analyzer

Goal: Generate waveform data.

Module:

    WaveformAnalyzer

Pipeline:

    audio decode
     ↓
    downsample
     ↓
    amplitude envelope

Return:

    [Float]

Expected result:

-   waveform array generated

------------------------------------------------------------------------

# Phase 8 --- Waveform Rendering

Goal: Render waveform UI.

Use:

SwiftUI Canvas

Example concept:

    Canvas { context, size in
        draw waveform lines
    }

Expected result:

-   waveform visible

------------------------------------------------------------------------

# Phase 9 --- Turntable UI

Goal: Add vinyl platter interaction.

Component:

    TurntableView

Gestures:

    drag → rotate
    tap → play/pause
    pinch → waveform zoom

Expected result:

-   platter rotates visually

------------------------------------------------------------------------

# Phase 10 --- Vinyl Pitch Control

Goal: Control playback speed.

Formula:

    playbackRate = targetBPM / trackBPM

Implementation:

-   adjust player rate
-   update BPM display

Expected result:

-   BPM changes with pitch slider

------------------------------------------------------------------------

# Phase 11 --- aubio Integration

Goal: Add BPM detection.

Steps:

1.  integrate aubio XCFramework
2.  add C shim
3.  implement Swift wrapper

Class:

    BPMDetector

Expected result:

-   BPM detected from audio samples

------------------------------------------------------------------------

# Phase 12 --- BPM Detection on Track Load

Goal: Compute BPM when file loads.

Pipeline:

    load file
     ↓
    decode PCM
     ↓
    aubio_tempo
     ↓
    estimated BPM

Expected result:

-   BPM displayed

------------------------------------------------------------------------

# Phase 13 --- Microphone BPM Detection

Goal: Estimate BPM from environment.

Requirements:

-   AVAudioSession playAndRecord
-   mixWithOthers

Pipeline:

    mic input
     ↓
    aubio tempo
     ↓
    external BPM

Expected result:

-   BPM sync possible

------------------------------------------------------------------------

# Phase 14 --- Turntable Physics

Goal: Add realistic platter behaviour.

Variables:

    angularVelocity
    inertia
    dragForce

Expected result:

-   smooth rotation

------------------------------------------------------------------------

# Phase 15 --- Scratching

Goal: Allow record scratching.

Behavior:

    touchDown → scratch mode
    drag → position control
    touchUp → resume motor

Expected result:

-   DJ-style scratch interaction

------------------------------------------------------------------------

# Phase 16 --- Beat Grid

Goal: Generate beat grid.

Pipeline:

    onset detection
     ↓
    tempo estimation
     ↓
    beat tracking

Expected result:

-   beat markers

------------------------------------------------------------------------

# Phase 17 --- Cue Points

Goal: Add cue points.

Data structure:

    CuePoint {
     time: TimeInterval
    }

Expected result:

-   jump to cue instantly

------------------------------------------------------------------------

# Phase 18 --- Debug Tools

Add developer overlay showing:

    FPS
    audio latency
    CPU load
    buffer underruns

------------------------------------------------------------------------

# Phase 19 --- Final Optimization

Tasks:

-   reduce allocations
-   ensure realtime safety
-   validate latency

Targets:

  Metric                Target
  --------------------- ---------
  Audio latency         \<20 ms
  Interaction latency   \<10 ms
  UI FPS                60

------------------------------------------------------------------------

# Definition of Done

Project considered complete when:

-   full project builds via Tuist
-   audio playback stable
-   waveform rendering works
-   BPM detection functional
-   scratching responsive
-   sync with external BPM possible

------------------------------------------------------------------------

# End of Codex Execution Plan
