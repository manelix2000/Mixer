package dev.manelix.mixer.core.audio

import android.content.Context

@Deprecated(
    message = "Use MediaPlayerAudioEngineController for runtime audio playback.",
    replaceWith = ReplaceWith("MediaPlayerAudioEngineController(appContext, clock, defaultDurationSeconds)"),
)
class SkeletonAudioEngineController(
    appContext: Context? = null,
    clock: MonotonicClock = SystemMonotonicClock,
    defaultDurationSeconds: Double = 180.0,
    private val delegate: MediaPlayerAudioEngineController = MediaPlayerAudioEngineController(
        appContext = appContext,
        clock = clock,
        defaultDurationSeconds = defaultDurationSeconds,
    ),
) : AudioEngineController by delegate,
    AudioEngineRoutingProvider by delegate,
    AudioEnginePerformanceProvider by delegate
