# Phase 0 - Code Parity Matrix (iOS Source of Truth)

This document is the authoritative Android migration baseline.

Rules:

1. iOS Swift code is the source of truth.
2. If docs/specs conflict with code, code wins.
3. Android implementation decisions must map to referenced iOS files/classes/functions.

## iOS Runtime Topology to Mirror

1. Root screen and composition:
`Modules/DeckFeature/Sources/DeckView.swift:6`
2. Per-deck UI and interaction surface:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:9`
3. Root state coordinator:
`Modules/DeckFeature/Sources/DeckViewModel.swift:9`
4. Per-deck state + playback interaction coordinator:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:12`
5. Audio control contract:
`Modules/AudioEngine/Sources/AudioEngineManager.swift:14`
6. Split routing wrapper:
`Modules/AudioEngine/Sources/SplitAudioEngineManager.swift:12`
7. Turntable visual component:
`Modules/UIComponents/Sources/TurntableView.swift:6`
8. Waveform visual component:
`Modules/UIComponents/Sources/WaveformView.swift:3`
9. Waveform analysis contract/impl:
`Modules/Waveform/Sources/WaveformAnalyzer.swift:4`, `Modules/Waveform/Sources/WaveformAnalyzer.swift:35`
10. Tempo detection contract:
`Modules/DSP/Sources/TempoDetecting.swift:31`
11. Aubio-backed tempo detector:
`Modules/DSP/Sources/AubioTempoDetector.swift:13`

## UI Parity Matrix (SwiftUI -> Compose)

1. Main layout shell with controls rail, settings card, and deck row:
`Modules/DeckFeature/Sources/DeckView.swift:52`
2. Controls visibility and settings toggles:
`Modules/DeckFeature/Sources/DeckView.swift:89`
3. Mic BPM toggle and pitch lock toggle buttons:
`Modules/DeckFeature/Sources/DeckView.swift:137`, `Modules/DeckFeature/Sources/DeckView.swift:155`
4. Split mode settings controls:
`Modules/DeckFeature/Sources/DeckView.swift:253`, `Modules/DeckFeature/Sources/DeckView.swift:256`
5. Split cue controls UI (deck cue toggles + mix + cue level):
`Modules/DeckFeature/Sources/DeckView.swift:481`
6. Deck pan controls behavior in standard mode:
`Modules/DeckFeature/Sources/DeckView.swift:671`
7. Deck waveform card and transport controls:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:359`
8. Track import action:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:375`
9. Waveform tap-to-seek:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:449`
10. Waveform pinch zoom:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:467`
11. Waveform drag scratch:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:869`
12. Platter touch begin/move/end pipeline:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:743`, `Modules/DeckFeature/Sources/TurntableDeckView.swift:766`, `Modules/DeckFeature/Sources/TurntableDeckView.swift:835`
13. Pitch fader and sensitivity controls:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:649`, `Modules/DeckFeature/Sources/TurntableDeckView.swift:698`
14. Start/Pause and Stop transport buttons:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:512`, `Modules/DeckFeature/Sources/TurntableDeckView.swift:566`
15. Equalizer overlay structure:
`Modules/DeckFeature/Sources/TurntableDeckView.swift:247`

## ViewModel State Parity Matrix

### Root deck state (`DeckViewModel`)

1. Split/cue and external BPM state fields:
`Modules/DeckFeature/Sources/DeckViewModel.swift:16`
2. Split mode behavior transitions:
`Modules/DeckFeature/Sources/DeckViewModel.swift:119`
3. Split layout change handling:
`Modules/DeckFeature/Sources/DeckViewModel.swift:141`
4. Cue mix/level behavior:
`Modules/DeckFeature/Sources/DeckViewModel.swift:165`, `Modules/DeckFeature/Sources/DeckViewModel.swift:180`, `Modules/DeckFeature/Sources/DeckViewModel.swift:186`
5. Pitch lock orchestration with external BPM:
`Modules/DeckFeature/Sources/DeckViewModel.swift:264`, `Modules/DeckFeature/Sources/DeckViewModel.swift:283`
6. Microphone BPM lifecycle:
`Modules/DeckFeature/Sources/DeckViewModel.swift:296`, `Modules/DeckFeature/Sources/DeckViewModel.swift:308`, `Modules/DeckFeature/Sources/DeckViewModel.swift:330`, `Modules/DeckFeature/Sources/DeckViewModel.swift:411`

### Per-deck state (`TurntableDeckViewModel`)

1. Playback/waveform/pitch/interaction state fields:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:34`
2. Track selection/import and load behavior:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:156`
3. Transport actions:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:273`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:289`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:302`
4. Mixer controls:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:333`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:344`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:350`
5. BPM/pitch operations:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:387`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:405`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:414`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:470`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:478`
6. Waveform zoom and seek operations:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:486`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:524`
7. Scrub/scratch lifecycle:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:553`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:587`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:635`
8. Pressure touch lifecycle:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:678`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:701`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:725`
9. Background processing hooks:
`Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:949`, `Modules/DeckFeature/Sources/TurntableDeckViewModel.swift:1080`

## Audio Engine Contract Parity (must be preserved)

Android `core/audio` must provide equivalent operations to:
`Modules/AudioEngine/Sources/AudioEngineManager.swift:14`

Required operation parity:

1. `startEngine`, `stopEngine`
2. `loadFile`, `play`, `pause`, `seek`
3. `beginScratch`, `scratch`, `endScratch`
4. `setVolume`, `setPan`, `setPlaybackRate`, `setEqualizer`
5. `startMicrophoneCapture`, `stopMicrophoneCapture`

Related implementation behavior references:

1. Playback load/schedule flow:
`Modules/AudioEngine/Sources/AudioEngineManager.swift:205`, `Modules/AudioEngine/Sources/AudioEngineManager.swift:608`
2. Scratch scheduling path:
`Modules/AudioEngine/Sources/AudioEngineManager.swift:294`, `Modules/AudioEngine/Sources/AudioEngineManager.swift:313`, `Modules/AudioEngine/Sources/AudioEngineManager.swift:1135`
3. Microphone ingest path:
`Modules/AudioEngine/Sources/AudioEngineManager.swift:428`, `Modules/AudioEngine/Sources/AudioEngineManager.swift:1178`
4. Split wrapper role-safe pan behavior:
`Modules/AudioEngine/Sources/SplitAudioEngineManager.swift:102`, `Modules/AudioEngine/Sources/SplitAudioEngineManager.swift:137`

## DSP and Waveform Contract Parity

1. Tempo input contract:
`Modules/DSP/Sources/TempoDetecting.swift:12`
2. Tempo detection API and result semantics:
`Modules/DSP/Sources/TempoDetecting.swift:31`, `Modules/DSP/Sources/BPMResult.swift:5`
3. Aubio detector behavior:
`Modules/DSP/Sources/AubioTempoDetector.swift:25`
4. Fallback detector behavior:
`Modules/DSP/Sources/StubTempoDetector.swift:8`
5. Waveform analyzer contract:
`Modules/Waveform/Sources/WaveformAnalyzer.swift:4`
6. Progressive waveform generation behavior:
`Modules/Waveform/Sources/WaveformAnalyzer.swift:42`

## Persistence and Runtime Mode Parity

1. Audio engine mode storage and overrides:
`Modules/AudioEngine/Sources/AudioEngineMode.swift:21`, `Modules/AudioEngine/Sources/AudioEngineMode.swift:50`
2. Split deck layout storage and overrides:
`Modules/AudioEngine/Sources/AudioEngineMode.swift:69`, `Modules/AudioEngine/Sources/AudioEngineMode.swift:98`

Android equivalent required:

1. Persisted engine mode and split layout.
2. Safe defaults matching iOS startup behavior.

## Android Phase 1 Input Checklist

Before bootstrapping code:

1. Keep this matrix as the parity reference for all migrations.
2. Use iOS file/class/function references in every Android PR/task note.
3. Do not implement features not represented in mapped iOS code.
4. Keep all Android artifacts under `Projects/MixerAndroid`.

## Known Phase 0 Limitations (Intentional)

1. This matrix captures behavior and contracts; it does not yet include pixel-level measurements.
2. This phase does not create Android runtime code.
3. Device-specific audio route behavior tuning will be validated in later phases on Android hardware.
