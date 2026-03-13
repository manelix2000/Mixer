# dev.manelix.Mixer --- BeatGrid & PhraseMarker Rendering Specification

## Overview

This document defines how **beats, bars, and phrases** are visually
rendered on the waveform in `dev.manelix.Mixer`.

The visualization must support DJ workflows:

-   beat matching
-   phrase alignment
-   loop placement
-   cue positioning

The rendering system must remain lightweight and maintain **60 FPS UI
performance**.

------------------------------------------------------------------------

# 1. Visual Hierarchy

Rhythmic markers have three levels:

  Level    Unit                Visual Importance
  -------- ------------------- -------------------
  Beat     1 beat              lowest
  Bar      4 beats             medium
  Phrase   16 bars (typical)   highest

This hierarchy helps DJs understand track structure instantly.

------------------------------------------------------------------------

# 2. Beat Markers

Beat markers represent each beat detected in the track.

Spacing is derived from the beat grid.

Example:

    | •   •   •   • | •   •   •   • |

Rendering rules:

-   small vertical tick
-   low visual opacity
-   evenly spaced

Data source:

    BeatGrid.beats

Example model:

    struct BeatMarker {
        time: TimeInterval
    }

------------------------------------------------------------------------

# 3. Bar Markers

Bar markers represent the **start of a bar**.

Every 4 beats:

    1 2 3 4 | 1 2 3 4 |
    ↑       ↑
    bar start

Rendering rules:

-   thicker vertical line
-   slightly taller than beat markers
-   stronger contrast

Example:

    | |   •   •   • | |   •   •   • |

Model:

    struct BarMarker {
        startBeatIndex: Int
    }

------------------------------------------------------------------------

# 4. Phrase Markers

Phrase markers represent **musical sections**.

Typical phrase lengths in techno:

  Bars   Meaning
  ------ -----------------
  8      short phrase
  16     standard phrase
  32     section

Example:

    | phrase start
    ↓
    | |   •   •   • | |   •   •   • |
    | |   •   •   • | |   •   •   • |

Rendering rules:

-   very tall line
-   high contrast color
-   optionally labeled

Example label:

    PHRASE
    DROP
    BREAK

Model:

    struct PhraseMarker {
        startBar: Int
        lengthBars: Int
    }

------------------------------------------------------------------------

# 5. Waveform Position Mapping

Markers must map time → x position.

Formula:

    x = (markerTime - viewportStartTime) / viewportDuration * viewWidth

Where:

    viewportStartTime = visible waveform start
    viewportDuration = visible time window
    viewWidth = waveform view width

Markers outside viewport are not rendered.

------------------------------------------------------------------------

# 6. Scrolling Model

The waveform should scroll under a **fixed playhead**.

Layout:

    waveform scrolling
     ↓

    ──────────▲──────────
              playhead

Benefits:

-   easier beat alignment
-   common DJ UI pattern
-   stable cue point reference

Playhead position:

    centerX = viewWidth / 2

------------------------------------------------------------------------

# 7. Zoom Behavior

Zoom affects **time scale**, not marker data.

Zoom in:

    1 bar fills more pixels

Zoom out:

    more bars visible

Zoom limits:

  Level    Visible Time
  -------- --------------
  close    1--2 bars
  medium   8 bars
  far      32--64 bars

Zoom should preserve:

    playhead alignment
    marker spacing

------------------------------------------------------------------------

# 8. Performance Strategy

Rendering must remain efficient.

Rules:

-   draw markers inside `SwiftUI.Canvas`
-   avoid creating individual SwiftUI views per marker
-   batch drawing commands
-   only draw markers inside viewport

Pseudo pipeline:

    visible markers
     ↓
    Canvas draw pass
     ↓
    lines drawn in batch

------------------------------------------------------------------------

# 9. Marker Colors (Suggested)

  Marker   Color
  -------- --------------
  Beat     light gray
  Bar      white
  Phrase   accent color

Phrase markers may use:

    blue
    orange
    purple

depending on section type.

------------------------------------------------------------------------

# 10. Phrase Highlight Regions

Optionally highlight entire phrase blocks.

Example:

    [ phrase block ]
    | |   •   •   • | |   •   •   • |

Rendering:

-   subtle background tint
-   low opacity

This improves phrase visibility when zoomed out.

------------------------------------------------------------------------

# 11. Cue Point Markers

Cue points should also appear in the waveform.

Example:

    ▲ cue
    | |   •   •   • | |   •   •   • |

Rules:

-   triangular marker
-   high contrast color
-   anchored to playhead alignment

Model:

    struct CueMarker {
        time: TimeInterval
    }

------------------------------------------------------------------------

# 12. Loop Region Visualization

Loop regions should highlight selected bars.

Example:

    [ LOOP ]
    | |   •   •   • | |   •   •   • |

Rendering:

-   colored background band
-   loop start and end markers

Loop lengths typically:

    4 bars
    8 bars
    16 bars

------------------------------------------------------------------------

# 13. Interaction Feedback

During scratching or scrubbing:

-   playhead remains fixed
-   waveform moves
-   markers stay aligned

Visual feedback:

-   playhead glow
-   slight marker fade during fast scrubbing

------------------------------------------------------------------------

# 14. Debug Mode

Developer mode may render additional overlays:

    beat index
    bar index
    phrase index
    BPM value

Example:

    Bar 64
    Phrase 4

This helps validate beat detection accuracy.

------------------------------------------------------------------------

# 15. Rendering Architecture

Component structure:

    WaveformView
     ↓
    BeatGridRenderer
     ↓
    PhraseRenderer
     ↓
    Canvas draw

Separation of concerns:

  Component          Role
  ------------------ ------------------
  WaveformView       container
  BeatGridRenderer   beat/bar markers
  PhraseRenderer     phrase blocks
  CueRenderer        cue markers

------------------------------------------------------------------------

# Implementation Priorities

Recommended order:

1.  Beat markers
2.  Bar markers
3.  Phrase markers
4.  Cue markers
5.  Loop regions
6.  Phrase highlighting

------------------------------------------------------------------------

# End of BeatGrid & PhraseMarker Rendering Specification
