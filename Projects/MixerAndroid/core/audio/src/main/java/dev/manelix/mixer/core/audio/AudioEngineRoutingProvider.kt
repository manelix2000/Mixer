package dev.manelix.mixer.core.audio

import dev.manelix.mixer.core.common.model.PanControlRange
import dev.manelix.mixer.core.common.model.SplitDeckRole

interface AudioEngineRoutingProvider {
    val splitDeckRole: SplitDeckRole?
    val panControlRange: PanControlRange

    fun setRoutingPolicy(
        role: SplitDeckRole?,
        panRange: PanControlRange,
    )
}
