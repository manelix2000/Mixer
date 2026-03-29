package dev.manelix.mixer.core.dsp.model

data class TempoInputBuffer(
    val samples: FloatArray,
    val sampleRate: Double,
    val channelCount: Int = 1,
    val isInterleaved: Boolean = false,
)
