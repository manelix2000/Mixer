# dev.manelix.Mixer --- Audio Latency & Real‑Time Constraints Specification

## Overview

This document defines the **real‑time audio constraints and latency
targets** for the `dev.manelix.Mixer` iOS application.

Apps that emulate DJ turntables must obey strict real‑time audio rules.
Violating them can cause:

-   audio dropouts
-   buffer underruns
-   timing drift
-   unstable scratching

This specification describes the **safe architecture for real‑time audio
on iOS**.

Core frameworks:

-   AVFoundation
-   AVAudioEngine

------------------------------------------------------------------------

# 1. Real‑Time Audio Thread Rules

Audio callback threads are **real‑time threads**.

The following operations **must never occur** inside the real‑time audio
path:

Forbidden operations:

    memory allocation
    file I/O
    locks or mutexes
    network calls
    logging
    Objective‑C messaging that allocates
    Swift ARC heavy allocations

Safe operations:

    simple math
    buffer reads
    pre‑allocated memory access
    atomic state reads

------------------------------------------------------------------------

# 2. Latency Targets

Professional DJ apps target extremely low latency.

  Metric                     Target
  -------------------------- ---------------
  Audio output latency       \< 20 ms
  Gesture → audio response   \< 10 ms
  UI refresh                 60 FPS
  Scratching response        \< 5 ms ideal

------------------------------------------------------------------------

# 3. Audio Buffer Size

Recommended buffer sizes:

    256 frames
    512 frames

Tradeoffs:

  Size   Result
  ------ ---------------------------------
  256    lower latency but higher CPU
  512    safer but slightly more latency

Default recommendation:

    512 frames

------------------------------------------------------------------------

# 4. Scheduling Strategy

Avoid repeated scheduling calls during playback.

Recommended pipeline:

    audio file
     ↓
    decode PCM
     ↓
    ring buffer
     ↓
    scheduled playback blocks

This ensures stable playback.

------------------------------------------------------------------------

# 5. Ring Buffer Design

A **lock‑free ring buffer** should store decoded audio.

Structure:

    RingBuffer
    ├─ writeIndex
    ├─ readIndex
    └─ sampleStorage

Benefits:

-   constant‑time reads
-   no locks required
-   safe for real‑time thread

------------------------------------------------------------------------

# 6. Memory Allocation Policy

All memory used by the audio thread must be **pre‑allocated**.

Allocate during:

    engine initialization
    track loading
    DSP module setup

Never allocate during:

    audio callback
    scratching
    playback updates

------------------------------------------------------------------------

# 7. Thread Model

Recommended threads:

    UI thread
    Audio engine thread
    DSP worker thread

Responsibilities:

  Thread   Responsibility
  -------- ----------------------------
  UI       gestures, display
  Audio    playback
  DSP      analysis (aubio, waveform)

------------------------------------------------------------------------

# 8. DSP Work Scheduling

Heavy DSP tasks should run **off the audio thread**.

Example tasks:

    BPM detection
    waveform analysis
    beat grid detection

Execution strategy:

    background queue
     ↓
    process samples
     ↓
    publish results

------------------------------------------------------------------------

# 9. Gesture → Audio Pipeline

Turntable gestures must propagate quickly.

Pipeline:

    touch event
     ↓
    gesture recognition
     ↓
    platter physics update
     ↓
    playback position update

Avoid intermediate queues when possible.

------------------------------------------------------------------------

# 10. Scratch Latency

Scratch movement must translate to audio immediately.

Strategy:

-   modify playback position directly
-   avoid restarting engine
-   adjust read pointer inside ring buffer

------------------------------------------------------------------------

# 11. Bluetooth Latency

Bluetooth audio devices introduce large delays.

Typical values:

    150–250 ms

Recommended UX:

Display warning when Bluetooth is detected:

    "Use wired headphones for the best DJ experience."

------------------------------------------------------------------------

# 12. AVAudioSession Configuration

Session should allow mixing with other apps.

Configuration:

    category = playAndRecord
    options = mixWithOthers
    mode = default

This allows:

-   microphone capture
-   external music playback

------------------------------------------------------------------------

# 13. Sample Format Standard

All DSP modules should use a single sample format.

Recommended format:

  Field         Value
  ------------- --------------------
  Type          Float32
  Channels      mono for analysis
  Sample rate   44.1 kHz or 48 kHz

Convert input early.

------------------------------------------------------------------------

# 14. Timing Source

Use **audio sample time** as the primary timing reference.

Avoid:

    wall clock timers
    display refresh timers

Audio sample time guarantees accurate beat alignment.

------------------------------------------------------------------------

# 15. Debug Metrics

Provide optional developer overlay.

Metrics:

    audio latency
    buffer underruns
    CPU usage
    frame time
    scratch response time

------------------------------------------------------------------------

# 16. Failure Modes

Possible issues:

    buffer underrun
    audio engine restart
    invalid sample rate
    DSP overload

Recovery strategies:

    reset audio engine
    increase buffer size
    disable heavy DSP

------------------------------------------------------------------------

# 17. Testing Scenarios

Test with:

    continuous scratching
    rapid pitch changes
    large audio files
    low battery CPU throttling
    Bluetooth headphones

------------------------------------------------------------------------

# 18. Performance Profiling

Recommended tools:

    Xcode Instruments
    Time Profiler
    System Trace

Monitor:

    CPU spikes
    audio callback duration
    memory allocation

------------------------------------------------------------------------

# 19. Safe Coding Guidelines

Rules for DSP and audio modules:

    no dynamic allocations in hot paths
    avoid ARC churn
    use stack or pre‑allocated buffers
    prefer structs over classes where possible

------------------------------------------------------------------------

# 20. Definition of Real‑Time Safety

A module is considered **real‑time safe** when:

-   it performs no allocations
-   it performs no blocking operations
-   it completes execution within the audio callback deadline

------------------------------------------------------------------------

# End of Audio Latency & Real‑Time Constraints Specification
