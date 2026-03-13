# dev.manelix.Mixer --- Audio Engine Deep Specification

## Overview

This document describes the internal audio engine design for
**dev.manelix.Mixer**, an iOS DJ overlay application that emulates the
behavior of a professional vinyl turntable using **vinyl mode**.

Goals:

-   low-latency audio playback
-   turntable-style interaction
-   BPM synchronization
-   scratching support
-   stereo channel routing
-   waveform visualization
-   optional BPM detection via microphone

The system is designed to work alongside other audio apps playing
simultaneously.

------------------------------------------------------------------------

# 1. Audio Engine Architecture

Audio graph:

``` text
audio file
 ↓
AVAudioPlayerNode
 ↓
AVAudioMixerNode
 ↓
output node
```

Core components:

``` text
AudioEngineManager
DeckPlayer
BPMDetector
WaveformAnalyzer
```

Responsibilities:

  -----------------------------------------------------------------------
  Component                           Role
  ----------------------------------- -----------------------------------
  AudioEngineManager                  Manages `AVAudioEngine` lifecycle
                                      and graph wiring

  DeckPlayer                          Playback control, file scheduling,
                                      rate, seek, and scratching
                                      coordination

  BPMDetector                         BPM analysis using aubio

  WaveformAnalyzer                    Waveform extraction for UI
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 2. Audio Buffer Strategy

Continuous scrubbing should avoid naïvely calling `seek()` repeatedly on
the player.

Recommended solution: **PCM ring buffer**.

Pipeline:

``` text
audio file
 ↓
decode PCM
 ↓
ring buffer
 ↓
scheduled playback
```

Benefits:

-   smoother scrubbing
-   fewer audio glitches
-   more stable playback timing

Design notes:

-   decode file content into PCM in chunks
-   keep read/write indices separate
-   keep the hot path allocation-free
-   prefer a lock-free or minimal-lock design

------------------------------------------------------------------------

# 3. Turntable Physics Model

Simulate platter movement similar to a physical vinyl turntable.

State variables:

``` text
angularVelocity
platterPosition
dragForce
inertia
damping
```

Basic model:

``` text
velocity += appliedForce
velocity *= damping
position += velocity
```

Recommended starting parameters:

``` text
inertia = 0.98
damping = 0.995
```

These values are only tuning seeds and should be adjusted after real
interaction testing.

------------------------------------------------------------------------

# 4. Scratch Interaction

Interaction states:

``` text
motor mode
scratch mode
brake mode
```

Behavior:

``` text
touchDown → stop motor-driven control
drag → direct position control
touchUp → restore motor velocity
```

This supports a realistic DJ-style scratch model:

-   while touched, platter movement follows finger input
-   when released, playback returns to motor-driven rotation
-   rate recovery should be smooth, not abrupt

------------------------------------------------------------------------

# 5. Brake Simulation

To simulate platter stopping:

``` text
velocity *= brakeFactor
```

Example:

``` text
brakeFactor = 0.90 per frame
```

Use cases:

-   optional brake button
-   realistic stop behavior when pausing
-   future "power off platter" interactions

------------------------------------------------------------------------

# 6. Vinyl Pitch Control

Playback speed directly modifies **both BPM and pitch**.

Formula:

``` text
playbackRate = targetBPM / trackBPM
```

Example:

``` text
Track BPM = 120
External BPM = 128

playbackRate = 1.066
```

Important:

-   this is **not** time-stretching
-   this matches Technics-style vinyl behavior
-   tempo and pitch change together

------------------------------------------------------------------------

# 7. BPM Detection

Performed using the aubio library.

Pipeline:

``` text
audio samples
 ↓
onset detection
 ↓
tempo estimation
```

Outputs:

``` text
estimated BPM
beat timestamps
```

Recommended usage:

-   offline BPM detection when a track is imported
-   optional realtime BPM estimation from microphone input

------------------------------------------------------------------------

# 8. Beat Phase Alignment (Optional)

Align track beats with external music.

Formula:

``` text
beatOffset = externalBeat - trackBeat
```

Adjustment:

``` text
seek(currentTime + beatOffset)
```

Notes:

-   BPM match alone is insufficient for sync
-   phase alignment is needed for beat-accurate layering
-   this can remain optional in the first release

------------------------------------------------------------------------

# 9. Waveform Rendering

Waveform is displayed above the turntable.

Processing pipeline:

``` text
audio decode
 ↓
downsample
 ↓
amplitude envelope
 ↓
waveform samples
```

Rendering approach:

-   use `SwiftUI.Canvas` for the first implementation
-   keep drawing lightweight
-   keep playhead fixed while waveform scrolls underneath

------------------------------------------------------------------------

# 10. Latency Targets

  Metric                Target
  --------------------- ----------
  Interaction latency   \< 10 ms
  Audio latency         \< 20 ms
  UI frame rate         60 FPS

Recommended audio buffer size:

``` text
256–512 frames
```

Tradeoff:

-   256 frames: lower latency, higher CPU risk
-   512 frames: safer default for initial versions

------------------------------------------------------------------------

# 11. Audio Session Configuration

The session must allow coexistence with other apps.

Recommended configuration intent:

``` text
category = playAndRecord
options = mixWithOthers
```

Purpose:

-   allow microphone capture
-   allow background playback from other apps
-   preserve the app's ability to output its own track

Implementation should use official Apple APIs from AVFoundation.

------------------------------------------------------------------------

# 12. Bluetooth Latency Warning

Bluetooth audio introduces substantial delay.

Typical latency:

``` text
150–250 ms
```

Recommended UX:

Display a warning such as:

``` text
"For best DJ experience, use wired headphones."
```

This matters particularly for:

-   scratching
-   cueing
-   beat alignment by ear

------------------------------------------------------------------------

# 13. Error Handling

Potential issues:

``` text
audio decode failure
BPM detection failure
audio engine restart
file access errors
unsupported file format edge cases
```

Fallback strategies:

``` text
manual BPM entry
reload engine
fallback waveform generation
disable advanced sync features
```

------------------------------------------------------------------------

# 14. Performance Monitoring

A developer debug overlay is recommended.

Metrics:

``` text
FPS
CPU load
audio latency
buffer underruns
current playbackRate
selected buffer size
```

This helps tune:

-   scratch responsiveness
-   waveform cost
-   DSP scheduling behavior

------------------------------------------------------------------------

# 15. Testing Strategy

Recommended stress tests:

``` text
rapid scrubbing
continuous pitch adjustment
microphone BPM detection
large audio files
Bluetooth output
interruption/resume scenarios
```

Also test:

-   app background/foreground transitions
-   route changes (wired ↔ Bluetooth)
-   invalid or inaccessible file URLs

------------------------------------------------------------------------

# 16. System Architecture Overview

``` text
UI
 ↓
DeckViewModel
 ↓
AudioEngineManager
 ↓
DeckPlayer
 ↓
AVAudioEngine
```

DSP components:

``` text
BPMDetector
WaveformAnalyzer
```

Separation of concerns:

-   UI handles gestures and rendering
-   ViewModel coordinates state
-   AudioEngine handles playback graph
-   DSP handles offline or realtime analysis

------------------------------------------------------------------------

# 17. Implementation Roadmap

1.  Create Tuist project\
2.  Implement `AudioEngineManager`\
3.  Implement audio playback\
4.  Implement waveform generation\
5.  Implement pitch slider\
6.  Implement turntable gestures\
7.  Implement BPM detection\
8.  Implement microphone BPM detection

------------------------------------------------------------------------

# 18. Design Principles

-   Keep the audio thread realtime-safe
-   Avoid allocations in hot paths
-   Keep DSP isolated from UI
-   Prefer simple, stable first implementations
-   Add realism incrementally after playback is stable

------------------------------------------------------------------------

# End of Audio Engine Deep Specification
