package dev.manelix.mixer.core.audio

import dev.manelix.mixer.core.audio.model.AudioPerformanceMetrics

interface AudioEnginePerformanceProvider {
    fun snapshotPerformanceMetrics(): AudioPerformanceMetrics
}
