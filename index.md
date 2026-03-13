# dev.manelix.Mixer --- Master Specification Index

This document provides a **single entry point** to all technical
specifications for the `dev.manelix.Mixer` project.

The project is an iOS DJ-style audio application designed with:

-   Swift 6
-   SwiftUI
-   MVVM architecture
-   Tuist project generation
-   AVFoundation / AVAudioEngine
-   aubio DSP integration

The documents below together form the **complete technical design
package**.

------------------------------------------------------------------------

# 1. Core Application Specification

Defines the product concept, UI layout, and main functionality.

Includes:

-   single screen DJ deck layout
-   waveform visualization
-   vinyl platter interaction
-   BPM controls
-   stereo routing
-   microphone BPM detection

Document:

`codex_spec.md`

------------------------------------------------------------------------

# 2. Audio Engine Deep Specification

Describes the **low-level audio architecture**.

Includes:

-   AVAudioEngine topology
-   ring buffer strategy
-   playback scheduling
-   scratch interaction model
-   latency targets

Document:

`audio_engine_deep_spec.md`

------------------------------------------------------------------------

# 3. Turntable Physics & Beatgrid Specification

Defines the **vinyl simulation model**.

Includes:

-   platter inertia simulation
-   scratch interaction model
-   beat grid detection
-   beat phase synchronization
-   cue point behavior

Document:

`turntable_physics_spec.md`

------------------------------------------------------------------------

# 4. Aubio iOS Integration Specification

Explains how the **aubio DSP library** is integrated.

Includes:

-   XCFramework packaging
-   Tuist dependency setup
-   C shim layer
-   Swift wrapper API
-   BPM detection pipeline

Document:

`aubio_ios_integration_spec.md`

------------------------------------------------------------------------

# 5. Codex Execution Plan

Step-by-step engineering roadmap.

Includes:

-   incremental build phases
-   module rollout strategy
-   audio engine introduction
-   DSP integration milestones

Document:

`codex_execution_plan.md`

------------------------------------------------------------------------

# 6. Audio Latency & Real-Time Constraints

Defines strict rules for **real-time audio safety**.

Includes:

-   forbidden operations on audio threads
-   buffer sizing strategy
-   threading model
-   DSP scheduling rules

Document:

`audio_latency_spec.md`

------------------------------------------------------------------------

# 7. Audio Engine Architecture Diagram

Visual system architecture.

Includes:

-   UI → ViewModel → Audio → DSP layers
-   audio graph design
-   gesture pipeline
-   thread model

Document:

`architecture_diagram.md`

------------------------------------------------------------------------

# Recommended Reading Order

For engineers joining the project:

1.  Core Application Specification
2.  Audio Engine Architecture Diagram
3.  Audio Engine Deep Specification
4.  Audio Latency & Real-Time Constraints
5.  Turntable Physics & Beatgrid Specification
6.  Aubio Integration Specification
7.  Codex Execution Plan
8.  Codex Prompt Pack

------------------------------------------------------------------------

# Project Architecture Summary

    UI (SwiftUI)
     ↓
    DeckViewModel (MVVM)
     ↓
    AudioEngineManager
     ↓
    AVAudioEngine
     ↓
    DSP Layer (aubio, waveform, beat grid)

------------------------------------------------------------------------

# Core Design Principles

1.  Keep audio thread real-time safe
2.  Avoid allocations in playback path
3.  Isolate DSP from UI
4.  Keep module boundaries clear
5.  Prefer deterministic offline analysis
6.  Maintain low-latency gesture interaction

------------------------------------------------------------------------

# End of Master Specification Index
