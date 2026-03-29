# Mixer iOS -> Android Migration Plan

## Scope

This plan defines how to migrate the current iOS app behavior to Android in `Projects/MixerAndroid` only.

Hard constraints:

1. Keep Android work strictly inside `Projects/MixerAndroid`.
2. Deliver one phase at a time.
3. Keep Android compilation passing at the end of every phase.
4. Preserve feature parity with current iOS implementation.
5. Replicate SwiftUI behavior in Jetpack Compose (layout, controls, interactions, states).
6. Keep DSP isolated in a dedicated module.
7. Build Aubio manually for Android (NDK/CMake + JNI shim).
8. Do not implement future phases early.

## Source of Truth Mapping

Android parity is code-first and mirrors the current iOS implementation directly.

Authoritative source:

- iOS Swift code in:
  - `Modules/DeckFeature`
  - `Modules/UIComponents`
  - `Modules/AudioEngine`
  - `Modules/DSP`
  - `Modules/Waveform`
  - `Projects/MixerApp`

Non-authoritative references:

- Markdown specs and docs can be used only as context when code behavior is ambiguous.
- If docs conflict with code, iOS code wins.

## Android Target Architecture

Proposed multi-module layout in `Projects/MixerAndroid`:

- `app` -> Android entrypoint, Activity, navigation host
- `feature/deck` -> main deck screen + ViewModels + UI orchestration
- `core/audio` -> playback engine abstraction/implementation, mic capture, routing
- `core/dsp` -> tempo detection API + Aubio JNI bridge + fallback detector
- `core/waveform` -> waveform analysis pipeline
- `core/ui` -> reusable Compose components (turntable, waveform, faders, badges)
- `core/common` -> shared models, result types, dispatchers, utilities
- `third_party/aubio` -> aubio source/build scripts/artifacts metadata
- `scripts` -> reproducible native build scripts and verification helpers

Dependency direction:

`app -> feature/deck -> core/audio -> core/dsp`

`feature/deck -> core/ui, core/waveform, core/common`

`core/dsp` is the only module that knows about Aubio/JNI.

## UI Parity Baseline (SwiftUI -> Compose)

The Compose screen must match existing SwiftUI behavior:

1. Top-level deck layout with controls rail, settings card, and one/two deck visibility behavior.
2. Left and right turntable decks with waveform card + platter + transport + faders.
3. Equalizer overlay state and deck-level overlay controls.
4. Split mode controls (cue toggles, mix mode fader, cue level fader).
5. Mic BPM status badge and external BPM lock/unlock state.
6. Waveform interactions: tap seek, drag scratch, pinch zoom.
7. Platter interactions: touch begin/move/end, scratch transition, pressure touch path.
8. Pitch fader behavior and sensitivity controls.
9. Deck settings persistence equivalents (audio engine mode, split layout).
10. Visual/animation timing parity where feasible.

## Migration Phases

### Phase 0 - Baseline and Parity Inventory

Scope:

- Freeze parity checklist from current iOS implementation.
- Capture exact UI/state/interaction inventory from current Swift code (views, viewmodels, engine, DSP, waveform).
- Define Android API-level baseline, ABI set, and build tooling versions.

Exit criteria:

- Written parity checklist and behavior matrix committed.
- No Android runtime code yet.

### Phase 1 - Android Project Bootstrap

Scope:

- Initialize Android multi-module project in `Projects/MixerAndroid`.
- Configure Kotlin, Compose, Hilt, Room 3 (if needed by current scope), coroutines.
- Add version catalog, convention plugins/build logic, lint/test scaffolding.

Exit criteria:

- `./gradlew assembleDebug` succeeds.
- App launches with minimal placeholder host screen.

### Phase 2 - Core Contracts and State Models

Scope:

- Port shared contracts from iOS:
  - track model
  - playback state
  - BPM result/status
  - split/cue routing state
  - waveform state
  - turntable physics state containers

Exit criteria:

- Module compile passes.
- Unit tests for core value/state transformations pass.

### Phase 3 - Compose UI Skeleton with Structural Parity

Scope:

- Build static Compose screen hierarchy matching iOS structure.
- Add left/right deck containers, controls rail, settings card, equalizer overlay placeholders.
- Add all visible controls with no-op handlers first.

Exit criteria:

- UI hierarchy visually aligned with iOS layout.
- Screenshot baseline captured for parity tracking.

### Phase 4 - Audio Engine Skeleton

Scope:

- Introduce `AudioEngineController` abstraction in `core/audio`.
- Implement Android playback skeleton (load/play/pause/seek/rate/volume/pan, playback clock).
- Add lifecycle-safe start/stop and error surface.

Exit criteria:

- Compile and basic playback control contract tests pass.

### Phase 5 - Track Import and Basic Playback Wiring

Scope:

- Integrate Android document picker and URI handling.
- Load selected track into audio engine.
- Wire play/pause/stop and playback time/progress updates to Compose state.

Exit criteria:

- User can import and play a track.
- Deck state updates in UI.

### Phase 6 - Waveform Analysis and Rendering

Scope:

- Implement waveform analyzer pipeline in `core/waveform`.
- Render waveform in Compose with progress and loading states.
- Add zoom controls + pinch zoom + tap seek behavior.

Exit criteria:

- Waveform displays and responds to interactions.

### Phase 7 - Turntable Physics and Gesture Wiring

Scope:

- Port turntable physics model (inertia, damping, motor target velocity).
- Port scratch/scrub gesture transitions and angular mapping.
- Wire platter/tonearm visual state to playback/physics.

Exit criteria:

- Turntable interaction is functional and smooth.
- Gesture -> audio path works without blocking UI.

### Phase 8 - Split/Cue Routing and Control Parity

Scope:

- Implement split mode and deck role policies.
- Add cue toggles, cue mix mode, cue level routing behavior.
- Persist and restore mode/layout settings.

Exit criteria:

- Split/cue controls reproduce iOS behavior at feature level.

### Phase 9 - Manual Aubio Build for Android + JNI Bridge

Scope:

- Add Aubio source under `third_party/aubio` (pinned version metadata).
- Build static libs for target ABIs via NDK/CMake.
- Create minimal JNI shim and Kotlin wrapper in `core/dsp`.
- Add fallback stub detector for unsupported/runtime failure paths.

Exit criteria:

- Native build reproducible via script.
- App builds for all target ABIs with no missing symbols.

### Phase 10 - BPM Detection Integration

Scope:

- Offline BPM detection on track load.
- Mic BPM detection pipeline and status handling.
- External BPM lock-to-pitch behavior parity.

Exit criteria:

- BPM values are surfaced in UI and influence pitch lock flow.

### Phase 11 - Real-Time Safety and Performance Hardening

Scope:

- Enforce real-time constraints:
  - no allocations in hot audio paths
  - no blocking calls on audio-critical threads
  - isolated DSP/background analysis threads
- Add instrumentation for latency/underrun metrics.

Exit criteria:

- Stable playback under stress scenarios.
- Latency targets validated as close as hardware allows.

### Phase 12 - Verification and Release Readiness

Scope:

- Full parity audit against iOS checklist.
- End-to-end QA matrix (device classes, Android versions, route changes).
- Document known non-parity items if any.

Exit criteria:

- Migration report complete.
- Android project is shippable for defined scope.

## Aubio Manual Build Plan (Detailed)

1. Pin Aubio version and commit metadata in `third_party/aubio/README.md`.
2. Add CMake integration and NDK toolchain config for target ABIs.
3. Build static aubio libs per ABI.
4. Build JNI bridge exposing only required functions:
   - create/destroy tempo detector
   - process float mono frames
   - read BPM/confidence/beat-detected flag
5. Keep JNI API narrow; hide raw aubio types from Kotlin.
6. Provide deterministic build script under `scripts/`:
   - clean previous outputs
   - build all ABIs
   - verify artifacts
   - print versions/ABIs/output paths

## Quality Gates Per Phase

For every phase:

1. Compile check passes.
2. No scope creep into future phases.
3. Behavior parity decisions reference concrete iOS code paths (file/class/function).
4. Public APIs documented in changed modules.
5. Brief phase summary and intentional limitations documented.

## Initial Risks and Mitigations

1. Exact UI parity in Compose vs SwiftUI rendering differences.
   - Mitigation: parity checklist + screenshot diff baseline + iterative tuning.
2. Low-latency scratch path on heterogeneous Android devices.
   - Mitigation: strict thread model, instrumentation, conservative defaults.
3. Native Aubio build complexity across ABIs/toolchains.
   - Mitigation: pinned NDK, scripted reproducible builds, minimal JNI surface.
4. Audio routing differences vs iOS split behavior.
   - Mitigation: model routing policy first, then hardware-specific tuning.

## Out of Scope for Initial Migration

1. New features not present in current iOS implementation.
2. Architectural rewrites that alter current feature behavior.
3. Future roadmap features not already in iOS scope/specs.

## Definition of Done

Migration is complete when:

1. Android app reproduces current iOS feature set for deck workflow.
2. Compose UI matches SwiftUI interaction model and core visual structure.
3. Audio playback, waveform, turntable interaction, split/cue, and BPM flows are functional.
4. Aubio manual build + JNI pipeline is reproducible and integrated.
5. All Android artifacts and source remain inside `Projects/MixerAndroid`.
