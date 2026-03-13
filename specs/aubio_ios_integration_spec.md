# dev.manelix.Mixer --- Aubio iOS Integration Specification

## Overview

This document describes a practical integration strategy for using
**aubio** in the `dev.manelix.Mixer` iOS project.

Primary goals:

-   build aubio for iOS device and simulator
-   package aubio as an XCFramework
-   integrate the binary into a Tuist project
-   expose a small Swift-friendly wrapper API
-   keep the integration stable and easy to rebuild

This specification assumes:

-   Swift 6
-   Tuist project generation
-   iOS app target
-   BPM detection and onset detection use cases only

------------------------------------------------------------------------

# 1. Why XCFramework

Recommended integration approach:

-   build aubio as a **binary library/framework**
-   package it as an **XCFramework**
-   reference that artifact from Tuist

Why:

-   device and simulator slices can be distributed together
-   avoids per-developer local C build complexity inside the app target
-   keeps the Swift side isolated from aubio build-system details

Recommended artifact strategy:

``` text
External/
└─ aubio/
   ├─ Aubio.xcframework
   ├─ include/
   │  └─ aubio/
   └─ LICENSE
```

------------------------------------------------------------------------

# 2. Source of Truth

Use the official aubio upstream repository as the source of truth for C
sources and public headers.

Pin a specific aubio version and record it in project documentation.

Recommended metadata:

``` text
aubio version
git commit or tag
build date
build script revision
supported architectures
```

------------------------------------------------------------------------

# 3. Supported Apple Targets

Initial support should include:

-   iOS device: `arm64`
-   iOS Simulator: `arm64`
-   optionally `x86_64` simulator if local tooling still requires it

Recommended target matrix:

  Platform          Architectures
  ----------------- ------------------------
  iPhoneOS          arm64
  iPhoneSimulator   arm64, optional x86_64

Deployment baseline should match the app deployment target.

------------------------------------------------------------------------

# 4. Build Output Type

Prefer one of these two options:

## Option A --- Static library inside XCFramework

Recommended default.

Pros:

-   simple linking model
-   smaller integration surface
-   no runtime embedding concerns

Cons:

-   requires header management

## Option B --- Framework inside XCFramework

Pros:

-   clearer bundle structure

Cons:

-   slightly more packaging work

Recommendation for this project:

``` text
Use a static library packaged as an XCFramework.
```

------------------------------------------------------------------------

# 5. Public aubio Surface to Expose

Do **not** expose the full aubio API to Swift.

Expose only the subset needed by the app:

-   tempo detection
-   onset detection
-   optional pitch detection later

Initial aubio C functions likely involved:

``` text
new_aubio_tempo
aubio_tempo_do
aubio_tempo_get_bpm
new_aubio_onset
aubio_onset_do
del_aubio_tempo
del_aubio_onset
new_fvec
del_fvec
```

Keep the Swift-facing layer narrow.

------------------------------------------------------------------------

# 6. Recommended Module Layout

``` text
dev.manelix.Mixer
├─ Tuist
├─ Modules
│  ├─ App
│  ├─ DeckFeature
│  ├─ AudioEngine
│  ├─ DSP
│  │  ├─ Sources
│  │  │  └─ Aubio
│  │  │     ├─ AubioBridge.swift
│  │  │     ├─ BPMDetector.swift
│  │  │     └─ OnsetDetector.swift
│  │  └─ Headers
│  │     └─ AubioShim.h
│  └─ UIComponents
└─ External
   └─ aubio
      └─ Aubio.xcframework
```

Only the DSP module should know aubio exists.

------------------------------------------------------------------------

# 7. C Shim Layer

Add a very small C or Objective-C shim so Swift never talks directly to
low-level aubio details.

Example bridging surface:

``` c
// AubioShim.h

typedef struct AubioTempoHandle AubioTempoHandle;

AubioTempoHandle * AubioTempoCreate(unsigned int bufferSize,
                                    unsigned int hopSize,
                                    unsigned int sampleRate);

void AubioTempoDestroy(AubioTempoHandle * handle);

float AubioTempoProcess(AubioTempoHandle * handle,
                        const float * samples,
                        unsigned int frameCount,
                        int * didDetectBeat);

float AubioTempoGetBPM(AubioTempoHandle * handle);
```

Benefits:

-   isolates aubio symbols and memory management
-   provides Swift a stable interface
-   reduces exposure to aubio vector types

------------------------------------------------------------------------

# 8. Swift Wrapper API

Wrap the shim in a Swift-friendly API.

Example:

``` swift
public final class BPMDetector {
    public init(bufferSize: Int, hopSize: Int, sampleRate: Double)
    public func process(_ samples: UnsafeBufferPointer<Float>) throws -> BPMResult
}

public struct BPMResult {
    public let bpm: Double?
    public let didDetectBeat: Bool
}
```

Rules:

-   no raw aubio structs outside the wrapper
-   wrapper owns lifecycle of native handles
-   wrapper validates frame counts before forwarding to C

------------------------------------------------------------------------

# 9. Memory Ownership Rules

Strict ownership rules are required.

Rules:

-   Swift wrapper owns the shim handle
-   shim owns aubio internal state
-   no shared mutable detector instance across unrelated threads
-   one detector instance per processing stream

Destroy in reverse order:

``` text
Swift wrapper deinit
→ shim destroy
→ aubio cleanup
```

------------------------------------------------------------------------

# 10. Threading Model

The aubio wrapper should be called from a deterministic processing path.

Rules:

-   detector instance should not be shared across multiple concurrent
    producers
-   if used near realtime processing, avoid allocations in the hot path
-   avoid locks in audio-critical paths

Recommended approach:

-   pre-create detector instances
-   reuse sample buffers
-   publish compact results back to UI state

------------------------------------------------------------------------

# 11. Input Format Contract

Define one normalized input contract.

Recommended contract:

  Field           Value
  --------------- -----------------------------
  Sample type     Float32
  Channel count   mono
  Sample rate     44.1 kHz or 48 kHz
  Buffer style    contiguous hop-sized frames

Rules:

-   convert stereo input to mono before passing to aubio
-   resample only if needed by the broader engine
-   do not let the wrapper guess format implicitly

------------------------------------------------------------------------

# 12. Audio Preprocessing

Before sending samples to aubio:

1.  decode to PCM\
2.  convert to `Float32`\
3.  downmix to mono\
4.  segment into hop-sized chunks\
5.  feed chunks in chronological order

Recommended downmix:

``` text
mono = 0.5 * (left + right)
```

------------------------------------------------------------------------

# 13. Suggested aubio Processing Parameters

Reasonable starting points for BPM detection:

  Parameter    Suggested value
  ------------ -----------------
  bufferSize   1024 or 2048
  hopSize      256 or 512
  sampleRate   44100 or 48000

Notes:

-   lower hop sizes improve responsiveness
-   larger windows can improve tempo stability
-   these values should remain configurable in the DSP module

------------------------------------------------------------------------

# 14. Offline vs Realtime Detection

Support two modes.

## Offline file analysis

Use when loading a track.

Pros:

-   easier to stabilize
-   not constrained by the audio callback deadline
-   can analyze the full file and compute a more stable BPM estimate

## Realtime mic analysis

Use for optional external BPM estimation.

Pros:

-   supports synchronization against environmental music

Cons:

-   noisier
-   more sensitive to room acoustics and latency

Recommendation:

``` text
Implement offline track BPM first, then add microphone BPM detection.
```

------------------------------------------------------------------------

# 15. XCFramework Packaging Workflow

High-level process:

1.  build aubio static library for iPhoneOS\
2.  build aubio static library for iPhoneSimulator\
3.  collect matching public headers\
4.  create XCFramework with `xcodebuild -create-xcframework`\
5.  verify headers resolve correctly from the consuming target\
6.  store artifact in `External/aubio`

Expected packaging shape:

``` text
Aubio.xcframework
├─ ios-arm64
└─ ios-arm64_x86_64-simulator
```

If `x86_64` is not produced, use the actual simulator slice present.

------------------------------------------------------------------------

# 16. Tuist Integration Strategy

Reference the XCFramework only from the DSP module.

Recommended dependency direction:

``` text
App
↓
DeckFeature
↓
AudioEngine
↓
DSP
↓
Aubio.xcframework
```

Why:

-   keeps aubio isolated to DSP
-   prevents unnecessary binary dependency spread
-   makes future replacement easier

------------------------------------------------------------------------

# 17. Tuist Target Design

Recommended DSP target responsibilities:

-   own Swift wrappers
-   own bridging headers
-   own aubio-specific tests
-   expose stable APIs to the rest of the app

Suggested public DSP interfaces:

``` swift
public protocol TempoDetecting {
    func process(_ samples: UnsafeBufferPointer<Float>) throws -> BPMResult
}

public protocol WaveformAnalyzing {
    func analyze(url: URL) throws -> [Float]
}
```

Only the DSP module should import aubio-related implementation details.

------------------------------------------------------------------------

# 18. Error Handling

Possible failure categories:

-   XCFramework missing or wrong slice
-   header import mismatch
-   unsupported architecture
-   invalid buffer sizes
-   detector initialization failure
-   unstable or missing BPM estimate

Recommended strategy:

-   fail early during detector creation
-   expose typed Swift errors
-   provide manual BPM fallback in the app UI

Example:

``` swift
public enum AubioError: Error {
    case initializationFailed
    case invalidFrameCount
    case unsupportedFormat
}
```

------------------------------------------------------------------------

# 19. Testing Strategy

## Build validation

-   app builds for device
-   app builds for simulator
-   no missing symbols
-   no duplicate-symbol conflicts

## DSP validation

-   known BPM fixture files
-   silence input
-   noisy mic-like input
-   abrupt transient input

## Integration validation

-   `tuist generate` succeeds
-   app builds after integration
-   archive or device build succeeds

Recommended fixtures:

``` text
90 BPM click track
120 BPM click track
128 BPM house loop
silence
pink noise
```

------------------------------------------------------------------------

# 20. Common Integration Pitfalls

Watch for:

-   wrong simulator slice in XCFramework
-   public headers not exposed correctly
-   mismatch between static and dynamic linking expectations
-   symbol collisions if another dependency bundles the same C library
-   doing allocations inside the audio callback
-   feeding stereo or irregular frame counts directly into the detector

------------------------------------------------------------------------

# 21. Licensing and Attribution

Store aubio license text in the repository near the binary artifact.

Recommended files:

``` text
External/aubio/LICENSE
External/aubio/NOTICE.md
```

Document:

-   upstream project URL
-   pinned version
-   any local packaging modifications

------------------------------------------------------------------------

# 22. Recommended Build Artifacts to Commit

Commit:

-   XCFramework artifact
-   public headers required by the wrapper
-   version metadata
-   build/rebuild script

Recommended files:

``` text
External/aubio/Aubio.xcframework
Scripts/build_aubio_ios.sh
Docs/aubio-version.md
```

This avoids tribal knowledge and makes rebuilds reproducible.

------------------------------------------------------------------------

# 23. Minimal Rebuild Script Requirements

The rebuild script should:

-   clean previous artifacts
-   build device slice
-   build simulator slice
-   assemble XCFramework
-   copy headers
-   validate final artifact structure

It should also print:

-   aubio version/tag
-   SDK used
-   architectures built
-   output path

------------------------------------------------------------------------

# 24. Recommended Rollout Plan

1.  Package aubio as XCFramework\
2.  Add DSP target in Tuist\
3.  Add shim header and implementation\
4.  Add Swift `BPMDetector` wrapper\
5.  Validate against offline click-track fixtures\
6.  Integrate with track-loading pipeline\
7.  Add optional mic BPM mode\
8.  Tune parameters with real music

------------------------------------------------------------------------

# 25. Definition of Done

The aubio integration is complete when:

-   app builds on simulator and device
-   DSP target imports the XCFramework cleanly
-   Swift wrapper API is stable and documented
-   known test audio returns acceptable BPM estimates
-   offline BPM detection is wired into loaded-track analysis
-   microphone BPM detection can be enabled behind a feature flag

------------------------------------------------------------------------

# End of Aubio iOS Integration Specification
