package dev.manelix.mixer.core.common.logic

import dev.manelix.mixer.core.common.model.CueMixMode
import dev.manelix.mixer.core.common.model.SplitDeckRole
import kotlin.test.Test
import kotlin.test.assertEquals

class DeckRoutingMathTest {
    @Test
    fun cueModeFactors_matchIosBehavior() {
        val result = DeckRoutingMath.cueFactors(mode = CueMixMode.CUE, cueLevelPercent = 80)

        assertEquals(expected = 0.0, actual = result.masterFactor)
        assertEquals(expected = 0.8, actual = result.cueFactor)
    }

    @Test
    fun blendModeFactors_matchIosBehavior() {
        val result = DeckRoutingMath.cueFactors(mode = CueMixMode.BLEND, cueLevelPercent = 65)

        assertEquals(expected = 1.0, actual = result.masterFactor)
        assertEquals(expected = 0.65, actual = result.cueFactor)
    }

    @Test
    fun masterModeFactors_matchIosBehavior() {
        val result = DeckRoutingMath.cueFactors(mode = CueMixMode.MASTER, cueLevelPercent = 90)

        assertEquals(expected = 1.0, actual = result.masterFactor)
        assertEquals(expected = 0.0, actual = result.cueFactor)
    }

    @Test
    fun panRoutingText_thresholdsMatchIosBehavior() {
        assertEquals(expected = "L", actual = DeckRoutingMath.panRoutingText(-0.11))
        assertEquals(expected = "C", actual = DeckRoutingMath.panRoutingText(0.00))
        assertEquals(expected = "R", actual = DeckRoutingMath.panRoutingText(0.11))
    }

    @Test
    fun splitDefaultPan_matchesIosRoles() {
        assertEquals(expected = -1.0, actual = DeckRoutingMath.splitDefaultPan(SplitDeckRole.MASTER))
        assertEquals(expected = 1.0, actual = DeckRoutingMath.splitDefaultPan(SplitDeckRole.CUE))
    }
}
