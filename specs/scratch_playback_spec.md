# dev.manelix.Mixer --- Continuous Scratch Playback Specification

## Overview

This document defines the **continuous scratch playback architecture**
for `dev.manelix.Mixer`.

Goal:

Allow the user to move the virtual vinyl platter **forward and
backward** while the track continues playing, producing natural
**vinyl‑style scratching**, including:

-   forward scratch
-   reverse scratch
-   motor playback when released
-   smooth return from scratch to normal playback
-   low latency audio response

The design must avoid repeated seeking on the audio engine and instead
use **sample‑level playback control**.

Primary audio framework:

-   AVAudioEngine

------------------------------------------------------------------------

# 1. Core Concept

The scratch system uses **two independent playback positions**.

  -----------------------------------------------------------------------
  Position                            Description
  ----------------------------------- -----------------------------------
  Motor Position                      Where the track would be if it
                                      continued playing normally

  Audible Position                    Where audio is currently being read
                                      during scratching
  -----------------------------------------------------------------------

During scratching:

    audiblePosition ≠ motorPosition

When scratching ends:

    audiblePosition → smoothly converge to motorPosition

This matches the behavior of a real vinyl turntable.

------------------------------------------------------------------------

# 2. Audio Data Model

Tracks must be decoded to PCM before scratching.

Recommended structure:

    struct AudioTrack {
        samples: [Float]
        sampleRate: Double
        totalSamples: Int
    }

Playback state:

    struct PlaybackState {
        motorSamplePosition: Double
        audibleSamplePosition: Double
        playbackIncrement: Double
    }

Where:

    playbackIncrement = samples advanced per frame

------------------------------------------------------------------------

# 3. PCM Preprocessing

Pipeline:

    audio file
     ↓
    decode to PCM
     ↓
    convert to Float32
     ↓
    store in sample buffer

Stereo handling:

    mono = 0.5 * (left + right)

Mono is sufficient for scratch playback control.

------------------------------------------------------------------------

# 4. Motor Playback Model

The **motor** represents normal playback progression.

Each render frame:

    motorPosition += motorRate

Where:

    motorRate = sampleRate / outputSampleRate

Motor playback always moves forward unless paused.

------------------------------------------------------------------------

# 5. Scratch Mode

When the platter is touched:

    isScratching = true

Playback follows platter movement instead of the motor.

Update rule:

    audiblePosition += scratchDeltaSamples

Where:

    scratchDeltaSamples = angularVelocity * sensitivity * sampleRate

If:

    scratchDeltaSamples < 0

The audio plays **in reverse**.

------------------------------------------------------------------------

# 6. Gesture Mapping

Input from platter gesture:

    touchPosition
    previousTouchPosition
    platterCenter

Angle computation:

    angle = atan2(y - centerY, x - centerX)
    deltaAngle = angle - previousAngle

Angular velocity:

    angularVelocity = deltaAngle / deltaTime

Scratch displacement:

    scratchDeltaSamples = angularVelocity * scratchSensitivity * sampleRate

------------------------------------------------------------------------

# 7. Audio Rendering

During audio callback:

    for each output frame:

        sample = samples[audiblePosition]

        output = sample

        audiblePosition += playbackIncrement

Where:

    playbackIncrement = scratchVelocity   (during scratch)
    playbackIncrement = motorRate         (during normal playback)

Reverse playback occurs when:

    playbackIncrement < 0

------------------------------------------------------------------------

# 8. Release Behavior

When the user releases the platter:

    isScratching = false

Blend audible position back toward motor position.

Example interpolation:

    error = motorPosition - audiblePosition
    audiblePosition += error * catchupFactor

Typical factor:

    catchupFactor ≈ 0.1

This produces a natural "record catches up to platter" effect.

------------------------------------------------------------------------

# 9. Boundary Handling

Clamp sample position:

    0 ≤ audiblePosition ≤ totalSamples

Edge cases:

  Condition                Behavior
  ------------------------ ---------------------
  Track start              stop reverse motion
  Track end                stop forward motion
  Large scratch velocity   clamp delta

------------------------------------------------------------------------

# 10. Latency Targets

Scratch responsiveness must feel immediate.

  Metric                     Target
  -------------------------- ----------
  Gesture → audio response   \< 10 ms
  Audio output latency       \< 20 ms
  UI refresh                 60 FPS

------------------------------------------------------------------------

# 11. Visual Synchronization

The waveform should reflect **audiblePosition**, not motor position.

Playhead model:

    waveform scrolls
    playhead fixed

Rendering alignment:

    waveformTime = audiblePosition / sampleRate

------------------------------------------------------------------------

# 12. Performance Requirements

The scratch engine must be realtime‑safe.

Rules:

Do not perform inside audio callback:

-   memory allocations
-   file reads
-   locks
-   heavy DSP

Allowed operations:

-   pointer reads
-   simple math
-   buffer indexing

------------------------------------------------------------------------

# 13. Architecture Integration

Suggested system structure:

    TurntableGestureHandler
     ↓
    TurntablePhysics
     ↓
    ScratchController
     ↓
    AudioPlaybackCursor
     ↓
    AVAudioEngine output

Component roles:

  Component                 Responsibility
  ------------------------- ----------------------------------
  TurntableGestureHandler   converts touch to angular motion
  TurntablePhysics          calculates angular velocity
  ScratchController         determines playback increment
  AudioPlaybackCursor       reads PCM samples
  AVAudioEngine             outputs audio

------------------------------------------------------------------------

# 14. Optional Enhancements

Future improvements may include:

-   slip mode
-   platter inertia simulation
-   brake effect
-   torque adjustment
-   needle drop gesture
-   vinyl noise layer

These should be added only after stable scratch playback is achieved.

------------------------------------------------------------------------

# 15. Implementation Priority

Recommended order:

1.  PCM decoding
2.  audibleSamplePosition playback
3.  reverse sample reading
4.  platter gesture mapping
5.  motor position tracking
6.  release interpolation
7.  waveform sync
8.  advanced physics

------------------------------------------------------------------------

# Definition of Done

The system is considered complete when:

-   scratching can move audio forward and backward
-   reverse playback is stable
-   no clicks or glitches occur during motion
-   waveform and audio remain synchronized
-   playback returns smoothly to motor speed

------------------------------------------------------------------------

# End of Continuous Scratch Playback Specification
