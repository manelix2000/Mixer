# dev.manelix.Mixer --- DJ Turntable Physics & Beatgrid Specification

## Overview

This document defines the algorithms and system design needed to emulate
a **realistic DJ turntable** inside the dev.manelix.Mixer iOS
application.

Focus areas:

-   realistic vinyl platter physics
-   scratching behavior
-   beat grid detection
-   beat phase synchronization
-   cue points
-   accurate waveform alignment

The goal is to replicate the interaction model of professional DJ
systems similar to Technics SL‑1200 workflows.

------------------------------------------------------------------------

# 1. Turntable Physics Model

The virtual platter must simulate inertia similar to a physical
turntable.

State variables:

    platterPosition
    angularVelocity
    motorTargetVelocity
    dragForce
    inertia

Update model:

    angularVelocity += appliedForce
    angularVelocity *= damping
    platterPosition += angularVelocity

Recommended constants:

    inertia = 0.98
    damping = 0.995
    motorAcceleration = 0.002

This allows smooth acceleration and deceleration.

------------------------------------------------------------------------

# 2. Motor Simulation

When the platter is not touched, the motor drives it toward a constant
speed.

    angularVelocity += (motorTargetVelocity - angularVelocity) * motorAcceleration

Typical target speed:

    33.33 RPM equivalent playback speed

Pitch control modifies this target velocity.

------------------------------------------------------------------------

# 3. Touch Interaction Model

Three interaction states:

    motor mode
    scratch mode
    brake mode

Transitions:

    touchDown → enter scratch mode
    drag → update platter position directly
    touchUp → return to motor mode

------------------------------------------------------------------------

# 4. Scratch Mapping

Touch movement must map to platter rotation.

Angle calculation:

    angle = atan2(y, x)
    deltaAngle = angle - previousAngle

Convert to audio position shift:

    deltaTime = deltaAngle * sensitivity

Sensitivity constant example:

    0.002 seconds per degree

------------------------------------------------------------------------

# 5. Slip Simulation

Real DJ turntables allow slip between record and platter.

Simplified model:

    platterPosition = motorPosition + recordOffset

When scratching:

    recordOffset changes
    motorPosition continues

When released:

    recordOffset → gradually returns to zero

------------------------------------------------------------------------

# 6. Brake Behavior

Stopping the platter should feel natural.

Brake algorithm:

    angularVelocity *= brakeFactor

Example:

    brakeFactor = 0.9 per frame

------------------------------------------------------------------------

# 7. Beat Grid Detection

Beat grid represents estimated beat positions across the track.

Pipeline:

    audio file
     ↓
    onset detection
     ↓
    tempo estimation
     ↓
    beat tracking

Output:

    trackBPM
    beatPositions[]

Beat grid stored in the Track model.

------------------------------------------------------------------------

# 8. Beat Phase

Tracks have a phase relative to beat grid.

Definition:

    phase = currentTime % beatInterval

Where:

    beatInterval = 60 / BPM

Phase allows alignment between tracks.

------------------------------------------------------------------------

# 9. Beat Sync Algorithm

When syncing with external music:

    targetRate = externalBPM / trackBPM

Apply rate adjustment first.

Then correct phase:

    phaseError = externalPhase - trackPhase
    seek(currentTime + phaseError)

------------------------------------------------------------------------

# 10. Cue Points

Cue points allow instant jump to a stored position.

Data structure:

    struct CuePoint {
        time: TimeInterval
    }

Operations:

    setCue(time)
    jumpToCue()

Cue accuracy must be sample‑accurate.

------------------------------------------------------------------------

# 11. Waveform Alignment

Waveform rendering must align with beat grid.

Rendering pipeline:

    audio decode
     ↓
    downsample
     ↓
    amplitude envelope
     ↓
    waveform data

Beat markers overlayed visually on waveform.

------------------------------------------------------------------------

# 12. Visual Beat Markers

Displayed above waveform as vertical lines.

Spacing determined by:

    beatInterval = 60 / BPM

Markers scroll as track plays.

------------------------------------------------------------------------

# 13. Performance Targets

  Metric              Target
  ------------------- ----------
  Turntable latency   \< 10 ms
  Audio latency       \< 20 ms
  UI frame rate       60 FPS

------------------------------------------------------------------------

# 14. Precision Requirements

Cue and beat positions should have precision of:

    ≤ 1 ms

Preferred internal timing resolution:

    audio sample time

------------------------------------------------------------------------

# 15. Debug Visualization

Developer tools recommended:

Display:

    platter velocity
    beat grid overlay
    phase difference
    audio latency

------------------------------------------------------------------------

# 16. Integration with Audio Engine

System architecture:

    TurntablePhysics
          ↓
    DeckViewModel
          ↓
    AudioEngineManager
          ↓
    AVAudioEngine

DSP components:

    BeatGridAnalyzer
    BPMDetector
    WaveformAnalyzer

------------------------------------------------------------------------

# 17. Implementation Roadmap

1.  Implement platter physics
2.  Add scratch gesture handling
3.  Implement motor simulation
4.  Implement beat detection
5.  Generate beat grid
6.  Implement cue points
7.  Add beat phase alignment
8.  Render beat markers on waveform

------------------------------------------------------------------------

# End of DJ Turntable Physics & Beatgrid Specification
