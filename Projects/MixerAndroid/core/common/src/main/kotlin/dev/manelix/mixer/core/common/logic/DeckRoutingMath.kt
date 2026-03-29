package dev.manelix.mixer.core.common.logic

import dev.manelix.mixer.core.common.model.CueMixMode
import dev.manelix.mixer.core.common.model.PanControlRange
import dev.manelix.mixer.core.common.model.SplitDeckRole

object DeckRoutingMath {
    fun cueFactors(
        mode: CueMixMode,
        cueLevelPercent: Int,
    ): CueFactors {
        val cueLevel = (cueLevelPercent.coerceIn(0, 100) / 100.0)
        return when (mode) {
            CueMixMode.CUE -> CueFactors(masterFactor = 0.0, cueFactor = cueLevel)
            CueMixMode.BLEND -> CueFactors(masterFactor = 1.0, cueFactor = cueLevel)
            CueMixMode.MASTER -> CueFactors(masterFactor = 1.0, cueFactor = 0.0)
        }
    }

    fun splitDefaultPan(role: SplitDeckRole): Double =
        when (role) {
            SplitDeckRole.MASTER -> -1.0
            SplitDeckRole.CUE -> 1.0
        }

    fun panRoutingText(pan: Double): String =
        when {
            pan < -0.1 -> "L"
            pan > 0.1 -> "R"
            else -> "C"
        }

    fun panRangeForRole(role: SplitDeckRole?): PanControlRange =
        when (role) {
            SplitDeckRole.MASTER -> PanControlRange.MasterSplit
            SplitDeckRole.CUE -> PanControlRange.CueSplit
            null -> PanControlRange.Standard
        }
}

data class CueFactors(
    val masterFactor: Double,
    val cueFactor: Double,
)
