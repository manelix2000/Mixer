# dev.manelix.Mixer --- Turntable Gesture & Scratching Algorithm Specification

## Overview

This document defines how **turntable gestures and scratching** are
implemented in `dev.manelix.Mixer`.

The goal is to emulate the interaction of a **vinyl turntable (e.g.,
Technics SL‑1200)** while maintaining stable playback using
`AVAudioEngine`.

Key requirements:

-   circular platter gestures
-   low latency response
-   stable audio playback
-   no audio glitches during scrubbing
-   smooth return to motor-driven playback

The system should feel natural for DJs familiar with vinyl decks.

------------------------------------------------------------------------

# 1. Interaction Model

The turntable operates in three states:

  State          Description
  -------------- --------------------------------
  Motor Mode     platter rotates automatically
  Scratch Mode   user directly controls platter
  Release Mode   platter returns to motor speed

State transitions:

    touchDown → Scratch Mode
    touchMove → control playback position
    touchUp → Release Mode → Motor Mode

------------------------------------------------------------------------

# 2. Gesture Detection

Use a **circular drag gesture** around the platter.

Inputs:

    touchPosition
    previousTouchPosition
    platterCenter

Compute angle:

    angle = atan2(y - centerY, x - centerX)

Angular movement:

    deltaAngle = angle - previousAngle

This value drives the scratch motion.

------------------------------------------------------------------------

# 3. Angular Velocity Calculation

Angular velocity determines playback motion.

    angularVelocity = deltaAngle / deltaTime

Playback displacement:

    audioOffset = angularVelocity * scratchSensitivity

Example sensitivity:

    scratchSensitivity = 0.02 seconds per radian

------------------------------------------------------------------------

# 4. Playback Position Control

During scratching the playback position follows platter movement.

    newTime = currentTime + audioOffset

Constraints:

    newTime >= 0
    newTime <= trackDuration

Update player position carefully to avoid discontinuities.

------------------------------------------------------------------------

# 5. Motor Simulation

When the platter is not touched, the motor drives playback.

Variables:

    motorSpeed
    targetSpeed

Where:

    targetSpeed = playbackRate

Motor acceleration model:

    velocity += (targetSpeed - velocity) * accelerationFactor

Typical value:

    accelerationFactor ≈ 0.1

------------------------------------------------------------------------

# 6. Release Behavior

When the user releases the platter:

    scratchVelocity → gradually blend into motorVelocity

Interpolation example:

    velocity = mix(scratchVelocity, motorVelocity, t)

This prevents abrupt jumps in playback.

------------------------------------------------------------------------

# 7. Scrubbing vs Scratching

Two behaviors should be supported:

  Mode      Behavior
  --------- -------------------------
  Scrub     slow precise movement
  Scratch   fast vinyl-style motion

Heuristic:

    if |angularVelocity| > threshold
        → scratch mode
    else
        → scrub mode

Threshold example:

    threshold = 3 radians/second

------------------------------------------------------------------------

# 8. Latency Requirements

Scratch interaction must feel immediate.

Targets:

  Metric                     Target
  -------------------------- ----------
  Gesture → audio response   \< 10 ms
  Audio latency              \< 20 ms
  UI frame rate              60 FPS

------------------------------------------------------------------------

# 9. Playback Stability Strategy

Avoid frequent full player seeks.

Instead:

    small offset adjustments
    buffered playback

Possible strategies:

-   short buffer scheduling
-   ring buffer playback
-   controlled rescheduling of `AVAudioPlayerNode`

------------------------------------------------------------------------

# 10. Visual Feedback

The platter UI must reflect motion.

Rotation mapping:

    rotationAngle += deltaAngle

Visual rules:

-   platter rotation matches scratch direction
-   subtle inertia animation when released
-   waveform scroll synchronized with playback

------------------------------------------------------------------------

# 11. Edge Cases

Handle:

    track start reached
    track end reached
    very fast rotations
    touch jitter
    multi-touch interruptions

Suggested mitigations:

-   clamp playback range
-   smooth angular velocity
-   ignore tiny jitter movements

------------------------------------------------------------------------

# 12. Performance Considerations

To maintain performance:

-   avoid allocations during gestures
-   keep calculations lightweight
-   update only necessary UI components
-   limit gesture update frequency to display refresh rate

------------------------------------------------------------------------

# 13. Integration with Audio Engine

Gesture pipeline:

    TurntableView
     ↓
    TurntableGestureHandler
     ↓
    TurntablePhysics
     ↓
    DeckViewModel
     ↓
    AudioEngineManager
     ↓
    AVAudioPlayerNode

This separation keeps UI and audio logic decoupled.

------------------------------------------------------------------------

# 14. Future Improvements

Possible enhancements:

-   inertia simulation like real vinyl
-   adjustable platter torque
-   brake effect
-   slip mode
-   needle drop gesture

These features can be added once base scratching is stable.

------------------------------------------------------------------------

# End of Turntable Gesture & Scratching Algorithm Specification
