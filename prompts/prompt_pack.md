# dev.manelix.Mixer --- Codex Prompt Pack

## Purpose

This document contains **ready-to-use prompts for Codex** to build
`dev.manelix.Mixer` incrementally and safely.

Rules for every phase:

-   keep compilation green after every change
-   do not introduce placeholder code that breaks build
-   prefer official Apple APIs and stable architecture
-   keep modules small and explicit
-   do not implement future phases early
-   produce code, tests, and brief notes for each phase

Project baseline:

-   app name: `Mixer`
-   bundle id: `dev.manelix.Mixer`
-   platform: iOS
-   language: Swift 6
-   UI: SwiftUI
-   architecture: MVVM
-   project generation: Tuist
-   audio stack: AVFoundation / AVAudioEngine
-   DSP library: aubio
-   interaction model: vinyl mode
-   orientation: landscape only

------------------------------------------------------------------------

# Global Prompt to Prefix Every Codex Run

Use this prefix before each phase prompt.

``` text
You are implementing a production-minded iOS app called dev.manelix.Mixer.

Constraints:
- Swift 6
- SwiftUI
- MVVM
- Tuist project
- iOS only
- landscape only
- prefer official Apple frameworks
- keep compilation passing after every step
- do not add unfinished features
- keep APIs small and explicit
- isolate DSP concerns in a dedicated module
- isolate aubio integration behind a Swift wrapper
- avoid speculative abstractions
- add concise comments only where useful
- when making tradeoffs, prioritize low-latency audio safety and maintainability

For this phase:
1. implement only the requested scope
2. update project files as needed
3. keep all targets compiling
4. provide a short summary of files created/changed
5. list any known limitations introduced intentionally
```

------------------------------------------------------------------------

# Phase 1 Prompt --- Create Base Tuist Project

``` text
Create the initial Tuist-based iOS project for dev.manelix.Mixer.

Requirements:
- app target name: MixerApp
- bundle id: dev.manelix.Mixer
- Swift 6
- iOS deployment target: 17.0
- SwiftUI app entry point
- a minimal ContentView that renders a visible placeholder
- landscape-only orientation configuration
- clean folder structure prepared for future modules

Deliverables:
- Tuist project files
- app target
- minimal SwiftUI app that builds and launches

Do not add audio code yet.
Keep the project compiling.
```

------------------------------------------------------------------------

# Phase 2 Prompt --- Create Module Structure

``` text
Extend the existing Tuist project by introducing internal modules.

Create these modules:
- DeckFeature
- AudioEngine
- DSP
- Waveform
- UIComponents

Requirements:
- wire dependencies explicitly
- App depends on DeckFeature
- DeckFeature can depend on AudioEngine, Waveform, UIComponents
- AudioEngine can depend on DSP
- DSP must remain isolated from UI modules
- each module must compile with minimal placeholder implementations where needed
- keep public APIs extremely small

Deliverables:
- updated Tuist configuration
- module targets
- minimal compile-safe source files in each module

Do not implement real app logic yet.
Do not add aubio yet.
```

------------------------------------------------------------------------

# Phase 3 Prompt --- App Shell and Main Deck Screen

``` text
Implement the first real app shell for dev.manelix.Mixer.

Requirements:
- create a main Deck screen
- use SwiftUI
- landscape layout
- left control panel placeholder
- right turntable area placeholder
- DeckFeature owns the screen and its ViewModel
- introduce a DeckViewModel with only state needed for placeholder rendering
- keep styling simple and functional

UI sections:
- controls column on the left
- deck area on the right
- visible labels for future BPM, waveform, platter, volume, and pan controls

Deliverables:
- DeckView
- DeckViewModel
- simple placeholder UI connected to app launch

No real audio yet.
```

------------------------------------------------------------------------

# Phase 4 Prompt --- AudioEngine Skeleton

``` text
Implement the first version of the AudioEngine module.

Requirements:
- create AudioEngineManager
- internally own AVAudioEngine
- attach an AVAudioPlayerNode
- connect player to main mixer
- expose small APIs:
  - startEngine()
  - stopEngine()
  - isRunning
- ensure the engine can start safely
- no playback file loading yet
- do not expose AVFoundation internals beyond what is necessary

Deliverables:
- compile-safe AudioEngineManager
- minimal unit-testable design where practical

Keep the rest of the app building.
```

------------------------------------------------------------------------

# Phase 5 Prompt --- File Import from Files App

``` text
Implement audio file import for the app.

Requirements:
- add a Load Track button to the left control panel
- use the appropriate Apple document picker flow
- support these formats:
  - mp3
  - wav
  - aiff
  - m4a
- when a file is selected, pass the URL into DeckViewModel
- persist only the in-memory URL for now
- show the selected file name in the UI
- keep the design modular and compile-safe

Deliverables:
- file import UI flow
- ViewModel state for selected track URL and display name

Do not start playback yet.
```

------------------------------------------------------------------------

# Phase 6 Prompt --- Basic Playback

``` text
Add basic audio playback to the existing app.

Requirements:
- use AudioEngineManager
- load an AVAudioFile from the selected URL
- schedule the file on AVAudioPlayerNode
- add Play and Pause controls
- keep playback state in DeckViewModel
- show a minimal status label in the UI
- handle the case where no file is selected
- keep APIs narrow and maintainable

Deliverables:
- loadFile(url:)
- play()
- pause()
- current playback state exposed to DeckFeature

Do not implement rate changes, waveform, or BPM detection yet.
```

------------------------------------------------------------------------

# Phase 7 Prompt --- Track Time Display

``` text
Add time display for the loaded track.

Requirements:
- show current playback time
- show total duration
- use a DJ-style time format: mm:ss / mm:ss
- keep updates lightweight
- avoid overengineering timing infrastructure
- expose only the minimum state needed from AudioEngine to DeckViewModel

Deliverables:
- total duration shown after track load
- current time updates while playing
- UI label in the control panel

Do not add waveform yet.
```

------------------------------------------------------------------------

# Phase 8 Prompt --- Volume and Stereo Pan Controls

``` text
Add volume and stereo routing controls.

Requirements:
- add volume up/down controls
- add a stereo pan slider with range -1...1
- map pan to left/center/right behavior
- keep volume and pan in AudioEngineManager
- reflect current values in the UI
- ensure changes apply during playback

Deliverables:
- volume control API
- pan control API
- DeckViewModel bindings for these controls

Do not implement crossfader logic between multiple decks.
This is single-track pan routing only.
```

------------------------------------------------------------------------

# Phase 9 Prompt --- Waveform Analysis Engine

``` text
Implement waveform extraction in the Waveform module.

Requirements:
- create WaveformAnalyzer
- input: local audio file URL
- output: downsampled waveform data as [Float]
- use AVFoundation to decode
- compute a simple amplitude envelope suitable for UI rendering
- keep the analyzer independent from SwiftUI
- return deterministic results for the same file
- make the API synchronous or async only if justified clearly

Deliverables:
- WaveformAnalyzer API
- waveform data model if needed
- integration point for DeckViewModel after file load

Do not render the waveform yet.
```

------------------------------------------------------------------------

# Phase 10 Prompt --- Waveform Rendering UI

``` text
Render the analyzed waveform in the deck area.

Requirements:
- create a reusable WaveformView in UIComponents or Waveform module
- use SwiftUI and Canvas
- render the waveform above the platter area
- center a fixed playhead marker
- make the waveform horizontally scroll relative to current playback time
- keep the first implementation simple and smooth
- if needed, start with a non-zoomable waveform

Deliverables:
- waveform view wired to DeckViewModel
- waveform updates during playback

No beat markers yet.
```

------------------------------------------------------------------------

# Phase 11 Prompt --- Waveform Zoom

``` text
Add waveform zoom in/out.

Requirements:
- support pinch gesture or explicit +/- zoom controls
- zoom affects the horizontal time scale of the waveform
- keep current playhead centered
- store zoom state in DeckViewModel
- enforce sensible min/max zoom limits
- avoid unnecessary redraw complexity

Deliverables:
- zoomable waveform
- stable playback-linked scrolling

Do not add beat grid overlays yet.
```

------------------------------------------------------------------------

# Phase 12 Prompt --- Turntable UI

``` text
Implement the visual turntable component.

Requirements:
- create TurntableView
- show a vinyl-like platter on the right side below the waveform
- animate rotation while playback is active
- rotation speed should reflect playback rate conceptually, even if rate is still fixed at 1.0 now
- keep the visual design lightweight and performant
- connect play/pause state from DeckViewModel

Deliverables:
- reusable TurntableView
- playback-driven rotation animation

No scratching yet.
```

------------------------------------------------------------------------

# Phase 13 Prompt --- Vinyl Mode Rate Control

``` text
Implement vinyl-mode speed control.

Requirements:
- changing playback rate must change both BPM and pitch
- do not use digital time-stretching
- add BPM display in the left panel
- add increment and decrement controls for BPM adjustment
- maintain:
  - original track BPM (placeholder/manual if detection not built yet)
  - current target BPM
  - playbackRate
- use a Technics-style mental model:
  - playbackRate = targetBPM / originalBPM
- keep the first implementation conservative and stable

Deliverables:
- rate control APIs
- BPM display UI
- BPM +/- buttons wired to playback rate

Do not add automatic BPM detection yet.
```

------------------------------------------------------------------------

# Phase 14 Prompt --- DSP Module and Aubio Integration Surface

``` text
Prepare the DSP module for aubio integration.

Requirements:
- do not expose raw aubio C APIs to the rest of the app
- add a small Swift-facing abstraction for tempo detection
- add placeholder protocols/types that the app can compile against:
  - TempoDetecting
  - BPMResult
- if aubio binary integration is not yet added in this phase, structure the code so it can be dropped in cleanly later
- document expected sample format contracts in code comments

Deliverables:
- DSP abstractions
- compile-safe placeholder implementation if needed

Do not fake real BPM detection results beyond clearly-marked stub behavior.
```

------------------------------------------------------------------------

# Phase 15 Prompt --- Add Aubio XCFramework Integration

``` text
Integrate aubio into the DSP module.

Requirements:
- assume aubio is provided as an XCFramework or binary dependency
- add the dependency only to the DSP module
- create a narrow wrapper around the aubio tempo detector
- manage native memory safely
- expose a Swift API suitable for offline BPM estimation
- keep the wrapper small and explicit
- avoid leaking aubio-specific types outside DSP

Deliverables:
- DSP target linked to aubio
- wrapper implementation
- clear error handling

Do not integrate mic BPM yet.
```

------------------------------------------------------------------------

# Phase 16 Prompt --- Offline BPM Detection on Track Load

``` text
Implement offline BPM detection for imported tracks.

Requirements:
- when a file is selected, analyze it through the DSP module
- estimate BPM using aubio
- store the result as original track BPM
- update the BPM display in the UI
- handle failure cases gracefully
- allow manual BPM adjustment if detection fails
- keep analysis work off the audio playback hot path

Deliverables:
- BPM analysis pipeline on track load
- UI update path for detected BPM
- clear fallback behavior

Do not attempt beat grid detection yet.
```

------------------------------------------------------------------------

# Phase 17 Prompt --- Microphone BPM Detection

``` text
Add optional microphone-based BPM detection.

Requirements:
- configure AVAudioSession to allow microphone capture while mixing with other apps
- capture mic input safely
- feed suitable analysis buffers into the DSP module
- display detected external BPM separately from track BPM
- keep this feature optional and easy to disable
- respect real-time audio safety rules

Deliverables:
- mic BPM detection pipeline
- UI display for external BPM
- feature toggle or explicit start/stop control

Do not attempt system audio capture from other apps.
Use microphone input only.
```

------------------------------------------------------------------------

# Phase 18 Prompt --- First Turntable Gesture Support

``` text
Add the first turntable gesture behavior.

Requirements:
- detect circular dragging on the platter
- translate angular drag into track position movement
- keep the implementation simple and stable first
- support scrubbing forward and backward
- ensure UI and playback stay in sync
- avoid glitchy restart-heavy behavior as much as possible

Deliverables:
- turntable gesture recognition
- basic scrub behavior
- DeckViewModel integration

This phase can use a simpler seek-based implementation before advanced buffer-based scratching.
```

------------------------------------------------------------------------

# Phase 19 Prompt --- Turntable Physics Layer

``` text
Introduce a dedicated turntable physics model.

Requirements:
- create a TurntablePhysics component or model
- represent at least:
  - platterPosition
  - angularVelocity
  - inertia/damping
- separate visual platter state from audio engine details
- update the platter smoothly during playback
- prepare the architecture for future scratch realism

Deliverables:
- turntable physics type
- integration with TurntableView and DeckViewModel

Do not overcomplicate the first model.
```

------------------------------------------------------------------------

# Phase 20 Prompt --- Improved Scratching Architecture

``` text
Refine scratching behavior toward a DJ-style vinyl interaction.

Requirements:
- support touch-down, drag, and release states
- platter touch should temporarily override motor-driven motion
- release should resume motor-driven behavior smoothly
- improve audio responsiveness compared with simple seek jumps
- keep the implementation production-minded
- if a ring buffer or buffered playback strategy is needed, introduce it carefully and keep compilation stable

Deliverables:
- improved scratch state model
- smoother transition between scratch mode and playback mode

Document any remaining limitations clearly.
```

------------------------------------------------------------------------

# Phase 21 Prompt --- Beat Grid Analysis

``` text
Add beat grid analysis for loaded tracks.

Requirements:
- extend the DSP layer with beat-position analysis if practical with current tooling
- at minimum, produce a simple beat marker model aligned to detected BPM
- store beat marker data with the loaded track
- keep the first version approximate but structured for future refinement
- do not block playback while analyzing

Deliverables:
- beat marker or beat grid model
- integration with DeckViewModel

Do not add cue points yet.
```

------------------------------------------------------------------------

# Phase 22 Prompt --- Beat Markers in Waveform

``` text
Render beat markers on top of the waveform.

Requirements:
- show vertical beat lines over the waveform
- keep markers aligned with waveform time scale
- preserve zoom behavior
- make visuals clear but lightweight
- keep playhead centered

Deliverables:
- waveform overlay for beat markers
- integration with beat grid data
```

------------------------------------------------------------------------

# Phase 23 Prompt --- Cue Points

``` text
Add cue point support.

Requirements:
- support at least one stored cue point for the loaded track
- allow setting cue at current playback time
- allow jumping back to cue
- keep timing as accurate as practical with the current engine
- reflect cue availability in the UI

Deliverables:
- cue point model
- set/jump cue actions
- basic cue UI
```

------------------------------------------------------------------------

# Phase 24 Prompt --- Debug Overlay and Diagnostics

``` text
Add a developer-oriented debug overlay.

Requirements:
- optionally show:
  - current playback rate
  - track BPM
  - external BPM
  - waveform zoom
  - audio engine running state
  - selected file name
- keep the overlay easy to disable
- do not add expensive profiling logic in the audio thread

Deliverables:
- debug overlay UI
- debug state plumbing
```

------------------------------------------------------------------------

# Phase 25 Prompt --- Real-Time Audio Safety Pass

``` text
Perform a real-time audio safety pass over the codebase.

Requirements:
- identify code that may allocate or block in hot audio paths
- move heavy work off the audio path where needed
- reduce unnecessary timer churn
- ensure DSP analysis is not performed unsafely on the playback hot path
- keep the code readable and maintainable
- document key safety decisions in concise comments or notes

Deliverables:
- targeted refactors only where needed
- short summary of real-time safety improvements
```

------------------------------------------------------------------------

# Phase 26 Prompt --- Final Integration and Polish

``` text
Perform final integration for the current scope of dev.manelix.Mixer.

Requirements:
- ensure the full project builds cleanly via Tuist
- ensure module boundaries are respected
- remove dead placeholder code that is no longer needed
- tighten naming and API consistency
- ensure the UI reflects the implemented features cleanly
- keep the project focused on:
  - single deck
  - vinyl mode
  - waveform
  - BPM detection
  - mic BPM detection
  - stereo pan routing
  - turntable interaction

Deliverables:
- compile-safe integrated project
- short final summary of implemented scope
- concise list of known non-goals or deferred items
```

------------------------------------------------------------------------

# Optional Prompt --- Generate Tests for Current Phase

Use this after any phase where Codex adds meaningful logic.

``` text
Add focused tests for the code introduced in the current phase.

Requirements:
- prefer small, deterministic tests
- avoid UI snapshot tests unless clearly justified
- test pure logic first:
  - waveform math
  - BPM-related conversions
  - turntable physics
  - cue logic
- do not add brittle tests that depend on timing-sensitive audio playback when pure logic tests are available

Deliverables:
- compile-safe tests only
- short explanation of what is covered
```

------------------------------------------------------------------------

# Optional Prompt --- Refactor Without Changing Behavior

Use this when a phase got messy.

``` text
Refactor the existing implementation without changing behavior.

Requirements:
- keep all public behavior unchanged
- preserve buildability
- reduce duplication
- improve naming
- simplify state flow between DeckViewModel and underlying modules
- do not introduce new features
- summarize the refactor clearly
```

------------------------------------------------------------------------

# Optional Prompt --- Produce Phase Summary

``` text
Summarize the current implementation status of dev.manelix.Mixer.

Include:
- modules currently implemented
- features complete
- features partial
- known technical debt
- next recommended phase from the execution plan

Keep the summary concise and engineering-focused.
```

------------------------------------------------------------------------

# Recommended Codex Operating Notes

-   Run one phase at a time.
-   Do not combine distant phases unless the codebase is already stable.
-   Ask Codex to keep diffs small.
-   After major audio changes, run a build before asking for the next
    phase.
-   Introduce aubio only after the non-DSP architecture is already
    stable.
-   Prefer deterministic offline BPM analysis before real-time mic
    analysis.
-   Keep the first scratching version simple before introducing advanced
    buffered playback.

------------------------------------------------------------------------

# End of Codex Prompt Pack
