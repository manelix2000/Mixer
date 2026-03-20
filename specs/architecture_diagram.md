# dev.manelix.Mixer — Architecture Diagram (Current Implementation)

## Scope
This file reflects the real state of the codebase as implemented today, not the target/future architecture.

## Module Graph
```text
Projects/MixerApp (iOS app target)
  └─ DeckFeature
      ├─ AudioEngine
      │   └─ DSP
      ├─ Waveform
      └─ UIComponents
```

All modules currently target iOS 17+.  
App also runs as iOS-on-Mac (`ProcessInfo.processInfo.isiOSAppOnMac`) with dedicated runtime handling in audio/mic paths.

## Runtime Layering
```text
SwiftUI View Layer
  DeckView
    ├─ TurntableDeckView (left)
    ├─ TurntableDeckView (right)
    ├─ SplitCueControls or per-deck Pan controls
    └─ Settings card (engine mode + split deck layout)

State / Coordinator Layer
  DeckViewModel
    ├─ left TurntableDeckViewModel
    ├─ right TurntableDeckViewModel
    ├─ split/cue UI state + routing gain policy
    └─ microphone BPM pipeline coordinator

Per-Deck Engine Layer
  TurntableDeckViewModel
    └─ AudioEngineControlling (factory-produced)

Audio Engine Implementations
  DefaultAudioEngineFactory -> SplitAudioEngineManager wrapper
    └─ AudioEngineManager (actual AVAudioEngine playback/mic implementation)

DSP / Analysis Layer
  DSPModule.makeTempoDetector()
    ├─ AubioTempoDetector (when Aubio is linked)
    └─ StubTempoDetector (fallback)
  WaveformAnalyzer
```

## Audio Engine Topology (Per Deck)
`AudioEngineManager` configures:
```text
AVAudioEngine
  AVAudioPlayerNode -> AVAudioUnitVarispeed -> mainMixerNode -> outputNode
```

Controls applied per deck:
- playback state: load / play / pause / seek
- scratch/scrub methods (`beginScratch`, `scratch`, `endScratch`)
- output gain (`setVolume`)
- pan (`setPan`)
- playback rate (`setPlaybackRate`)

## Split Mode and Routing
`DefaultAudioEngineFactory` currently always returns `SplitAudioEngineManager` (split-capable wrapper), so runtime mode changes apply without rebuilding deck engines.

`SplitAudioEngineManager`:
- reads `AudioEngineMode` (`standard` / `split`) from `UserDefaultsAudioEngineModeStore`
- reads `SplitDeckLayout` from `UserDefaultsSplitDeckLayoutStore`
- derives deck role (`master` or `cue`) from slot index + selected layout
- constrains pan range by role in split mode:
  - `master`: `-1...0`
  - `cue`: `0...1`

`DeckViewModel` split controls currently use gain/pan policy:
- per-deck `CUE` toggles
- cue mix mode (`cue`, `blend`, `master`) via horizontal fader
- cue level via horizontal fader
- applies effective per-deck master volume factors according to current mode/role

Important: this is not yet a true dual-bus master/cue mixer graph; it is a functional routing policy over current per-deck outputs.

## BPM Pipelines
### Track BPM
```text
Imported file -> TempoInputBuffer -> DSP tempo detector -> BPMResult -> TurntableDeckViewModel
```

### Microphone BPM
```text
Mic capture buffer -> DeckViewModel.MicrophoneBPMPipeline -> DSP tempo detector -> BPMResult -> DeckViewModel UI state
```

Mic capture implementation:
- iPhone/iPad path: `AVAudioSession` + `AVAudioEngine` input tap
- iOS-on-Mac path:
  - try dedicated `AVAudioEngine` input tap first
  - fallback to `AVCaptureSession` audio output delegate when needed

## Waveform Pipeline
`WaveformAnalyzer`:
```text
Audio file -> AVAudioFile read chunks -> bucketed RMS/peak blend -> normalized waveform [Float] -> UI rendering
```

Progressive loading snapshots are emitted during analysis.

## Gesture and Turntable Flow
```text
Touch/drag/press gestures (TurntableDeckView)
  -> TurntableDeckViewModel interaction methods
  -> TurntablePhysics state update
  -> audioEngine seek/scratch/rate changes
  -> playback + platter UI update
```

Includes:
- scrub/scratch mode switching
- angular velocity smoothing
- pressure/long-press pitch modulation path

## Persistence and Runtime Overrides
Persistent settings:
- `dev.manelix.Mixer.audioEngine.selectedMode`
- `dev.manelix.Mixer.audioEngine.splitDeckLayout`

Runtime overrides supported:
- `-MixerAudioEngineMode` / `MIXER_AUDIO_ENGINE_MODE`
- `-MixerSplitDeckLayout` / `MIXER_SPLIT_DECK_LAYOUT`

## Threading Model (Current)
- MainActor:
  - SwiftUI views and all published deck state
- AVAudioEngine real-time threads:
  - playback render path
- Dedicated dispatch queues:
  - mic ingest + BPM analysis pipeline queue
  - background utility tasks for waveform/BPM/artwork loading
  - mic capture session control queue on iOS-on-Mac

## Known Architectural Gaps vs Ideal DJ Engine
- No dedicated master/cue output buses yet (current split mode is policy-based).
- No independent hardware output assignment layer (speaker vs headphones per bus) in current graph.
- Scratch/transport and cue routing share existing deck engine instances rather than a separate mixer bus stage.
