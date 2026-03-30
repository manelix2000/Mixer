package dev.manelix.mixer.core.audio.model

data class AudioPerformanceMetrics(
    val micFramesProcessed: Long = 0L,
    val micCallbacksOverBudget: Long = 0L,
    val micCallbackAverageMillis: Double = 0.0,
    val micCallbackMaxMillis: Double = 0.0,
    val micCallbackLastMillis: Double = 0.0,
)
