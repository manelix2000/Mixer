package dev.manelix.mixer.feature.deck.model

import dev.manelix.mixer.core.common.model.AudioEngineMode
import dev.manelix.mixer.core.common.model.CueMixMode
import dev.manelix.mixer.core.common.model.SplitDeckLayout

data class DeckUiState(
    val volume: Double = 0.8,
    val pan: Double = 0.0,
    val externalBpmText: String = "-- BPM",
    val externalBpmStatusText: String = "Mic BPM stopped",
    val isExternalBpmLoading: Boolean = false,
    val isMicrophoneBpmDetectionActive: Boolean = false,
    val isPitchLockedToExternalBpm: Boolean = false,
    val isLeftDeckCueEnabled: Boolean = true,
    val isRightDeckCueEnabled: Boolean = true,
    val cueMixMode: CueMixMode = CueMixMode.BLEND,
    val cueLevelPercent: Int = 80,
    val selectedAudioEngineMode: AudioEngineMode = AudioEngineMode.STANDARD,
    val selectedSplitDeckLayout: SplitDeckLayout = SplitDeckLayout.LEFT_MASTER_RIGHT_CUE,
)
