package dev.manelix.mixer.core.waveform

import android.content.Context
import dev.manelix.mixer.core.waveform.model.WaveformProgress

interface WaveformAnalyzer {
    fun generateWaveform(
        sourceUri: String,
        sampleCount: Int = 512,
        appContext: Context? = null,
        onProgress: (WaveformProgress) -> Unit = {},
    ): FloatArray
}
