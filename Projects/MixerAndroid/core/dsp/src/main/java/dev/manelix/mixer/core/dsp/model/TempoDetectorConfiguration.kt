package dev.manelix.mixer.core.dsp.model

data class TempoDetectorConfiguration(
    val method: String = "default",
    val windowSize: Int = 1024,
    val hopSize: Int = 512,
)
