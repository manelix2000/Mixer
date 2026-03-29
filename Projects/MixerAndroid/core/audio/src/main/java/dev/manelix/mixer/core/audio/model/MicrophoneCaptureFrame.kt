package dev.manelix.mixer.core.audio.model

data class MicrophoneCaptureFrame(
    val samples: FloatArray,
    val sampleRate: Double,
    val channelCount: Int = 1,
    val isInterleaved: Boolean = false,
)
