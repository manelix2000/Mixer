package dev.manelix.mixer.core.waveform

import dev.manelix.mixer.core.waveform.model.WaveformProgress

interface WaveformAnalyzer {
    fun generateWaveform(
        sourceUri: String,
        sampleCount: Int = 512,
        onProgress: (WaveformProgress) -> Unit = {},
    ): FloatArray
}
