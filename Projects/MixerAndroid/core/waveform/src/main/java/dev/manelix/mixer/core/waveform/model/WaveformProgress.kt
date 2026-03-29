package dev.manelix.mixer.core.waveform.model

data class WaveformProgress(
    val samples: FloatArray,
    val completedBuckets: Int,
    val totalBuckets: Int,
) {
    val fraction: Double
        get() {
            if (totalBuckets <= 0) return 0.0
            return (completedBuckets.toDouble() / totalBuckets.toDouble()).coerceIn(0.0, 1.0)
        }
}
