# dev.manelix.Mixer --- Traktor-Style Continuous Waveform Rendering Specification

## Overview

This document defines a **continuous waveform rendering model** for
`dev.manelix.Mixer` inspired by the visual style commonly associated
with modern DJ software such as Traktor-like horizontal scrolling
waveforms.

The goal is to move away from a rendering model based on **discrete
vertical sample lines** and instead render a waveform that feels:

-   continuous
-   dense
-   smooth
-   readable at multiple zoom levels
-   useful for cueing, phrase alignment, and beatmatching

This specification focuses on the **visual waveform renderer**, not BPM
detection or audio playback logic.

------------------------------------------------------------------------

# 1. Design Goal

The waveform should look like a **continuous audio shape**, not a bar
chart.

Current undesired look:

``` text
| | | | | | | | |
```

Target look:

``` text
~~~~~~~~continuous waveform shape~~~~~~~~
```

Desired properties:

-   contiguous silhouette
-   visually filled body
-   smooth interpolation between neighboring samples
-   clear center playhead
-   horizontal scrolling under a fixed playhead

------------------------------------------------------------------------

# 2. Rendering Model

The waveform should be rendered as a **continuous path**, not as
independent vertical strokes.

Recommended approach:

1.  decode audio to PCM
2.  downsample into display buckets
3.  compute min/max amplitude per bucket
4.  convert buckets into a continuous upper path
5.  mirror into a lower path
6.  close the shape
7.  fill and/or stroke the final waveform body

This produces a waveform that resembles a continuous ribbon or body.

------------------------------------------------------------------------

# 3. Data Model

Use a bucketed waveform model rather than raw PCM at render time.

Recommended structure:

``` swift
struct WaveformBucket {
    let minAmplitude: Float
    let maxAmplitude: Float
    let rms: Float?
    let time: TimeInterval
}
```

Recommended container:

``` swift
struct WaveformData {
    let duration: TimeInterval
    let sampleRate: Double
    let buckets: [WaveformBucket]
}
```

Notes:

-   `minAmplitude` and `maxAmplitude` are preferred over a single
    magnitude value
-   this preserves transient shape better than average amplitude only
-   RMS is optional for future coloring/intensity logic

------------------------------------------------------------------------

# 4. Why Min/Max Buckets Instead of Single Values

Rendering only one amplitude per x-coordinate tends to produce a thin,
jagged, or histogram-like waveform.

Using **min/max envelopes** gives a fuller shape:

-   captures peaks and troughs
-   preserves transient energy
-   looks more like Traktor/Rekordbox-style waveform bodies
-   remains stable at different zoom levels

For each visible column or logical bucket:

``` text
bucket → [minAmplitude, maxAmplitude]
```

------------------------------------------------------------------------

# 5. Continuous Path Construction

For the visible viewport:

1.  map each bucket to x-position
2.  map `maxAmplitude` to upper y
3.  map `minAmplitude` to lower y
4.  build a left-to-right upper contour
5.  build a right-to-left lower contour
6.  close the path

Pseudo shape:

``` text
upper contour →
                 close
lower contour ←
```

This creates a **single filled polygon**.

------------------------------------------------------------------------

# 6. Interpolation Strategy

To avoid a jagged "stepped" look, apply interpolation between
neighboring bucket points.

Recommended options:

## Option A --- Linear interpolation

Good first version.

Pros: - simple - stable - predictable

## Option B --- Quadratic smoothing

Better visual continuity.

Pros: - smoother curves - more premium visual feel

Cons: - slightly more complexity

Recommendation:

``` text
Start with linear interpolation for correctness.
Add quadratic smoothing once the renderer is stable.
```

Important:

-   smoothing must not overshoot excessively
-   waveform should remain visually faithful to the underlying signal
    envelope

------------------------------------------------------------------------

# 7. Filled Body Rendering

The waveform should be rendered primarily as a **filled shape**, not
just an outline.

Recommended style:

-   filled body with medium opacity
-   optional subtle stroke on top edge
-   centerline alignment around vertical midpoint

Visual model:

``` text
      upper envelope
████████████████████
────── center line ──────
████████████████████
      lower envelope
```

This gives the "continuous Traktor-style body" feeling.

------------------------------------------------------------------------

# 8. Centered Playhead Layout

The waveform should scroll horizontally beneath a **fixed playhead**.

Layout:

``` text
waveform scrolling → 
──────────▲──────────
          playhead
```

The playhead remains fixed at:

``` text
centerX = viewWidth / 2
```

The waveform renderer maps visible time around the current playback
time.

This is essential for:

-   beatmatching
-   cue placement
-   scratch feedback
-   phrase alignment

------------------------------------------------------------------------

# 9. Viewport-Based Rendering

Only render the waveform buckets visible in the current viewport.

Formula:

``` text
visibleStartTime = currentTime - viewportDuration / 2
visibleEndTime   = currentTime + viewportDuration / 2
```

Then filter buckets:

``` text
visibleBuckets = buckets where time ∈ [visibleStartTime, visibleEndTime]
```

Benefits:

-   lower render cost
-   smoother UI
-   easier zoom support

------------------------------------------------------------------------

# 10. Zoom Model

Zoom changes **time density**, not waveform source data.

At close zoom:

-   fewer buckets visible
-   more detail per beat/bar

At far zoom:

-   more buckets visible
-   more aggressive aggregation may be needed

Recommended zoom levels:

  Zoom Level   Visible Musical Range
  ------------ -----------------------
  Close        1--2 bars
  Medium       4--8 bars
  Far          16--32 bars
  Very Far     64+ bars

Important:

-   do not reuse the exact same bucket resolution for every zoom
-   use level-of-detail strategy if needed

------------------------------------------------------------------------

# 11. Multi-Resolution Waveform Data

For better performance and visual quality, support **multi-resolution
waveform pyramids**.

Recommended levels:

``` text
LOD0 = finest detail
LOD1 = 2x aggregated
LOD2 = 4x aggregated
LOD3 = 8x aggregated
...
```

Select resolution based on zoom.

Benefits:

-   avoids aliasing
-   avoids overly dense draw operations
-   preserves readable structure at far zoom

------------------------------------------------------------------------

# 12. Channel Strategy

For the first version, use a **mono downmix** for waveform rendering.

Recommended downmix:

``` text
mono = 0.5 * (left + right)
```

Why:

-   simpler
-   visually stable
-   good enough for DJ navigation

Future options:

-   split stereo lanes
-   stereo width coloring
-   per-channel overlays

------------------------------------------------------------------------

# 13. Vertical Scaling

Amplitude should be scaled to fit the waveform view cleanly.

Recommended mapping:

``` text
y = centerY ± amplitude * verticalScale
```

Guidelines:

-   keep headroom near top and bottom edges
-   avoid clipping the waveform body
-   normalize consistently across the track

Possible normalization strategies:

## Global normalization

Normalize to the loudest bucket in the track.

Pros: - stable visual scale

Cons: - quiet passages may look too small

## Window normalization

Normalize per visible window.

Pros: - always readable

Cons: - waveform "breathes" while scrolling

Recommendation:

``` text
Use global normalization for the first version.
Optionally add soft dynamic gain later.
```

------------------------------------------------------------------------

# 14. Anti-Aliasing and Pixel Alignment

To achieve a premium look:

-   enable anti-aliased path drawing
-   align coordinates sensibly to pixel boundaries where needed
-   avoid drawing one path segment per raw sample
-   prefer bucketed path generation

Canvas should draw a small number of continuous shapes, not thousands of
tiny independent lines.

------------------------------------------------------------------------

# 15. Color and Styling

Recommended first style:

-   filled waveform body in a single accent or neutral color
-   slightly brighter area near the playhead optional
-   subtle contrast between played and upcoming regions optional

Possible styling modes:

## Neutral monochrome

Best for MVP.

## Played vs upcoming split

Useful for navigation.

## Energy-colored waveform

Future enhancement.

For a Traktor-like feel, the shape matters more than aggressive coloring
in the first version.

------------------------------------------------------------------------

# 16. Played/Upcoming Segmentation

Optional but recommended:

Split the waveform visually at the playhead.

Example:

-   left of playhead: dimmed / "played"
-   right of playhead: brighter / "upcoming"

This helps orientation during playback and scratching.

Implementation options:

-   clip the waveform fill into two regions
-   apply different opacity or color per region

------------------------------------------------------------------------

# 17. Beat, Bar, and Phrase Overlay Compatibility

The continuous waveform renderer must support overlays without degrading
readability.

Overlays:

-   beat markers
-   bar markers
-   phrase markers
-   cue markers
-   loop regions

Rules:

-   waveform body is the base layer
-   beat/bar/phrase markers are drawn above waveform
-   markers should not fragment the waveform silhouette
-   phrase blocks should use subtle backgrounds, not obscure waveform
    detail

------------------------------------------------------------------------

# 18. Performance Strategy

Rendering must stay smooth at 60 FPS.

Recommended strategy:

-   precompute waveform buckets offline on track load
-   keep rendering data immutable during playback
-   draw only visible buckets
-   use `SwiftUI.Canvas`
-   batch into one or a few paths
-   avoid per-bucket SwiftUI subviews

Do not:

-   decode audio on every frame
-   compute amplitude in the render loop
-   allocate large arrays every redraw

------------------------------------------------------------------------

# 19. Suggested Rendering Pipeline

``` text
audio file
 ↓
decode PCM
 ↓
downmix mono
 ↓
bucket into min/max envelope
 ↓
build multi-resolution waveform data
 ↓
store WaveformData
 ↓
on render:
    choose visible range
    choose LOD
    build continuous path
    fill waveform body
    draw overlays
```

------------------------------------------------------------------------

# 20. Scratch and Seek Behavior

The waveform must remain visually stable during:

-   scratching
-   rapid scrubbing
-   jumps to cue points
-   loop in/out edits

Requirements:

-   playhead remains fixed
-   waveform instantly repositions to new current time
-   no delayed "catch-up" animation after hard seeks unless explicitly
    designed
-   overlays remain time-accurate

------------------------------------------------------------------------

# 21. Edge Cases

Handle:

-   very quiet intros/outros
-   clipped/loud masters
-   sparse breakdowns
-   very long tracks
-   high zoom and very low zoom extremes

Mitigations:

-   minimum visible amplitude floor for extremely quiet sections
-   global normalization cap
-   LOD switching for long tracks
-   bucket count clamping per viewport

------------------------------------------------------------------------

# 22. Architecture Recommendation

Suggested rendering components:

``` text
WaveformAnalyzer
 ↓
WaveformData
 ↓
WaveformRenderer
 ↓
Canvas
```

Possible Swift structure split:

  Component                Responsibility
  ------------------------ --------------------------------------------
  WaveformAnalyzer         decode + bucket generation
  WaveformLODBuilder       multi-resolution aggregation
  WaveformViewportMapper   time-to-screen mapping
  WaveformRenderer         builds continuous drawing paths
  WaveformView             SwiftUI container and playhead composition

This keeps analysis separate from drawing.

------------------------------------------------------------------------

# 23. MVP Implementation Order

Recommended order:

1.  min/max bucket generation
2.  mono waveform body
3.  continuous filled path rendering
4.  fixed playhead scrolling
5.  zoom support
6.  multi-resolution LOD
7.  played/upcoming styling
8.  beat/bar/phrase overlays

------------------------------------------------------------------------

# 24. Definition of Done

The renderer is considered successful when:

-   the waveform is visually continuous, not a field of separate
    vertical lines
-   playback scrolls smoothly under a fixed playhead
-   waveform remains readable at multiple zoom levels
-   rendering remains stable at 60 FPS on target devices
-   scratch and seek updates remain visually coherent
-   beat/bar/phrase overlays can be added without breaking readability

------------------------------------------------------------------------

# End of Traktor-Style Continuous Waveform Rendering Specification
