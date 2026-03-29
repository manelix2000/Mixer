package dev.manelix.mixer.core.common.model

enum class AudioEngineMode {
    STANDARD,
    SPLIT,
}

enum class SplitDeckLayout {
    LEFT_MASTER_RIGHT_CUE,
    LEFT_CUE_RIGHT_MASTER,
}

enum class SplitDeckRole {
    MASTER,
    CUE,
}

enum class CueMixMode(val shortCode: String) {
    CUE("C"),
    BLEND("B"),
    MASTER("M"),
}

enum class AudioPlaybackState {
    IDLE,
    FILE_LOADED,
    PLAYING,
    PAUSED,
}

data class PanControlRange(
    val lowerBound: Double,
    val upperBound: Double,
) {
    init {
        require(lowerBound <= upperBound) { "lowerBound must be <= upperBound" }
    }

    fun clamp(value: Double): Double = value.coerceIn(lowerBound, upperBound)

    companion object {
        val Standard = PanControlRange(lowerBound = -1.0, upperBound = 1.0)
        val MasterSplit = PanControlRange(lowerBound = -1.0, upperBound = 0.0)
        val CueSplit = PanControlRange(lowerBound = 0.0, upperBound = 1.0)
    }
}
