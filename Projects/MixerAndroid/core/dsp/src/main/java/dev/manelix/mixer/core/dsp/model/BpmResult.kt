package dev.manelix.mixer.core.dsp.model

sealed interface BpmResult {
    data class Detected(
        val bpm: Double,
        val confidence: Double,
    ) : BpmResult

    data class Unavailable(
        val reason: String,
    ) : BpmResult
}
