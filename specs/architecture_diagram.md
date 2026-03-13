# dev.manelix.Mixer --- DJ Audio Engine Architecture Diagram

## Overview

This document summarizes the **complete system architecture** for the
dev.manelix.Mixer iOS DJ application.

The system supports:

-   vinyl-style playback
-   scratching interaction
-   BPM synchronization
-   waveform visualization
-   microphone BPM detection
-   low-latency audio processing

Core technologies:

-   Swift 6
-   SwiftUI
-   Tuist
-   AVFoundation / AVAudioEngine
-   aubio DSP library

------------------------------------------------------------------------

# High-Level Architecture

    UI Layer
     ├─ DeckView
     ├─ TurntableView
     ├─ WaveformView
     └─ ControlPanel
            ↓
    ViewModel Layer
     └─ DeckViewModel
            ↓
    Audio Engine Layer
     ├─ AudioEngineManager
     ├─ DeckPlayer
     └─ RingBuffer
            ↓
    DSP Layer
     ├─ BPMDetector (aubio)
     ├─ BeatGridAnalyzer
     └─ WaveformAnalyzer
            ↓
    AVFoundation Graph
     ├─ AVAudioPlayerNode
     ├─ AVAudioMixerNode
     └─ OutputNode

------------------------------------------------------------------------

# Audio Graph

    AVAudioEngine
       │
       ├── AVAudioPlayerNode
       │        │
       │        └── ring buffer audio stream
       │
       ├── AVAudioMixerNode
       │        │
       │        └── stereo pan routing
       │
       └── OutputNode
                │
                └── headphones / speakers

------------------------------------------------------------------------

# Gesture → Audio Pipeline

    touch input
     ↓
    gesture recognizer
     ↓
    TurntablePhysics
     ↓
    DeckViewModel
     ↓
    DeckPlayer
     ↓
    audio playback position

Latency goal:

    < 10 ms

------------------------------------------------------------------------

# BPM Detection

## Track BPM

    audio file
     ↓
    PCM decode
     ↓
    mono downmix
     ↓
    aubio tempo detection
     ↓
    BPM estimate

## External BPM

    microphone input
     ↓
    audio buffer
     ↓
    aubio tempo detection
     ↓
    external BPM

------------------------------------------------------------------------

# Waveform Pipeline

    audio file
     ↓
    decode
     ↓
    downsample
     ↓
    amplitude envelope
     ↓
    waveform samples

Rendered using:

    SwiftUI Canvas

------------------------------------------------------------------------

# Threading Model

    UI Thread
     ├─ gestures
     └─ rendering

    Audio Thread
     ├─ AVAudioEngine
     └─ playback

    DSP Worker
     ├─ BPM detection
     ├─ beat grid analysis
     └─ waveform analysis

------------------------------------------------------------------------

# Data Flow

    audio file
     ↓
    decode
     ↓
    ring buffer
     ↓
    AVAudioPlayerNode
     ↓
    AVAudioMixerNode
     ↓
    output device

Parallel DSP:

    PCM samples
     ↓
    aubio DSP
     ↓
    BPM detection
     ↓
    UI update

------------------------------------------------------------------------

# Performance Targets

  Metric             Target
  ------------------ ---------
  Audio latency      \<20 ms
  Scratch response   \<10 ms
  UI frame rate      60 FPS

------------------------------------------------------------------------

# Core Design Rules

1.  Never block the audio thread
2.  Avoid allocations in real-time path
3.  Precompute heavy DSP
4.  Isolate aubio in DSP module
5.  Keep UI independent from audio timing

------------------------------------------------------------------------

# Full System Flow

    User Gesture
     ↓
    Turntable Physics
     ↓
    DeckViewModel
     ↓
    AudioEngineManager
     ↓
    AVAudioPlayerNode
     ↓
    AVAudioMixerNode
     ↓
    Output Audio

Parallel DSP:

    PCM samples
     ↓
    aubio
     ↓
    BPM detection
     ↓
    UI update

------------------------------------------------------------------------

# End of Architecture Diagram
