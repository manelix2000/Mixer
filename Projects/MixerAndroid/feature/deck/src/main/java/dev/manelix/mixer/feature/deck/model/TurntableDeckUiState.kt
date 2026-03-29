package dev.manelix.mixer.feature.deck.model

import dev.manelix.mixer.core.common.model.AudioPlaybackState
import dev.manelix.mixer.core.common.model.PanControlRange
import dev.manelix.mixer.core.common.model.SplitDeckRole

enum class ScratchInteractionState {
    IDLE,
    TOUCH_DOWN,
    DRAGGING,
    RELEASE,
}

data class TurntableDeckUiState(
    val bpmText: String = "-- BPM",
    val waveformText: String = "Waveform Placeholder",
    val platterText: String = "Platter Placeholder",
    val selectedTrackUri: String? = null,
    val selectedTrackName: String? = null,
    val playbackStatusText: String = "",
    val playbackState: AudioPlaybackState = AudioPlaybackState.IDLE,
    val playbackTimeText: String = "00:00 / 00:00",
    val playbackProgress: Double = 0.0,
    val waveformZoom: Double = 1.0,
    val waveformData: FloatArray = floatArrayOf(),
    val isWaveformLoading: Boolean = false,
    val isBpmLoading: Boolean = false,
    val bpmDetectionStatusText: String? = null,
    val originalBpm: Double = 0.0,
    val targetBpm: Double = 0.0,
    val pitchSensitivityPercent: Int = 8,
    val isPitchLockedToExternalBpm: Boolean = false,
    val platterRotationDegrees: Double = 0.0,
    val scratchInteractionState: ScratchInteractionState = ScratchInteractionState.IDLE,
    val volume: Double = 1.0,
    val pan: Double = 0.0,
    val panControlRange: PanControlRange = PanControlRange.Standard,
    val splitDeckRole: SplitDeckRole? = null,
    val equalizerLow: Double = 0.5,
    val equalizerMid: Double = 0.5,
    val equalizerHigh: Double = 0.5,
)

val TurntableDeckUiState.hasSelectedTrack: Boolean
    get() = !selectedTrackUri.isNullOrBlank()

val TurntableDeckUiState.isPlaybackActive: Boolean
    get() = playbackState == AudioPlaybackState.PLAYING
