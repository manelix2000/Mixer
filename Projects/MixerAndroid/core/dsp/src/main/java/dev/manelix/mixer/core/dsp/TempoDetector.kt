package dev.manelix.mixer.core.dsp

import dev.manelix.mixer.core.dsp.model.BpmResult
import dev.manelix.mixer.core.dsp.model.TempoInputBuffer

interface TempoDetector {
    fun detectTempo(input: TempoInputBuffer): BpmResult
}
